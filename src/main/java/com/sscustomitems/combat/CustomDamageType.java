package com.sscustomitems.combat;

import java.util.Map;

/**
 * 自定义伤害类型：实际伤害仍通过 baseType（原版伤害类型）结算，
 * 死亡时使用 messages 中对应语言/场景的文本覆盖死亡消息。
 *
 * 场景：byplayer（被玩家杀死）、byentity（因实体死亡）、bynature（自然死亡）。
 */
public record CustomDamageType(String typeId,
                               String baseType,
                               Map<String, Map<String, String>> messages) {

    public String message(String language, String scenario) {
        Map<String, String> map = messages.get(language);
        if (map == null) {
            map = messages.get("en_us");
        }
        if (map == null && !messages.isEmpty()) {
            map = messages.values().iterator().next();
        }
        return map == null ? null : map.get(scenario);
    }
}
