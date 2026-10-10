package com.sscustomitems;

import com.google.gson.JsonObject;

import java.util.Objects;

public final class CustomItem {

    private final String sourceFile;
    private final String vanillaId;
    private final String id;
    private final boolean enableModel;
    private final JsonObject components;
    private final ActionBindings actions;

    public CustomItem(String sourceFile, String vanillaId, String id,
                      boolean enableModel, JsonObject components, ActionBindings actions) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile");
        this.vanillaId = Objects.requireNonNull(vanillaId, "vanillaId");
        this.id = Objects.requireNonNull(id, "id");
        this.enableModel = enableModel;
        this.components = components != null ? components : new JsonObject();
        this.actions = actions != null ? actions : new ActionBindings();
    }

    public String sourceFile() { return sourceFile; }
    public String vanillaId() { return vanillaId; }
    public String id() { return id; }
    public boolean enableModel() { return enableModel; }
    public JsonObject components() { return components; }
    public ActionBindings actions() { return actions; }
}
