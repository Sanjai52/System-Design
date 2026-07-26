var initialized = false
try {
  var s = rs.status()
  initialized = s.ok === 1
} catch(e) {
  initialized = e.codeName === "AlreadyInitialized"
}
if (!initialized) {
  rs.initiate({
    _id: "shard2rs",
    members: [{ _id: 0, host: "shard2:27017" }]
  })
}