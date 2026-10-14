package com.sscustomitems;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 物品触发动作的运行时：
 * - 定时轮询六个位置，检测“切换/穿戴”边缘触发与 in* 持续触发；
 * - PlayerInteractEvent 处理左键/右键；
 * - 玩家退出清理状态。
 */
public final class ActionManager implements Listener {

    private final SSCustomItemsPlugin plugin;
    private final ItemManager itemManager;
    private int checkIntervalTicks;
    private int repeatIntervalTicks;

    private final Map<UUID, EnumMap<SlotType, String>> previous = new java.util.HashMap<>();
    private final Map<UUID, EnumMap<SlotType, Integer>> countdown = new java.util.HashMap<>();
    private org.bukkit.scheduler.BukkitTask task;

    public ActionManager(SSCustomItemsPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.checkIntervalTicks = Math.max(1,
                plugin.getConfig().getInt("check-interval-ticks", 5));
        this.repeatIntervalTicks = Math.max(1,
                plugin.getConfig().getInt("repeat-interval-ticks", 20));
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                checkIntervalTicks, checkIntervalTicks);
    }

    /** 热重载后按 config 新间隔重启检测任务（状态保留）。 */
    public void reloadSettings() {
        checkIntervalTicks = Math.max(1,
                plugin.getConfig().getInt("check-interval-ticks", 5));
        repeatIntervalTicks = Math.max(1,
                plugin.getConfig().getInt("repeat-interval-ticks", 20));
        if (task != null) {
            task.cancel();
            start();
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        previous.clear();
        countdown.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            EnumMap<SlotType, String> prevMap =
                    previous.computeIfAbsent(uuid, u -> new EnumMap<>(SlotType.class));
            EnumMap<SlotType, Integer> countdownMap =
                    countdown.computeIfAbsent(uuid, u -> freshCountdown());

            for (SlotType slot : SlotType.values()) {
                String current = readId(slot.getItem(player));
                String prev = prevMap.get(slot);

                if (current != null) {
                    if (!current.equals(prev)) {
                        fire(player, current, slot.edgeTrigger());
                    }
                    int remaining = countdownMap.get(slot) - checkIntervalTicks;
                    if (remaining <= 0) {
                        fire(player, current, slot.continuousTrigger());
                        remaining = repeatIntervalTicks;
                    }
                    countdownMap.put(slot, remaining);
                } else {
                    countdownMap.put(slot, repeatIntervalTicks);
                }
                prevMap.put(slot, current);
            }
        }
    }

    private EnumMap<SlotType, Integer> freshCountdown() {
        EnumMap<SlotType, Integer> map = new EnumMap<>(SlotType.class);
        for (SlotType slot : SlotType.values()) {
            map.put(slot, repeatIntervalTicks);
        }
        return map;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Trigger trigger;
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> trigger = Trigger.LEFTCLICK;
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> trigger = Trigger.RIGHTCLICK;
            default -> {
                return;
            }
        }
        // 右键副手物品时 Bukkit 会以 OFF_HAND 手触发事件，getItem() 即对应手的物品
        if (trigger == Trigger.RIGHTCLICK && event.getHand() != EquipmentSlot.HAND
                && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        String id = readId(event.getItem());
        if (id != null) {
            fire(event.getPlayer(), id, trigger);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        previous.remove(uuid);
        countdown.remove(uuid);
    }

    /** 以控制台身份执行某物品某触发下的全部命令。 */
    private void fire(Player player, String itemId, Trigger trigger) {
        CustomItem item = itemManager.getItem(itemId);
        if (item == null) {
            return;
        }
        List<String> commands = item.actions().get(trigger);
        if (commands.isEmpty()) {
            return;
        }
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String raw : commands) {
            String command = raw.trim();
            if (command.isEmpty()) {
                continue;
            }
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            command = command.replace("%player%", player.getName());
            try {
                Bukkit.dispatchCommand(console, command);
            } catch (Exception e) {
                plugin.getLogger().warning("物品 " + itemId + " 的触发 " + trigger
                        + " 执行命令失败 \"" + raw + "\": " + e.getMessage());
            }
        }
    }

    private String readId(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(itemManager.itemIdKey(), org.bukkit.persistence.PersistentDataType.STRING);
    }
}
