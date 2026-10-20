package com.nexuscraft.nexusgatewatch;

/** One row of login history. {@code method} is a human label, never a typed value. */
public final class Entry {

    public enum Kind { LOGIN, NO_PASSWORD, WRONG, TIMEOUT, CODE_REJECTED }

    public final long time;
    public final Kind kind;
    public final String player;
    public final String uuid;
    public final String ip;
    public final String platform;
    public final String method;
    public final String detail;

    public Entry(long time, Kind kind, String player, String uuid, String ip, String platform, String method, String detail) {
        this.time = time;
        this.kind = kind;
        this.player = player;
        this.uuid = uuid == null ? "" : uuid;
        this.ip = ip == null ? "" : ip;
        this.platform = platform == null ? "" : platform;
        this.method = method == null ? "" : method;
        this.detail = detail == null ? "" : detail;
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    public String toLine() {
        return time + "\t" + kind + "\t" + clean(player) + "\t" + clean(uuid) + "\t" + clean(ip) + "\t"
                + clean(platform) + "\t" + clean(method) + "\t" + clean(detail);
    }

    public static Entry fromLine(String line) {
        if (line == null) {
            return null;
        }
        String[] p = line.split("\t", -1);
        if (p.length < 8) {
            return null;
        }
        try {
            return new Entry(Long.parseLong(p[0]), Kind.valueOf(p[1]), p[2], p[3], p[4], p[5], p[6], p[7]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
