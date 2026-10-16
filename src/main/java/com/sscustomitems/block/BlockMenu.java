package com.sscustomitems.block;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个已放置自定义方块的菜单实例，可包含多页库存。
 * 多页时每页最左下角为“上一页”、最右下角为“下一页”导航槽，
 * 这些槽位不参与内容物存储与掉落。
 */
public final class BlockMenu {

    private final String key;
    private final MenuSpec spec;
    private final List<Inventory> pages;

    public BlockMenu(String key, MenuSpec spec, List<Inventory> pages) {
        this.key = key;
        this.spec = spec;
        this.pages = pages;
    }

    public String key() {
        return key;
    }

    public MenuSpec spec() {
        return spec;
    }

    public List<Inventory> pages() {
        return pages;
    }

    public Inventory firstPage() {
        return pages.get(0);
    }

    public int pageCount() {
        return pages.size();
    }

    /** 按顺序列出所有参与存储的槽位（跳过导航槽）。 */
    public List<int[]> storageSlots() {
        List<int[]> slots = new ArrayList<>();
        for (int p = 0; p < pages.size(); p++) {
            for (int s = 0; s < spec.pageSize(); s++) {
                if (spec.multipages() && (s == spec.prevSlot() || s == spec.nextSlot())) {
                    continue;
                }
                slots.add(new int[]{p, s});
            }
        }
        return slots;
    }

    /** 收集所有存储槽中的非空物品（按页、槽顺序）。 */
    public List<ItemStack> storedContents() {
        List<ItemStack> items = new ArrayList<>();
        for (int[] loc : storageSlots()) {
            ItemStack item = pages.get(loc[0]).getItem(loc[1]);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /** 判断某个库存是否为本菜单的某一页，返回页码（-1 表示不是）。 */
    public int pageIndexOf(Inventory inventory) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).equals(inventory)) {
                return i;
            }
        }
        return -1;
    }
}
