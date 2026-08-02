import express from 'express'
import { MongoClient } from 'mongodb'

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
  console.log('Connected')
}
await connect()

app.get('/api/students', async (req, res) => {
  const unified = await mongos.db('College').collection('Student').find().sort({ RollNo: 1 }).toArray()

  const s1docs = await shard1.db('College').collection('Student').find({}, { projection: { RollNo: 1 } }).toArray()
  const s2docs = await shard2.db('College').collection('Student').find({}, { projection: { RollNo: 1 } }).toArray()
  const s1set = new Set(s1docs.map(d => d.RollNo))
  const s2set = new Set(s2docs.map(d => d.RollNo))

  const students = unified.map(d => ({
    RollNo: d.RollNo, Name: d.Name, Dept: d.Dept, Year: d.Year,
    shard: s1set.has(d.RollNo) && s2set.has(d.RollNo) ? 'both'
      : s1set.has(d.RollNo) ? 'shard1' : 'shard2'
  }))

  res.json(students)
})

app.get('/api/shard-data', async (req, res) => {
  const [s1, s2] = await Promise.all([
    shard1.db('College').collection('Student').find().sort({ RollNo: 1 }).toArray(),
    shard2.db('College').collection('Student').find().sort({ RollNo: 1 }).toArray()
  ])
  res.json({ shard1: s1.map(d => ({ RollNo: d.RollNo, Name: d.Name })), shard2: s2.map(d => ({ RollNo: d.RollNo, Name: d.Name })) })
})

app.post('/api/student', async (req, res) => {
  const { RollNo, Name, Dept, Year } = req.body
  await mongos.db('College').collection('Student').insertOne({ RollNo, Name, Dept, Year })
  await new Promise(r => setTimeout(r, 800))
  const on1 = await shard1.db('College').collection('Student').findOne({ RollNo })
  const on2 = await shard2.db('College').collection('Student').findOne({ RollNo })
  const landed = on1 && on2 ? 'both' : on1 ? 'shard1' : on2 ? 'shard2' : 'unknown'
  res.json({ RollNo, landed })
})

app.post('/api/split', async (req, res) => {
  const { at } = req.body
  const splitAt = at || 2000
  try {
    const s1count = await shard1.db('College').collection('Student').countDocuments()
    const s2count = await shard2.db('College').collection('Student').countDocuments()
    if (s2count === 0) {
      await mongos.db('College').command({ split: 'College.Student', middle: { RollNo: splitAt } })
      await new Promise(r => setTimeout(r, 1000))
      await mongos.db('College').command({ moveChunk: 'College.Student', find: { RollNo: splitAt }, to: 'shard2rs', _waitForDelete: true })
      await new Promise(r => setTimeout(r, 2000))
      await shard1.db('College').collection('Student').deleteMany({ RollNo: { $gte: splitAt } })
    }
    const n1 = await shard1.db('College').collection('Student').countDocuments()
    const n2 = await shard2.db('College').collection('Student').countDocuments()
    res.json({ ok: true, shard1: n1, shard2: n2 })
  } catch (e) {
    res.status(400).json({ error: e.message })
  }
})

app.post('/api/seed', async (req, res) => {
  const count = Math.min(req.body?.count || 100, 500)
  const half = Math.ceil(count / 2)

  const docs = Array.from({ length: count }, (_, i) => ({
    RollNo: i < half ? 50000 + i : 200000 + (i - half),
    Name: `Student${i + 1}`,
    Dept: ['CS','EC','ME','EE'][i % 4],
    Year: (i % 4) + 1
  }))

  const rnos = docs.map(d => d.RollNo)
  await Promise.all([
    mongos.db('College').collection('Student').deleteMany({ RollNo: { $in: rnos } }),
    shard1.db('College').collection('Student').deleteMany({ RollNo: { $in: rnos } }),
    shard2.db('College').collection('Student').deleteMany({ RollNo: { $in: rnos } })
  ])
  await new Promise(r => setTimeout(r, 500))

  try {
    await mongos.db('College').collection('Student').insertMany(docs, { ordered: false })
  } catch (e) {
    // some may have been inserted despite errors
  }

  const s1 = await shard1.db('College').collection('Student').countDocuments({ RollNo: { $gte: 50000, $lt: 50000 + half } })
  const s2 = await shard2.db('College').collection('Student').countDocuments({ RollNo: { $gte: 200000, $lt: 200000 + count - half } })
  res.json({ count, inserted: docs.length, shard1Actual: s1, shard2Actual: s2 })
})

app.post('/api/reset', async (req, res) => {
  await Promise.all([
    shard1.db('College').collection('Student').deleteMany({}),
    shard2.db('College').collection('Student').deleteMany({}),
    mongos.db('College').collection('Student').deleteMany({})
  ])
  res.json({ ok: true })
})

app.get('/api/benchmark', async (req, res) => {
  const results = []
  for (const label of ['targeted (RollNo)', 'scatter (Name)']) {
    const isTargeted = label.includes('RollNo')
    const query = isTargeted ? { RollNo: 1005 } : { Name: 'Eve' }
    const times = []
    for (let i = 0; i < 5; i++) {
      const start = Date.now()
      if (isTargeted) {
        await mongos.db('College').collection('Student').find(query).explain('executionStats')
      } else {
        await mongos.db('College').collection('Student').find(query).toArray()
      }
      times.push(Date.now() - start)
    }
    const avg = (times.reduce((a, b) => a + b, 0) / times.length).toFixed(2)
    const explain = isTargeted
      ? await mongos.db('College').collection('Student').find(query).explain('executionStats')
      : null
    const stage = explain?.queryPlanner?.winningPlan?.stage || 'N/A'
    const nShards = explain?.queryPlanner?.winningPlan?.shards?.length || 'N/A'
    results.push({ label, avgMs: avg, stage, nShards })
  }
  res.json(results)
})

app.post('/api/bulk-benchmark', async (req, res) => {
  const clients = Math.min(req.body?.clients || 4, 16)
  const docsPerClient = Math.min(req.body?.docsPerClient || 500, 100000)
  const total = clients * docsPerClient

  const genDoc = (clientId, seq) => ({
    RollNo: (clientId % 2 === 0 ? 1 : 100000) + Math.floor(clientId / 2) * docsPerClient + seq,
    Name: `Client${clientId}_Doc${seq}`,
    Dept: ['CS','EC','ME','EE'][seq % 4],
    Year: (seq % 4) + 1,
    bio: 'x'.repeat(500),
    tags: Array.from({length: 20}, (_, j) => `tag${j}`)
  })

  const writeBatch = (db, clientId, seq) => {
    const docs = Array.from({ length: Math.min(2000, docsPerClient - seq) }, (_, i) => genDoc(clientId, seq + i))
    return db.collection('Student').insertMany(docs, { ordered: false })
  }

  const simulateClients = async (db) => {
    const allWriters = []
    for (let c = 0; c < clients; c++) {
      for (let s = 0; s < docsPerClient; s += 2000) {
        allWriters.push(writeBatch(db, c, s))
      }
    }
    return Promise.all(allWriters)
  }

  const cleanup = async () => {
    const promises = []
    for (const db of [mongos.db('College'), shard1.db('College'), shard2.db('College')]) {
      for (let c = 0; c < clients; c++) {
        const base = c % 2 === 0 ? 1 : 100000
        const offset = Math.floor(c / 2) * docsPerClient
        const minRn = base + offset
        const maxRn = base + offset + docsPerClient - 1
        promises.push(db.collection('Student').deleteMany({ RollNo: { $gte: minRn, $lte: maxRn } }))
      }
    }
    await Promise.all(promises)
  }
  await cleanup()

  const start1 = Date.now()
  await simulateClients(mongos.db('College'))
  const shardedMs = Date.now() - start1
  await cleanup()

  const start2 = Date.now()
  await simulateClients(shard1.db('College'))
  const standaloneMs = Date.now() - start2
  await cleanup()

  res.json({
    clients, docsPerClient, total,
    shardedMs,
    standaloneMs,
    speedup: standaloneMs > 0 ? (standaloneMs / shardedMs).toFixed(2) + 'x' : 'N/A',
    shardedRate: shardedMs > 0 ? (total / (shardedMs / 1000)).toFixed(0) : 'N/A',
    standaloneRate: standaloneMs > 0 ? (total / (standaloneMs / 1000)).toFixed(0) : 'N/A',
    note: 'Note: On single machine, all shards share the same disk — write throughput is comparable. Sharding\'s write advantage shines across physical machines (parallel disk I/O). The query benchmark below shows the targeted vs scatter advantage clearly.'
  })
})

app.listen(3000, () => console.log('Visualizer: http://localhost:3000'))