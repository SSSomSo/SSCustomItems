package com.sscustomitems.block;

import com.sscustomitems.SSCustomItemsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BlockCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sscustomitems.block";
    private static final double REACH = 6.0;

    private final SSCustomItemsPlugin plugin;
    private final BlockManager blockManager;

    public BlockCommand(SSCustomItemsPlugin plugin, BlockManager blockManager) {
        this.plugin = plugin;
        this.blockManager = blockManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("你没有权限使用该指令。", NamedTextColor.RED));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("该指令只能由玩家执行。", NamedTextColor.RED));
            return true;
        }
        if (args.length != 2 || !(args[0].equals("place") || args[0].equals("give"))) {
            sender.sendMessage(Component.text("用法: /cb place <命名空间:id> 或 /cb give <命名空间:id>", NamedTextColor.YELLOW));
            return true;
        }

        CustomBlock def = blockManager.getDefinition(args[1]);
        if (def == null) {
            sender.sendMessage(Component.text("不存在的自定义方块: " + args[1], NamedTextColor.RED));
            return true;
        }

        if (args[0].equals("give")) {
            org.bukkit.inventory.ItemStack item = blockManager.taggedItem(def);
            player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            sender.sendMessage(Component.text("已获得 " + def.id() + " 的载体物品。", NamedTextColor.GREEN));
            return true;
        }

        RayTraceResult result = player.rayTraceBlocks(REACH);
        if (result == null || result.getHitBlock() == null || result.getHitBlockFace() == null) {
            sender.sendMessage(Component.text("视角未指向任何方块。", NamedTextColor.YELLOW));
            return true;
        }
        BlockFace face = result.getHitBlockFace();
        Block target = result.getHitBlock().getRelative(face);
        if (!(target.getType().isAir() || target.isReplaceable())) {
            sender.sendMessage(Component.text("该位置无法放置方块。", NamedTextColor.YELLOW));
            return true;
        }
        if (org.bukkit.Tag.DOORS.isTagged(blockManager.materialOf(def.vanillaId()))
                && !(target.getRelative(BlockFace.UP).getType().isAir()
                || target.getRelative(BlockFace.UP).isReplaceable())) {
            sender.sendMessage(Component.text("门上方位置被占用，无法放置。", NamedTextColor.YELLOW));
            return true;
        }

        try { blockManager.placeBlock(player, def, target); }
        catch (IllegalStateException e) {
            sender.sendMessage(Component.text("放置失败: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("已放置 " + def.id() + "。", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return filterPrefix(List.of("place", "give"), args[0]);
        if (args.length == 2) return filterPrefix(blockManager.definitionIds(), args[1]);
        return List.of();
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options)
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(option);
        return result;
    }
}
