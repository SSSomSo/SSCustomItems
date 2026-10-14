package com.sscustomitems.block;

import java.util.List;
import java.util.Map;

/**
 * 一个自定义方块定义：
 * 以原版方块 vanilla_id 为载体，位置被标记后按 events 执行命令，
 * 并可选挂载箱子/大箱子/末影箱/潜影盒菜单、门锁，或设为无法破坏。
 */
public record CustomBlock(String id,
                          String vanillaId,
                          Map<String, List<String>> events,
                          MenuSpec menu,
                          DoorSpec door,
                          boolean unbreakable) {

    public List<String> commands(String eventName) {
        return events.getOrDefault(eventName, List.of());
    }
}
