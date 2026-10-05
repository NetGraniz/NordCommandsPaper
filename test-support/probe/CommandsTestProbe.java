package com.nordfjell.nordcommandspaper.test;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Synthetic LOCAL commands only. Never shipped in a production release. */
public final class CommandsTestProbe extends JavaPlugin implements Listener {
    private int safe, unsafe, guarded, delay;
    private String lastRaw = "";
    private Object originalLoader;
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        for (String label : List.of("cptest", "safe", "unsafe", "guarded")) getCommand(label).setExecutor(this);
    }
    private JavaPlugin target() { return (JavaPlugin) Bukkit.getPluginManager().getPlugin("NordCommands"); }
    private Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private Object invoke(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void treeTrace(PlayerCommandSendEvent event) {
        getLogger().info("CPTREE_THREAD primary=" + Bukkit.isPrimaryThread() + " async=" + event.isAsynchronous()
                + " count=" + event.getCommands().size() + " thread=" + Thread.currentThread().getName());
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void rewrite(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equals("/safe rewrite")) event.setMessage("/unsafe REWRITTEN_SYNTHETIC");
        if (event.getMessage().equals("/safe namespace")) event.setMessage("/commandstestprobe:unsafe REWRITTEN_NAMESPACE");
        if (event.getMessage().equals("/safe alias")) event.setMessage("/msg CPBeta ALLOWED_REWRITE");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observe(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().startsWith("/safe ")) lastRaw = event.getMessage();
    }
    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equals("cptest")) {
            switch (command.getName()) {
                case "safe" -> { safe++; sender.sendMessage("SAFE_EXECUTED " + String.join(" ", args)); }
                case "unsafe" -> { unsafe++; sender.sendMessage("UNSAFE_EXECUTED " + String.join(" ", args)); }
                case "guarded" -> {
                    if (!sender.hasPermission("commandstest.guarded")) { sender.sendMessage("OWN_PERMISSION_DENIED"); return true; }
                    guarded++; sender.sendMessage("GUARDED_EXECUTED");
                }
            }
            return true;
        }
        if (sender instanceof Player) { sender.sendMessage("Console only."); return true; }
        try {
            switch (args[0]) {
                case "state" -> {
                    long workers = Thread.getAllStackTraces().keySet().stream()
                            .filter(t -> t.isAlive() && t.getName().equals("NordCommands-policy-reader")).count();
                    int notices = target().getDescription().getVersion().equals("1.1.0") ? ((Map<?, ?>) field(target(), "noticeAt")).size() : -1;
                    int refresh = target().getDescription().getVersion().equals("1.1.0") ? (int) invoke(field(target(), "refresh"), "size") : -1;
                    getLogger().info("CPSTATE " + args[1] + " safe=" + safe + " unsafe=" + unsafe + " guarded=" + guarded
                            + " workers=" + workers + " notices=" + notices + " refresh=" + refresh + " raw=" + encode(lastRaw));
                }
                case "permission" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    player.addAttachment(this, args[2], Boolean.parseBoolean(args[3])); player.updateCommands();
                }
                case "direct" -> target().onCommand(Bukkit.getPlayerExact(args[1]), target().getCommand("nordcommands"),
                        "nordcommands", Arrays.copyOfRange(args, 2, args.length));
                case "slow" -> {
                    delay = Integer.parseInt(args[1]);
                    Field loader = target().getClass().getDeclaredField("loader"); loader.setAccessible(true);
                    if (originalLoader == null) originalLoader = loader.get(target());
                    Object replacement = Proxy.newProxyInstance(loader.getType().getClassLoader(), new Class<?>[]{loader.getType()},
                            (proxy, method, parameters) -> {
                                int wait = delay; if (wait > 0) Thread.sleep(wait);
                                method.setAccessible(true);
                                try { return method.invoke(originalLoader, parameters); }
                                catch (InvocationTargetException e) { throw e.getCause(); }
                            });
                    loader.set(target(), replacement);
                }
                case "tab" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    String buffer = new String(Base64.getDecoder().decode(args[3]), StandardCharsets.UTF_8);
                    TabCompleteEvent event = new TabCompleteEvent(player, buffer, new ArrayList<>(List.of("synthetic")));
                    Bukkit.getPluginManager().callEvent(event);
                    getLogger().info("CPTAB " + args[2] + " cancelled=" + event.isCancelled() + " count=" + event.getCompletions().size());
                }
                case "async" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/safe async");
                    Method finalGate = target().getClass().getDeclaredMethod("onCommandFinal", PlayerCommandPreprocessEvent.class);
                    CompletableFuture.runAsync(() -> {
                        try {
                            finalGate.invoke(target(), event);
                            getLogger().info("CPASYNC " + args[2] + " cancelled=" + event.isCancelled());
                        } catch (Exception e) { getLogger().severe("CPTEST_FAILED async " + e); }
                    });
                }
                default -> throw new IllegalArgumentException("Unknown probe command");
            }
            getLogger().info("CPTEST_OK " + String.join(" ", args));
        } catch (Exception e) { getLogger().severe("CPTEST_FAILED " + e); }
        return true;
    }
}

