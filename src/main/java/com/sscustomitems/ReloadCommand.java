package com.sscustomitems;

import com.sscustomitems.block.BlockManager;
import com.sscustomitems.combat.DamageTypeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /sscustomitems reload [all|items|damagetypes|blocks|config]
 * 别名 /ssci。在线热重载，无需重启。
 */
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("save")) {
            return handleSave(sender, args);
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text(
                    "用法: /" + label + " reload <all|items|damagetypes|blocks|config>，或 /"
                            + label + " save <文件名> <命名空间:id>",
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

    /**
     * /ssci save <文件名> <命名空间:id>：把主手物品快速保存为自定义物品 JSON 并热重载。
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text("用法: /ssci save <文件名> <命名空间:id>",
                    NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }
        if (!itemManager.isReady()) {
            sender.sendMessage(Component.text("插件物品系统未就绪（初始化失败），请查看控制台日志。",
                    NamedTextColor.RED));
            return true;
        }
        String fileName = args[1];
        String id = args[2];
        try {
            org.bukkit.inventory.ItemStack hand =
                    player.getInventory().getItemInMainHand();
            Path file = itemManager.saveHandItem(hand, id, fileName);
            // 立即热重载，使新物品可用
            itemManager.load();
            sender.sendMessage(Component.text("已将手中物品保存为 " + id + "（"
                    + file.getFileName() + "），并已热重载。", NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "保存手中物品失败", e);
            sender.sendMessage(Component.text("保存失败: " + e.getMessage() + "，详见控制台日志。",
                    NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filterPrefix(List.of("reload", "save"), args[0]);
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
