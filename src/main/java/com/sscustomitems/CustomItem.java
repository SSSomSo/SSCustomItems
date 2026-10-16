package com.sscustomitems;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * 一个自定义物品的配置定义，对应 items 目录下的一个 json 文件。
 *
 * @param sourceFile  来源文件名
 * @param vanillaId   作为载体的原版物品 id（已补全 minecraft: 命名空间）
 * @param id          自定义物品的命名空间 id，如 sscustomitems:ruby
 * @param enableModel 为 true 时，给予物品自动附带 item_model 组件，值为 id
 * @param components  给予物品时附带的原版数据组件
 * @param actions     物品在各触发条件下执行的命令
 */
public final class CustomItem {

    private final String sourceFile;
    private final String vanillaId;
    private final String id;
    private final boolean enableModel;
    private final JsonObject components;
    private final ActionBindings actions;
    private final RandomItemSpec random;

    public CustomItem(String sourceFile, String vanillaId, String id,
                      boolean enableModel, JsonObject components, ActionBindings actions,
                      RandomItemSpec random) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.vanillaId = Objects.requireNonNull(vanillaId, "vanillaId");
        this.id = Objects.requireNonNull(id, "id");
        this.enableModel = enableModel;
        this.components = components != null ? components : new JsonObject();
        this.actions = actions != null ? actions : new ActionBindings();
        this.random = random;
    }

    public String sourceFile() {
        return sourceFile;
    }

    public String vanillaId() {
        return vanillaId;
    }

    public String id() {
        return id;
    }

    public boolean enableModel() {
        return enableModel;
    }

    public JsonObject components() {
        return components;
    }

    public ActionBindings actions() {
        return actions;
    }

    /** random 配置（随机变体 + 随机数占位符），无则为 null。 */
    public RandomItemSpec random() {
        return random;
    }
}
