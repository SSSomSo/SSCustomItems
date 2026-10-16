package com.sscustomitems.block;

import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * 自定义方块的菜单配置（data.chest_menu）。
 *
 * 模式：
 * - CHEST      普通箱子（27 格，插件存储）
 * - BIG_CHEST  大箱子（54 格，插件存储）
 * - SHULKER    潜影盒（27 格，插件存储；单页时破坏写入物品，多页时内容物掉落）
 * - ENDER      原版玩家末影箱（打开玩家个人末影箱，无插件存储，不支持多页）
 * - BIG_ENDER  大末影箱（isender + isbig，54 格，插件存储）
 *
 * 多页：multipages=true 时，pageCount 决定页数；每个页面最左下角为“上一页”、
 * 最右下角为“下一页”导航按钮（不可取走）。普通箱子、大箱子、潜影盒、大末影箱均可用。
 */
public record MenuSpec(Mode mode,
                       boolean multipages,
                       int pageCount,
                       List<String> defaultItemSnbt,
                       Component openName,
                       String storageName) {

    public enum Mode {
        CHEST(27),
        BIG_CHEST(54),
        SHULKER(27),
        ENDER(27),
        BIG_ENDER(54);

        private final int size;

        Mode(int size) {
            this.size = size;
        }

        public int size() {
            return size;
        }
    }

    /** 是否使用插件托管的库存（而非玩家原版末影箱）。 */
    public boolean persistentInventory() {
        return mode != Mode.ENDER;
    }

    /** 每页格数。 */
    public int pageSize() {
        return mode.size();
    }

    /** 实际页数（至少 1）。 */
    public int effectivePages() {
        return multipages ? Math.max(1, pageCount) : 1;
    }

    /** “上一页”导航槽（最左下角）。 */
    public int prevSlot() {
        return mode.size() - 9;
    }

    /** “下一页”导航槽（最右下角）。 */
    public int nextSlot() {
        return mode.size() - 1;
    }

    /** 某槽位是否为多页导航槽。 */
    public boolean isNavSlot(int slot) {
        return multipages && (slot == prevSlot() || slot == nextSlot());
    }
}
