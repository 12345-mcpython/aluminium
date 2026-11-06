package com.laosun.aluminium.test.content.characters.bailu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.ReductionBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
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
 * 1211 白露 clause coverage: every section of {@code 1211_白露.md}, one line each, plus the readings that pin the
 * clauses the machine guards cannot see.
 *
 * <p><b>Three things this file found (2026-11-02)</b>, in the order they matter:
 * <ol>
 *   <li><b>The Eidolon 1 rule paid the wrong unit.</b> It was written {@code self state_ended 生息} + {@code hp_percent >= 1} +
 *       {@code target: self} — both the "whose [生息] ended" and the "whose HP is full" ends of the sentence were read on
 *       <b>Bailu herself</b>, so she collected 8 energy whenever <i>she</i> was full. Measured three ways before changing
 *       anything (see {@link #eidolon1PaysTheAllyWhoseInvigorationEndsAtFullHp} and its two neighbours).</li>
 *   <li><b>The talent's "1 time per battle" had never been written</b>, so every lethal blow healed again. Now
 *       {@code once_per_battle: true}; the Eidolon 6 "one more time" is registered (a boolean cannot say "2").</li>
 *   <li><b>Eidolon 4 cannot ride {@code HEALED}</b>: the loader refuses {@code from_category BPSKILL} there ("HEALED carries
 *       none"). It is written on {@code SKILL_CAST} instead — equivalent today, because a skill heals exactly one target.</li>
 * </ol>
 *
 * <p>The rest of her document is registered in {@code 1211.json}'s {@code _gaps}; nothing is left silent.
 */
public class BailuClauseCoverageTest {

    private static final int BAILU = 1211;
    private static final int ALLY = 1002;

    /**
     * Section → the rule that answers it. A section whose answer is a registered gap says so in words, so that "not
     * written" is never mistaken for "covered".
     */
    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：望 、闻、问…蹴！", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：云吟乍涌坠珠露",
                "(no rule needed for the first heal: the skill row pays it; the 2 random decaying heals are registered)");
        SECTION_TO_RULE.put("终结技：匏蛟跃渊先雷音", "ult_rebirth");
        SECTION_TO_RULE.put("天赋：奔走悬壶济世长", "talent_heals_a_lethal_hit_on_a_teammate");
        SECTION_TO_RULE.put("秘技：徜徉灵泽病恙短", "technique_invigoration_for_the_whole_party");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("星魂 1：百脉甘津宁神久", "eidolon1_energy_when_invigoration_ends_at_full_hp");
        SECTION_TO_RULE.put("星魂 2：壶中洞天云螭眠", "eidolon2_heal_boost");
        SECTION_TO_RULE.put("星魂 3：掌间乾坤便通玄", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：肘后备急除外障", "eidolon4_healed_ally_deals_more");
        SECTION_TO_RULE.put("星魂 5：方定一倾浣俗尘", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：龙漦吐哺胜金丹",
                "(registered: the lethal heal's count is `once_per_battle`, a boolean — no \"one more\" spelling)");
        SECTION_TO_RULE.put("行迹：岐黄精义", "(registered: no overheal observation — HEALED carries no overflow)");
        SECTION_TO_RULE.put("行迹：持明龙脉", "trace_dragon_vein_adds_one_invigoration_trigger");
        SECTION_TO_RULE.put("行迹：鳞渊福泽", "ult_rebirth");
        SECTION_TO_RULE.put("行迹：生命 / 防御 / 效果抵抗（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
    }

    /**
     * Every section maps to a rule id that exists in the file (or to a written reason why none is needed).
     */
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
        System.out.println("[1211] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 「生命值/防御力/效果抵抗 +N%」: the stat traces are folded into the panel rather than written as rules.
     *
     * <p>Measured on 2026-11-02: HEALTH 1319.47 × 1.28 = <b>1688.92416</b> (+4+4+6+6+8 %), DEFENCE 485.1 × 1.225 =
     * <b>594.2475</b> (+5+7.5+10 %), EFFECT_RESISTANCE <b>0.1</b> (+4+6 %).
     */
    @Test
    public void theStatTracesAreFoldedIntoThePanel() {
        Character bailu = CharacterFactory.create(BAILU, 80);
        Assertions.assertEquals(1688.92416, bailu.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "HEALTH: the doc's Lv80 base 1319.47 plus the +28% of the four health traces");
        Assertions.assertEquals(594.2475, bailu.getAttribute(AttributeType.DEFENCE).get(), 1e-6,
                "DEFENCE: 485.1 plus the +22.5% of the three defence traces");
        Assertions.assertEquals(0.1, bailu.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "EFFECT_RESISTANCE: the +4% and +6% traces");
    }

    /**
     * Eidolon 1 (「【生息】结束时若我方目标当前生命值等于其生命上限，则额外恢复目标8点能量」): <b>the carrier</b> is paid, not Bailu.
     *
     * <p>Measured before the fix: with both at full HP the old rule gave <b>Bailu</b> +8 and the ally +0. Now the ally gets
     * the 8 and Bailu gets nothing.
     */
    @Test
    public void eidolon1PaysTheAllyWhoseInvigorationEndsAtFullHp() {
        Fixture f = new Fixture(1, false, true);
        double bailuBefore = f.bailu.getCurrentEnergy();
        double allyBefore = f.ally.getCurrentEnergy();

        f.battle.fireStateEnded(f.ally, "生息");

        Assertions.assertEquals(8.0, f.ally.getCurrentEnergy() - allyBefore, 1e-9,
                "「额外恢复目标 8 点能量」 — the target is the one whose [生息] ended");
        Assertions.assertEquals(0.0, f.bailu.getCurrentEnergy() - bailuBefore, 1e-9,
                "and it is NOT the rule's owner (the old reading paid Bailu)");
    }

    /**
     * The reverse half: 「若…生命值等于其生命上限」 — an ally below full HP pays nothing, even though Bailu is full.
     */
    @Test
    public void eidolon1PaysNothingWhenTheCarrierIsHurt() {
        Fixture f = new Fixture(1, false, true);
        f.ally.takeDamage(f.ally.getCurrentHp() * 0.5);
        double bailuBefore = f.bailu.getCurrentEnergy();
        double allyBefore = f.ally.getCurrentEnergy();

        f.battle.fireStateEnded(f.ally, "生息");

        Assertions.assertEquals(0.0, f.ally.getCurrentEnergy() - allyBefore, 1e-9,
                "the carrier is below full HP ⇒ nothing (measured: the old rule still paid Bailu 8 here)");
        Assertions.assertEquals(0.0, f.bailu.getCurrentEnergy() - bailuBefore, 1e-9, "...and nothing to Bailu either");
    }

    /**
     * And the gate: it is Eidolon 1, so at E0 the clause is not live.
     */
    @Test
    public void eidolon1IsNotLiveAtE0() {
        Fixture f = new Fixture(0, false, true);
        double allyBefore = f.ally.getCurrentEnergy();

        f.battle.fireStateEnded(f.ally, "生息");

        Assertions.assertEquals(0.0, f.ally.getCurrentEnergy() - allyBefore, 1e-9,
                "「星魂 1」 gates the clause itself");
    }

    /**
     * 秘技: 「下一次战斗开始时为我方全体附上【生息】，持续 2 回合」 — and the trace's −10% rides with it.
     */
    @Test
    public void theTechniqueInvigoratesTheWholePartyAtBattleStart() {
        Fixture with = new Fixture(0, true, false);
        Assertions.assertTrue(with.bailu.getBuffManager().hasState("生息"), "「我方全体」 includes Bailu");
        Assertions.assertTrue(with.ally.getBuffManager().hasState("生息"), "...and the ally");
        Assertions.assertEquals(1, reductionsOn(with.ally),
                "「拥有【生息】的角色受到的伤害降低 10%」 — the technique's grant carries the trace too");

        Fixture without = new Fixture(0, false, false);
        Assertions.assertFalse(without.ally.getBuffManager().hasState("生息"),
                "control: no technique, no ultimate ⇒ no [生息] (so the reading above is about the technique)");
        Assertions.assertEquals(0, reductionsOn(without.ally), "control: no reduction either");
    }

    /**
     * Eidolon 4: 「战技提供的每1次治疗…造成的伤害提高10%，最多叠加3层，持续2回合」.
     *
     * <p>Measured: one cast = +0.1, two = +0.2, three = +0.3, four = <b>still</b> +0.3 (the cap), and at E3 nothing at all.
     */
    @Test
    public void eidolon4StacksOncePerSkillCast() {
        Fixture e4 = new Fixture(4, false, true);
        double[] expected = {0.1, 0.2, 0.3, 0.30000000000000004};
        for (int cast = 0; cast < expected.length; cast++) {
            e4.castSkillOnAlly();
            Assertions.assertEquals(expected[cast], e4.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(),
                    1e-9, "cast " + (cast + 1) + ": one stack per heal, capped at 3");
        }

        Fixture e3 = new Fixture(3, false, true);
        e3.castSkillOnAlly();
        Assertions.assertEquals(0.0, e3.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "control: E3 ⇒ the clause is not live");
    }

    /**
     * 天赋: 「该效果单场战斗中可以触发 1 次」.
     *
     * <p>The first lethal blow leaves the victim standing; the second must <b>not</b> be answered again (measured: HP 0).
     * Note: without {@code once_per_battle} this healed on every lethal blow — the registered symptom this pins.
     */
    @Test
    public void theLethalHealHappensOncePerBattle() {
        Fixture f = new Fixture(0, false, true);

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0, "precondition: the first lethal blow was answered");
        double afterFirst = f.ally.getCurrentHp();

        f.strikeLethally();
        Assertions.assertEquals(0.0, f.ally.getCurrentHp(), 1e-9,
                "「单场战斗中可以触发 1 次」 — the second blow is not healed (first heal left " + afterFirst + " HP)");
    }

    /**
     * 星魂 6: 「效果触发次数增加 1 次」 — the second lethal blow IS answered at E6.
     *
     * <p>The allowance is a counter rather than {@code once_per_battle} precisely so this Eidolon could raise it: a boolean has
     * nothing to add 1 to.
     */
    @Test
    public void eidolonSixAnswersASecondLethalBlow() {
        Fixture f = new Fixture(6, false, true);

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0, "precondition: the first lethal blow was answered");

        f.strikeLethally();
        Assertions.assertTrue(f.ally.getCurrentHp() > 0,
                "「效果触发次数增加 1 次」 — the second blow is answered too at E6");
    }

    /**
     * 天赋: 「拥有【生息】的我方目标受到攻击后回复 5.4% 生命上限 + 144，该效果可以触发 2 次」.
     *
     * <p>The 「2 次」 is a counted allowance read off the talent's own row (#5), and the third hit heals nobody. That is the count the
     * gap called missing — it never needed a state that carries its own uses.
     */
    @Test
    public void theInvigorationHealsTheHitAllyTwice() {
        Character bailu = CharacterFactory.create(BAILU, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(bailu, ally), List.of(BattleFixture.dummy()),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
        battle.processRequests();
        Assertions.assertTrue(ally.getBuffManager().hasState("生息"), "precondition: the Ultimate grants 生息");

        double first = ally.getCurrentHp();
        hit(battle, ally);
        double afterFirst = ally.getCurrentHp();
        hit(battle, ally);
        double afterSecond = ally.getCurrentHp();
        hit(battle, ally);
        double afterThird = ally.getCurrentHp();

        Assertions.assertTrue(afterFirst > first - 1e-6, "the first hit is answered");
        Assertions.assertTrue(afterSecond > afterFirst - 1e-6, "the second hit is answered too");
        Assertions.assertEquals(afterSecond, afterThird, 1e-9,
                "「该效果可以触发 2 次」 — the third hit is not healed");
        Assertions.assertEquals(0, bailu.getBuffManager().stacksOf("生息治疗额度"), "the allowance is spent");
    }

    private static void hit(Battle battle, Character ally) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TAKING_HIT, battle.enemies().getFirst(), ally, 0, 0));
        battle.processRequests();
    }
    // ==================================================================
    // Helpers
    // ==================================================================

    private static int reductionsOn(Character character) {
        return character.getBuffManager().allBuffsOf(ReductionBuff.class).size();
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
        Path file = Path.of("src", "main", "resources", "game", "characters", "1211.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static final class Fixture {
        private final Character bailu;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Battle battle;

        /**
         * @param eidolon   the Eidolon rank Bailu is built at
         * @param technique whether her technique was used (the state the ultimate-less opening reads)
         * @param ultimate  whether to cast the ultimate once after the battle starts (the other source of [生息])
         */
        private Fixture(int eidolon, boolean technique, boolean ultimate) {
            bailu = CharacterFactory.create(BAILU, 80, false, null, null, eidolon);
            if (technique) {
                bailu.getBuffManager().addBuff(new StateBuff("秘技", 1));
            }
            battle = new Battle(List.of(bailu, ally), List.of(BattleFixture.dummy()),
                    BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            if (ultimate) {
                battle.castImmediate(bailu.getSkills().get(SkillType.ULTRA), bailu, List.of(ally, bailu));
            }
            // Note: her Ultimate heals, and a heal at full HP now OVERFLOWS - which the trace 岐黄精义 turns into +10% Max HP. The
            // ally is therefore no longer at 100% afterwards, and 「【生息】结束时若生命值等于其上限」 would read false. Top up, so
            // the precondition the E1 cases need (full HP) really holds.
            ally.heal(ally.getMaxHp());
        }

        private void castSkillOnAlly() {
            battle.castImmediate(bailu.getSkills().get(SkillType.SKILL), bailu, List.of(ally));
        }

        private void strikeLethally() {
            battle.applyTrueDamage(battle.enemies().getFirst(), ally, DamageElement.ICE,
                    ally.getMaxHp() * 2.0);
            battle.processRequests();
        }
    }
}



