package com.studentsharding.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.studentsharding.config.ShardProperties;
import com.studentsharding.domain.ShardNode;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class NodeLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(NodeLifecycleService.class);

    private final String containerPrefix;
    private final String composeFile;

    public NodeLifecycleService(ShardProperties props) {
        this.containerPrefix = props.containerPrefix();
        this.composeFile = props.composeFile();
    }

    public void start(ShardNode node) {
        String container = containerPrefix + node.id();
        if (!runDocker("start", container)) {
            runDocker("compose", "-f", composeFile, "up", "-d", node.id());
        }
        if (!waitForPort(node.host(), node.port(), 60_000)) {
            throw new IllegalStateException(
                    "Mongo node " + node.id() + " not reachable at " + node.host() + ":" + node.port());
        }
    }

    public void stop(String nodeId) {
        runDocker("stop", containerPrefix + nodeId);
    }

    private boolean runDocker(String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = "docker";
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("docker {} timed out: {}", cmd[1], output);
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("docker {} failed ({}): {}", cmd[1], process.exitValue(), output.trim());
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("docker CLI unavailable: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean waitForPort(String host, int port, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (MongoClient probe = MongoClients.create("mongodb://" + host + ":" + port)) {
                probe.getDatabase("admin").runCommand(new Document("ping", 1));
                return true;
            } catch (Exception ignored) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
