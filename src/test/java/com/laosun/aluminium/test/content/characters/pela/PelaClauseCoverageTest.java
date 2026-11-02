package com.laosun.aluminium.test.content.characters.pela;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1106 佩拉 clause coverage: every section of {@code 1106_佩拉.md}, plus the readings that pin the clauses no machine
 * guard can see.
 *
 * <p><b>What was added on 2026-11-02</b> (three of her six Eidolons had never been read):
 * <ul>
 *   <li><b>Eidolon 1</b> — 「敌方目标被消灭时，佩拉恢复 5 点能量」. Note the sentence names <b>no killer</b>, so the rule carries
 *       <b>no</b> {@code actor == self}: measured, an ally's kill pays her too (E0 pays nothing). The only gate is
 *       {@code !target is_ally}, so our own deaths do not pay.</li>
 *   <li><b>Eidolon 6</b> — the additional 40%-ATK instance against a debuffed target, same event and condition as the talent.</li>
 *   <li><b>Eidolon 2</b> — +10% SPD for 2 turns after the dispel.</li>
 *   <li><b>The technique's damage half</b> — 「对敌方随机单体造成等同于佩拉 80% 攻击力的冰属性伤害」 had never been written
 *       (only its defence-down half was).</li>
 * </ul>
 *
 * <p><b>What is registered instead</b>: 「施放战技<b>解除增益效果时</b>」 cannot be checked — there is no "how many buffs does the
 * target carry" variable and {@code REMOVE_BUFF} announces nothing — so the trace and Eidolon 2 fire on the <b>cast</b>.
 * {@link #theDispelTriggerIsApproximatedByTheCast} is the gap witness: it pins today's reading and will go red when the
 * engine can tell that something was really removed.
 */
public class PelaClauseCoverageTest {

    private static final int PELA = 1106;
    private static final int ALLY = 1002;

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：冰点射击",
                "(no rule needed: the basic attack reads its own skill row — two segments of 50%)");
        SECTION_TO_RULE.put("战技：低温妨害", "skill_dispel_buff");
        SECTION_TO_RULE.put("终结技：领域压制", "ult_zone_suppression");
        SECTION_TO_RULE.put("天赋：数据采集", "attack_effect");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：先发制人", "technique_preemptive_strike");
        SECTION_TO_RULE.put("行迹：痛击", "trace_hit_the_afflicted");
        SECTION_TO_RULE.put("行迹：秘策", "battle_start");
        SECTION_TO_RULE.put("行迹：追歼", "trace_next_attack_after_dispel");
        SECTION_TO_RULE.put("行迹：冰伤 / 攻击 / 效果命中（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：胜利反馈", "eidolon1_energy_on_an_enemy_kill");
        SECTION_TO_RULE.put("星魂 2：疾进不止", "eidolon2_speed_after_the_dispel");
        SECTION_TO_RULE.put("星魂 3：压制升级", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：完全剖析", "eidolon4_ice_resistance_down_on_skill");
        SECTION_TO_RULE.put("星魂 5：零度妨害", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：疲弱追击", "eidolon6_extra_damage_on_a_debuffed_target");
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
        System.out.println("[1106] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 「生命值/防御力/效果抵抗 +N%」are folded in; her five ice-damage traces add up to 22.4%.
     */
    @Test
    public void theStatTracesAreFoldedIntoThePanel() {
        Character pela = CharacterFactory.create(PELA, 80);
        Assertions.assertEquals(0.224, pela.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), 1e-9,
                "3.20 + 3.20 + 4.80 + 4.80 + 6.40 = 22.4% of ice damage");
        Assertions.assertEquals(0.1, pela.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "the +4% and +6% effect-hit traces");
        Assertions.assertEquals(546.84 * 1.18, pela.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "ATK: the Lv80 base 546.84 plus the +4/+6/+8% attack traces");
    }

    /**
     * Eidolon 1: 「敌方目标被消灭时，佩拉恢复 5 点能量」 — and the killer is <b>not</b> part of the sentence.
     *
     * <p>Measured: at E1 an <b>ally's</b> kill pays her 5 (at E0 it pays nothing), and the same fixture without a kill pays
     * nothing — so the reading is about the kill, not about the fixture.
     */
    @Test
    public void eidolon1PaysForAnyEnemyKill() {
        Fixture e1 = new Fixture(1);
        double before = e1.pela.getCurrentEnergy();
        e1.killBy(e1.ally);
        Assertions.assertEquals(5.0, e1.pela.getCurrentEnergy() - before, 1e-9,
                "「敌方目标被消灭时」 — no `actor == self`: somebody else's kill pays her too");

        Fixture e0 = new Fixture(0);
        double e0Before = e0.pela.getCurrentEnergy();
        e0.killBy(e0.ally);
        Assertions.assertEquals(0.0, e0.pela.getCurrentEnergy() - e0Before, 1e-9,
                "control: it is Eidolon 1");

        Fixture none = new Fixture(1);
        double noneBefore = none.pela.getCurrentEnergy();
        none.battle.processRequests();
        Assertions.assertEquals(0.0, none.pela.getCurrentEnergy() - noneBefore, 1e-9,
                "control: nothing died");
    }

    /**
     * The other half of Eidolon 1's gate: our own deaths must not pay, which is what {@code !target is_ally} is for.
     *
     * <p>Structural rather than behavioural: a dead unit's own table is skipped, so killing Pela herself cannot show it.
     */
    @Test
    public void eidolon1ExcludesOurOwnDeaths() throws IOException {
        JsonObject rule = rule("eidolon1_energy_on_an_enemy_kill");
        Assertions.assertTrue(rule.getAsJsonArray("when").toString().contains("!target is_ally"),
                "the gate must be `!target is_ally` (not `target is_enemy`, which the vocabulary does not have)");
        Assertions.assertFalse(rule.getAsJsonArray("when").toString().contains("actor == self"),
                "and there must be NO killer gate: 「敌方目标被消灭时」 says nothing about who killed it");
    }

    /**
     * Eidolon 6: 「施放攻击后，若敌方目标处于负面效果，则对其造成…40% 攻击力的冰属性附加伤害」.
     *
     * <p>Measured with the engine's own {@code ALLY_ATTACK} announcement on a clean target: the extra instance is
     * <b>126.01593746437902</b> at E6 with a DOT on the enemy and exactly <b>0</b> at E0 or with no DOT. (A real basic
     * attack was measured first, but its own weakness break debuffs the target on impact — which makes the "no debuff"
     * half impossible to build that way, and is exactly why the announcement is fired directly here.)
     */
    @Test
    public void eidolon6PaysOnlyAgainstADebuffedTarget() {
        Fixture e6 = new Fixture(6);
        e6.enemy.getBuffManager().addBuff(new DotBuff(e6.pela, DamageElement.FIRE, 1, 99));
        double before = e6.enemy.getCurrentHp();
        e6.announceAttack();
        Assertions.assertEquals(126.01593746437902, before - e6.enemy.getCurrentHp(), 1e-6,
                "40% of her ATK, settled as an ADDITIONAL instance");

        Fixture clean = new Fixture(6);
        double cleanBefore = clean.enemy.getCurrentHp();
        clean.announceAttack();
        Assertions.assertEquals(0.0, cleanBefore - clean.enemy.getCurrentHp(), 1e-9,
                "control: no negative effect ⇒ 「若敌方目标处于负面效果」 is false");

        Fixture e0 = new Fixture(0);
        e0.enemy.getBuffManager().addBuff(new DotBuff(e0.pela, DamageElement.FIRE, 1, 99));
        double e0Before = e0.enemy.getCurrentHp();
        e0.announceAttack();
        Assertions.assertEquals(0.0, e0Before - e0.enemy.getCurrentHp(), 1e-9,
                "control: it is Eidolon 6");
    }

    /**
     * Eidolon 2: +10% SPD for 2 turns after the skill. Measured 105 → <b>115.5</b> at E2, unchanged at E1.
     *
     * <p><b>Gap witness</b>: the target here carries <b>no</b> buff at all, yet the speed still lands — that is the
     * 「解除增益效果时」 approximation registered in {@code _gaps}. This test will go red when the engine can tell that a
     * buff was really removed.
     */
    @Test
    public void theDispelTriggerIsApproximatedByTheCast() {
        Fixture e2 = new Fixture(2);
        Assertions.assertTrue(e2.enemy.getBuffManager().activeBuffs().isEmpty(), "precondition: nothing to dispel");
        e2.castSkill();
        Assertions.assertEquals(115.50000000000001, e2.pela.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "105 × 1.1 — the speed lands even though there was no buff to remove (the registered approximation)");

        Fixture e1 = new Fixture(1);
        e1.castSkill();
        Assertions.assertEquals(105.0, e1.pela.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "control: it is Eidolon 2");
    }

    /**
     * The technique's two halves: 「对敌方随机单体造成 80% 攻击力的冰伤」 + 「防御力降低 20%」.
     *
     * <p>Measured: the enemy starts at <b>16498.2960225</b> and takes <b>216.634139685956</b> at battle start, and its
     * DEFENCE falls from 1100 to <b>880</b> (exactly −20%).
     */
    @Test
    public void theTechniqueLandsBothHalves() {
        Fixture tech = new Fixture(0, true);
        Assertions.assertEquals(216.634139685956, tech.enemy.getMaxHp() - tech.enemy.getCurrentHp(), 1e-6,
                "「对敌方随机单体造成等同于佩拉 80% 攻击力的冰属性伤害」");
        Assertions.assertEquals(879.9999600000001, tech.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "「防御力降低 20%」 — 1100 × 0.8");

        Fixture plain = new Fixture(0);
        Assertions.assertEquals(plain.enemy.getCurrentHp(), plain.enemy.getMaxHp(), 1e-6,
                "control: without the technique nothing is taken and nothing is lowered");
        Assertions.assertEquals(1099.9999500000001, plain.enemy.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "control: the defence is untouched");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean has(JsonArray rules, String id) {
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject rule(String id) throws IOException {
        for (var element : rules()) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return candidate;
            }
        }
        throw new AssertionError("no rule " + id);
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1106.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static final class Fixture {
        private final Character pela;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            this(eidolon, false);
        }

        private Fixture(int eidolon, boolean technique) {
            pela = CharacterFactory.create(PELA, 80, false, null, null, eidolon);
            battle = new Battle(List.of(pela, ally), List.of(enemy), BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(pela);
            }
            battle.startBattle();
            battle.processRequests();
        }

        private void killBy(Character killer) {
            battle.applyTrueDamage(killer, enemy, DamageElement.ICE, enemy.getMaxHp() * 2);
            battle.processRequests();
        }

        private void announceAttack() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, pela, enemy, 0, 0));
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(pela.getSkills().get(SkillType.SKILL), pela, List.of(enemy));
            battle.processRequests();
        }
    }
}

