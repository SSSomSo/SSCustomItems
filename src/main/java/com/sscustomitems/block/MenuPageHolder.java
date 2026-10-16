package com.sscustomitems.block;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * 多页方块菜单中某一页的 InventoryHolder，
 * 用于在 InventoryClickEvent 中反查所属 {@link BlockMenu} 与页码。
 */
public final class MenuPageHolder implements InventoryHolder {

    private final BlockMenu menu;
    private final int page;
    private Inventory inventory;

    public MenuPageHolder(BlockMenu menu, int page) {
        this.menu = menu;
        this.page = page;
    }

    public BlockMenu menu() {
        return menu;
    }

    public int page() {
        return page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
