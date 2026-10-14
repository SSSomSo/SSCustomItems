package com.sscustomitems.combat;

import com.sscustomitems.SSCustomItemsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 四个战斗指令的统一执行与补全：
 * /damagelook <类型> [最大距离] [散布] [下坠] [伤害]
 * /firelook   <秒数> [最大距离] [散布] [下坠]
 * /damageln    <类型> <半径> [散布] [下坠] [伤害]
 * /fireln      <秒数> <半径> [散布] [下坠]
 */
public final class CombatCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "sscustomitems.combat";

    private final SSCustomItemsPlugin plugin;
    private final DamageTypeManager damageTypeManager;
    private final CombatManager combatManager;

    public CombatCommand(SSCustomItemsPlugin plugin,
                         DamageTypeManager damageTypeManager,
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

        Kind kind = Kind.fromCommandName(command.getName());
        if (kind == null) {
            return false;
        }

        try {
            FileConfiguration config = plugin.getConfig();
            switch (kind) {
                case DAMAGELOOK -> {
                    if (args.length < 1 || args.length > 5) {
                        return usage(sender, kind);
                    }
                    String type = args[0];
                    double maxDistance = number(args, 1, config.getDouble("combat.max-distance", 64.0));
                    double spread = number(args, 2, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 3, config.getDouble("combat.projectile-drop", 0.05));
                    double damage = number(args, 4, config.getDouble("combat.damage-amount", 1000.0));
                    int result = combatManager.lookDamage(player, type,
                            shotSettings(maxDistance, spread, drop, damage));
                    sender.sendMessage(result == 1
                            ? Component.text("已对视角所指实体造成 " + damage + " 点伤害。", NamedTextColor.GREEN)
                            : Component.text("未命中目标（可能超出最大距离或被方块遮挡）。", NamedTextColor.YELLOW));
                }
                case FIRELOOK -> {
                    if (args.length < 1 || args.length > 4) {
                        return usage(sender, kind);
                    }
                    int seconds = Integer.parseInt(args[0]);
                    double maxDistance = number(args, 1, config.getDouble("combat.max-distance", 64.0));
                    double spread = number(args, 2, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 3, config.getDouble("combat.projectile-drop", 0.05));
                    int result = combatManager.lookFire(player, seconds,
                            shotSettings(maxDistance, spread, drop,
                                    config.getDouble("combat.damage-amount", 1000.0)));
                    sender.sendMessage(result == 1
                            ? Component.text("已点燃视角所指实体，燃烧 " + seconds + " 秒。", NamedTextColor.GREEN)
                            : Component.text("未命中目标（可能超出最大距离或被方块遮挡）。", NamedTextColor.YELLOW));
                }
                case DAMAGELN -> {
                    if (args.length < 2 || args.length > 5) {
                        return usage(sender, kind);
                    }
                    String type = args[0];
                    double radius = Double.parseDouble(args[1]);
                    double spread = number(args, 2, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 3, config.getDouble("combat.projectile-drop", 0.05));
                    double damage = number(args, 4, config.getDouble("combat.damage-amount", 1000.0));
                    double maxDistance = config.getDouble("combat.max-distance", 64.0);
                    int result = combatManager.locationDamage(player, type, radius,
                            shotSettings(maxDistance, spread, drop, damage));
                    sender.sendMessage(Component.text("已对点位半径内 " + result + " 个实体各造成 "
                            + damage + " 点伤害。", NamedTextColor.GREEN));
                }
                case FIRELN -> {
                    if (args.length < 2 || args.length > 4) {
                        return usage(sender, kind);
                    }
                    int seconds = Integer.parseInt(args[0]);
                    double radius = Double.parseDouble(args[1]);
                    double spread = number(args, 2, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 3, config.getDouble("combat.projectile-drop", 0.05));
                    double maxDistance = config.getDouble("combat.max-distance", 64.0);
                    int result = combatManager.locationFire(player, seconds, radius,
                            shotSettings(maxDistance, spread, drop,
                                    config.getDouble("combat.damage-amount", 1000.0)));
                    sender.sendMessage(Component.text("已点燃点位半径内 " + result + " 个实体，燃烧 " + seconds + " 秒。", NamedTextColor.GREEN));
                }
                case KILLLOOK -> {
                    if (args.length > 3) {
                        return usage(sender, kind);
                    }
                    double maxDistance = number(args, 0, config.getDouble("combat.max-distance", 64.0));
                    double spread = number(args, 1, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 2, config.getDouble("combat.projectile-drop", 0.05));
                    int result = combatManager.lookKill(player,
                            shotSettings(maxDistance, spread, drop,
                                    config.getDouble("combat.damage-amount", 1000.0)));
                    sender.sendMessage(result == 1
                            ? Component.text("已清除视角所指实体。", NamedTextColor.GREEN)
                            : Component.text("未命中目标（可能超出最大距离或被方块遮挡）。", NamedTextColor.YELLOW));
                }
                case KILLLN -> {
                    if (args.length < 1 || args.length > 3) {
                        return usage(sender, kind);
                    }
                    double radius = Double.parseDouble(args[0]);
                    double spread = number(args, 1, config.getDouble("combat.spread", 0.0));
                    double drop = number(args, 2, config.getDouble("combat.projectile-drop", 0.05));
                    double maxDistance = config.getDouble("combat.max-distance", 64.0);
                    int result = combatManager.locationKill(player, radius,
                            shotSettings(maxDistance, spread, drop,
                                    config.getDouble("combat.damage-amount", 1000.0)));
                    sender.sendMessage(Component.text("已清除点位半径内 " + result + " 个实体。",
                            NamedTextColor.GREEN));
                }
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("参数必须是数字。", NamedTextColor.RED));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "战斗指令执行失败", e);
            sender.sendMessage(Component.text("执行失败：" + e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private CombatManager.ShotSettings shotSettings(double maxDistance, double spread,
                                                    double drop, double damageAmount) {
        double raySize = plugin.getConfig().getDouble("combat.ray-size", 0.2);
        return new CombatManager.ShotSettings(maxDistance, spread, drop, raySize, (float) damageAmount);
    }

    private boolean usage(CommandSender sender, Kind kind) {
        sender.sendMessage(Component.text("用法: " + kind.usage, NamedTextColor.YELLOW));
        return true;
    }

    private static double number(String[] args, int index, double defaultValue) {
        return index < args.length ? Double.parseDouble(args[index]) : defaultValue;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        Kind kind = Kind.fromCommandName(command.getName());
        if (kind == null) {
            return List.of();
        }

        boolean typeAtFirst = kind == Kind.DAMAGELOOK || kind == Kind.DAMAGELN;
        if (typeAtFirst && args.length == 1) {
            return filterPrefix(damageTypeManager.allTypeNames(), args[0]);
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

    private enum Kind {
        DAMAGELOOK("/damagelook <伤害类型> [最大距离] [散布范围] [弹道下坠] [伤害大小]"),
        FIRELOOK("/firelook <燃烧秒数> [最大距离] [散布范围] [弹道下坠]"),
        DAMAGELN("/damageln <伤害类型> <半径> [散布范围] [弹道下坠] [伤害大小]"),
        FIRELN("/fireln <燃烧秒数> <半径> [散布范围] [弹道下坠]"),
        KILLLOOK("/killlook [最大距离] [散布范围] [弹道下坠]"),
        KILLLN("/killln <半径> [散布范围] [弹道下坠]");

        private final String usage;

        Kind(String usage) {
            this.usage = usage;
        }

        private static Kind fromCommandName(String name) {
            for (Kind kind : values()) {
                if (kind.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                    return kind;
                }
            }
            return null;
        }
    }
}
