package com.nexuscraft.nexusgatewatch;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Plain main, no JUnit. Lines below are written exactly the way NexusGate 0.9.0 writes them. */
public final class WatchTest {

    private static int checks = 0;
    private static int failures = 0;

    static final String U1 = "11111111-1111-1111-1111-111111111111";
    static final String U2 = "22222222-2222-2222-2222-222222222222";

    static final class Rec implements WatchTracker.Sink {
        final List<String> alerts = new ArrayList<>();
        final List<Entry> saved = new ArrayList<>();
        public void alert(String m) { alerts.add(m); }
        public void persist(Entry e) { saved.add(e); }
    }

    static class Tester extends Player {
        final String name; final UUID id; final List<String> said = new ArrayList<>(); boolean watch = true;
        Tester(String name, UUID id) { this.name = name; this.id = id; }
        @Override public String getName() { return name; }
        @Override public UUID getUniqueId() { return id; }
        @Override public void sendMessage(String m) { said.add(m); }
        @Override public boolean hasPermission(String n) { return watch; }
    }

    public static void main(String[] args) throws Exception {
        parser();
        labels();
        tracker();
        noPasswordPath();
        store();
        handlerAndListener();
        command();
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static void parser() {
        GateLine s = GateLineParser.parse("SUCCESS  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA first-time=true");
        expect(s != null && s.type == GateLine.Type.SUCCESS && s.player.equals("Steve") && s.uuid.equals(U1)
                && s.ip.equals("1.2.3.4") && "JAVA".equals(s.kind) && s.flag("first-time"), "plain SUCCESS line");
        GateLine pref = GateLineParser.parse("[NexusGate] [NexusGate] SUCCESS  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=BEDROCK");
        expect(pref != null && pref.type == GateLine.Type.SUCCESS && "BEDROCK".equals(pref.kind) && !pref.flag("first-time"), "doubled logger prefix is ignored");
        GateLine inv = GateLineParser.parse("INVITE-SUCCESS  player=Alex uuid=" + U2 + " ip=5.6.7.8 kind=BEDROCK code=ABCD2345 created-by=Boss uses-left=2 personal-for=Alex claimed=true first-time=true");
        expect(inv != null && inv.type == GateLine.Type.INVITE_SUCCESS && "ABCD2345".equals(inv.get("code")) && "Alex".equals(inv.get("personal-for"))
                && inv.flag("claimed") && "2".equals(inv.get("uses-left")), "INVITE-SUCCESS is not mistaken for SUCCESS and keeps its fields");
        expect(GateLineParser.parse("FAIL     player=Bo uuid=" + U1 + " ip=9.9.9.9 attempt=2/3 kind=JAVA").type == GateLine.Type.FAIL, "FAIL");
        expect(GateLineParser.parse("TIMEOUT  player=Bo uuid=" + U1 + " ip=9.9.9.9 kind=JAVA").type == GateLine.Type.TIMEOUT, "TIMEOUT");
        expect(GateLineParser.parse("PENDING  player=Bo uuid=" + U1 + " ip=9.9.9.9 kind=JAVA").type == GateLine.Type.PENDING, "PENDING");
        expect(GateLineParser.parse("INVITE-FAIL  player=Bo uuid=" + U1 + " ip=9.9.9.9 code=ABCD2345 status=EXPIRED").type == GateLine.Type.INVITE_FAIL, "INVITE-FAIL");
        expect(GateLineParser.parse("PERSONAL-MISMATCH  player=Bo uuid=" + U1 + " ip=9.9.9.9 code=ABCD2345 personal-for=Al bound-to=" + U2).type == GateLine.Type.PERSONAL_MISMATCH, "PERSONAL-MISMATCH");
        expect(GateLineParser.parse("NexusGate enabled.") == null, "unrelated line ignored");
        expect(GateLineParser.parse("LOCKOUT-REJECT  player=Bo uuid=" + U1 + " ip=1.1.1.1 remaining=5m") == null, "lockout line ignored (not a login event)");
        expect(GateLineParser.parse(null) == null, "null is safe");
        expect(GateLineParser.parse("SUCCESS  player= uuid=1") == null, "empty player name rejected");
        expect(GateLineParser.parse("MYSUCCESS  player=Bo uuid=1 ip=2") == null, "keyword inside a longer word is ignored");
    }

    private static void labels() {
        GateLine java = GateLineParser.parse("SUCCESS  player=A uuid=" + U1 + " ip=1 kind=JAVA");
        GateLine bed = GateLineParser.parse("SUCCESS  player=A uuid=" + U1 + " ip=1 kind=BEDROCK");
        expect(Labels.method(java, null).equals("the Java server password"), "Java password label");
        expect(Labels.method(bed, null).equals("the Bedrock server password"), "Bedrock password label");
        expect(!Labels.method(java, null).matches(".*\\d{4}.*"), "the shared password's text is never in the label");
        GateLine personal = GateLineParser.parse("INVITE-SUCCESS  player=A uuid=" + U1 + " ip=1 kind=JAVA code=ABCD2345 created-by=Boss uses-left=unlimited personal-for=Alex");
        expect(Labels.method(personal, null).equals("personal code ABCD-2345 (for Alex)"), "personal code label");
        GateLine team = GateLineParser.parse("INVITE-SUCCESS  player=A uuid=" + U1 + " ip=1 kind=JAVA code=ABCD2345 created-by=NexusRealms uses-left=unlimited");
        expect(Labels.method(team, c -> c.equals("ABCD2345") ? "team-7" : null).equals("team code ABCD-2345 (team-7)"), "team code label via lookup");
        expect(Labels.method(team, null).equals("invite code ABCD-2345 (made by NexusRealms)"), "no lookup: invite code label with creator");
        expect(Labels.method(team, c -> null).startsWith("invite code"), "lookup finds nothing: invite code");
        expect(Labels.code("ABC").equals("ABC") && Labels.code(null).equals("?"), "odd code lengths");
        expect(Labels.platform("JAVA").equals("Java") && Labels.platform("bedrock").equals("Bedrock") && Labels.platform(null).isEmpty(), "platform names");
        expect(Labels.ago(59_999).equals("59s") && Labels.ago(60_000).equals("1m") && Labels.ago(3_599_000).equals("59m")
                && Labels.ago(3_600_000).equals("1h 0m") && Labels.ago(47L * 3_600_000).equals("47h 0m") && Labels.ago(48L * 3_600_000).equals("2d"), "ago boundaries");
        GateLine noBy = GateLineParser.parse("INVITE-SUCCESS  player=A uuid=" + U1 + " ip=1 kind=JAVA code=ABCD2345 uses-left=1");
        expect(Labels.method(noBy, null).equals("invite code ABCD-2345"), "invite code with no creator listed");
        expect(Labels.ago(5000).equals("5s") && Labels.ago(65_000).equals("1m") && Labels.ago(3_700_000).equals("1h 1m") && Labels.ago(3L * 86_400_000).equals("3d"), "ago formatting");
    }

    private static void tracker() {
        Rec rec = new Rec();
        WatchTracker t = new WatchTracker(rec, null, 500);
        long now = 1_000_000L;
        t.onLine(GateLineParser.parse("PENDING  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA"), now);
        t.onJoinFinished(U1, "Steve", "1.2.3.4", now);
        expect(t.sessionOf(U1).state == WatchTracker.State.WAITING, "after join with a prompt: waiting at the prompt");
        expect(rec.alerts.isEmpty() && rec.saved.isEmpty(), "joining to the prompt records and alerts nothing yet");
        t.onLine(GateLineParser.parse("FAIL     player=Steve uuid=" + U1 + " ip=1.2.3.4 attempt=1/3 kind=JAVA"), now + 1000);
        expect(rec.alerts.size() == 1 && rec.alerts.get(0).contains("wrong password") && rec.alerts.get(0).contains("1/3"), "wrong attempt alerts with the count");
        expect(rec.saved.get(0).kind == Entry.Kind.WRONG && rec.saved.get(0).detail.equals("attempt 1/3"), "wrong attempt is saved");
        expect(rec.saved.get(0).method.equals("wrong password"), "the saved row says 'wrong password', not what was typed");
        t.onLine(GateLineParser.parse("SUCCESS  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA first-time=true"), now + 5000);
        expect(t.sessionOf(U1).state == WatchTracker.State.IN && t.sessionOf(U1).method.equals("the Java server password"), "login sets the session method");
        String a = rec.alerts.get(1);
        expect(a.contains("Steve") && a.contains("Java") && a.contains("the Java server password") && a.contains("first time ever") && a.contains("1.2.3.4"), "login alert has name, platform, method, first-time, ip");
        t.showIp = false;
        t.onLine(GateLineParser.parse("INVITE-SUCCESS  player=Alex uuid=" + U2 + " ip=5.6.7.8 kind=BEDROCK code=ABCD2345 created-by=Boss uses-left=1"), now + 6000);
        expect(rec.alerts.get(2).contains("invite code ABCD-2345") && !rec.alerts.get(2).contains("5.6.7.8"), "show-ip: false hides the address");
        t.alertOnLogin = false;
        t.onLine(GateLineParser.parse("SUCCESS  player=Cy uuid=33333333-3333-3333-3333-333333333333 ip=1 kind=JAVA"), now + 7000);
        expect(rec.alerts.size() == 3 && t.recent("Cy", 5).size() == 1, "alerts.on-login: false silences the alert but still records");
        t.alertOnFailure = false;
        int before = rec.alerts.size();
        t.onLine(GateLineParser.parse("TIMEOUT  player=Di uuid=" + U1 + " ip=1 kind=JAVA"), now + 8000);
        t.onLine(GateLineParser.parse("FAIL     player=Di uuid=" + U1 + " ip=1 attempt=1/3 kind=JAVA"), now + 8100);
        expect(rec.alerts.size() == before && t.recent("Di", 5).size() == 2, "alerts.on-failure: false silences failures but still records them");
        t.alertOnFailure = true;
        t.onLine(GateLineParser.parse("TIMEOUT  player=Ed uuid=" + U2 + " ip=1 kind=JAVA"), now + 9000);
        expect(rec.alerts.get(rec.alerts.size() - 1).contains("ran out of time"), "timeout alert");
        int n = rec.alerts.size();
        t.onLine(GateLineParser.parse("INVITE-FAIL  player=Fa uuid=" + U1 + " ip=1 code=ABCD2345 status=REVOKED"), now + 9500);
        t.onLine(GateLineParser.parse("PERSONAL-MISMATCH  player=Fa uuid=" + U1 + " ip=1 code=ABCD2345 personal-for=Al bound-to=" + U2), now + 9600);
        expect(rec.alerts.size() == n, "rejected codes are history only (NexusGate already alerts for them)");
        expect(t.recent("Fa", 5).size() == 2 && t.recent("Fa", 5).get(0).detail.contains("someone else's"), "...and are in the history");
        t.onQuit(U1);
        expect(t.sessionOf(U1) == null, "quit clears the session");
        expect(t.recent("steve", 5).size() == 2, "player filter is case-insensitive");
        expect(t.recent(null, 3).size() == 3, "count limit");
        expect(t.recent(null, 100).get(0).time >= t.recent(null, 100).get(1).time, "newest first");
        WatchTracker small = new WatchTracker(rec, null, 10);
        for (int i = 0; i < 30; i++) small.onLine(GateLineParser.parse("SUCCESS  player=P" + i + " uuid=u" + i + " ip=1 kind=JAVA"), i);
        expect(small.historyCount() == 10 && small.recent(null, 1).get(0).player.equals("P29"), "history is capped, newest kept");
    }

    private static void noPasswordPath() {
        Rec rec = new Rec();
        WatchTracker t = new WatchTracker(rec, null, 500);
        t.onJoinFinished(U1, "Opie", "7.7.7.7", 10);
        expect(t.sessionOf(U1).state == WatchTracker.State.NO_PASSWORD, "no prompt at join: marked as no password needed");
        expect(rec.alerts.size() == 1 && rec.alerts.get(0).contains("without a password") && rec.alerts.get(0).contains("Opie"), "...and operators are told");
        expect(rec.saved.get(0).kind == Entry.Kind.NO_PASSWORD, "...and it is in the history");
        Rec r2 = new Rec();
        WatchTracker t2 = new WatchTracker(r2, null, 500);
        t2.alertOnNoPassword = false;
        t2.onJoinFinished(U1, "Opie", "", 10);
        expect(r2.alerts.isEmpty() && r2.saved.size() == 1, "alerts.on-no-password: false silences it, still records");
        // a stale WAITING from a previous visit must not hide a bypass: quit clears it
        Rec r3 = new Rec();
        WatchTracker t3 = new WatchTracker(r3, null, 500);
        t3.onLine(GateLineParser.parse("PENDING  player=Q uuid=" + U1 + " ip=1 kind=JAVA"), 1);
        t3.onQuit(U1);
        t3.onJoinFinished(U1, "Q", "1", 2);
        expect(t3.sessionOf(U1).state == WatchTracker.State.NO_PASSWORD, "after quitting at the prompt, a later passwordless join is seen as such");
        // joining twice without quit keeps working
        t3.onJoinFinished(U1, "Q", "1", 3);
        expect(r3.saved.size() == 2, "a repeated passwordless join is recorded again");
    }

    private static void store() throws Exception {
        File dir = Files.createTempDirectory("ngw").toFile();
        File f = new File(dir, "logins.tsv");
        HistoryStore st = new HistoryStore(f, Logger.getLogger("t"));
        expect(st.load(10).isEmpty(), "no file: empty history");
        Entry e = new Entry(123L, Entry.Kind.LOGIN, "Steve", U1, "1.2.3.4", "Java", "personal code ABCD-2345 (for Alex)", "first time ever");
        st.append(e);
        st.append(new Entry(124L, Entry.Kind.WRONG, "Bo\tb", U2, "", "Bedrock", "wrong password", "attempt 1/3\nx"));
        List<Entry> back = st.load(10);
        expect(back.size() == 2 && back.get(0).method.equals("personal code ABCD-2345 (for Alex)") && back.get(0).time == 123L, "round trip");
        expect(back.get(1).player.equals("Bo b") && back.get(1).detail.equals("attempt 1/3 x") && back.get(1).kind == Entry.Kind.WRONG, "tabs and newlines in values can't break the file");
        Files.writeString(f.toPath(), Files.readString(f.toPath()) + "garbage line\n1\tNOPE\n");
        expect(st.load(10).size() == 2, "bad lines are skipped");
        for (int i = 0; i < 100; i++) st.append(new Entry(i, Entry.Kind.LOGIN, "P" + i, "", "", "", "m", ""));
        List<Entry> trimmed = st.load(10);
        expect(trimmed.size() == 10 && trimmed.get(9).player.equals("P99"), "a file grown past 4x the limit is trimmed to the newest");
        expect(Files.readAllLines(f.toPath()).size() == 10, "...on disk too");
        Entry nul = new Entry(5L, Entry.Kind.LOGIN, "N", null, null, null, null, null);
        expect(nul.uuid.isEmpty() && nul.ip.isEmpty() && nul.platform.isEmpty() && nul.method.isEmpty() && nul.detail.isEmpty() && !nul.toLine().contains("null"), "missing fields become empty, never the word null");
        File f2 = new File(dir, "exact.tsv");
        HistoryStore st2 = new HistoryStore(f2, Logger.getLogger("t"));
        for (int i = 0; i < 40; i++) st2.append(new Entry(i, Entry.Kind.LOGIN, "P" + i, "", "", "", "m", ""));
        expect(st2.load(10).size() == 40 && Files.readAllLines(f2.toPath()).size() == 40, "exactly 4x the limit is not trimmed yet");
        WatchTracker t = new WatchTracker(new Rec(), null, 500);
        t.load(back);
        t.load(back);
        expect(t.historyCount() == 2, "loading twice does not double the history");
        expect(t.historyCount() == 2 && t.recent(null, 1).get(0).player.equals("Bo b"), "history loads with the newest first");
    }

    private static void handlerAndListener() {
        Rec rec = new Rec();
        WatchTracker t = new WatchTracker(rec, null, 500);
        Logger gate = Logger.getLogger("fake-nexusgate");
        gate.setUseParentHandlers(false);
        GateLogHandler h = new GateLogHandler(t, () -> 42L);
        gate.addHandler(h);
        gate.info("[NexusGate] PENDING  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA");
        gate.info("[NexusGate] SUCCESS  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA");
        gate.info("some other message");
        gate.log(Level.WARNING, "No Java password is set yet!");
        expect(rec.saved.size() == 1 && rec.saved.get(0).time == 42L && rec.saved.get(0).method.equals("the Java server password"), "lines through a real java.util.logging logger are picked up");
        gate.removeHandler(h);
        gate.info("SUCCESS  player=Late uuid=" + U2 + " ip=1 kind=JAVA");
        expect(rec.saved.size() == 1, "after the handler is removed (plugin disabled) nothing more is read");
        GateLogHandler bad = new GateLogHandler(t, () -> { throw new IllegalStateException("boom"); });
        bad.publish(new LogRecord(Level.INFO, "SUCCESS  player=X uuid=1 ip=1"));
        expect(true, "a failing clock/tracker never throws into NexusGate's logging");
        bad.publish(null);

        // listener: join with a prompt vs. without
        WatchListener l = new WatchListener(t);
        Tester steve = new Tester("Steve", UUID.fromString(U1));
        l.onQuit(new PlayerQuitEvent(steve));
        expect(t.sessionOf(U1) == null, "listener quit clears");
        Tester free = new Tester("Free", UUID.fromString(U2)) {
            @Override public java.net.InetSocketAddress getAddress() { return new java.net.InetSocketAddress("127.0.0.9", 25565); }
        };
        l.onJoin(new PlayerJoinEvent(free));
        expect(t.recent("Free", 1).get(0).ip.equals("127.0.0.9"), "the joining player's IP is recorded for passwordless joins");
        expect(t.sessionOf(U2) != null && t.sessionOf(U2).state == WatchTracker.State.NO_PASSWORD, "listener join without a PENDING line = no password needed");
        gate.addHandler(h);
        Tester gated = new Tester("Gated", UUID.fromString("44444444-4444-4444-4444-444444444444"));
        gate.info("PENDING  player=Gated uuid=44444444-4444-4444-4444-444444444444 ip=1 kind=BEDROCK");
        l.onJoin(new PlayerJoinEvent(gated));
        expect(t.sessionOf("44444444-4444-4444-4444-444444444444").state == WatchTracker.State.WAITING, "listener join after a PENDING line leaves the prompt state alone");
    }

    private static void command() {
        Rec rec = new Rec();
        WatchTracker t = new WatchTracker(rec, null, 500);
        WatchCommand cmd = new WatchCommand(t);
        Command c = new Command();
        Tester op = new Tester("Op", UUID.randomUUID());
        t.onLine(GateLineParser.parse("SUCCESS  player=Steve uuid=" + U1 + " ip=1.2.3.4 kind=JAVA"), System.currentTimeMillis() - 120_000);
        expect(cmd.onCommand(op, c, "gatewatch", new String[] {"logins"}), "command reports handled");
        expect(cmd.onCommand(op, c, "gatewatch", new String[] {"bogus"}) && cmd.onCommand(op, c, "gatewatch", new String[] {}), "usage and default also report handled");
        expect(op.said.stream().anyMatch(m -> m.contains("Steve") && m.contains("the Java server password")), "/gatewatch logins lists the login");
        op.said.clear();
        cmd.onCommand(op, c, "gatewatch", new String[] {"logins", "nobody"});
        expect(op.said.stream().anyMatch(m -> m.contains("Nothing recorded for that player")), "unknown player filter says nothing recorded");
        op.said.clear();
        cmd.onCommand(op, c, "gatewatch", new String[] {"logins", "steve", "1"});
        expect(op.said.size() == 2 && op.said.get(0).contains("for steve"), "player and count arguments, either order");
        op.said.clear();
        cmd.onCommand(op, c, "gatewatch", new String[] {"bogus"});
        expect(op.said.get(0).contains("Usage"), "usage on unknown subcommand");
        op.said.clear();
        cmd.onCommand(op, c, "whoisin", new String[] {});
        expect(op.said.get(0).contains("Who is in"), "no arguments shows who is in");
        // who is in: logged in, waiting at the prompt, no password, and unknown
        org.bukkit.Bukkit.ONLINE.clear();
        Tester steve = new Tester("Steve", UUID.fromString(U1));
        Tester wait = new Tester("Waiter", UUID.fromString(U2));
        Tester free = new Tester("Free", UUID.fromString("55555555-5555-5555-5555-555555555555"));
        Tester ghost = new Tester("Ghost", UUID.fromString("66666666-6666-6666-6666-666666666666"));
        org.bukkit.Bukkit.ONLINE.addAll(List.of(steve, wait, free, ghost));
        t.onLine(GateLineParser.parse("PENDING  player=Waiter uuid=" + U2 + " ip=1 kind=BEDROCK"), System.currentTimeMillis() - 30_000);
        t.onJoinFinished("55555555-5555-5555-5555-555555555555", "Free", "", System.currentTimeMillis());
        op.said.clear();
        cmd.onCommand(op, c, "gatewatch", new String[] {"whoisin"});
        String all = String.join("\n", op.said);
        expect(all.contains("Who is in (4)"), "whoisin counts online players");
        expect(all.contains("Steve") && all.contains("the Java server password") && all.contains("2m ago"), "whoisin: logged-in player with method and time");
        expect(all.contains("Waiter") && all.contains("(Bedrock)") && all.contains("at the password prompt"), "whoisin: waiting at the prompt");
        expect(all.contains("Free") && all.contains("no password needed"), "whoisin: passwordless join");
        expect(all.contains("Ghost") && all.contains("before the watcher started"), "whoisin: player online before the watcher started");
        org.bukkit.Bukkit.ONLINE.clear();
        op.said.clear();
        cmd.onCommand(op, c, "gatewatch", new String[] {"whoisin"});
        expect(op.said.get(op.said.size() - 1).contains("Nobody is online"), "whoisin with nobody online");
        Tester nope = new Tester("Plain", UUID.randomUUID());
        nope.watch = false;
        cmd.onCommand(nope, c, "gatewatch", new String[] {"logins"});
        expect(nope.said.size() == 1 && nope.said.get(0).contains("permission"), "players without nexusgate.watch are refused");
    }

    private static void expect(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAILED: " + what); }
    }
}
