package com.nexuscraft.nexusgatewatch;

import java.util.function.LongSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Listens to NexusGate's own plugin logger. NexusGate writes one line per gate event
 * (PENDING / SUCCESS / INVITE-SUCCESS / FAIL / TIMEOUT ...) to it, which is all this needs; nothing
 * inside NexusGate is changed.
 */
public final class GateLogHandler extends Handler {

    private final WatchTracker tracker;
    private final LongSupplier clock;

    public GateLogHandler(WatchTracker tracker, LongSupplier clock) {
        this.tracker = tracker;
        this.clock = clock;
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null) {
            return;
        }
        try {
            GateLine line = GateLineParser.parse(record.getMessage());
            if (line != null) {
                tracker.onLine(line, clock.getAsLong());
            }
        } catch (RuntimeException ignored) {
            // A bad line must never break NexusGate's own logging.
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
