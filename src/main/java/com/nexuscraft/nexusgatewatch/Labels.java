package com.nexuscraft.nexusgatewatch;

/** Turns a parsed line into the wording operators read. */
public final class Labels {

    /** Looks up whether a code belongs to a team (reads NexusGate's invites.yml). May return null. */
    public interface TeamLookup {
        String teamOf(String code);
    }

    private Labels() {
    }

    public static String platform(String kind) {
        if (kind == null) {
            return "";
        }
        return kind.equalsIgnoreCase("BEDROCK") ? "Bedrock" : kind.equalsIgnoreCase("JAVA") ? "Java" : kind;
    }

    /** NexusGate shows 8-character codes as ABCD-EFGH. */
    public static String code(String raw) {
        if (raw == null) {
            return "?";
        }
        return raw.length() == 8 ? raw.substring(0, 4) + "-" + raw.substring(4) : raw;
    }

    /**
     * Which password or code got the player in. The shared passwords are named, not printed: every
     * player who used one typed the same text, and copying it into a log would only spread it.
     * Codes are printed because they are what identifies who was let in and by whom.
     */
    public static String method(GateLine line, TeamLookup teams) {
        if (line.type == GateLine.Type.SUCCESS) {
            return "BEDROCK".equalsIgnoreCase(line.kind) ? "the Bedrock server password" : "the Java server password";
        }
        String code = line.get("code");
        String personal = line.get("personal-for");
        if (personal != null && !personal.isEmpty() && !"null".equals(personal)) {
            return "personal code " + code(code) + " (for " + personal + ")";
        }
        String team = teams == null || code == null ? null : teams.teamOf(code);
        if (team != null && !team.isEmpty()) {
            return "team code " + code(code) + " (" + team + ")";
        }
        String by = line.get("created-by");
        return "invite code " + code(code) + (by == null || by.isEmpty() ? "" : " (made by " + by + ")");
    }

    public static String ago(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s < 60) {
            return s + "s";
        }
        long m = s / 60;
        if (m < 60) {
            return m + "m";
        }
        long h = m / 60;
        if (h < 48) {
            return h + "h " + (m % 60) + "m";
        }
        return (h / 24) + "d";
    }
}
