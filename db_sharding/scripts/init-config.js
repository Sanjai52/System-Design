var initialized = false
try {
  var s = rs.status()
  initialized = s.ok === 1
} catch(e) {
  initialized = e.codeName === "AlreadyInitialized"
}
if (!initialized) {
  rs.initiate({
    _id: "configrs",
    configsvr: true,
    members: [{ _id: 0, host: "configsvr:27017" }]
  })
}