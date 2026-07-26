if (rs.status().codeName !== "AlreadyInitialized") {
  rs.initiate({
    _id: "configrs",
    configsvr: true,
    members: [{ _id: 0, host: "configsvr:27017" }]
  })
}