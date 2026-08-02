db = db.getSiblingDB("College")

db.Student.deleteMany({ RollNo: { $gte: 1, $lte: 2002 } })

var depts = ["CS", "EC", "ME", "IT"]
var docs = []
for (var i = 1; i <= 2002; i++) {
  docs.push({
    RollNo: i,
    Name: "Student" + i,
    Dept: depts[i % 4],
    Year: (i % 4) + 1
  })
  if (docs.length === 500) {
    db.Student.insertMany(docs)
    docs = []
  }
}
if (docs.length > 0) {
  db.Student.insertMany(docs)
}
