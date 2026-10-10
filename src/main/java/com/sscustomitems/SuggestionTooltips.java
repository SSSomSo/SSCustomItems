package com.sscustomitems;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.sscustomitems.block.CustomBlock;
import com.sscustomitems.block.MenuSpec;
import com.sscustomitems.combat.CustomDamageType;
import com.sscustomitems.combat.DamageTypeManager;
import com.sscustomitems.block.BlockManager;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SuggestionTooltips implements Listener {

    private final SSCustomItemsPlugin plugin;
    private final ItemManager itemManager;
    private final DamageTypeManager damageTypeManager;
    private final BlockManager blockManager;

    public SuggestionTooltips(SSCustomItemsPlugin plugin, ItemManager itemManager,
                              DamageTypeManager damageTypeManager, BlockManager blockManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.damageTypeManager = damageTypeManager;
        this.blockManager = blockManager;
    }

    @EventHandler
    public void onSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        try {
            String rawBuffer = event.getBuffer();
            String buffer = rawBuffer.startsWith("/") ? rawBuffer.substring(1) : rawBuffer;
            String[] tokens = buffer.split(" ", -1);
            String commandName = tokens[0].toLowerCase(Locale.ROOT);
            int position = tokens.length - 2;
            String current = tokens[tokens.length - 1];

            SuggestionsBuilder builder = new SuggestionsBuilder(rawBuffer, rawBuffer.lastIndexOf(' ') + 1);
            if (!fill(commandName, position, current, builder)) return;
            Suggestions suggestions = builder.build();
            if (!suggestions.getList().isEmpty()) event.setSuggestions(suggestions);
        } catch (Exception e) { plugin.getLogger().warning("生成增强补全失败: " + e.getMessage()); }
    }

    private boolean fill(String command, int position, String current, SuggestionsBuilder builder) {
        switch (command) {
            case "ci" -> {
                if (position == 0) add(builder, current, "give", tip("给予自定义物品", "获取 1 个已配置的自定义物品"));
                else if (position == 1) {
                    for (String id : new ArrayList<>(itemManager.ids())) {
                        CustomItem item = itemManager.getItem(id);
                        add(builder, current, id, tip(id, "载体原版物品: " + item.vanillaId()));
                    }
                } else return false;
            }
            case "cb" -> {
                if (position == 0) {
                    add(builder, current, "place", tip("放置自定义方块", "在视角所指方块面上放置"));
                    add(builder, current, "give", tip("给予方块物品", "获得带标记的载体物品，可正常放置"));
                } else if (position == 1) {
                    for (String id : blockManager.definitionIds()) {
                        CustomBlock block = blockManager.getDefinition(id);
                        String extra = "载体原版方块: " + block.vanillaId();
                        if (block.menu() != null) extra += "\n菜单: " + menuLabel(block.menu().mode());
                        if (block.door() != null) extra += "\n门锁钥匙: " + block.door().lock() + "\n红石可开: " + (block.door().allowRedstone() ? "是" : "否");
                        if (block.unbreakable()) extra += "\n无法破坏: 是（挖掘与爆炸均无效）";
                        add(builder, current, id, tip(id, extra));
                    }
                } else return false;
            }
            case "damagelook" -> {
                if (position == 0) addDamageTypes(builder, current);
                else if (position == 1) add(builder, current, "64", tip("最大距离（格）", "弹道最远判定距离，超出不伤害；默认 64"));
                else if (position == 2) add(builder, current, "0", tip("散布范围（格）", "最大距离处视角平面随机偏移半径；默认 0"));
                else if (position == 3) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "0=直线 0.03≈雪球 0.05≈箭矢 0.1≈曲射；默认 0.05"));
                else if (position == 4) add(builder, current, "1000", tip("伤害大小（点）", "每个目标受到的伤害；默认 1000"));
                else return false;
            }
            case "firelook" -> {
                if (position == 0) add(builder, current, "8", tip("燃烧时间（秒）", "目标被点燃后持续燃烧的秒数"));
                else if (position == 1) add(builder, current, "64", tip("最大距离（格）", "默认 64"));
                else if (position == 2) add(builder, current, "0", tip("散布范围（格）", "默认 0"));
                else if (position == 3) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "默认 0.05"));
                else return false;
            }
            case "damageln" -> {
                if (position == 0) addDamageTypes(builder, current);
                else if (position == 1) add(builder, current, "4", tip("影响半径（格）", "以弹道落点为球心，球内实体全部受伤"));
                else if (position == 2) add(builder, current, "0", tip("散布范围（格）", "默认 0"));
                else if (position == 3) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "默认 0.05"));
                else if (position == 4) add(builder, current, "1000", tip("伤害大小（点）", "默认 1000"));
                else return false;
            }
            case "fireln" -> {
                if (position == 0) add(builder, current, "8", tip("燃烧时间（秒）", "默认持续燃烧的秒数"));
                else if (position == 1) add(builder, current, "4", tip("影响半径（格）", "落点球内实体全部被点燃"));
                else if (position == 2) add(builder, current, "0", tip("散布范围（格）", "默认 0"));
                else if (position == 3) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "默认 0.05"));
                else return false;
            }
            case "killlook" -> {
                if (position == 0) add(builder, current, "64", tip("最大距离（格）", "直接清除视角所指实体；默认 64"));
                else if (position == 1) add(builder, current, "0", tip("散布范围（格）", "默认 0"));
                else if (position == 2) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "默认 0.05"));
                else return false;
            }
            case "killln" -> {
                if (position == 0) add(builder, current, "4", tip("影响半径（格）", "直接清除落点球内全部实体"));
                else if (position == 1) add(builder, current, "0", tip("散布范围（格）", "默认 0"));
                else if (position == 2) add(builder, current, "0.05", tip("弹道下坠（格/tick²）", "默认 0.05"));
                else return false;
            }
            case "sscustomitems", "ssci" -> {
                if (position == 0) add(builder, current, "reload", tip("热重载", "不重启服务器重新读取配置"));
                else if (position == 1) {
                    add(builder, current, "all", tip("all", "重载配置/物品/伤害类型/方块"));
                    add(builder, current, "items", tip("items", "重载 items/ 自定义物品"));
                    add(builder, current, "damagetypes", tip("damagetypes", "重载 damage_types/"));
                    add(builder, current, "blocks", tip("blocks", "重载 blocks/ 自定义方块"));
                    add(builder, current, "config", tip("config", "重载 config.yml"));
                } else return false;
            }
            default -> { return false; }
        }
        return true;
    }

    private void addDamageTypes(SuggestionsBuilder builder, String current) {
        List<String> vanilla = new ArrayList<>();
        List<String> custom = new ArrayList<>();
        for (String name : damageTypeManager.allTypeNames()) {
            CustomDamageType type = damageTypeManager.get(name);
            if (type != null) custom.add(name); else vanilla.add(name);
        }
        for (String name : vanilla) add(builder, current, name, tip("原版伤害类型", name));
        for (String name : custom) {
            CustomDamageType type = damageTypeManager.get(name);
            add(builder, current, name, tip("自定义伤害类型", "实际结算类型: " + type.baseType()));
        }
    }

    private static String menuLabel(MenuSpec.Mode mode) {
        return switch (mode) {
            case CHEST -> "箱子（27 格）";
            case BIG_CHEST -> "大箱子（54 格）";
            case SHULKER -> "潜影盒（内容随物品保留）";
            case ENDER -> "末影箱（玩家个人）";
        };
    }

    private static Component tip(String title, String description) {
        Component component = Component.text(title, NamedTextColor.GOLD);
        for (String line : description.split("\n"))
            component = component.append(Component.newline()).append(Component.text(line, NamedTextColor.GRAY));
        return component;
    }

    private static void add(SuggestionsBuilder builder, String prefix, String suggestion, Component tooltip) {
        if (suggestion.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            Message message = MessageComponentSerializer.message().serialize(tooltip);
            builder.suggest(suggestion, message);
        }
    }
}
