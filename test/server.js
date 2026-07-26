import express from 'express'
import { MongoClient } from 'mongodb'
import { execSync } from 'child_process'

const MONGO_URI = process.env.MONGO_URI || 'mongodb://localhost:27017'
const SHARD1_URI = process.env.SHARD1_URI || 'mongodb://localhost:27020/?directConnection=true'
const SHARD2_URI = process.env.SHARD2_URI || 'mongodb://localhost:27021/?directConnection=true'

const app = express()
app.use(express.json())
app.use(express.static('public'))

let mongos, shard1, shard2

async function connect() {
  mongos = await MongoClient.connect(MONGO_URI)
  shard1 = await MongoClient.connect(SHARD1_URI)
  shard2 = await MongoClient.connect(SHARD2_URI)
  console.log('Connected to MongoDB cluster')
}

app.get('/api/students', async (req, res) => {
  const docs = await mongos.db('College').collection('Student').find().sort({ RollNo: 1 }).toArray()

  const s1 = await shard1.db('College').collection('Student').find({}, { projection: { RollNo: 1 } }).toArray()
  const s2 = await shard2.db('College').collection('Student').find({}, { projection: { RollNo: 1 } }).toArray()
  const s1set = new Set(s1.map(d => d.RollNo))
  const s2set = new Set(s2.map(d => d.RollNo))

  const students = docs.map(d => ({
    RollNo: d.RollNo,
    Name: d.Name,
    Dept: d.Dept,
    Year: d.Year,
    shard: s1set.has(d.RollNo) && s2set.has(d.RollNo) ? 'both'
      : s1set.has(d.RollNo) ? 'shard1'
      : s2set.has(d.RollNo) ? 'shard2'
      : 'unknown'
  }))

  res.json(students)
})

app.get('/api/distribution', async (req, res) => {
  const s1 = await shard1.db('College').collection('Student').countDocuments()
  const s2 = await shard2.db('College').collection('Student').countDocuments()
  const total = await mongos.db('College').collection('Student').countDocuments()
  res.json({ shard1: s1, shard2: s2, total })
})

app.post('/api/student', async (req, res) => {
  const { RollNo, Name, Dept, Year } = req.body
  if (!RollNo || !Name) return res.status(400).json({ error: 'RollNo and Name required' })

  await mongos.db('College').collection('Student').insertOne({ RollNo, Name, Dept, Year })

  await new Promise(r => setTimeout(r, 500))

  const onShard1 = await shard1.db('College').collection('Student').findOne({ RollNo })
  const onShard2 = await shard2.db('College').collection('Student').findOne({ RollNo })

  const landed = onShard1 && onShard2 ? 'both'
    : onShard1 ? 'shard1'
    : onShard2 ? 'shard2'
    : 'unknown'

  res.json({ RollNo, landed })
})

app.post('/api/reset', async (req, res) => {
  await shard1.db('College').collection('Student').deleteMany({})
  await shard2.db('College').collection('Student').deleteMany({})
  await mongos.db('College').collection('Student').deleteMany({})
  res.json({ ok: true })
})

app.post('/api/split', async (req, res) => {
  const { at } = req.body
  const splitAt = at || 1010
  try {
    const splitCmd = `mongosh mongodb://localhost:27017/College --quiet --eval "sh.splitAt('College.Student', { RollNo: ${splitAt} })" 2>/dev/null`
    const moveCmd = `mongosh mongodb://localhost:27017/College --quiet --eval "sh.moveChunk('College.Student', { RollNo: ${splitAt} }, 'shard2rs')" 2>/dev/null`
    execSync(splitCmd, { timeout: 10000, shell: '/bin/bash' })
    execSync(moveCmd, { timeout: 30000, shell: '/bin/bash' })
    await new Promise(r => setTimeout(r, 2000))
    await shard1.db('College').collection('Student').deleteMany({ RollNo: { $gte: splitAt } })
    res.json({ ok: true, splitAt })
  } catch (e) {
    res.status(400).json({ error: e.stderr?.toString() || e.message })
  }
})

await connect()
app.listen(3000, () => console.log('Shard Visualizer: http://localhost:3000'))