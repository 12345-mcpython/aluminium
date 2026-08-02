package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.models.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registry of all {@link CharacterKit}s.
 *
 * <p>To add a new character: implement {@link CharacterKit} and register it
 * here. Characters without a dedicated kit are served by {@link GenericKit},
 * which interprets their passives from the game data.
 */
public final class KitRegistry {

    private static final Map<Integer, CharacterKit> KITS = new ConcurrentHashMap<>();

    static {
        register(new GenericKit());
        registerAll(TenStarKits.all());
        registerAll(ElevenStarKits.all());
        registerAll(TwelveStarKits.all());
        registerAll(ThirteenStarKits.all());
        registerAll(FourteenStarKits.all());
        registerAll(FifteenStarKits.all());
        register(new TrailblazerKit(8005));
        register(new TrailblazerKit(8006));
    }

    private KitRegistry() {
    }

    private static void register(CharacterKit kit) {
        KITS.put(kit.cid(), kit);
    }

    private static void registerAll(List<? extends CharacterKit> kits) {
        for (CharacterKit kit : kits) {
            register(kit);
        }
    }

    /**
     * Returns the kit serving the given character ID (never null).
     */
    public static CharacterKit forCid(int cid) {
        return KITS.computeIfAbsent(cid, c -> new GenericKit());
    }

    /**
     * Builds the trace passives of the given character (行迹 A2/A4/A6).
     */
    public static List<Trace> traces(int cid, Map<Integer, SkillPoint> points) {
        return forCid(cid).traces(points);
    }

    /**
     * Builds the trace passives of the given character from a raw skill-point
     * list (the point.json nodes).
     */
    public static List<Trace> traces(int cid, List<SkillPoint> points) {
        Map<Integer, SkillPoint> byId = new java.util.HashMap<>();
        if (points != null) {
            for (SkillPoint point : points) {
                if ("skill".equals(point.pointType())) {
                    byId.put(point.pointId(), point);
                }
            }
        }
        return forCid(cid).traces(byId);
    }

    /**
     * Builds the eidolon passives (星魂) up to the given level.
     */
    public static List<Trace> eidolons(int cid, int level, Map<Integer, Eidolon> ranks) {
        List<Trace> result = new ArrayList<>();
        if (level <= 0 || ranks == null) {
            return result;
        }
        CharacterKit kit = forCid(cid);
        for (int r = 1; r <= Math.min(6, level); r++) {
            Eidolon e = ranks.get(r);
            if (e == null) {
                continue;
            }
            Trace trace = kit.eidolon(r, e);
            if (trace != null) {
                result.add(trace);
            }
        }
        return result;
    }

    /**
     * Returns the special skill behavior for (cid, skillId), or null if the
     * skill is handled by the generic executor.
     */
    public static SkillBehavior skillBehavior(int cid, int skillId) {
        return forCid(cid).skillBehavior(skillId);
    }

    /**
     * Returns the skill-level bonuses granted by all eidolon ranks up to the
     * given level (星魂3/5: 终结技+2级 etc.).
     *
     * @return map of skill ID (1 = basic, 2 = skill, 3 = ultimate, 4 = talent)
     *         to bonus levels
     */
    public static Map<Integer, Integer> skillLevelBonuses(int cid, int level,
                                                          Map<Integer, Eidolon> ranks) {
        Map<Integer, Integer> result = new java.util.HashMap<>();
        if (ranks == null) {
            return result;
        }
        for (int r = 1; r <= Math.min(6, level); r++) {
            Eidolon e = ranks.get(r);
            if (e == null || e.skillAdd() == null) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : e.skillAdd().entrySet()) {
                int skillId = Integer.parseInt(entry.getKey()) % 100;
                result.merge(skillId, entry.getValue(), Integer::sum);
            }
        }
        return result;
    }
}
