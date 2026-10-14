package com.sscustomitems.block;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 自定义方块的菜单配置（data.chest_menu）。
 */
public record MenuSpec(Mode mode,
                       List<String> defaultItemSnbt,
                       Component openName) {

    public enum Mode {
        CHEST(27),
        BIG_CHEST(54),
        SHULKER(27),
        ENDER(-1);

        private final int size;

        Mode(int size) {
            this.size = size;
        }

        public int size() {
            return size;
        }
    }
}
