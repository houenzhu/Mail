package com.zhe.mall.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

public final class SnowflakeIdGenerator {

    private static final long EPOCH = 1_700_000_000_000L;

    private static final int WORKER_ID_BITS = 5;

    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;

    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    private static final int WORKER_ID_SHIFT = SEQUENCE_BITS;

    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator(resolveWorkerId());

    private final long workerId;

    private long lastTimestamp = -1L;

    private long sequence;

    private SnowflakeIdGenerator(long workerId) {
        this.workerId = workerId;
    }

    public static long nextId() {
        return INSTANCE.next();
    }

    public static String nextIdStr() {
        return Long.toString(INSTANCE.next());
    }

    public static String nextOrderNo() {
        return nextIdStr();
    }

    private static long resolveWorkerId() {
        String configured = System.getenv("WORKER_ID");
        if (configured != null && !configured.isBlank()) {
            return Math.floorMod(Long.parseLong(configured.trim()), MAX_WORKER_ID + 1);
        }
        try {
            return Math.floorMod((long) InetAddress.getLocalHost().getHostName().hashCode(), MAX_WORKER_ID + 1);
        } catch (UnknownHostException e) {
            return ThreadLocalRandom.current().nextLong(MAX_WORKER_ID + 1);
        }
    }

    private synchronized long next() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    private long waitUntil(long targetTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp < targetTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
