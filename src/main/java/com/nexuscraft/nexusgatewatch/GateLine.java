package com.nexuscraft.nexusgatewatch;

import java.util.Map;

/** One parsed NexusGate audit-log line. {@code fields} holds the key=value pairs as written. */
public final class GateLine {

    public enum Type { PENDING, SUCCESS, INVITE_SUCCESS, FAIL, TIMEOUT, INVITE_FAIL, PERSONAL_MISMATCH }

    public final Type type;
    public final String player;
    public final String uuid;
    public final String ip;
    public final String kind;      // JAVA / BEDROCK as NexusGate prints it, or null
    private final Map<String, String> fields;

    public GateLine(Type type, String player, String uuid, String ip, String kind, Map<String, String> fields) {
        this.type = type;
        this.player = player;
        this.uuid = uuid;
        this.ip = ip;
        this.kind = kind;
        this.fields = fields;
    }

    public String get(String key) {
        return fields.get(key);
    }

    public boolean flag(String key) {
        return "true".equals(fields.get(key));
    }
}
