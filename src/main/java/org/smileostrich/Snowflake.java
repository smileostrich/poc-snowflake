package org.smileostrich;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.*;

public class Snowflake {

    private static final int EPOCH_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long DEFAULT_CUSTOM_EPOCH = 1680000000000L;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    private final long nodeId;
    private final long customEpoch;

    public Snowflake(long nodeId, long customEpoch) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID)
            throw new IllegalArgumentException(String.format("invalid-node-id : node id must be between %d and %d", 0, MAX_NODE_ID));

        this.nodeId = nodeId;
        this.customEpoch = customEpoch;
    }

    public Snowflake(long nodeId) {
        this(nodeId, DEFAULT_CUSTOM_EPOCH);
    }

    public Snowflake() {
        this.nodeId = createNodeId();
        this.customEpoch = DEFAULT_CUSTOM_EPOCH;
    }

    public synchronized long nextId() {
        long currentTimestamp = timestamp();

        if (currentTimestamp < lastTimestamp)
            throw new IllegalStateException("invalid-system-clock");

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0)
                currentTimestamp = waitNextMillis(currentTimestamp);
        } else
            sequence = 0;

        lastTimestamp = currentTimestamp;

        return currentTimestamp << (NODE_ID_BITS + SEQUENCE_BITS)
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    private long timestamp() {
        return System.currentTimeMillis() - customEpoch;
    }

    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp)
            currentTimestamp = timestamp();

        return currentTimestamp;
    }

    private static long createNodeId() {
        long nodeId;
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();

                if (mac != null)
                    for (byte macPort : mac)
                        sb.append(String.format("%02X", macPort));
            }
            nodeId = sb.toString().hashCode();
        } catch (Exception ignored) {
            nodeId = new SecureRandom().nextInt();
        }

        return nodeId & MAX_NODE_ID;
    }

    public long[] parse(long id) {
        long maskNodeId = ((1L << NODE_ID_BITS) - 1) << SEQUENCE_BITS;
        long maskSequence = (1L << SEQUENCE_BITS) - 1;
        long timestamp = (id >> (NODE_ID_BITS + SEQUENCE_BITS)) + customEpoch;
        long nodeId = (id & maskNodeId) >> SEQUENCE_BITS;
        long sequence = id & maskSequence;

        return new long[] {timestamp, nodeId, sequence};
    }

    @Override
    public String toString() {
        return "My Custom Snowflake [EPOCH_BITS=" + EPOCH_BITS + ", NODE_ID_BITS=" + NODE_ID_BITS
                + ", SEQUENCE_BITS=" + SEQUENCE_BITS + ", CUSTOM_EPOCH=" + customEpoch
                + ", NodeId=" + nodeId + "]";
    }

}
