sh.addShard("shard1rs/shard1:27017")
sh.addShard("shard2rs/shard2:27017")
sh.enableSharding("College")
sh.shardCollection("College.Student", { RollNo: 1 })