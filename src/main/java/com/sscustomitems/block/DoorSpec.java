package com.sscustomitems.block;

/**
 * 自定义门的门锁配置（data.lock / data.allow_redstone）：
 * 玩家必须手持 id 为 lock 的自定义物品才能开门；
 * allowRedstone 为 true 时红石也可以开门（默认 false）。
 */
public record DoorSpec(String lock, boolean allowRedstone) {
}
