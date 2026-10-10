package com.sscustomitems.combat;

import net.kyori.adventure.text.Component;

import java.util.Locale;

public final class CustomDamageType {

    private final String typeId;
    private final String baseType;
    private final String byPlayer;
    private final String byEntity;
    private final String byNature;

    public CustomDamageType(String typeId, String baseType,
                            String byPlayer, String byEntity, String byNature) {
        this.typeId = typeId;
        this.baseType = baseType != null ? baseType : "minecraft:player_attack";
        this.byPlayer = byPlayer;
        this.byEntity = byEntity;
        this.byNature = byNature;
    }

    public String typeId() { return typeId; }
    public String baseType() { return baseType; }

    public Component deathMessage(String victim, String killer, String entityName) {
        String template;
        if (killer != null && byPlayer != null) template = byPlayer;
        else if (entityName != null && byEntity != null) template = byEntity;
        else if (byNature != null) template = byNature;
        else return Component.text(victim + " 死了");
        String text = template.replace("<PLAYER>", victim)
                .replace("<PLAYER1>", victim)
                .replace("<PLAYER2>", killer != null ? killer : "")
                .replace("<ENTITY>", entityName != null ? entityName : "");
        return Component.text(text);
    }

    public static String normalize(String id) {
        String value = id.toLowerCase(Locale.ROOT);
        return value.contains(":") ? value : "minecraft:" + value;
    }
}
