function run(label, fn) {
  try {
    fn()
    print(label + ": ok")
  } catch (e) {
    print(label + ": skipped (" + e.codeName + ")")
  }
}

run("addShard shard1", function() { sh.addShard("shard1rs/shard1:27017") })
run("addShard shard2", function() { sh.addShard("shard2rs/shard2:27017") })
run("enableSharding", function() { sh.enableSharding("College") })
run("shardCollection", function() { sh.shardCollection("College.Student", { RollNo: 1 }) })
run("splitAt 1001", function() { sh.splitAt("College.Student", { RollNo: 1001 }) })
run("moveChunk to shard1", function() { sh.moveChunk("College.Student", { RollNo: 1001 }, "shard1rs") })
