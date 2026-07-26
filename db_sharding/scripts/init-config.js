var status
try {
  status = rs.status()
} catch(e) {
  status = { codeName: "NotYetInitialized" }
}
if (status.codeName !== "AlreadyInitialized") {
  rs.initiate({
    _id: "configrs",
    configsvr: true,
    members: [{ _id: 0, host: "configsvr:27017" }]
  })
}