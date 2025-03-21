package me.opaque.genstools.tools.types;

import me.opaque.genstools.tools.GensTool;

import java.util.List;

public class GensPickaxe extends GensTool {

    public GensPickaxe(String id, String displayName, List<String> lore) {
        super(id, displayName, lore);
    }

    public String getType() {
        return "PICKAXE";
    }
}