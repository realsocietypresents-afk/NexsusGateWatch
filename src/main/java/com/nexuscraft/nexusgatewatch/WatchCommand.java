package com.nexuscraft.nexusgatewatch;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** {@code /gatewatch whoisin} and {@code /gatewatch logins [player] [count]}. */
final class WatchCommand implements CommandExecutor {

    static final String PERMISSION = "nexusgate.watch";

    private final WatchTracker tracker;

    WatchCommand(WatchTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return true;
        }
        String sub = args.length == 0 ? "whoisin" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "whoisin" -> whoIsIn(sender);
            case "logins" -> logins(sender, args);
            default -> sender.sendMessage("§7Usage: /gatewatch <whoisin|logins [player] [count]>");
        }
        return true;
    }

    private void whoIsIn(CommandSender sender) {
        long now = System.currentTimeMillis();
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        sender.sendMessage("§6=== Who is in (" + online.size() + ") ===");
        if (online.isEmpty()) {
            sender.sendMessage("§7Nobody is online.");
            return;
        }
        for (Player p : online) {
            WatchTracker.Session s = tracker.sessionOf(p.getUniqueId().toString());
            String line;
            if (s == null) {
                line = "§7was already online before the watcher started - method unknown";
            } else if (s.state == WatchTracker.State.WAITING) {
                line = "§eat the password prompt, not logged in yet §8(" + Labels.ago(now - s.since) + ")";
            } else {
                line = "§f" + s.method + " §8(" + Labels.ago(now - s.since) + " ago)";
            }
            String plat = s == null || s.platform.isEmpty() ? "" : " §7(" + s.platform + ")";
            sender.sendMessage("§a" + p.getName() + plat + "§7: " + line);
        }
    }

    private void logins(CommandSender sender, String[] args) {
        String player = null;
        int count = 15;
        for (int i = 1; i < args.length; i++) {
            try {
                count = Math.max(1, Math.min(50, Integer.parseInt(args[i])));
            } catch (NumberFormatException e) {
                player = args[i];
            }
        }
        List<Entry> rows = tracker.recent(player, count);
        sender.sendMessage("§6=== Recent logins" + (player == null ? "" : " for " + player) + " ===");
        if (rows.isEmpty()) {
            sender.sendMessage("§7Nothing recorded" + (player == null ? " yet." : " for that player."));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        for (Entry e : rows) {
            String color = switch (e.kind) {
                case LOGIN -> "§a";
                case NO_PASSWORD -> "§7";
                default -> "§c";
            };
            sender.sendMessage("§8" + fmt.format(new Date(e.time)) + " " + color + e.player
                    + (e.platform.isEmpty() ? "" : " §7(" + e.platform + ")")
                    + "§7: §f" + e.method + (e.detail.isEmpty() ? "" : " §8- " + e.detail));
        }
    }
}
