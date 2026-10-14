package com.sscustomitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /ci give <命名空间:物品名>
 */
public final class CiCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sscustomitems.give";

    private final SSCustomItemsPlugin plugin;
    private final ItemManager itemManager;

    public CiCommand(SSCustomItemsPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限使用该指令。", NamedTextColor.RED));
            return true;
        }
        if (!itemManager.isReady()) {
            sender.sendMessage(Component.text("插件物品系统未就绪（初始化失败），请查看控制台日志。", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("用法: /ci give <命名空间:物品名>", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        try {
            ItemStack item = itemManager.createItem(id);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            leftover.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(player.getLocation(), stack));
            player.sendMessage(Component.text("已获得自定义物品 " + id, NamedTextColor.GREEN));
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            plugin.getLogger().severe("给予自定义物品 " + id + " 失败: " + e.getMessage());
            player.sendMessage(Component.text("给予物品失败，具体原因请查看控制台日志。", NamedTextColor.RED));
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
            return filterPrefix(List.of("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filterPrefix(new ArrayList<>(itemManager.ids()), args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(option);
            }
        }
        return result;
    }
}
