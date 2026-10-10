package com.sscustomitems;

import com.sscustomitems.block.BlockManager;
import com.sscustomitems.combat.DamageTypeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReloadCommand implements CommandExecutor, TabCompleter {

    public static final List<String> TARGETS =
            List.of("all", "items", "damagetypes", "blocks", "config");

    private static final String PERMISSION = "sscustomitems.reload";

    private final SSCustomItemsPlugin plugin;
    private final ItemManager itemManager;
    private final ActionManager actionManager;
    private final DamageTypeManager damageTypeManager;
    private final BlockManager blockManager;

    public ReloadCommand(SSCustomItemsPlugin plugin,
                         ItemManager itemManager,
                         ActionManager actionManager,
                         DamageTypeManager damageTypeManager,
                         BlockManager blockManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.actionManager = actionManager;
        this.damageTypeManager = damageTypeManager;
        this.blockManager = blockManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限使用该指令。", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text(
                    "用法: /" + label + " reload <all|items|damagetypes|blocks|config>",
                    NamedTextColor.YELLOW));
            return true;
        }

        String target = args[1].toLowerCase(Locale.ROOT);
        if (!TARGETS.contains(target)) {
            sender.sendMessage(Component.text("未知的重载目标: " + target, NamedTextColor.RED));
            return true;
        }

        try {
            switch (target) {
                case "config" -> reloadConfig(sender);
                case "items" -> {
                    itemManager.load();
                    sender.sendMessage(Component.text("已热重载自定义物品。", NamedTextColor.GREEN));
                }
                case "damagetypes" -> {
                    damageTypeManager.load();
                    sender.sendMessage(Component.text("已热重载自定义伤害类型。", NamedTextColor.GREEN));
                }
                case "blocks" -> {
                    blockManager.reloadDefinitions();
                    sender.sendMessage(Component.text("已热重载自定义方块。", NamedTextColor.GREEN));
                }
                case "all" -> {
                    reloadConfig(sender);
                    itemManager.load();
                    damageTypeManager.load();
                    blockManager.reloadDefinitions();
                    sender.sendMessage(Component.text("已热重载全部内容（配置/物品/伤害类型/方块）。",
                            NamedTextColor.GREEN));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "热重载失败", e);
            sender.sendMessage(Component.text("热重载失败: " + e.getMessage()
                    + "，详见控制台日志。", NamedTextColor.RED));
        }
        return true;
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfig();
        if (actionManager != null) {
            actionManager.reloadSettings();
        }
        if (blockManager != null) {
            blockManager.reloadSettings();
        }
        sender.sendMessage(Component.text("已重载 config.yml。", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("reload"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return filterPrefix(TARGETS, args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(normalized)) {
                result.add(option);
            }
        }
        return result;
    }
}
