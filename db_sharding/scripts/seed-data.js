db = db.getSiblingDB("College")
db.Student.insertMany([
  { RollNo: 1001, Name: "Alice", Dept: "CS", Year: 3 },
  { RollNo: 1002, Name: "Bob", Dept: "EC", Year: 2 },
  { RollNo: 1003, Name: "Charlie", Dept: "CS", Year: 4 },
  { RollNo: 1004, Name: "Diana", Dept: "ME", Year: 1 },
  { RollNo: 1005, Name: "Eve", Dept: "EC", Year: 3 },
  { RollNo: 1006, Name: "Frank", Dept: "CS", Year: 2 },
  { RollNo: 1007, Name: "Grace", Dept: "ME", Year: 4 },
  { RollNo: 1008, Name: "Henry", Dept: "EC", Year: 1 },
  { RollNo: 1009, Name: "Ivy", Dept: "CS", Year: 3 },
  { RollNo: 1010, Name: "Jack", Dept: "ME", Year: 2 },
  { RollNo: 1011, Name: "Kate", Dept: "CS", Year: 4 },
  { RollNo: 1012, Name: "Leo", Dept: "EC", Year: 1 },
  { RollNo: 1013, Name: "Mia", Dept: "ME", Year: 3 },
  { RollNo: 1014, Name: "Noah", Dept: "CS", Year: 2 },
  { RollNo: 1015, Name: "Olivia", Dept: "EC", Year: 4 },
  { RollNo: 1016, Name: "Paul", Dept: "ME", Year: 1 },
  { RollNo: 1017, Name: "Quinn", Dept: "CS", Year: 3 },
  { RollNo: 1018, Name: "Rose", Dept: "EC", Year: 2 },
  { RollNo: 1019, Name: "Sam", Dept: "ME", Year: 4 },
  { RollNo: 1020, Name: "Tina", Dept: "CS", Year: 1 }
])