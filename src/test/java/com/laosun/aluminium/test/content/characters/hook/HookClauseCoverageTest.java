package com.laosun.aluminium.test.content.characters.hook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1109 虎克 clause coverage: every section of {@code 1109_虎克.md}, plus the readings that pin the two Eidolons nobody had written.
 *
 * <p><b>What was added on 2026-11-02</b> (a section-by-section scan found 星魂 1 and 星魂 4 had no rule at all, even though the
 * file's own gap text claimed only three approximations were left):
 * <ul>
 *   <li><b>Eidolon 1</b> — 「战技强化后造成的伤害提高 20%」. The gate is the enhanced form itself: {@code damage_skill_key == 9}
 *       (the loader's slot that {@code REPLACE_SKILL} installs). ⚠ {@code from_skill_id} does <b>not</b> work on
 *       {@code DEALING_DAMAGE}: its payload carries no cast facts — measured, the reading was identical at E0 and E1.</li>
 *   <li><b>Eidolon 4</b> — 「使指定敌方目标的相邻目标也陷入等同于战技的灼烧状态」. Needed a new selector,
 *       {@code adjacent_enemies}: {@code random_adjacent_enemy} answers only one of the two neighbours.</li>
 * </ul>
 */
public class HookClauseCoverageTest {

    private static final int HOOK = 1109;

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：请…请让一下！", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：嘿！记得虎克吗", "skill_burn");
        SECTION_TO_RULE.put("终结技：轰！飞来焰火", "ult_enhances_the_next_skill");
        SECTION_TO_RULE.put("强化战技：嘿！记得虎克吗", "ult_enhances_the_next_skill");
        SECTION_TO_RULE.put("天赋：哈！火上浇油", "talent_followup_against_burning");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：哎！瞧这一团糟", "technique_burn_on_entry");
        SECTION_TO_RULE.put("行迹：童真", "trace_childlike");
        SECTION_TO_RULE.put("行迹：无邪", "trace_innocence");
        SECTION_TO_RULE.put("行迹：玩火", "trace_play_with_fire");
        SECTION_TO_RULE.put("行迹：冰伤 / 攻击 / 暴击（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：早睡早起很健康", "eidolon1_enhanced_skill_damage_up");
        SECTION_TO_RULE.put("星魂 2：吃好喝好长身体", "eidolon2_longer_burn");
        SECTION_TO_RULE.put("星魂 3：不挑不选全都要", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：稀里糊涂没关系", "eidolon4_adjacent_burn");
        SECTION_TO_RULE.put("星魂 5：好事留名鼹鼠党", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：随时准备打坏人", "eidolon6_bonus_against_burning");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        int checked = 0;
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            if (entry.getValue().startsWith("(")) {
                continue;
            }
            checked++;
            Assertions.assertTrue(has(rules, entry.getValue()),
                    entry.getKey() + " has no rule (expected " + entry.getValue() + ")");
        }
        System.out.println("[1109] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * Eidolon 1: 「战技强化后造成的伤害提高 20%」 — measured on the enhanced Skill's main target.
     *
     * <p>E0 <b>1430.016034048001</b> → E1 <b>1640.755239065601</b>. Note the ratio is not 1.2: {@code BOOST_DAMAGE} adds into
     * the damage-boost zone rather than multiplying the settled hit (the same shape 1013 and 1412 record), so the honest
     * assertion is the two measured numbers, not a ratio.
     */
    @Test
    public void eidolon1BoostsOnlyTheEnhancedSkill() {
        Assertions.assertEquals(1430.016034048001, enhancedSkillLoss(0), 1e-6, "E0: the enhanced Skill's own row");
        Assertions.assertEquals(1640.755239065601, enhancedSkillLoss(1), 1e-6,
                "E1: 「战技强化后造成的伤害提高 20%」");

        // The other half: a PLAIN Skill (no Ultimate first) must not be boosted -- 「强化后」 says which form it is.
        Assertions.assertEquals(plainSkillLoss(0), plainSkillLoss(1), 1e-6,
                "a plain Skill is not the enhanced form, so Eidolon 1 leaves it alone");
    }

    /**
     * Eidolon 4: 「使指定敌方目标的相邻目标也陷入等同于战技的灼烧状态」.
     *
     * <p>Measured with three enemies: after the Skill burns the middle one and a basic attack triggers the talent, both
     * neighbours are burning at E4 and neither is at E0.
     */
    @Test
    public void eidolon4BurnsTheNeighbours() {
        Assertions.assertArrayEquals(new boolean[]{false, false},
                neighboursBurning(0), "control: at E0 the neighbours are untouched");
        Assertions.assertArrayEquals(new boolean[]{true, true},
                neighboursBurning(4), "「相邻目标也陷入…灼烧状态」 — both of them");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Casts the Ultimate (which installs the enhanced Skill) then the Skill on the first enemy; returns the HP it lost.
     */
    private static double enhancedSkillLoss(int eidolon) {
        Fixture f = new Fixture(eidolon, 1);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.ULTRA), f.hook, List.of(f.enemies.getFirst()));
        f.battle.processRequests();
        return f.measureSkillHit(0);
    }

    private static double plainSkillLoss(int eidolon) {
        Fixture f = new Fixture(eidolon, 1);
        return f.measureSkillHit(0);
    }

    private static boolean[] neighboursBurning(int eidolon) {
        Fixture f = new Fixture(eidolon, 3);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.COMMON), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        return new boolean[]{
                f.enemies.get(0).getBuffManager().hasState("灼烧"),
                f.enemies.get(2).getBuffManager().hasState("灼烧")};
    }

    private static boolean has(JsonArray rules, String id) {
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1109.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static final class Fixture {
        private final Character hook;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private Fixture(int eidolon, int enemyCount) {
            hook = CharacterFactory.create(HOOK, 80, false, null, null, eidolon);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
            }
            battle = new Battle(List.of(hook), List.copyOf(enemies), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** Casts the Skill at enemy {@code index} and returns the HP that enemy lost. */
        private double measureSkillHit(int index) {
            Enemy target = enemies.get(index);
            double before = target.getCurrentHp();
            battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(target));
            battle.processRequests();
            return before - target.getCurrentHp();
        }
    }
}
