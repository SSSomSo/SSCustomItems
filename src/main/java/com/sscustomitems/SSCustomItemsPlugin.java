package com.sscustomitems;

import com.sscustomitems.block.BlockCommand;
import com.sscustomitems.block.BlockManager;
import com.sscustomitems.combat.CombatCommand;
import com.sscustomitems.combat.CombatManager;
import com.sscustomitems.combat.DamageTypeManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SSCustomItemsPlugin extends JavaPlugin {

    private ItemManager itemManager;
    private ActionManager actionManager;
    private DamageTypeManager damageTypeManager;
    private CombatManager combatManager;
    private BlockManager blockManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 首次启动：把 jar 内 example-pack/（示例自定义内容）解压到 resourcepack/，
        // 供 ComponentsAPI 的资源包托管合并（ComponentsAPI 自身不提供具体自定义内容）。
        try {
            installExamplePack();
        } catch (Exception e) {
            getLogger().warning("示例资源包解压失败: " + e.getMessage());
        }

        itemManager = new ItemManager(this);

        // 尽早注册指令：即使后续初始化失败，/ci 也能返回明确提示，而不是“意外错误”
        PluginCommand ciCommand = getCommand("ci");
        if (ciCommand == null) {
            getLogger().severe("plugin.yml 中未注册 ci 指令，插件将被禁用");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CiCommand ci = new CiCommand(this, itemManager);
        ciCommand.setExecutor(ci);
        ciCommand.setTabCompleter(ci);

        try {
            itemManager.init();
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "物品系统初始化失败", e);
        }

        try {
            actionManager = new ActionManager(this, itemManager);
            getServer().getPluginManager().registerEvents(actionManager, this);
            actionManager.start();
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "触发动作系统初始化失败", e);
        }

        try {
            damageTypeManager = new DamageTypeManager(this);
            damageTypeManager.init();

            combatManager = new CombatManager(this, damageTypeManager);
            getServer().getPluginManager().registerEvents(combatManager, this);

            CombatCommand combatCommand = new CombatCommand(this, damageTypeManager, combatManager);
            for (String name : new String[]{
                    "damagelook", "firelook", "damageln", "fireln", "killlook", "killln"}) {
                PluginCommand command = getCommand(name);
                if (command != null) {
                    command.setExecutor(combatCommand);
                    command.setTabCompleter(combatCommand);
                }
            }
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "战斗系统初始化失败", e);
        }

        try {
            blockManager = new BlockManager(this, itemManager);
            blockManager.init();
            getServer().getPluginManager().registerEvents(blockManager, this);

            PluginCommand cbCommand = getCommand("cb");
            if (cbCommand != null) {
                BlockCommand blockCommand = new BlockCommand(this, blockManager);
                cbCommand.setExecutor(blockCommand);
                cbCommand.setTabCompleter(blockCommand);
            }
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "自定义方块系统初始化失败", e);
        }

        // 管理指令（热重载）
        PluginCommand adminCommand = getCommand("sscustomitems");
        if (adminCommand != null) {
            ReloadCommand reload = new ReloadCommand(this, itemManager, actionManager,
                    damageTypeManager, blockManager);
            adminCommand.setExecutor(reload);
            adminCommand.setTabCompleter(reload);
        }

        // 原版风带注释补全
        getServer().getPluginManager().registerEvents(
                new SuggestionTooltips(this, itemManager, damageTypeManager, blockManager), this);

        // 护甲等级系统
        try {
            getServer().getPluginManager().registerEvents(new ArmorSystem(this), this);
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "护甲等级系统初始化失败", e);
        }
    }

    /**
     * 若 plugins/SSCustomItems/resourcepack 不存在，则从本插件 jar 的 example-pack/ 目录解压。
     * 已存在则保留用户内容，不覆盖。
     */
    private void installExamplePack() throws Exception {
        File target = new File(getDataFolder(), "resourcepack");
        if (target.exists()) {
            return;
        }
        File jarFile = new File(getClass().getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("example-pack/")) {
                    continue;
                }
                String relative = name.substring("example-pack/".length());
                if (relative.isEmpty()) {
                    continue;
                }
                File out = new File(target, relative);
                if (entry.isDirectory()) {
                    getLogger().info("创建目录 " + out.getPath());
                    continue;
                }
                out.getParentFile().mkdirs();
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    java.nio.file.Files.copy(in, out.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        getLogger().info("已解压示例资源包到 " + target.getPath()
                + "，ComponentsAPI 启动时会自动合并托管。");
    }

    @Override
    public void onDisable() {
        if (actionManager != null) {
            actionManager.stop();
        }
        if (blockManager != null) {
            blockManager.stop();
        }
    }
}
