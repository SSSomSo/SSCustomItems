package com.sscustomitems.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sscustomitems.NmsBridge;
import com.sscustomitems.ItemManager;
import com.sscustomitems.SSCustomItemsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class BlockManager implements Listener {

    private static final List<String> EVENT_NAMES =
            List.of("tick", "rightclick", "leftclick", "break", "place");

    private final SSCustomItemsPlugin plugin;
    private final ItemManager itemManager;
    private final Path directory;
    private final Path indexFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, CustomBlock> definitions = new LinkedHashMap<>();
    private final Map<String, StoredBlock> index = new LinkedHashMap<>();
    private final Map<String, Inventory> menus = new LinkedHashMap<>();

    private NmsBridge nms;
    private BukkitTask tickTask;
    private BukkitTask redstoneTask;

    public BlockManager(SSCustomItemsPlugin plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.directory = plugin.getDataFolder().toPath().resolve("blocks");
        this.indexFile = plugin.getDataFolder().toPath().resolve("blocks_index.json");
    }

    public void init() throws IOException {
        nms = new NmsBridge();
        Files.createDirectories(directory);
        createExamplesIfEmpty();
        loadDefinitions();
        loadIndex();
        validateAndRebuild();
        startTickTask();
        startRedstoneGuard();
    }

    public synchronized void reloadDefinitions() {
        for (Map.Entry<String, Inventory> entry : menus.entrySet()) {
            persistMenu(entry.getKey());
            for (org.bukkit.entity.HumanEntity viewer : entry.getValue().getViewers()) viewer.closeInventory();
        }
        menus.clear();
        loadDefinitions();
        validateAndRebuild();
    }

    public void reloadSettings() {
        if (tickTask != null) { tickTask.cancel(); startTickTask(); }
    }

    private void loadDefinitions() {
        definitions.clear();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(files::add);
        } catch (IOException e) { plugin.getLogger().severe("无法读取 blocks 目录: " + e.getMessage()); return; }
        for (Path file : files) loadDefinition(file);
        plugin.getLogger().info("已加载 " + definitions.size() + " 个自定义方块");
    }

    private void loadDefinition(Path file) {
        String fileName = file.getFileName().toString();
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String vanillaId = json.get("vanilla_id").getAsString();
            String id = json.get("id").getAsString();
            if (materialFor(vanillaId) == null) throw new IllegalStateException("无效的原版方块 id: " + vanillaId);

            Map<String, List<String>> events = new LinkedHashMap<>();
            if (json.has("events") && json.get("events").isJsonObject()) {
                JsonObject eventsJson = json.getAsJsonObject("events");
                for (String eventName : EVENT_NAMES)
                    if (eventsJson.has(eventName) && eventsJson.get(eventName).isJsonArray())
                        events.put(eventName, readStringList(eventsJson.getAsJsonArray(eventName)));
            }

            MenuSpec menu = null;
            DoorSpec door = null;
            boolean unbreakable = false;
            if (json.has("data") && json.get("data").isJsonObject()) {
                JsonObject data = json.getAsJsonObject("data");
                if (data.has("chest_menu") && data.get("chest_menu").isJsonObject())
                    menu = parseMenu(data.getAsJsonObject("chest_menu"));
                unbreakable = data.has("unbreakable") && data.get("unbreakable").getAsBoolean();
                if (data.has("lock") && data.get("lock").isJsonPrimitive()) {
                    String lock = data.get("lock").getAsString();
                    boolean allowRedstone = data.has("allow_redstone") && data.get("allow_redstone").getAsBoolean();
                    door = new DoorSpec(lock, allowRedstone);
                    if (!Tag.DOORS.isTagged(materialFor(vanillaId)))
                        plugin.getLogger().warning("方块 " + id + " 配置了门锁但 vanilla_id 不是门，门锁不生效");
                }
            }
            definitions.put(id, new CustomBlock(id, vanillaId, events, menu, door, unbreakable));
        } catch (Exception e) { plugin.getLogger().warning("加载方块文件 " + fileName + " 失败: " + e.getMessage()); }
    }

    private MenuSpec parseMenu(JsonObject chestMenu) {
        MenuSpec.Mode mode;
        if (chestMenu.has("isender") && chestMenu.get("isender").getAsBoolean()) mode = MenuSpec.Mode.ENDER;
        else if (chestMenu.has("isshulkerbox") && chestMenu.get("isshulkerbox").getAsBoolean()) mode = MenuSpec.Mode.SHULKER;
        else if (chestMenu.has("ischest") && chestMenu.get("ischest").getAsBoolean()) {
            boolean big = chestMenu.has("isbig") && chestMenu.get("isbig").getAsBoolean();
            mode = big ? MenuSpec.Mode.BIG_CHEST : MenuSpec.Mode.CHEST;
        } else mode = MenuSpec.Mode.CHEST;

        List<String> defaultItems = new ArrayList<>();
        if (chestMenu.has("default_items") && chestMenu.get("default_items").isJsonArray())
            for (JsonElement element : chestMenu.getAsJsonArray("default_items"))
                if (element.isJsonPrimitive()) defaultItems.add(element.getAsString());

        Component openName = null;
        if (chestMenu.has("open_name")) {
            JsonElement nameElement = chestMenu.get("open_name");
            if (nameElement.isJsonObject()) openName = GsonComponentSerializer.gson().deserialize(nameElement.toString());
            else if (nameElement.isJsonPrimitive()) openName = Component.text(nameElement.getAsString());
        }
        return new MenuSpec(mode, defaultItems, openName);
    }

    private void loadIndex() {
        index.clear();
        if (!Files.exists(indexFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(indexFile, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("entries")) return;
            JsonObject entries = root.getAsJsonObject("entries");
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                String id = value.get("id").getAsString();
                List<String> items = value.has("items") && value.get("items").isJsonArray()
                        ? readStringList(value.getAsJsonArray("items")) : new ArrayList<>();
                index.put(entry.getKey(), new StoredBlock(id, items));
            }
        } catch (Exception e) { plugin.getLogger().warning("读取方块索引失败: " + e.getMessage()); }
    }

    private void saveIndex() {
        JsonObject entries = new JsonObject();
        for (Map.Entry<String, StoredBlock> entry : index.entrySet()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", entry.getValue().id());
            JsonArray items = new JsonArray();
            for (String snbt : entry.getValue().items()) items.add(snbt);
            value.add("items", items);
            entries.add(entry.getKey(), value);
        }
        JsonObject root = new JsonObject();
        root.add("entries", entries);
        try { Files.writeString(indexFile, gson.toJson(root), StandardCharsets.UTF_8); }
        catch (IOException e) { plugin.getLogger().warning("保存方块索引失败: " + e.getMessage()); }
    }

    private void validateAndRebuild() {
        List<String> invalid = new ArrayList<>();
        for (Map.Entry<String, StoredBlock> entry : index.entrySet()) {
            String key = entry.getKey();
            StoredBlock stored = entry.getValue();
            CustomBlock def = definitions.get(stored.id());
            Block block = blockFromKey(key);
            if (def == null || block == null || !block.getType().equals(materialFor(def.vanillaId()))) { invalid.add(key); continue; }
            if (Tag.DOORS.isTagged(block.getType()) && block.getBlockData() instanceof Door door) {
                Block other = doorOtherHalf(block);
                if (other == null || customIdAt(other) == null) { invalid.add(key); continue; }
            }
            if (def.menu() != null && def.menu().mode() != MenuSpec.Mode.ENDER) {
                Inventory inventory = createInventory(def);
                int slot = 0;
                for (String itemSnbt : stored.items()) {
                    ItemStack restored = nms.fromSnbt(itemSnbt);
                    while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
                    if (slot < inventory.getSize()) inventory.setItem(slot, restored);
                }
                menus.put(key, inventory);
            }
        }
        for (String key : invalid) index.remove(key);
        if (!invalid.isEmpty()) saveIndex();
    }

    public void placeBlock(Player player, CustomBlock def, Block target) {
        Material material = materialFor(def.vanillaId());
        if (Tag.DOORS.isTagged(material)) placeDoor(player, def, target, material);
        else {
            target.setType(material, false);
            stampTile(target, def);
            String key = key(target);
            index.put(key, new StoredBlock(def.id(), new ArrayList<>()));
            if (def.menu() != null && def.menu().mode() != MenuSpec.Mode.ENDER) menus.put(key, createInventory(def));
        }
        saveIndex();
        runCommands(def.commands("place"), player, target);
    }

    private void placeDoor(Player player, CustomBlock def, Block bottom, Material material) {
        Block top = bottom.getRelative(BlockFace.UP);
        if (!canPlaceAt(top)) throw new IllegalStateException("门上方位置被占用，无法放置");
        BlockFace facing = player.getFacing().getOppositeFace();
        Door bottomData = (Door) material.createBlockData();
        bottomData.setFacing(facing); bottomData.setHalf(Door.Half.BOTTOM);
        bottomData.setHinge(Door.Hinge.LEFT); bottomData.setOpen(false);
        bottom.setBlockData(bottomData, false);
        Door topData = (Door) material.createBlockData();
        topData.setFacing(facing); topData.setHalf(Door.Half.TOP);
        topData.setHinge(Door.Hinge.LEFT); topData.setOpen(false);
        top.setBlockData(topData, false);
        stampTile(bottom, def); stampTile(top, def);
        index.put(key(bottom), new StoredBlock(def.id(), new ArrayList<>()));
        index.put(key(top), new StoredBlock(def.id(), new ArrayList<>()));
    }

    private void stampTile(Block block, CustomBlock def) {
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(blockIdKey(), PersistentDataType.STRING, def.id());
            tileState.update();
        }
    }

    private void handleBreakDrops(Block block, CustomBlock def) {
        if (def == null) return;
        Material material = materialFor(def.vanillaId());
        String key = key(block);
        MenuSpec menu = def.menu();
        if (menu != null && menu.mode() == MenuSpec.Mode.SHULKER) {
            ItemStack drop = taggedItem(def);
            Inventory inventory = menus.get(key);
            if (inventory != null) drop = nms.withContainerContents(drop, nonNullContents(inventory));
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
            return;
        }
        if (material != null && material.isItem())
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), taggedItem(def));
        if (menu != null && (menu.mode() == MenuSpec.Mode.CHEST || menu.mode() == MenuSpec.Mode.BIG_CHEST)) {
            Inventory inventory = menus.get(key);
            if (inventory != null)
                for (ItemStack item : nonNullContents(inventory))
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
        }
    }

    private void removeTracking(Block block) {
        String key = key(block);
        index.remove(key); menus.remove(key); saveIndex();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getHand() != null
                && event.getItem() != null && event.getClickedBlock() != null) {
            String heldBlockId = taggedBlockId(event.getItem());
            if (heldBlockId != null) {
                CustomBlock def = definitions.get(heldBlockId);
                Block target = event.getClickedBlock().getRelative(event.getBlockFace());
                if (def != null && canPlaceAt(target)) {
                    event.setCancelled(true);
                    placeBlock(player, def, target);
                    if (!player.getGameMode().name().equals("CREATIVE")) {
                        ItemStack item = event.getItem(); item.setAmount(item.getAmount() - 1);
                    }
                    return;
                }
            }
        }
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked != null) {
            String id = customIdAt(clicked);
            if (id == null) return;
            CustomBlock def = definitions.get(id);
            if (def == null) return;
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (def.door() != null && Tag.DOORS.isTagged(clicked.getType())) {
                    event.setCancelled(true);
                    if (!holdsKey(player, def.door().lock())) {
                        clicked.getWorld().playSound(clicked.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
                        player.sendMessage(Component.translatable("container.isLocked", Component.text("")).color(NamedTextColor.RED));
                    } else { toggleDoor(clicked); runCommands(def.commands("rightclick"), player, clicked); }
                } else {
                    event.setCancelled(true);
                    if (def.menu() != null) openMenu(player, clicked, def);
                    else runCommands(def.commands("rightclick"), player, clicked);
                }
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) runCommands(def.commands("leftclick"), player, clicked);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String id = customIdAt(block);
        if (id == null) return;
        CustomBlock def = definitions.get(id);
        if (def != null && def.unbreakable() && !Tag.DOORS.isTagged(block.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("此方块无法破坏。", NamedTextColor.RED));
            return;
        }
        event.setDropItems(false); event.setExpToDrop(0);
        if (Tag.DOORS.isTagged(block.getType())) {
            if (def != null && def.door() != null && !holdsKey(event.getPlayer(), def.door().lock())) {
                event.setCancelled(true);
                block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.BLOCK_CHEST_LOCKED, 1.0f, 1.0f);
                event.getPlayer().sendMessage(Component.translatable("container.isLocked", Component.text("")).color(NamedTextColor.RED));
                return;
            }
            Block other = doorOtherHalf(block);
            handleBreakDrops(block, def);
            if (other != null && customIdAt(other) != null) { index.remove(key(other)); menus.remove(key(other)); other.setType(Material.AIR, false); }
            index.remove(key(block)); menus.remove(key(block));
            if (def != null) runCommands(def.commands("break"), event.getPlayer(), block);
            saveIndex(); return;
        }
        handleBreakDrops(block, def);
        if (def != null) runCommands(def.commands("break"), event.getPlayer(), block);
        removeTracking(block);
    }

    private Block doorOtherHalf(Block block) {
        if (block.getBlockData() instanceof Door door)
            return block.getRelative(door.getHalf() == Door.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { handleExplosion(event.blockList()); }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { handleExplosion(event.blockList()); }

    private void handleExplosion(List<Block> blocks) {
        boolean changed = false;
        java.util.Set<String> handledDoors = new java.util.HashSet<>();
        for (Block block : new ArrayList<>(blocks)) {
            String id = customIdAt(block);
            if (id == null) continue;
            CustomBlock def = definitions.get(id);
            if (Tag.DOORS.isTagged(block.getType())) {
                Block bottom = block.getBlockData() instanceof Door door && door.getHalf() == Door.Half.TOP
                        ? block.getRelative(BlockFace.DOWN) : block;
                if (!handledDoors.add(key(bottom))) { blocks.remove(block); continue; }
                blocks.remove(block);
                Block top = bottom.getRelative(BlockFace.UP); blocks.remove(top);
                continue;
            }
            if (def != null && def.unbreakable()) { blocks.remove(block); continue; }
            handleBreakDrops(block, def);
            index.remove(key(block)); menus.remove(key(block)); changed = true;
        }
        if (changed) saveIndex();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) if (customIdAt(block) != null) { event.setCancelled(true); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) if (customIdAt(block) != null) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        for (Map.Entry<String, Inventory> entry : menus.entrySet())
            if (entry.getValue().equals(event.getInventory())) { persistMenu(entry.getKey()); return; }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoorPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!Tag.DOORS.isTagged(block.getType())) return;
        String id = customIdAt(block);
        CustomBlock def = id == null ? null : definitions.get(id);
        if (def == null || def.door() == null || def.door().allowRedstone()) return;
        Bukkit.getScheduler().runTask(plugin, () -> enforceDoorClosed(block));
    }

    private void enforceDoorClosed(Block block) {
        Block bottom = doorBottom(block);
        if (bottom == null || !(bottom.getBlockData() instanceof Door bottomDoor) || !bottomDoor.isOpen()) return;
        Block top = bottom.getRelative(BlockFace.UP);
        if (!bottom.isBlockPowered() && !top.isBlockPowered()) return;
        bottomDoor.setOpen(false); bottom.setBlockData(bottomDoor, false);
        if (top.getBlockData() instanceof Door topDoor) { topDoor.setOpen(false); top.setBlockData(topDoor, true); }
    }

    private void toggleDoor(Block clicked) {
        Block bottom = doorBottom(clicked);
        if (bottom == null || !(bottom.getBlockData() instanceof Door bottomDoor)) return;
        Block top = bottom.getRelative(BlockFace.UP);
        boolean newOpen = !bottomDoor.isOpen();
        bottomDoor.setOpen(newOpen); bottom.setBlockData(bottomDoor, false);
        if (top.getBlockData() instanceof Door topDoor) { topDoor.setOpen(newOpen); top.setBlockData(topDoor, true); }
        boolean iron = bottom.getType() == Material.IRON_DOOR;
        org.bukkit.Sound sound = iron
                ? (newOpen ? org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN : org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE)
                : (newOpen ? org.bukkit.Sound.BLOCK_WOODEN_DOOR_OPEN : org.bukkit.Sound.BLOCK_WOODEN_DOOR_CLOSE);
        bottom.getWorld().playSound(bottom.getLocation(), sound, 1.0f, 1.0f);
    }

    private Block doorBottom(Block block) {
        if (!(block.getBlockData() instanceof Door door)) return null;
        return door.getHalf() == Door.Half.BOTTOM ? block : block.getRelative(BlockFace.DOWN);
    }

    private void openMenu(Player player, Block block, CustomBlock def) {
        MenuSpec menu = def.menu();
        if (menu.mode() == MenuSpec.Mode.ENDER) { player.openInventory(player.getEnderChest()); return; }
        String key = key(block);
        Inventory inventory = menus.get(key);
        if (inventory == null) { inventory = createInventory(def); menus.put(key, inventory); }
        player.openInventory(inventory);
    }

    private Inventory createInventory(CustomBlock def) {
        MenuSpec menu = def.menu();
        Component title = menu.openName() != null ? menu.openName() : Component.text(def.id());
        Inventory inventory = Bukkit.createInventory(null, menu.mode().size(), title);
        int slot = 0;
        for (String itemSnbt : menu.defaultItemSnbt()) {
            ItemStack defaultItem = nms.fromSnbt(itemSnbt);
            while (slot < inventory.getSize() && inventory.getItem(slot) != null) slot++;
            if (slot < inventory.getSize()) inventory.setItem(slot++, defaultItem);
        }
        return inventory;
    }

    private void persistMenu(String key) {
        StoredBlock stored = index.get(key);
        Inventory inventory = menus.get(key);
        if (stored == null || inventory == null) return;
        List<String> items = new ArrayList<>();
        for (ItemStack item : nonNullContents(inventory)) items.add(nms.toSnbt(item));
        index.put(key, new StoredBlock(stored.id(), items));
        saveIndex();
    }

    private void startTickTask() {
        int interval = Math.max(1, plugin.getConfig().getInt("block-tick-interval-ticks", 10));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, StoredBlock> entry : new ArrayList<>(index.entrySet())) {
                String key = entry.getKey();
                StoredBlock stored = entry.getValue();
                CustomBlock def = definitions.get(stored.id());
                Block block = blockFromKey(key);
                if (def == null || block == null) continue;
                if (!block.getType().equals(materialFor(def.vanillaId()))) {
                    index.remove(key); menus.remove(key); saveIndex(); continue;
                }
                if (Tag.DOORS.isTagged(block.getType()) && block.getBlockData() instanceof Door door) {
                    Block other = doorOtherHalf(block);
                    if (other == null || customIdAt(other) == null) {
                        index.remove(key); menus.remove(key); block.setType(Material.AIR, false); saveIndex(); continue;
                    }
                    if (door.getHalf() == Door.Half.TOP) continue;
                }
                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
                    runCommands(def.commands("tick"), null, block);
            }
        }, interval, interval);
    }

    private void startRedstoneGuard() {
        redstoneTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, StoredBlock> entry : new ArrayList<>(index.entrySet())) {
                StoredBlock stored = entry.getValue();
                CustomBlock def = definitions.get(stored.id());
                if (def == null || def.door() == null || def.door().allowRedstone()) continue;
                Block block = blockFromKey(entry.getKey());
                if (block == null || !(block.getBlockData() instanceof Door door) || door.getHalf() != Door.Half.BOTTOM) continue;
                enforceDoorClosed(block);
            }
        }, 2L, 2L);
    }

    public void stop() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        if (redstoneTask != null) { redstoneTask.cancel(); redstoneTask = null; }
    }

    public List<String> definitionIds() { return new ArrayList<>(definitions.keySet()); }
    public CustomBlock getDefinition(String id) { return definitions.get(id); }
    public Material materialOf(String vanillaId) { return materialFor(vanillaId); }

    public ItemStack taggedItem(CustomBlock def) {
        ItemStack item = new ItemStack(materialFor(def.vanillaId()));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(blockIdKey(), PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    private String taggedBlockId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(blockIdKey(), PersistentDataType.STRING);
    }

    private String customIdAt(Block block) {
        String key = key(block);
        StoredBlock stored = index.get(key);
        if (stored != null) return stored.id();
        BlockState state = block.getState();
        if (state instanceof TileState tileState)
            return tileState.getPersistentDataContainer().get(blockIdKey(), PersistentDataType.STRING);
        return null;
    }

    private org.bukkit.NamespacedKey blockIdKey() { return new org.bukkit.NamespacedKey(plugin, "block_id"); }

    private boolean holdsKey(Player player, String lockId) {
        return isKeyItem(player.getInventory().getItemInMainHand(), lockId)
                || isKeyItem(player.getInventory().getItemInOffHand(), lockId);
    }

    private boolean isKeyItem(ItemStack item, String lockId) {
        if (item == null || !item.hasItemMeta()) return false;
        String itemId = item.getItemMeta().getPersistentDataContainer()
                .get(itemManager.itemIdKey(), PersistentDataType.STRING);
        return lockId.equalsIgnoreCase(itemId);
    }

    private static boolean canPlaceAt(Block block) { return block.getType().isAir() || block.isReplaceable(); }

    private static Material materialFor(String vanillaId) {
        String path = vanillaId.contains(":") ? vanillaId.substring(vanillaId.indexOf(':') + 1) : vanillaId;
        Material material = Material.matchMaterial(path.toUpperCase(Locale.ROOT));
        return material != null && material.isBlock() ? material : null;
    }

    private static List<ItemStack> nonNullContents(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) if (item != null) items.add(item);
        return items;
    }

    private static List<String> readStringList(JsonArray array) {
        List<String> list = new ArrayList<>();
        for (JsonElement element : array) if (element.isJsonPrimitive()) list.add(element.getAsString());
        return list;
    }

    private void runCommands(List<String> commands, Player player, Block block) {
        for (String raw : commands) {
            String command = raw.startsWith("/") ? raw.substring(1) : raw;
            command = command.replace("%player%", player != null ? player.getName() : "")
                    .replace("%world%", block.getWorld().getName())
                    .replace("%x%", String.valueOf(block.getX()))
                    .replace("%y%", String.valueOf(block.getY()))
                    .replace("%z%", String.valueOf(block.getZ()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    private Block blockFromKey(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) return null;
        UUID worldUid; int x, y, z;
        try {
            worldUid = UUID.fromString(parts[0]);
            x = Integer.parseInt(parts[1]); y = Integer.parseInt(parts[2]); z = Integer.parseInt(parts[3]);
        } catch (IllegalArgumentException e) { return null; }
        World world = null;
        for (World candidate : Bukkit.getWorlds()) if (candidate.getUID().equals(worldUid)) { world = candidate; break; }
        if (world == null) return null;
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        return world.getBlockAt(x, y, z);
    }

    private void createExamplesIfEmpty() {
        if (!Files.exists(directory.resolve("example_chest.json"))) {
            String example = """
                    {
                      "vanilla_id": "minecraft:chest",
                      "id": "sscustomitems:example_chest",
                      "events": { "place": ["/say 自定义箱子已放置"], "break": ["/say 自定义箱子被破坏"] },
                      "data": {
                        "chest_menu": {
                          "ischest": true,
                          "default_items": [ "{id:\\"minecraft:diamond\\",count:2b}", "{id:\\"minecraft:gold_ingot\\",count:5b}" ],
                          "open_name": {"text": "自定义箱子", "color": "gold"}
                        }
                      }
                    }
                    """;
            try { Files.writeString(directory.resolve("example_chest.json"), example, StandardCharsets.UTF_8); }
            catch (IOException e) { plugin.getLogger().warning("无法生成示例方块: " + e.getMessage()); }
        }
        if (!Files.exists(directory.resolve("example_shulker.json"))) {
            String example = """
                    {
                      "vanilla_id": "minecraft:shulker_box",
                      "id": "sscustomitems:example_shulker",
                      "events": {},
                      "data": {
                        "chest_menu": {
                          "isshulkerbox": true,
                          "default_items": [ "{id:\\"minecraft:arrow\\",count:16b}" ],
                          "open_name": {"text": "收纳盒", "color": "light_purple"}
                        }
                      }
                    }
                    """;
            try { Files.writeString(directory.resolve("example_shulker.json"), example, StandardCharsets.UTF_8); }
            catch (IOException e) { plugin.getLogger().warning("无法生成示例潜影盒: " + e.getMessage()); }
        }
    }

    private record StoredBlock(String id, List<String> items) {}
}
