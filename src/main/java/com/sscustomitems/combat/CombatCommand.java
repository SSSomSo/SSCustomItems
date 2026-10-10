package com.sscustomitems.combat;

import com.sscustomitems.SSCustomItemsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CombatCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sscustomitems.combat";

    private final SSCustomItemsPlugin plugin;
    private final DamageTypeManager damageTypeManager;
    private final CombatManager combatManager;

    public CombatCommand(SSCustomItemsPlugin plugin, DamageTypeManager damageTypeManager,
                         CombatManager combatManager) {
        this.plugin = plugin;
        this.damageTypeManager = damageTypeManager;
        this.combatManager = combatManager;
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
        try {
            switch (label.toLowerCase(Locale.ROOT)) {
                case "damagelook" -> executeDamageLook(player, args);
                case "firelook" -> executeFireLook(player, args);
                case "damageln" -> executeDamageLine(player, args);
                case "fireln" -> executeFireLine(player, args);
                case "killlook" -> executeKillLook(player, args);
                case "killln" -> executeKillLine(player, args);
                default -> sender.sendMessage(Component.text("未知指令: " + label, NamedTextColor.RED));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "战斗指令执行失败", e);
            sender.sendMessage(Component.text("执行失败: " + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void executeDamageLook(Player player, String[] args) {
        String damageType = args.length > 0 ? args[0] : "minecraft:player_attack";
        double maxDistance = args.length > 1 ? parseDouble(args[1], "最大距离") : 64.0;
        double spread = args.length > 2 ? parseDouble(args[2], "散布范围") : 0.0;
        double gravity = args.length > 3 ? parseDouble(args[3], "弹道下坠") : 0.05;
        double damage = args.length > 4 ? parseDouble(args[4], "伤害大小") : 1000.0;
        combatManager.damageLook(player, damageType, maxDistance, spread, gravity, damage);
    }

    private void executeFireLook(Player player, String[] args) {
        int fireSeconds = args.length > 0 ? parseInt(args[0], "燃烧时间") : 8;
        double maxDistance = args.length > 1 ? parseDouble(args[1], "最大距离") : 64.0;
        double spread = args.length > 2 ? parseDouble(args[2], "散布范围") : 0.0;
        double gravity = args.length > 3 ? parseDouble(args[3], "弹道下坠") : 0.05;
        combatManager.fireLook(player, fireSeconds, maxDistance, spread, gravity);
    }

    private void executeDamageLine(Player player, String[] args) {
        String damageType = args.length > 0 ? args[0] : "minecraft:player_attack";
        double radius = args.length > 1 ? parseDouble(args[1], "影响半径") : 4.0;
        double spread = args.length > 2 ? parseDouble(args[2], "散布范围") : 0.0;
        double gravity = args.length > 3 ? parseDouble(args[3], "弹道下坠") : 0.05;
        double damage = args.length > 4 ? parseDouble(args[4], "伤害大小") : 1000.0;
        combatManager.damageLine(player, damageType, radius, spread, gravity, damage);
    }

    private void executeFireLine(Player player, String[] args) {
        int fireSeconds = args.length > 0 ? parseInt(args[0], "燃烧时间") : 8;
        double radius = args.length > 1 ? parseDouble(args[1], "影响半径") : 4.0;
        double spread = args.length > 2 ? parseDouble(args[2], "散布范围") : 0.0;
        double gravity = args.length > 3 ? parseDouble(args[3], "弹道下坠") : 0.05;
        combatManager.fireLine(player, fireSeconds, radius, spread, gravity);
    }

    private void executeKillLook(Player player, String[] args) {
        double maxDistance = args.length > 0 ? parseDouble(args[0], "最大距离") : 64.0;
        double spread = args.length > 1 ? parseDouble(args[1], "散布范围") : 0.0;
        double gravity = args.length > 2 ? parseDouble(args[2], "弹道下坠") : 0.05;
        combatManager.killLook(player, maxDistance, spread, gravity);
    }

    private void executeKillLine(Player player, String[] args) {
        double radius = args.length > 0 ? parseDouble(args[0], "影响半径") : 4.0;
        double spread = args.length > 1 ? parseDouble(args[1], "散布范围") : 0.0;
        double gravity = args.length > 2 ? parseDouble(args[2], "弹道下坠") : 0.05;
        combatManager.killLine(player, radius, spread, gravity);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        String name = label.toLowerCase(Locale.ROOT);
        int position = args.length - 1;
        String current = args[args.length - 1];
        return switch (name) {
            case "damagelook", "damageln" -> position == 0
                    ? filterPrefix(new ArrayList<>(damageTypeManager.allTypeNames()), current) : List.of();
            default -> List.of();
        };
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options)
            if (option.toLowerCase(Locale.ROOT).startsWith(normalized)) result.add(option);
        return result;
    }

    private static double parseDouble(String raw, String field) {
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(field + " 必须是数字，收到: " + raw); }
    }

    private static int parseInt(String raw, String field) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(field + " 必须是整数，收到: " + raw); }
    }
}
