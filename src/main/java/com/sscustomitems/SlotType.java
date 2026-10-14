package com.sscustomitems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 可被监测的六个装备/手持位置，以及各自对应的
 * “切换时触发一次”和“处于该位置时持续触发”两个 Trigger。
 */
public enum SlotType {

    MAIN_HAND(Trigger.MAINHAND, Trigger.INMAINHAND),
    OFF_HAND(Trigger.OFFHAND, Trigger.INOFFHAND),
    HELMET(Trigger.WEAR_HELMET, Trigger.INHELMET),
    CHESTPLATE(Trigger.WEAR_CHESTPLATE, Trigger.INCHESTPLATE),
    LEGGINGS(Trigger.WEAR_LEGGINGS, Trigger.INLEGGINGS),
    BOOTS(Trigger.WEAR_BOOTS, Trigger.INBOOTS);

    private final Trigger edgeTrigger;
    private final Trigger continuousTrigger;

    SlotType(Trigger edgeTrigger, Trigger continuousTrigger) {
        this.edgeTrigger = edgeTrigger;
        this.continuousTrigger = continuousTrigger;
    }

    public Trigger edgeTrigger() {
        return edgeTrigger;
    }

    public Trigger continuousTrigger() {
        return continuousTrigger;
    }

    public ItemStack getItem(Player player) {
        PlayerInventory inventory = player.getInventory();
        return switch (this) {
            case MAIN_HAND -> inventory.getItemInMainHand();
            case OFF_HAND -> inventory.getItemInOffHand();
            case HELMET -> inventory.getHelmet();
            case CHESTPLATE -> inventory.getChestplate();
            case LEGGINGS -> inventory.getLeggings();
            case BOOTS -> inventory.getBoots();
        };
    }
}
