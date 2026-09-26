package com.laosun.aluminium.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.models.skill.Skill;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads {@code data/skill_effects.json} (P10-3) — the table that says how to read a non-damaging
 * skill's parameters, indexed by {@code cid -> slot}.
 *
 * <p>Deliberately shaped like {@link TriggerTables}: loaded once, lazily, from the classpath, and a
 * <b>missing file is an empty table rather than an error</b> — the table is generated separately and
 * the engine must still run without it (it just cannot dispatch heals, which is exactly the state
 * before this task).
 *
 * <p>Keyed by {@code (cid, slot)} because that is how {@code skills.json} is indexed, so no id
 * arithmetic is involved; {@link Skill#getCid()} and {@link Skill#getSkillSlot()} supply the key and
 * return {@code 0} for skills that have no identity (hand-built test skills, placeholders).
 */
public final class SkillEffects {

    private static final String RESOURCE = "/data/skill_effects.json";

    private static final Type SHAPE =
            new TypeToken<Map<String, Map<String, SkillEffectSpec>>>() {
            }.getType();

    private static volatile Map<String, Map<String, SkillEffectSpec>> table;
    private static final AtomicInteger LOAD_COUNT = new AtomicInteger();

    private SkillEffects() {
    }

    /**
     * The effect spec for a skill, or {@code null} when there is none.
     *
     * <p>{@code null} is the normal answer for most skills — only Restore / Defence are in the table
     * today, and only for the characters where the parameters could be parsed — so callers must
     * handle it rather than assume the table covers everything.
     *
     * @param skill the skill being cast
     * @return the spec, or {@code null} when this skill has no entry
     */
    public static SkillEffectSpec forSkill(Skill skill) {
        if (skill == null || skill.getCid() == 0 || skill.getSkillSlot() == 0) {
            return null;
        }
        Map<String, SkillEffectSpec> slots = table().get(String.valueOf(skill.getCid()));
        return slots == null ? null : slots.get(String.valueOf(skill.getSkillSlot()));
    }

    /**
     * How many times the resource was actually read — lets a test assert "loaded once".
     */
    public static int loadCount() {
        return LOAD_COUNT.get();
    }

    private static Map<String, Map<String, SkillEffectSpec>> table() {
        Map<String, Map<String, SkillEffectSpec>> local = table;
        if (local != null) {
            return local;
        }
        synchronized (SkillEffects.class) {
            if (table == null) {
                table = read();
            }
            return table;
        }
    }

    private static Map<String, Map<String, SkillEffectSpec>> read() {
        LOAD_COUNT.incrementAndGet();
        try (InputStream in = SkillEffects.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Map.of();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, Map<String, SkillEffectSpec>> parsed = new Gson().fromJson(reader, SHAPE);
                return parsed == null ? Map.of() : parsed;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }
}
