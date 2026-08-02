/**
 * seed-data.js  —  informational reference seed script.
 *
 * NOTE: In this consistent-hashing lab, student records are routed by the
 * application (see StudentSeeder) which hashes each RollNo onto the virtual-
 * node hash ring and writes to the owning mongod. There is no mongos router,
 * so inserting all students via a single mongosh connection would NOT
 * distribute them across nodes.
 *
 * To seed a SINGLE standalone mongod directly for manual inspection, run:
 *   docker compose exec mongo1 mongosh /scripts/seed-data.js
 * This inserts the 20 students into mongo1's College.Student only.
 */
db = db.getSiblingDB("College");
db.Student.drop();
db.Student.insertMany([
  { _id: 1001, name: "Alice",   dept: "CS", year: 3 },
  { _id: 1002, name: "Bob",     dept: "EC", year: 2 },
  { _id: 1003, name: "Charlie", dept: "CS", year: 4 },
  { _id: 1004, name: "Diana",   dept: "ME", year: 1 },
  { _id: 1005, name: "Eve",     dept: "EC", year: 3 },
  { _id: 1006, name: "Frank",   dept: "CS", year: 2 },
  { _id: 1007, name: "Grace",   dept: "ME", year: 4 },
  { _id: 1008, name: "Henry",   dept: "EC", year: 1 },
  { _id: 1009, name: "Ivy",     dept: "CS", year: 3 },
  { _id: 1010, name: "Jack",    dept: "ME", year: 2 },
  { _id: 1011, name: "Kate",    dept: "CS", year: 4 },
  { _id: 1012, name: "Leo",     dept: "EC", year: 1 },
  { _id: 1013, name: "Mia",     dept: "ME", year: 3 },
  { _id: 1014, name: "Noah",    dept: "CS", year: 2 },
  { _id: 1015, name: "Olivia",  dept: "EC", year: 4 },
  { _id: 1016, name: "Paul",    dept: "ME", year: 1 },
  { _id: 1017, name: "Quinn",   dept: "CS", year: 3 },
  { _id: 1018, name: "Rose",    dept: "EC", year: 2 },
  { _id: 1019, name: "Sam",     dept: "ME", year: 4 },
  { _id: 1020, name: "Tina",    dept: "CS", year: 1 }
]);
print("Inserted " + db.Student.countDocuments() + " students into College.Student on this node.");
