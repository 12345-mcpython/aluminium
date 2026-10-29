package com.laosun.compare;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.RelicSet;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.equipment.Relic;
import com.laosun.aluminium.equipment.RelicSuit;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.utils.JSONReader;

import java.util.List;
import java.util.Map;

/**
 * Builds a real character from <b>the account dump</b>: {@code resources/game/data/dump_data.json} carries, per cid, the
 * six relics the account wears (set name, slot, main stat, sub stats at their promote/roll levels), in the engine's own
 * attribute names.
 *
 * <p><b>Why a loader and not six hand-built relics per test.</b> The dump's JSON is already the shape
 * {@link Relic.Setting#fromJson} reads, so a relic is one call; what needs a home is the two joins the dump does not
 * carry: the <b>set name to set id</b> mapping (the dump names the set in English, the engine counts ids) and the
 * <b>light cone</b>, which the dump does not hold at all and the caller therefore states.
 *
 * <p>⚠ <b>This is a tool, not engine behaviour.</b> Nothing in the engine reads the account dump: it is one player's
 * equipment, not game data. A panel computed this way is only meaningful as "what the account's own build gives",
 * which is exactly what a replay or a capture comparison needs.
 */
public final class AccountLoadout {

    /** One character's build as the caller states it: the cone (the dump has none) and the eidolon rank. */
    public record Build(int cid, int level, int eidolon, int coneId, int coneLevel, int coneRank) {
    }

    private AccountLoadout() {
    }

    /**
     * @param build the character and the cone the dump cannot state
     * @return the character with the account's relics and that cone
     */
    public static Character assemble(Build build) {
        return assemble(build, dump());
    }

    /**
     * The same, against an already-parsed dump (so a test that builds several characters parses it once).
     *
     * @param build the character and cone
     * @param dump  the parsed {@code dump_data.json}
     * @return the character
     */
    public static Character assemble(Build build, Map<Integer, List<JsonObject>> dump) {
        List<JsonObject> entries = dump.get(build.cid());
        if (entries == null) {
            throw new IllegalArgumentException("the account dump has no relics for cid " + build.cid());
        }
        RelicSuit suit = new RelicSuit();
        for (JsonObject entry : entries) {
            Relic relic = Relic.createBySetting(RelicType.getType(entry.get("type").getAsString()), 5, 15,
                    entry.get("json").toString());
            relic.setId = setOf(entry.get("set").getAsString());
            suit.addToSuit(relic);
        }
        Weapon cone = Weapon.build(build.coneId(), build.coneLevel(), true, build.coneRank());
        return CharacterFactory.create(build.cid(), build.level(), true, cone, suit, build.eidolon());
    }

    /**
     * @return the account dump, parsed
     */
    public static Map<Integer, List<JsonObject>> dump() {
        return JSONReader.fromJSON("dump_data.json", new TypeToken<Map<Integer, List<JsonObject>>>() {
        }.getType());
    }

    /**
     * The set id for an English set name, as {@code relic_sets.json} spells it.
     *
     * @param english the dump's set name
     * @return the set id, or {@link Constant#RELIC_SET_NONE} when no set carries that name
     */
    public static int setOf(String english) {
        for (RelicSet set : Constant.RELIC_SETS.values()) {
            if (set.name() != null && english.equals(set.name().english())) {
                return set.setId();
            }
        }
        return Constant.RELIC_SET_NONE;
    }
}
