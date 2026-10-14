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

/**
 * 护甲等级系统（custom_data: {armor_type:"plugin", armor_lvl:<short>}）：
 * - 低等级剑/箭矢攻击高等级护甲，每低一级伤害 -20%；
 * - 无 custom_data 的近战武器按默认 3 级结算；
 * - 被抵消的伤害（向上取整）转化为护甲耐久损耗；
 * - 插件护甲最大耐久降为原版的 15%，耐久归零也不会消失，但归零后不再提供减伤；
 * - 铁砧用对应修复材料修复时，每用 1 个材料恢复 25% 原版最大耐久，
 *   但最大耐久永久 -2（材料数量的两倍）。
 * 攻击普通护甲或无可生效插件护甲时一律采用原版伤害。
 */
public final class ArmorSystem implements Listener {

    public static final String ARMOR_TYPE_VALUE = "plugin";
    public static final String BOW_LEVEL_META = "ssci_bow_lvl";

    private static final double REDUCTION_PER_LEVEL = 0.20;
    private static final double MAX_DURABILITY_FACTOR = 0.15;
    private static final double RESTORE_PER_MATERIAL = 0.25;
    private static final int MAX_DURABILITY_LOSS_PER_MATERIAL = 2;
    /** 无 custom_data 的近战武器攻击插件护甲时，武器按 3 级结算。 */
    private static final short DEFAULT_MELEE_LEVEL = 3;

    private final SSCustomItemsPlugin plugin;
    private final NmsBridge nms;

    public ArmorSystem(SSCustomItemsPlugin plugin) {
        this.plugin = plugin;
        this.nms = new NmsBridge();
    }

    // ---------------------------------------------------------------- 伤害结算

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)
                || victim.getEquipment() == null) {
            return;
        }
        Short weaponLevel = weaponLevel(event);
        if (weaponLevel == null) {
            return; // 非插件箭矢等 → 不影响原版
        }

        ItemStack[] armor = victim.getEquipment().getArmorContents();
        short armorLevel = -1;
        int targetIndex = -1;
        for (int i = 0; i < armor.length; i++) {
            Short pieceLevel = levelOf(armor[i]);
            // 耐久归零的插件护甲不再提供减伤（其余件照常）
            if (pieceLevel != null && !isBroken(armor[i]) && pieceLevel > armorLevel) {
                armorLevel = pieceLevel;
                targetIndex = i;
            }
        }
        if (armorLevel < 0) {
            return; // 没有可生效的插件护甲（含普通护甲）→ 原版伤害
        }
        if (weaponLevel >= armorLevel) {
            return;
        }

        int levelDifference = armorLevel - weaponLevel;
        double multiplier = Math.max(0.0, 1.0 - REDUCTION_PER_LEVEL * levelDifference);
        double rawDamage = event.getDamage();
        double finalDamage = rawDamage * multiplier;
        double blocked = rawDamage - finalDamage;
        event.setDamage(finalDamage);

        int durabilityLoss = (int) Math.ceil(blocked);
        if (durabilityLoss > 0 && targetIndex >= 0) {
            applyDurability(victim, targetIndex, armor[targetIndex], durabilityLoss);
        }
    }

    private Short weaponLevel(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            Short level = levelOf(player.getInventory().getItemInMainHand());
            // 无 custom_data 的近战武器（含原版剑）按默认 3 级结算
            return level != null ? level : DEFAULT_MELEE_LEVEL;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.hasMetadata(BOW_LEVEL_META)) {
            Object value = projectile.getMetadata(BOW_LEVEL_META).get(0).value();
            if (value instanceof Integer integer) {
                return integer.shortValue();
            }
        }
        return null;
    }

    /** 插件护甲耐久是否已归零（归零后不再提供减伤）。 */
    private static boolean isBroken(ItemStack piece) {
        if (piece == null || !(piece.getItemMeta() instanceof Damageable damageable)
                || !damageable.hasMaxDamage()) {
            return false;
        }
        return damageable.getDamage() >= damageable.getMaxDamage();
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        Short level = levelOf(event.getBow());
        if (level == null) {
            return;
        }
        event.getProjectile().setMetadata(BOW_LEVEL_META,
                new FixedMetadataValue(plugin, level.intValue()));
    }

    // ---------------------------------------------------------------- 耐久与破坏保护

    private void applyDurability(LivingEntity victim, int armorIndex,
                                 ItemStack piece, int points) {
        ensureReducedMaxDurability(piece);
        Damageable meta = (Damageable) piece.getItemMeta();
        int next = Math.min(meta.getMaxDamage(), meta.getDamage() + points);
        meta.setDamage(next);
        piece.setItemMeta(meta);

        // 与 getArmorContents() 的顺序一致：[靴子, 护腿, 胸甲, 头盔]
        EquipmentSlot[] slots = {
                EquipmentSlot.FEET, EquipmentSlot.LEGS,
                EquipmentSlot.CHEST, EquipmentSlot.HEAD
        };
        victim.getEquipment().setItem(slots[armorIndex], piece);
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (levelOf(item) == null || !(item.getItemMeta() instanceof Damageable)) {
            return;
        }
        ensureReducedMaxDurability(item);
        Damageable meta = (Damageable) item.getItemMeta();
        int cap = meta.getMaxDamage();
        int next = meta.getDamage() + event.getDamage();
        if (next >= cap) {
            // 取消原版破坏：耐久停在上限，护甲保留在身上不消失
            event.setCancelled(true);
            meta.setDamage(cap);
            item.setItemMeta(meta);
        }
    }

    /** 把插件护甲的最大耐久降为原版的 15%（只处理一次）。 */
    public void ensureReducedMaxDurability(ItemStack piece) {
        if (!(piece.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        if (damageable.hasMaxDamage()) {
            return;
        }
        int original = piece.getType().getMaxDurability();
        int cap = Math.max(1, Math.round(original * (float) MAX_DURABILITY_FACTOR));
        damageable.setMaxDamage(cap);
        if (damageable.getDamage() > cap) {
            damageable.setDamage(cap);
        }
        piece.setItemMeta(damageable);
    }

    // ---------------------------------------------------------------- 铁砧修复

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack armor = inventory.getItem(0);
        ItemStack material = inventory.getItem(1);
        if (levelOf(armor) == null || material == null) {
            return;
        }
        Material repairMaterial = repairMaterialFor(armor.getType());
        if (repairMaterial == null || material.getType() != repairMaterial) {
            return;
        }

        ensureReducedMaxDurability(armor);
        ItemStack result = armor.clone();
        Damageable resultMeta = (Damageable) result.getItemMeta();
        int originalMax = armor.getType().getMaxDurability();
        int restore = Math.max(1, Math.round(originalMax * (float) RESTORE_PER_MATERIAL));

        int newMax = Math.max(1,
                resultMeta.getMaxDamage() - MAX_DURABILITY_LOSS_PER_MATERIAL);
        resultMeta.setMaxDamage(newMax);
        resultMeta.setDamage(Math.max(0, resultMeta.getDamage() - restore));
        result.setItemMeta(resultMeta);
        event.setResult(result);

        if (event.getView() instanceof AnvilView view) {
            view.setRepairItemCountCost(1);
            if (view.getRepairCost() < 1) {
                view.setRepairCost(1);
            }
        }
    }

    /** 各护甲对应的原版修复材料。 */
    private static Material repairMaterialFor(Material armor) {
        String name = armor.name();
        if (name.startsWith("LEATHER_")) {
            return Material.LEATHER;
        }
        if (name.startsWith("CHAINMAIL_")) {
            return Material.IRON_INGOT;
        }
        if (name.startsWith("IRON_")) {
            return Material.IRON_INGOT;
        }
        if (name.startsWith("GOLDEN_")) {
            return Material.GOLD_INGOT;
        }
        if (name.startsWith("DIAMOND_")) {
            return Material.DIAMOND;
        }
        if (name.startsWith("NETHERITE_")) {
            return Material.NETHERITE_INGOT;
        }
        if (name.equals("TURTLE_HELMET")) {
            return Material.TURTLE_SCUTE;
        }
        if (name.equals("WOLF_ARMOR")) {
            return Material.ARMADILLO_SCUTE;
        }
        return null;
    }

    // ---------------------------------------------------------------- 工具

    /** 读取物品的插件等级；不是插件装备返回 null。 */
    public Short levelOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        String type = nms.customDataString(item, "armor_type");
        if (!ARMOR_TYPE_VALUE.equals(type)) {
            return null;
        }
        short level = nms.customDataShort(item, "armor_lvl");
        return level < 0 ? null : level;
    }

    /** 判断某材料是否为护甲（用于给予物品时施加耐久上限）。 */
    public static boolean isArmorMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("TURTLE_HELMET")
                || name.equals("WOLF_ARMOR");
    }
}
