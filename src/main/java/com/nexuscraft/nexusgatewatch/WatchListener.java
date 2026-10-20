package com.nexuscraft.nexusgatewatch;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;

/** Runs last on join, after NexusGate's own join handler has either logged PENDING or not. */
public final class WatchListener implements Listener {

    private final WatchTracker tracker;

    public WatchListener(WatchTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        InetSocketAddress addr = p.getAddress();
        String ip = addr != null && addr.getAddress() != null ? addr.getAddress().getHostAddress() : "";
        tracker.onJoinFinished(p.getUniqueId().toString(), p.getName(), ip, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracker.onQuit(event.getPlayer().getUniqueId().toString());
    }
}
