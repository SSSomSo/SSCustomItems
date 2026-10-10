package com.sscustomitems.block;

import java.util.List;
import java.util.Map;

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
