package com.laosun.aluminium.data;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.ResourceSpec;
import com.google.gson.Gson;
import com.laosun.aluminium.utils.JSONReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The counters that belong to the <b>battle</b> rather than to one character - declared once, in
 * {@code game/data/battle_resources.json}.
 *
 * <p><b>Why such a file has to exist.</b> A party-scoped counter could only be declared by a CHARACTER file
 * ({@code "scope": "PARTY"}), so it existed only in parties that carried the declaring character. 笑点 (Punchline) is shared by
 * several kits - 1502 declares it, 1501 and 1513 gain it, relic 129's 4-piece reads it - and a party without 1502 had no such
 * counter at all, so any rule that READ it refused to fire: measured, 129's stair threw
 * {@code a weight step reads "笑点", which is neither an attribute … nor this party's counters}. Declaring it here gives every
 * battle the counter, at its stated initial value.
 *
 * <p>The file's rows are {@link ResourceSpec}s, exactly like a character's. A character may still declare the same name with
 * {@code scope: PARTY}; {@code PartyResources} merges by name and keeps the first, so the two spellings cannot disagree.
 */
public final class BattleResources {

    /**
     * The declared counters, in file order.
     */
    private static final List<ResourceSpec> DECLARED = List.copyOf(load());

    private BattleResources() {
    }

    /**
     * Reads {@code /game/battle_resources.json} - a HAND-WRITTEN file, which is why it does not live under {@code game/data/}:
     * that directory is gitignored because it holds the game's own dumps, and a declaration this project authors has to be in
     * the repository.
     *
     * @return the declarations
     */
    private static List<ResourceSpec> load() {
        String path = "/game/battle_resources.json";
        InputStream stream = BattleResources.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("missing " + path
                    + " - it declares the counters that belong to the battle rather than to one character");
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            List<ResourceSpec> parsed = new Gson().fromJson(reader, new TypeToken<List<ResourceSpec>>() {
            }.getType());
            if (parsed == null) {
                throw new IllegalStateException(path + " parsed to null; an empty declaration is written []");
            }
            return parsed;
        } catch (java.io.IOException failed) {
            throw new IllegalStateException("could not read " + path, failed);
        }
    }

    /**
     * @return every battle-scoped counter this pack declares
     */
    public static List<ResourceSpec> declared() {
        return DECLARED;
    }
}
