package com.consistenthashing.consistenthash;

public record StorageNode(String host, int port) {

    public String key() {
        return host + ":" + port;
    }
}
