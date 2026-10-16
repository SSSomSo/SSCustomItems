package com.sscustomitems.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 共享末影箱（isender，含 isbig）按名字持久化。
 *
 * 存储目录：plugins/SSCustomItems/ender_storage/
 * 每个存储文件：{key}.json，key 形如 np_<清洗后的名字>
 *   （np_ 前缀表示这是网络/插件共享存储，区别于玩家单人末影箱数据）。
 * 内容：按存储槽顺序排列的 ItemStack SNBT 字符串列表。
 * 任何玩家放下“相同名字”的自定义末影箱，都读写同一份文件、看到同一份内容。
 */
public final class SharedEnderStore {

    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    public SharedEnderStore(Path dataFolder) {
        this.directory = dataFolder.resolve("ender_storage");
    }

    /** 读取某 key 的 SNBT 列表（文件不存在或读取失败时返回空列表）。 */
    public List<String> load(String key) {
        Path file = directory.resolve(key + ".json");
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<String> items = gson.fromJson(reader, LIST_TYPE);
            return items != null ? items : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 保存某 key 的 SNBT 列表（pretty，UTF-8，先写临时文件再原子替换）。 */
    public void save(String key, List<String> items) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(key + ".json");
            Path tmp = directory.resolve(key + ".json.tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(items, LIST_TYPE, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                }
                throw e;
            }
        } catch (Exception e) {
            System.err.println("[SSCustomItems] 保存共享末影箱失败 " + key + ": " + e.getMessage());
        }
    }
}
