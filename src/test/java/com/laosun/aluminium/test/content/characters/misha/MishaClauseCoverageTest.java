package com.laosun.aluminium.test.content.characters.misha;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
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
 * 1312 米沙 clause coverage: every section of {@code 1312_米沙.md}, plus what this round added and what it could not.
 *
 * <p><b>Added on 2026-11-02</b>: 行迹 锁接 (effect-hit rate for the duration of the Ultimate, via {@code CAST_SETUP} +
 * {@code until: cast_end} — {@code ULT_CAST} would be too late: it fires after the freeze rolls), 行迹 传冲 (+30% crit damage
 * against a frozen target, as an instance modifier), the Eidolon 6 skill-point refund (a one-shot marker consumed on the next
 * Skill), the Eidolon 6 duration read as {@code until: turn_end}, and the Skill's own 「增加 1 段」 which had never been written.
 *
 * <p><b>Still registered</b>: 行迹 释放 needs the <b>segment ordinal</b> (it raises the FIRST segment's freeze chance only), and the
 * hit-count reset is a subtraction because the engine has no "set back to the initial count".
 */
public class MishaClauseCoverageTest {

    private static final int MISHA = 1312;

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：请…请让一下！", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：客…客房服务！", "skill_adds_one_segment_to_the_next_ultimate");
        SECTION_TO_RULE.put("终结技：要…要迟到了！", "ult_freeze_per_segment");
        SECTION_TO_RULE.put("天赋：擒纵机构", "talent_extra_segment_per_point");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：停一停吧，你真美丽！", "technique_dream_prison_raises_segments");
        SECTION_TO_RULE.put("行迹：释放", "(registered: no segment ordinal — it only raises the FIRST segment's chance)");
        SECTION_TO_RULE.put("行迹：锁接", "trace_lockstep_effect_hit_during_the_ultimate");
        SECTION_TO_RULE.put("行迹：传冲", "trace_momentum_crit_damage_against_frozen");
        SECTION_TO_RULE.put("行迹：冰伤 / 防御 / 暴击率（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：飘忽的幻影", "eidolon1_extra_segments_per_enemy");
        SECTION_TO_RULE.put("星魂 2：青春的怅望", "eidolon2_freeze_also_lowers_defence");
        SECTION_TO_RULE.put("星魂 3：欢乐年光的影子", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：亲挚的音容", "eidolon4_ult_segment_damage_up");
        SECTION_TO_RULE.put("星魂 5：最初的爱与友谊", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：久已生疏的憧憬", "eidolon6_damage_up_and_refunds_a_point");
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
        System.out.println("[1312] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 行迹 锁接: 「施放终结技时，效果命中提高 60%，持续至本次终结技行动结束」.
     *
     * <p>Measured: the effect-hit rate is <b>0.0</b> before the Ultimate, <b>0.6</b> on every damage instance it settles, and
     * <b>0.0</b> again once the cast ends. The window matters: the freeze rolls happen inside the settlement, so a rule on
     * {@code ULT_CAST} would arrive after them.
     */
    @Test
    public void theLockstepTraceRaisesEffectHitForTheWholeUltimate() {
        Fixture f = new Fixture(0);
        List<Double> during = f.effectHitDuringUltimate();

        Assertions.assertEquals(0.0, f.baseEffectHit, 1e-9, "precondition: no free effect-hit rate");
        Assertions.assertFalse(during.isEmpty(), "precondition: the Ultimate settled something");
        for (double seen : during) {
            Assertions.assertEquals(0.6, seen, 1e-9, "「效果命中提高 60%」 — during every instance of the cast");
        }
        Assertions.assertEquals(0.0, f.misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "「持续至本次终结技行动结束」 — and it is gone afterwards");
    }

    /**
     * 星魂 6's second half: 「下一次施放战技后为我方恢复 1 个战技点」.
     *
     * <p>Measured: the Ultimate leaves the marker, the next Skill turns it into <b>+1 skill point</b> (3 → 4) and clears it; a
     * second Skill pays nothing.
     */
    @Test
    public void eidolon6RefundsOnePointAfterTheNextSkill() {
        Fixture f = new Fixture(6);
        f.castUltimate();
        Assertions.assertEquals(1, f.misha.getBuffManager().stacksOf("下个战技返还战技点"),
                "the Ultimate arms the refund");

        int before = f.battle.getSkillPoints();
        f.castSkill();
        Assertions.assertEquals(before + 1, f.battle.getSkillPoints(), "「为我方恢复 1 个战技点」");
        Assertions.assertEquals(0, f.misha.getBuffManager().stacksOf("下个战技返还战技点"),
                "「下一次」 — only once");

        int after = f.battle.getSkillPoints();
        f.castSkill();
        Assertions.assertEquals(after, f.battle.getSkillPoints(), "a second Skill pays nothing");
    }

    /**
     * <b>Gap witness</b>: 行迹 释放 (「<b>终结技首段</b>攻击前…基础概率提高 80%」) is registered — the events carry no segment ordinal,
     * and giving every segment +80% would freeze on 100% from the second hit on.
     */
    @Test
    public void theFirstSegmentTraceIsStillRegistered() throws IOException {
        Assertions.assertTrue(gapMentions("释放"), "行迹 释放 is registered, not silently dropped");
    }

    /**
     * <b>Gap witness</b>: the hit-count reset is a subtraction (-6) rather than "set back to the initial 3"; the gap text says so
     * and will be removed when a "set" spelling exists.
     */
    @Test
    public void theHitCountResetIsStillAnApproximation() throws IOException {
        Assertions.assertTrue(gapMentions("段数复位"), "the reset approximation is registered");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean gapMentions(String needle) throws IOException {
        for (var element : gaps()) {
            if (element.getAsString().contains(needle)) {
                return true;
            }
        }
        return false;
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
        return character().getAsJsonArray("rules");
    }

    private static JsonArray gaps() throws IOException {
        return character().getAsJsonArray("_gaps");
    }

    private static JsonObject character() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1312.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final class Fixture {
        private final Character misha;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;
        private final double baseEffectHit;

        private Fixture(int eidolon) {
            misha = CharacterFactory.create(MISHA, 80, false, null, null, eidolon);
            battle = new Battle(List.of(misha), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            baseEffectHit = misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        }

        private List<Double> effectHitDuringUltimate() {
            List<Double> seen = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                    seen.add(misha.getAttribute(AttributeType.EFFECT_HIT_RATE).get()));
            battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
            battle.processRequests();
            return seen;
        }

        private void castUltimate() {
            battle.castImmediate(misha.getSkills().get(SkillType.ULTRA), misha, List.of(enemy));
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(misha.getSkills().get(SkillType.SKILL), misha, List.of(enemy));
            battle.processRequests();
        }
    }
}
