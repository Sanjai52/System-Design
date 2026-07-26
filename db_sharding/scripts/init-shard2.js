var status
try {
  status = rs.status()
} catch(e) {
  status = { codeName: "NotYetInitialized" }
}
if (status.codeName !== "AlreadyInitialized") {
  rs.initiate({
    _id: "shard2rs",
    members: [{ _id: 0, host: "shard2:27017" }]
  })
}