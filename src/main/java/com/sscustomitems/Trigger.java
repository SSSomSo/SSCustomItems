package com.sscustomitems;

/**
 * 支持的全部触发类型。
 *
 * slot 为 null 的（点击类）由事件直接触发；
 * continuous=true 的 in* 类在物品处于对应位置时按配置间隔反复执行，
 * 其余为状态变化时触发一次。
 */
public enum Trigger {

    MAINHAND(SlotType.MAIN_HAND, false),
    OFFHAND(SlotType.OFF_HAND, false),
    INMAINHAND(SlotType.MAIN_HAND, true),
    INOFFHAND(SlotType.OFF_HAND, true),

    WEAR_HELMET(SlotType.HELMET, false),
    WEAR_CHESTPLATE(SlotType.CHESTPLATE, false),
    WEAR_LEGGINGS(SlotType.LEGGINGS, false),
    WEAR_BOOTS(SlotType.BOOTS, false),
    INHELMET(SlotType.HELMET, true),
    INCHESTPLATE(SlotType.CHESTPLATE, true),
    INLEGGINGS(SlotType.LEGGINGS, true),
    INBOOTS(SlotType.BOOTS, true),

    LEFTCLICK(null, false),
    RIGHTCLICK(null, false);

    private final SlotType slot;
    private final boolean continuous;

    Trigger(SlotType slot, boolean continuous) {
        this.slot = slot;
        this.continuous = continuous;
    }

    public SlotType slot() {
        return slot;
    }

    public boolean continuous() {
        return continuous;
    }

    /**
     * 把规范化后的路径片段解析成 Trigger，无法识别返回 null。
     * 支持：offhand/mainhand/inoffhand/inmainhand/leftclick/rightclick、
     * wear.helmet（chestplate/leggings/boots）、
     * inhelmet/inchestplate/inleggings/inboots。
     */
    public static Trigger fromPath(java.util.List<String> segments) {
        if (segments.size() == 1) {
            return switch (segments.get(0)) {
                case "offhand" -> OFFHAND;
                case "mainhand" -> MAINHAND;
                case "inoffhand" -> INOFFHAND;
                case "inmainhand" -> INMAINHAND;
                case "leftclick", "left_click" -> LEFTCLICK;
                case "rightclick", "right_click" -> RIGHTCLICK;
                case "inhelmet" -> INHELMET;
                case "inchestplate" -> INCHESTPLATE;
                case "inleggings" -> INLEGGINGS;
                case "inboots" -> INBOOTS;
                default -> null;
            };
        }
        if (segments.size() == 2 && segments.get(0).equals("wear")) {
            return switch (segments.get(1)) {
                case "helmet" -> WEAR_HELMET;
                case "chestplate" -> WEAR_CHESTPLATE;
                case "leggings" -> WEAR_LEGGINGS;
                case "boots" -> WEAR_BOOTS;
                default -> null;
            };
        }
        return null;
    }
}
