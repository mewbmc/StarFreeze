package io.starseed.freezeplugin.commands;

import io.starseed.freezeplugin.FreezePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class FreezeHoldCommand implements CommandExecutor {
    private final FreezePlugin plugin;

    public FreezeHoldCommand(FreezePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("freeze.use")) {
            sender.sendMessage(plugin.getConfig().getString("no-permission"));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(plugin.getConfig().getString("freezehold-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.getConfig().getString("player-not-found"));
            return true;
        }

        if (!plugin.isFrozen(target)) {
            sender.sendMessage(plugin.getConfig().getString("player-not-frozen"));
            return true;
        }

        plugin.setHoldStatus(target, true);
        return true;
    }
}