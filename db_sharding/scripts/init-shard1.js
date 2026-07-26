if (rs.status().codeName !== "AlreadyInitialized") {
  rs.initiate({
    _id: "shard1rs",
    members: [{ _id: 0, host: "shard1:27017" }]
  })
}