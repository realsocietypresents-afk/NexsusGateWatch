package com.nexuscraft.nexusgatewatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps who is logged in with what, and the recent history. Pure: it gets parsed lines and join/quit
 * notices, and hands finished {@link Entry}s and alert texts to a {@link Sink}.
 */
public final class WatchTracker {

    public interface Sink {
        void alert(String legacyMessage);
        void persist(Entry entry);
    }

    public enum State { WAITING, IN, NO_PASSWORD }

    public static final class Session {
        public final String name;
        public State state;
        public String method = "";
        public String platform = "";
        public String ip = "";
        public long since;

        Session(String name, long since) {
            this.name = name;
            this.since = since;
        }
    }

    private final Sink sink;
    private final Labels.TeamLookup teams;
    private final Map<String, Session> sessions = new HashMap<>();   // by uuid string
    private final LinkedList<Entry> history = new LinkedList<>();
    private final int historySize;

    public boolean alertOnLogin = true;
    public boolean alertOnNoPassword = true;
    public boolean alertOnFailure = true;
    public boolean showIp = true;

    public WatchTracker(Sink sink, Labels.TeamLookup teams, int historySize) {
        this.sink = sink;
        this.teams = teams;
        this.historySize = Math.max(10, historySize);
    }

    public synchronized void load(List<Entry> old) {
        history.clear();
        for (Entry e : old) {
            add(e);
        }
    }

    private void add(Entry e) {
        history.addFirst(e);
        while (history.size() > historySize) {
            history.removeLast();
        }
    }

    private void record(Entry e) {
        add(e);
        sink.persist(e);
    }

    private String ipText(String ip) {
        return showIp && ip != null && !ip.isEmpty() ? " &8(" + ip + ")" : "";
    }

    public synchronized void onLine(GateLine l, long now) {
        String uuid = l.uuid == null ? l.player : l.uuid;
        String platform = Labels.platform(l.kind);
        switch (l.type) {
            case PENDING -> {
                Session s = new Session(l.player, now);
                s.state = State.WAITING;
                s.platform = platform;
                s.ip = l.ip == null ? "" : l.ip;
                sessions.put(uuid, s);
            }
            case SUCCESS, INVITE_SUCCESS -> {
                String method = Labels.method(l, teams);
                Session s = sessions.computeIfAbsent(uuid, k -> new Session(l.player, now));
                s.state = State.IN;
                s.method = method;
                s.platform = platform;
                s.ip = l.ip == null ? "" : l.ip;
                s.since = now;
                boolean first = l.flag("first-time");
                record(new Entry(now, Entry.Kind.LOGIN, l.player, l.uuid, l.ip, platform, method, first ? "first time ever" : ""));
                if (alertOnLogin) {
                    sink.alert("&a[Login] &f" + l.player + " &7(" + platform + ") joined using &f" + method
                            + (first ? " &b- first time ever" : "") + ipText(l.ip));
                }
            }
            case FAIL -> {
                String attempt = l.get("attempt");
                record(new Entry(now, Entry.Kind.WRONG, l.player, l.uuid, l.ip, platform, "wrong password", attempt == null ? "" : "attempt " + attempt));
                if (alertOnFailure) {
                    sink.alert("&c[Login] &f" + l.player + " &7(" + platform + ") typed a wrong password"
                            + (attempt == null ? "" : " &c(" + attempt + ")") + ipText(l.ip));
                }
            }
            case TIMEOUT -> {
                record(new Entry(now, Entry.Kind.TIMEOUT, l.player, l.uuid, l.ip, platform, "no password entered", "timed out and was kicked"));
                if (alertOnFailure) {
                    sink.alert("&c[Login] &f" + l.player + " &7(" + platform + ") ran out of time at the password prompt and was kicked" + ipText(l.ip));
                }
            }
            case INVITE_FAIL, PERSONAL_MISMATCH -> {
                String what = l.type == GateLine.Type.INVITE_FAIL ? "code no longer valid (" + l.get("status") + ")" : "someone else's personal code";
                record(new Entry(now, Entry.Kind.CODE_REJECTED, l.player, l.uuid, l.ip, platform, "code " + Labels.code(l.get("code")), what));
                // NexusGate already alerts watchers about these, so this only keeps the history.
            }
        }
    }

    /** Call at the end of the join (last handler). A player who was never asked for a password is let in free. */
    public synchronized void onJoinFinished(String uuid, String name, String ip, long now) {
        Session s = sessions.get(uuid);
        if (s != null && s.state != State.NO_PASSWORD) {
            return; // the gate asked for a password; the login line will follow
        }
        s = new Session(name, now);
        s.state = State.NO_PASSWORD;
        s.method = "no password needed";
        s.ip = ip == null ? "" : ip;
        sessions.put(uuid, s);
        record(new Entry(now, Entry.Kind.NO_PASSWORD, name, uuid, ip, "", "no password needed", "exempt, or no password set for this platform"));
        if (alertOnNoPassword) {
            sink.alert("&7[Login] &f" + name + " &7joined without a password (exempt, or no password set for them)" + ipText(ip));
        }
    }

    public synchronized void onQuit(String uuid) {
        sessions.remove(uuid);
    }

    public synchronized Session sessionOf(String uuid) {
        return sessions.get(uuid);
    }

    /** Newest first; filtered to one player if given (case-insensitive). */
    public synchronized List<Entry> recent(String player, int max) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : history) {
            if (player != null && !e.player.toLowerCase(Locale.ROOT).equals(player.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out.add(e);
            if (out.size() >= max) {
                break;
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized int historyCount() {
        return history.size();
    }
}
