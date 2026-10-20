package com.nexuscraft.nexusgatewatch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the lines NexusGate's audit logger writes, e.g.
 * {@code SUCCESS  player=Steve uuid=... ip=1.2.3.4 kind=JAVA first-time=true}.
 * It looks for the keyword anywhere in the line, so any "[NexusGate]" prefixes the server's logger
 * puts in front do not matter. Anything it does not recognise is ignored (null).
 *
 * <p>The audit lines never contain what the player typed, so neither can this plugin.
 */
public final class GateLineParser {

    private static final Pattern HEAD = Pattern.compile(
            "(?:^|[\\s\\]])(INVITE-SUCCESS|INVITE-FAIL|PERSONAL-MISMATCH|SUCCESS|FAIL|TIMEOUT|PENDING)\\s+(player=.*)$");
    private static final Pattern PAIR = Pattern.compile("([A-Za-z-]+)=(\\S*)");

    private GateLineParser() {
    }

    public static GateLine parse(String message) {
        if (message == null) {
            return null;
        }
        Matcher head = HEAD.matcher(message);
        if (!head.find()) {
            return null;
        }
        GateLine.Type type = switch (head.group(1)) {
            case "INVITE-SUCCESS" -> GateLine.Type.INVITE_SUCCESS;
            case "INVITE-FAIL" -> GateLine.Type.INVITE_FAIL;
            case "PERSONAL-MISMATCH" -> GateLine.Type.PERSONAL_MISMATCH;
            case "SUCCESS" -> GateLine.Type.SUCCESS;
            case "FAIL" -> GateLine.Type.FAIL;
            case "TIMEOUT" -> GateLine.Type.TIMEOUT;
            default -> GateLine.Type.PENDING;
        };
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher pair = PAIR.matcher(head.group(2));
        while (pair.find()) {
            fields.putIfAbsent(pair.group(1), pair.group(2));
        }
        String player = fields.get("player");
        if (player == null || player.isEmpty()) {
            return null;
        }
        return new GateLine(type, player, fields.get("uuid"), fields.get("ip"), fields.get("kind"), fields);
    }
}
