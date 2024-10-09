package io.starseed.freezeplugin;

import io.starseed.freezeplugin.commands.FreezeAllCommand;
import io.starseed.freezeplugin.commands.FreezeCommand;
import io.starseed.freezeplugin.commands.FreezeHoldCommand;
import io.starseed.freezeplugin.commands.UnfreezeCommand;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Particle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FreezePlugin extends JavaPlugin implements Listener {
    private Map<UUID, Long> frozenPlayers;
    private Map<UUID, Boolean> holdStatus;
    private Map<UUID, Integer> actionBarTasks;
    private Map<UUID, BukkitTask> particleTasks;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        frozenPlayers = new HashMap<>();
        holdStatus = new HashMap<>();
        actionBarTasks = new HashMap<>();
        particleTasks = new HashMap<>();

        // Save default config
        saveDefaultConfig();
        config = getConfig();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        getCommand("freeze").setExecutor(new FreezeCommand(this));
        getCommand("unfreeze").setExecutor(new UnfreezeCommand(this));
        getCommand("freezehold").setExecutor(new FreezeHoldCommand(this));
        getCommand("freezeall").setExecutor(new FreezeAllCommand(this));

        // Start checking frozen duration
        startFrozenChecker();
    }

    public void freezeAllPlayers(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("freeze.bypass") && !player.isOp() && !isFrozen(player)) {
                freezePlayer(player, sender instanceof Player ? (Player) sender : null);
            }
        }
        sender.sendMessage(ChatColor.GREEN + "All players without bypass permission have been frozen.");
    }

    @Override
    public void onDisable() {
        // Cancel all action bar tasks
        actionBarTasks.values().forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId));
        actionBarTasks.clear();
    }

    private String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    public void kickFrozenPlayer(Player player) {
        player.setMetadata("kicked_by_plugin", new FixedMetadataValue(this, true));
        player.kickPlayer(config.getString("kick-message"));
        frozenPlayers.remove(player.getUniqueId());
        holdStatus.remove(player.getUniqueId());
    }



    private void startFrozenChecker() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            frozenPlayers.forEach((uuid, freezeTime) -> {
                if (!holdStatus.getOrDefault(uuid, false)) {
                    long duration = currentTime - freezeTime;
                    long configDuration = config.getLong("freeze-duration") * 1000;

                    if (duration >= configDuration) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            kickFrozenPlayer(player);
                        }
                    }
                }
            });
        }, 20L, 20L);
    }

    private void startActionBar(Player player) {
        // Cancel existing task if there is one
        Integer existingTask = actionBarTasks.remove(player.getUniqueId());
        if (existingTask != null) {
            Bukkit.getScheduler().cancelTask(existingTask);
        }

        // Start new action bar task
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (player.isOnline() && frozenPlayers.containsKey(player.getUniqueId())) {
                String message = colorize(config.getString("actionbar-message"));
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(message));
            }
        }, 0L, config.getLong("actionbar-interval", 20L));

        actionBarTasks.put(player.getUniqueId(), taskId);
    }

    public void freezePlayer(Player target, Player sender) {
        UUID targetUUID = target.getUniqueId();
        frozenPlayers.put(targetUUID, System.currentTimeMillis());
        holdStatus.put(targetUUID, false);

        // Send freeze message
        String freezeMessage = colorize(config.getString("freeze-message")
                .replace("%player%", target.getName())
                .replace("%staff%", sender.getName()));
        target.sendMessage(freezeMessage);

        // Send title
        target.sendTitle(
                colorize(config.getString("freeze-title")),
                colorize(config.getString("freeze-subtitle")),
                20, 100, 20
        );

        // Start action bar if enabled
        if (config.getBoolean("enable-actionbar", true)) {
            startActionBar(target);
        }

        // Play sound if enabled
        if (config.getBoolean("enable-sounds")) {
            target.playSound(target.getLocation(),
                    Sound.valueOf(config.getString("freeze-sound")),
                    1.0f, 1.0f);
        }

        if (config.getBoolean("enable-particle-effect")) {
            startParticleEffect(target);
        }

        if (config.getBoolean("enable-frozen-gui")) {
            showFrozenGUI(target);
        }
    }

    public void unfreezePlayer(Player target) {
        UUID targetUUID = target.getUniqueId();
        frozenPlayers.remove(targetUUID);
        holdStatus.remove(targetUUID);

        // Cancel action bar task
        Integer taskId = actionBarTasks.remove(targetUUID);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Send unfreeze message
        String unfreezeMessage = colorize(config.getString("unfreeze-message")
                .replace("%player%", target.getName()));
        target.sendMessage(unfreezeMessage);

        // Play sound if enabled
        if (config.getBoolean("enable-sounds")) {
            target.playSound(target.getLocation(),
                    Sound.valueOf(config.getString("unfreeze-sound")),
                    1.0f, 1.0f);
        }

        stopParticleEffect(target);
        target.closeInventory();
    }

    public void setHoldStatus(Player target, boolean status) {
        holdStatus.put(target.getUniqueId(), status);
        String message = colorize(status ?
                config.getString("freeze-hold-enabled") :
                config.getString("freeze-hold-disabled"));
        target.sendMessage(message.replace("%player%", target.getName()));
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.containsKey(player.getUniqueId()) && !player.hasMetadata("kicked")) {
            handleFrozenPlayerDisconnect(player);
        }
        removeFrozenPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        player.setMetadata("kicked", new FixedMetadataValue(this, true));
        removeFrozenPlayer(player);
    }

    private void handleFrozenPlayerDisconnect(Player player) {
        for (String command : config.getStringList("logout-while-frozen-commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName()));
        }
    }

    private void removeFrozenPlayer(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        holdStatus.remove(player.getUniqueId());
        stopParticleEffect(player);
    }
    @EventHandler    public void onPlayerInteract(PlayerInteractEvent event) {
        if (frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            if (frozenPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(config.getString("frozen-gui-title"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(config.getString("frozen-gui-title")) &&
            frozenPlayers.containsKey(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () ->
                showFrozenGUI((Player) event.getPlayer()), 1L);
        }
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    private void startParticleEffect(Player player) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            player.getWorld().spawnParticle(
                Particle.valueOf(config.getString("particle-type")),
                player.getLocation().add(0, 1, 0),
                config.getInt("particle-count"),
                0.5, 0.5, 0.5, 0
            );
        }, 0L, config.getLong("particle-interval"));
        particleTasks.put(player.getUniqueId(), task);
    }

    private void stopParticleEffect(Player player) {
        BukkitTask task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void showFrozenGUI(Player player) {
        String title = ChatColor.translateAlternateColorCodes('&', config.getString("frozen-gui-title"));
        Inventory gui = Bukkit.createInventory(null, 9, title);
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "You are frozen!");
        barrier.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, barrier);
        }

        player.openInventory(gui);
    }

}