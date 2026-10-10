package com.sscustomitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.metadata.FixedMetadataValue;

public final class ArmorSystem implements Listener {

    public static final String ARMOR_TYPE_VALUE = "plugin";
    public static final String BOW_LEVEL_META = "ssci_bow_lvl";

    private static final double REDUCTION_PER_LEVEL = 0.20;
    private static final double MAX_DURABILITY_FACTOR = 0.15;
    private static final double RESTORE_PER_MATERIAL = 0.25;
    private static final int MAX_DURABILITY_LOSS_PER_MATERIAL = 2;
    private static final short DEFAULT_MELEE_LEVEL = 3;

    private final SSCustomItemsPlugin plugin;
    private final NmsBridge nms;

    public ArmorSystem(SSCustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.nms = new NmsBridge();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim.getEquipment() == null) return;
        Short weaponLevel = weaponLevel(event);
        if (weaponLevel == null) return;

        ItemStack[] armor = victim.getEquipment().getArmorContents();
        short armorLevel = -1;
        int targetIndex = -1;
        for (int i = 0; i < armor.length; i++) {
            Short pieceLevel = levelOf(armor[i]);
            if (pieceLevel != null && !isBroken(armor[i]) && pieceLevel > armorLevel) {
                armorLevel = pieceLevel;
                targetIndex = i;
            }
        }
        if (armorLevel < 0) return;
        if (weaponLevel >= armorLevel) return;

        int levelDifference = armorLevel - weaponLevel;
        double multiplier = Math.max(0.0, 1.0 - REDUCTION_PER_LEVEL * levelDifference);
        double rawDamage = event.getDamage();
        double finalDamage = rawDamage * multiplier;
        double blocked = rawDamage - finalDamage;
        event.setDamage(finalDamage);

        int durabilityLoss = (int) Math.ceil(blocked);
        if (durabilityLoss > 0 && targetIndex >= 0) applyDurability(victim, targetIndex, armor[targetIndex], durabilityLoss);
    }

    private Short weaponLevel(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Short level = levelOf(player.getInventory().getItemInMainHand());
            return level != null ? level : DEFAULT_MELEE_LEVEL;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.hasMetadata(BOW_LEVEL_META)) {
            Object value = projectile.getMetadata(BOW_LEVEL_META).get(0).value();
            if (value instanceof Integer integer) return integer.shortValue();
        }
        return null;
    }

    private static boolean isBroken(ItemStack piece) {
        if (piece == null || !(piece.getItemMeta() instanceof Damageable damageable) || !damageable.hasMaxDamage()) return false;
        return damageable.getDamage() >= damageable.getMaxDamage();
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        Short level = levelOf(event.getBow());
        if (level == null) return;
        event.getProjectile().setMetadata(BOW_LEVEL_META, new FixedMetadataValue(plugin, level.intValue()));
    }

    private void applyDurability(LivingEntity victim, int armorIndex, ItemStack piece, int points) {
        ensureReducedMaxDurability(piece);
        Damageable meta = (Damageable) piece.getItemMeta();
        int next = Math.min(meta.getMaxDamage(), meta.getDamage() + points);
        meta.setDamage(next);
        piece.setItemMeta(meta);
        EquipmentSlot[] slots = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };
        victim.getEquipment().setItem(slots[armorIndex], piece);
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (levelOf(item) == null || !(item.getItemMeta() instanceof Damageable)) return;
        ensureReducedMaxDurability(item);
        Damageable meta = (Damageable) item.getItemMeta();
        int cap = meta.getMaxDamage();
        int next = meta.getDamage() + event.getDamage();
        if (next >= cap) { event.setCancelled(true); meta.setDamage(cap); item.setItemMeta(meta); }
    }

    public void ensureReducedMaxDurability(ItemStack piece) {
        if (!(piece.getItemMeta() instanceof Damageable damageable)) return;
        if (damageable.hasMaxDamage()) return;
        int original = piece.getType().getMaxDurability();
        int cap = Math.max(1, Math.round(original * (float) MAX_DURABILITY_FACTOR));
        damageable.setMaxDamage(cap);
        if (damageable.getDamage() > cap) damageable.setDamage(cap);
        piece.setItemMeta(damageable);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack armor = inventory.getItem(0);
        ItemStack material = inventory.getItem(1);
        if (levelOf(armor) == null || material == null) return;
        Material repairMaterial = repairMaterialFor(armor.getType());
        if (repairMaterial == null || material.getType() != repairMaterial) return;

        ensureReducedMaxDurability(armor);
        ItemStack result = armor.clone();
        Damageable resultMeta = (Damageable) result.getItemMeta();
        int originalMax = armor.getType().getMaxDurability();
        int restore = Math.max(1, Math.round(originalMax * (float) RESTORE_PER_MATERIAL));
        int newMax = Math.max(1, resultMeta.getMaxDamage() - MAX_DURABILITY_LOSS_PER_MATERIAL);
        resultMeta.setMaxDamage(newMax);
        resultMeta.setDamage(Math.max(0, resultMeta.getDamage() - restore));
        result.setItemMeta(resultMeta);
        event.setResult(result);

        if (event.getView() instanceof AnvilView view) {
            view.setRepairItemCountCost(1);
            if (view.getRepairCost() < 1) view.setRepairCost(1);
        }
    }

    private static Material repairMaterialFor(Material armor) {
        String name = armor.name();
        if (name.startsWith("LEATHER_")) return Material.LEATHER;
        if (name.startsWith("CHAINMAIL_")) return Material.IRON_INGOT;
        if (name.startsWith("IRON_")) return Material.IRON_INGOT;
        if (name.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
        if (name.startsWith("DIAMOND_")) return Material.DIAMOND;
        if (name.startsWith("NETHERITE_")) return Material.NETHERITE_INGOT;
        if (name.equals("TURTLE_HELMET")) return Material.TURTLE_SCUTE;
        if (name.equals("WOLF_ARMOR")) return Material.ARMADILLO_SCUTE;
        return null;
    }

    public Short levelOf(ItemStack item) {
        if (item == null) return null;
        String type = nms.customDataString(item, "armor_type");
        if (!ARMOR_TYPE_VALUE.equals(type)) return null;
        short level = nms.customDataShort(item, "armor_lvl");
        return level < 0 ? null : level;
    }

    public static boolean isArmorMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET") || name.equals("WOLF_ARMOR");
    }
}
