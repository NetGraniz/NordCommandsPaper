package com.nordfjell.nordcommandspaper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Root-label allowlist. Underlying command permissions remain mandatory. */
public final class NordCommandsPaperPlugin extends JavaPlugin implements Listener {
    @FunctionalInterface interface SettingsLoader { CommandSettings load(Path path) throws IOException; }
    private volatile CommandSettings settings;
    private volatile SettingsLoader loader = CommandSettings::load;
    private final RefreshQueue<Player> refresh = new RefreshQueue<>();
    private final Map<UUID, Long> noticeAt = new ConcurrentHashMap<>();
    private ReloadGate<CommandSettings> reload;
    private CommandSender requester;
    private Path configPath;
    private ScheduledTask pump;
    private volatile boolean accepting;

    @Override public void onEnable() {
        saveDefaultConfig();
        configPath = getDataFolder().toPath().resolve("config.yml");
        try { settings = loader.load(configPath); }
        catch (IOException e) { getLogger().severe("Invalid command policy; player commands blocked except authorized management. " + e.getMessage()); }
        reload = new ReloadGate<>("NordCommands-policy-reader");
        accepting = true;
        getServer().getPluginManager().registerEvents(this, this);
        pump = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, ignored -> tick(), 1, 1);
        getLogger().info("NordCommands 1.1.0: policy " + (settings == null ? "blocked" : "ready (" + settings.allowed().size() + " labels)") + ".");
    }
    @Override public void onDisable() {
        accepting = false;
        if (pump != null) pump.cancel();
        if (reload != null) reload.close();
        refresh.clear(); noticeAt.clear(); requester = null; settings = null;
    }
    private void tick() {
        synchronized (reload) {
        reload.drain(outcome -> {
            String message;
            if (outcome.value() != null) {
                settings = outcome.value();
                message = "NordCommands configuration reloaded.";
                if (!refresh.replace(Bukkit.getOnlinePlayers()))
                    getLogger().warning("Command-view refresh capped; execution policy already active.");
            } else message = "NordCommands reload rejected; previous policy retained. " + outcome.error();
            getLogger().info(message);
            if (requester instanceof Player player) player.getScheduler().execute(this,() -> {
                if (player.isOnline()) player.sendMessage(Component.text(message));
            },null,1L);
            else if (requester != null) requester.sendMessage(Component.text(message));
            requester = null;
        });
        }
        refresh.drain(player -> {
            player.getScheduler().execute(this,() -> { if (accepting && player.isOnline()) player.updateCommands(); },null,1L);
        });
    }
    private boolean allowed(Player player, String root) {
        if (!accepting || root == null) return false;
        if (CommandInput.administrative(root))
            return player.hasPermission("nordcommands.admin");
        CommandSettings current = settings;
        return current != null && (player.hasPermission("nordcommands.bypass") || current.allows(root));
    }
    private void enforce(PlayerCommandPreprocessEvent event) {
        // Synthetic/off-thread callbacks fail closed without invoking Player permissions.
        if (!Bukkit.isOwnedByCurrentRegion(event.getPlayer())) { event.setCancelled(true); return; }
        if (allowed(event.getPlayer(), CommandInput.executionLabel(event.getMessage()))) return;
        event.setCancelled(true);
        long now = System.nanoTime();
        UUID id = event.getPlayer().getUniqueId();
        Long previous = noticeAt.get(id);
        if (previous == null || now - previous >= 250_000_000L) {
            noticeAt.put(id, now);
            CommandSettings current = settings;
            event.getPlayer().sendMessage(Component.text(current == null ? "Command policy is unavailable." : current.deniedMessage()));
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandEarly(PlayerCommandPreprocessEvent event) { enforce(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandFinal(PlayerCommandPreprocessEvent event) { enforce(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAvailableCommands(PlayerCommandSendEvent event) {
        if (!Bukkit.isOwnedByCurrentRegion(event.getPlayer())) { event.getCommands().clear(); return; }
        event.getCommands().removeIf(root -> !allowed(event.getPlayer(), CommandInput.label(root)));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) return;
        if (!Bukkit.isOwnedByCurrentRegion(player) || !allowed(player, CommandInput.label(event.getBuffer()))) {
            event.setCancelled(true); event.setCompletions(List.of());
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { noticeAt.remove(event.getPlayer().getUniqueId()); }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !Bukkit.isOwnedByCurrentRegion(player)) { getLogger().warning("Rejected off-region command-policy management."); return true; }
        if (!sender.hasPermission("nordcommands.admin")) {
            sender.sendMessage(Component.text("You do not have permission.")); return true;
        }
        if (!accepting) { sender.sendMessage(Component.text("Command policy is shutting down.")); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("health")) {
            sender.sendMessage(Component.text("NordCommands: " + (settings == null ? "blocked" : "ready")
                    + ", labels=" + (settings == null ? 0 : settings.allowed().size())
                    + ", reload pending=" + reload.busy() + ", refresh pending=" + refresh.size())); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            synchronized (reload) {
            if (reload.start(() -> loader.load(configPath))) {
                requester = sender;
                sender.sendMessage(Component.text("NordCommands reload queued."));
            } else sender.sendMessage(Component.text("NordCommands reload already pending."));
            }
            return true;
        }
        sender.sendMessage(Component.text("Usage: /nordcommands <reload|health>")); return true;
    }
}
