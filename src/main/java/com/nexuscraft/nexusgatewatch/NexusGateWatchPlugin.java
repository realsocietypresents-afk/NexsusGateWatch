package com.nexuscraft.nexusgatewatch;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * NexusGateWatch -- tells operators who logged in and which password or code they used.
 * A separate add-on: it reads NexusGate's own log lines and changes nothing inside NexusGate.
 */
public final class NexusGateWatchPlugin extends JavaPlugin {

    private GateLogHandler handler;
    private java.util.logging.Logger gateLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var cfg = getConfig();

        Plugin gate = Bukkit.getPluginManager().getPlugin("NexusGate");
        if (gate == null) {
            getLogger().severe("NexusGate is not installed, so there is nothing to watch. Disabling.");
            return;
        }

        HistoryStore store = new HistoryStore(new File(getDataFolder(), cfg.getString("history.file", "logins.tsv")), getLogger());
        int size = Math.max(10, Math.min(5000, cfg.getInt("history.size", 500)));

        WatchTracker.Sink sink = new WatchTracker.Sink() {
            @Override
            public void alert(String legacyMessage) {
                String colored = ChatColor.translateAlternateColorCodes('&', legacyMessage);
                Runnable send = () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission(WatchCommand.PERMISSION)) {
                            p.sendMessage(colored);
                        }
                    }
                    getLogger().info(ChatColor.stripColor(colored));
                };
                if (Bukkit.isPrimaryThread()) {
                    send.run();
                } else {
                    Bukkit.getScheduler().runTask(NexusGateWatchPlugin.this, send);
                }
            }

            @Override
            public void persist(Entry entry) {
                store.append(entry);
            }
        };

        File invites = new File(gate.getDataFolder(), "invites.yml");
        Labels.TeamLookup teams = code -> {
            try {
                if (!invites.exists()) {
                    return null;
                }
                return YamlConfiguration.loadConfiguration(invites).getString("invites." + code + ".team-id");
            } catch (RuntimeException e) {
                return null;
            }
        };

        WatchTracker tracker = new WatchTracker(sink, teams, size);
        tracker.alertOnLogin = cfg.getBoolean("alerts.on-login", true);
        tracker.alertOnNoPassword = cfg.getBoolean("alerts.on-no-password", true);
        tracker.alertOnFailure = cfg.getBoolean("alerts.on-failure", true);
        tracker.showIp = cfg.getBoolean("alerts.show-ip", true);
        tracker.load(store.load(size));

        this.gateLogger = gate.getLogger();
        this.handler = new GateLogHandler(tracker, System::currentTimeMillis);
        gateLogger.addHandler(handler);

        getServer().getPluginManager().registerEvents(new WatchListener(tracker), this);
        var command = getCommand("gatewatch");
        if (command != null) {
            command.setExecutor(new WatchCommand(tracker));
        }
        getLogger().info("NexusGateWatch enabled -- watching NexusGate's login events.");
    }

    @Override
    public void onDisable() {
        if (gateLogger != null && handler != null) {
            gateLogger.removeHandler(handler);
        }
    }
}
