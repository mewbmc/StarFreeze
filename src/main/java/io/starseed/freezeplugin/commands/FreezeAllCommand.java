package io.starseed.freezeplugin.commands;

import io.starseed.freezeplugin.FreezePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FreezeAllCommand implements CommandExecutor {
    private final FreezePlugin plugin;

    public FreezeAllCommand(FreezePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("freeze.all")) {
            sender.sendMessage(plugin.getConfig().getString("no-permission"));
            return true;
        }

        plugin.freezeAllPlayers(sender);
        return true;
    }
}
