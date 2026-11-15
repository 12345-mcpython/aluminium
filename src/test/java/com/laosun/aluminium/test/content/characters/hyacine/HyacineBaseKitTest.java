package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1409 风堇's base kit and the bookkeeping behind her heals: the ultimate's 【雨过天晴】 state and its Max-HP raise, the
 * ultimate's and the skill's heals, the technique, the memosprite's 50% inherit and its debuff immunity, the 累计治疗
 * tally, and the talent's per-layer damage boost.
 *
 * <p>The rules cover only what a skill's data does NOT do. Measured this round: the HEAL op is exact (`owner_max_hp` 10%
 * + 100 healed 219.52864 against a declared 219.52864000000002 when isolated on TURN_START), while a skill also heals
 * from its own data - so a rule that repeats the document's heal double-counts it. Her state and the party Max HP raise
 * are therefore the shipped clauses.
 *
 * <p>These were eight small files in the same package; one class keeps the character's base kit together without
 * changing any assertion.
 */
public class HyacineBaseKitTest {
    private static final int HYACINE = 1409;
    private static final int ALLY = 1001;
    /** 风堇's second ally in the 【雨过天晴】 reading: a different character id, so both allies are distinct units. */
    private static final int SECOND_ALLY = 1002;
    private static final int LEVEL = 80;
    /** 「#1」 of the skill's Lv10 row, and its 「#2」: 0.08 x Max HP + 160. */
    private static final double SHARE_AT_LV10 = 0.08;
    private static final double FLAT_AT_LV10 = 160;
    /** 1409's raw HEALTH base at Lv80 (the traces' +10% sits on top). */
    private static final double BASE_HP = 1086.624;
    private static final String TALLY = "internal:1409/ika_healing_total";

    /**
     * Note: "风堇进入[雨过天晴]状态" and "我方全体目标生命上限提高30.00%".
     */
    @Test
    public void herUltimateMarksHerAndRaisesThePartysMaxHp() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character ally = CharacterFactory.create(SECOND_ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(hyacine, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyMaxBefore = ally.getMaxHp();
        double herMaxBefore = hyacine.getMaxHp();

        battle.castImmediate(hyacine.getSkills().get(SkillType.ULTRA), hyacine, List.of(ally));

        Assertions.assertTrue(hyacine.getBuffManager().hasState("char:1409/after_the_rain"),
                "「风堇进入【char:1409/after_the_rain】状态」 (Hyacine enters the After Rain (【char:1409/after_the_rain】) state)");
        Assertions.assertTrue(ally.getMaxHp() > allyMaxBefore, "「我方全体目标生命上限提高30.00%」 (all allies' Max HP is raised by 30.00%) -- ally");
        Assertions.assertTrue(hyacine.getMaxHp() > herMaxBefore, "「我方全体」 (all allies) includes Hyacine herself");
    }

    /**
     * 「我方全体目标生命上限提高 30% + 600 点」 for 3 turns, and 「为我方全体目标回复等同于风堇 10% 生命上限 + 200 的生命值」
     * (141303): one ultimate cast, both facts read off the same ally.
     *
     * <p>Why the flat 600 is a rule and not data: the percentage half was modelled and the flat 600 was registered as
     * missing, because {@code MODIFY_ATTR} was believed to accept a flat {@code amount} only alongside a {@code scale}.
     * It does not - a bare {@code amount} is a flat point value (1503's trace rows raise SPEED by 9 that way) - so the
     * flat half is a second effect on the same rule, and this measures the whole sentence: {@code base x 1.3 + 600}.
     *
     * <p>Why the heal is a rule and not data: her Skill is {@code Restore}, so its own data heals (measured: 8% + 160),
     * while her Ultimate is {@code Support} - nothing in the data heals, measured as a 0 heal before this rule existed.
     * The memosprite half needs {@code self_summon_count >= 1}, which is why the ultimate also summons in an earlier
     * rule. Her Max HP is read BEFORE the cast, because the same cast raises it by 30% + 600 for 【雨过天晴】 and the heal
     * is computed from the panel the cast started with (measured: 319.52864 = 1195.2864 x 0.1 + 200).
     */
    @Test
    public void theUltimateRaisesThePartysMaxHpAndHealsForItsShare() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        double allyBase = ally.getAttribute(AttributeType.HEALTH).baseValue();
        double before = ally.getAttribute(AttributeType.HEALTH).get();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.6);
        double wounded = ally.getCurrentHp();
        double herMaxHpBefore = hyacine.getAttribute(AttributeType.HEALTH).get();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.ULTRA), hyacine,
                List.of(battle.enemies().get(0)));
        battle.processRequests();
        double after = ally.getAttribute(AttributeType.HEALTH).get();
        double healed = ally.getCurrentHp() - wounded;
        double expected = herMaxHpBefore * 0.1 + 200.0;

        Assertions.assertAll("ultimate: Max HP raise and heal",
                () -> Assertions.assertTrue(allyBase > 0, "precondition: the ally has a base MaxHP"),
                () -> Assertions.assertEquals(allyBase * 1.3 + 600.0, after, Math.max(1e-6, allyBase * 1e-9),
                        "「生命上限提高 30.00% + 600 点」 (before=" + before + ", after=" + after + ")"),
                () -> Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                        "「等同于风堇 10% 生命上限 + 200」 (healed=" + healed + ", expected=" + expected + ")"));
    }

    /**
     * The skill heals 「我方<b>全体</b>目标」 - the scope, not just the amount.
     *
     * <p>The heal used to come from the skill data (`skill_effects.json` 1409/2 = `Restore`), and a `Restore` heals the
     * targets the CALLER passed in - so in practice one ally was healed no matter what the sentence said. The scope is
     * now stated by a rule (`target: all_allies`), while the numbers still come from the same row.
     */
    @Test
    public void everyWoundedAllyIsHealedNotJustTheChosenOne() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character first = CharacterFactory.create(1001, 80);
        Character second = CharacterFactory.create(1003, 80);
        Battle battle = new Battle(List.of(hyacine, first, second), List.of(BattleFixture.dummy()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");

        // Every ally wounded, so a heal has room to show on each of them.
        for (Character ally : List.of(first, second)) {
            ally.consumeHp(ally.getMaxHp() * 0.5);
        }
        ika.consumeHp(ika.getMaxHp() * 0.9);
        double herMaxHp = hyacine.getMaxHp();
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();

        // The caller aims at ONE ally; the sentence heals the whole side.
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(first));
        battle.processRequests();

        double expected = SHARE_AT_LV10 * herMaxHp + FLAT_AT_LV10;
        Assertions.assertEquals(expected, first.getCurrentHp() - firstBefore, expected * 1e-9,
                "the ally the caller aimed at");
        Assertions.assertEquals(expected, second.getCurrentHp() - secondBefore, expected * 1e-9,
                "「我方全体目标」 means the ones the caller did NOT aim at are healed too");
    }

    /**
     * 小伊卡's own two facts on arrival: talent (140904) 「忆灵小伊卡初始拥有等同于风堇 <b>50%</b> 生命上限的生命上限」 - and
     * {@code AvatarServantConfig} states {@code HPInherit "#1"} with param #1 = 0.5, so the two sources agree - and
     * 「免疫负面效果」, where percent 1.0 IS immunity in this engine and has to hold for both debuff classes.
     */
    @Test
    public void ikachiInheritsHalfHerMaxHpAndIsImmuneToBothDebuffClasses() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Battle battle = new Battle(List.of(hyacine, CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
        double hers = hyacine.getAttribute(AttributeType.HEALTH).get();

        Assertions.assertAll("小伊卡: inherited Max HP and debuff immunity",
                () -> Assertions.assertEquals(hers * 0.5, ika.getMaxHp(), Math.max(1e-6, hers * 1e-9),
                        "「等同于风堇 50% 生命上限的生命上限」 (hers=" + hers + ", its=" + ika.getMaxHp() + ")"),
                () -> Assertions.assertEquals(1.0, ika.getBuffManager().debuffResistOf(DebuffClass.CONTROL), 1e-9,
                        "「免疫负面效果」 -- control class (percent 1.0 IS immunity in this engine)"),
                () -> Assertions.assertEquals(1.0, ika.getBuffManager().debuffResistOf(DebuffClass.DOT), 1e-9,
                        "「免疫负面效果」 -- damage-over-time class"));
    }

    /**
     * Skill (140902): 「为我方全体目标回复等同于风堇 <b>8%</b> 生命上限 + <b>160</b> 的生命值」 (params [0.08, 160,
     * 0.1, 200]).
     *
     * <p>Why the heal is worth measuring: the amount is a share of the <b>summoner's</b> Max HP, not of the healed
     * ally's - reading the wrong side gives a plausible-looking number of the wrong size, and this repository has been
     * bitten by that shape before (a memosprite's damage base read off the summoner doubled its hit).
     */
    @Test
    public void theSkillHealsEightPercentOfHerMaxHpPlus160() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        double before = ally.getCurrentHp();
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double healed = ally.getCurrentHp() - before;
        double expected = hyacine.getAttribute(AttributeType.HEALTH).get() * 0.08 + 160.0;
        Assertions.assertEquals(expected, healed, Math.max(1e-6, expected * 1e-9),
                "「等同于风堇 8% 生命上限 + 160」 (healed=" + healed + ", expected=" + expected + ")");
    }

    /**
     * Technique 【天气正好，万物可爱！】(140907): 「下一次战斗开始时，为我方全体回复等同于风堇 30% 生命上限 + 600
     * 的生命值并使生命上限提高 20%，持续 2 回合」.
     *
     * <p>The technique happens before the battle, so the engine is TOLD about it ({@code Battle.markTechniqueUsed} sets
     * the {@code 秘技} state); the rule then hangs on {@code BATTLE_START} behind {@code self has_state sys:technique}.
     */
    @Test
    public void theTechniqueRaisesThePartysMaxHpAndHealsTheWounded() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.markTechniqueUsed(hyacine);
        double maxBefore = hyacine.getAttribute(AttributeType.HEALTH).get();
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        double wounded = ally.getCurrentHp();

        battle.startBattle();
        battle.processRequests();

        Assertions.assertEquals(maxBefore + BASE_HP * 0.20, hyacine.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "「生命上限提高 20%」，持续 2 回合");
        Assertions.assertTrue(ally.getCurrentHp() > wounded,
                "「为我方全体回复等同于风堇 30% 生命上限 + 600 的生命值」（受伤前 " + wounded + "，之后 " + ally.getCurrentHp() + "）");
    }

    /** The same entry point without the 秘技 state: the rule must not fire, so the panel is untouched. */
    @Test
    public void withoutTheTechniqueNothingIsAdded() {
        Character hyacine = CharacterFactory.create(HYACINE, 80, true);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        double maxBefore = hyacine.getAttribute(AttributeType.HEALTH).get();
        battle.startBattle();
        battle.processRequests();
        Assertions.assertEquals(maxBefore, hyacine.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "no technique used: no 秘技 state, so the rule does not fire");
    }

    /**
     * The counting half of 忆灵技1 【乌云乌云快走开！】: 「对敌方全体造成等同于<b>本场战斗风堇与小伊卡累计治疗数值</b>
     * #1% 的风属性伤害并清空累计治疗数值的 50%」 - the tally keeps what was ACTUALLY restored, it is a running total,
     * and it belongs to 风堇 and 小伊卡 alone.
     *
     * <p>This is only the <b>counting</b> half of that sentence, and it is worth its own scenario because the resource
     * existed before it was ever the total: 昔涟 (1415)'s 【献予「天空」之诗】 writes the <em>extra</em> share into the
     * same counter, so a reader that mistook that for the tally would read a bonus, not a sum.
     *
     * <p>为什么量的是「实际回复量」: the engine does not fire {@code HEALED} for a heal that heals nothing (already at
     * full HP), so the tally follows what was really restored -- which is the game's own wording (「累计治疗数值」), not
     * the nominal amount the skill would have healed. A Resource is an INTEGER counter, so the tally keeps whole HP: the
     * error is below one point of healing, i.e. below 0.28 of the damage the tally will be multiplied by (Lv10's #1).
     */
    @Test
    public void theTallyCountsOnlyHerHealingAndAccumulates() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = woundedAlly(80);
        Battle battle = battle(hyacine, ally);
        double wounded = ally.getCurrentHp();

        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double healed = ally.getCurrentHp() - wounded;
        double afterFirst = hyacine.getResources().value(TALLY);

        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
        battle.processRequests();
        double afterSecond = hyacine.getResources().value(TALLY);

        // 治疗者是**队友自己**，不是风堇、也不是她的忆灵 ⇒ 进的是别人的账。
        Character otherHyacine = CharacterFactory.create(HYACINE, 80);
        Character otherAlly = woundedAlly(80);
        Battle otherBattle = battle(otherHyacine, otherAlly);
        double otherWounded = otherAlly.getCurrentHp();
        otherBattle.heal(otherAlly, otherAlly, otherAlly.getMaxHp() * 0.5);
        otherBattle.processRequests();
        double otherTally = otherHyacine.getResources().value(TALLY);

        Assertions.assertAll("累计治疗 tally",
                () -> Assertions.assertTrue(healed > 0, "precondition: the skill healed something (否则 HEALED 不发)"),
                () -> Assertions.assertEquals(Math.round(healed), afterFirst, 0.0,
                        "「本场战斗风堇…累计治疗数值」 is the amount actually restored, kept as whole HP (healed=" + healed
                                + ", tally=" + afterFirst + ")"),
                () -> Assertions.assertTrue(afterFirst > 0, "precondition: the first heal was tallied"),
                () -> Assertions.assertTrue(afterSecond > afterFirst, "「累计」: the counter accumulates across heals ("
                        + afterFirst + " -> " + afterSecond + ")"),
                () -> Assertions.assertTrue(otherAlly.getCurrentHp() > otherWounded,
                        "precondition: the heal really landed"),
                () -> Assertions.assertEquals(0.0, otherTally, 1e-9,
                        "「风堇与小伊卡累计治疗」 -- nobody else's healing counts"));
    }

    /**
     * Talent: 「风堇或小伊卡提供治疗时，小伊卡造成的伤害提高 80%，持续 2 回合，最多可叠加 3 层」 (140904, params
     * [0.5, 1, 0.8, 2, 3]).
     *
     * <h2>The two traps this test documents</h2>
     * <ol>
     *   <li>{@code CanHit.heal(-x)} does <b>nothing</b> (it ignores a non-positive amount), so "wound the ally first"
     *       needs {@code consumeHp} - a first attempt healed a full-HP ally, produced no {@code HEALED}, and read 0
     *       boost;</li>
     *   <li>at full HP a heal restores 0 and raises no event, so the ally has to be genuinely wounded before each
     *       cast.</li>
     * </ol>
     */
    @Test
    public void eachHealRaisesTheMemospriteDamageByEightyUpToThreeLayers() {
        double none = ikachiBoost(0);
        double one = ikachiBoost(1);
        double two = ikachiBoost(2);
        double three = ikachiBoost(3);
        double four = ikachiBoost(4);
        Assertions.assertEquals(0.8, one - none, 1e-6, "one heal: +80%");
        Assertions.assertEquals(1.6, two - none, 1e-6, "two heals: +160%");
        Assertions.assertEquals(2.4, three - none, 1e-6, "three heals: +240%");
        Assertions.assertEquals(2.4, four - none, 1e-6, "「最多可叠加 3 层」: the fourth heal adds nothing");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    private static Character woundedAlly(int level) {
        return CharacterFactory.create(ALLY, level);
    }

    private static Battle battle(Character hyacine, Character ally) {
        ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.dummy()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static double ikachiBoost(int heals) {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(hyacine, ally), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        battle.summonMemosprite(hyacine);
        battle.processRequests();
        Summon ika = (Summon) battle.memospriteOf(hyacine);
        Assertions.assertNotNull(ika, "precondition: 小伊卡 is on the field");
        for (int i = 0; i < heals; i++) {
            ally.consumeHp(ally.getAttribute(AttributeType.HEALTH).get() * 0.5);
            SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine, List.of(ally));
            battle.processRequests();
        }
        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, ika.skillAt(1), ika, List.of(battle.enemies().get(0)));
        battle.processRequests();
        Assertions.assertFalse(settled.isEmpty(), "precondition: 小伊卡's hit settled");
        // ⚠ 2026-10-18: 忆灵技1 的「按累计治疗出伤害」现在是 CAST_SETUP 上的一条**规则**，它在数据那一发**之前**结算
        // ⇒ 第一个实例是那条规则的（它的攻击者是**风堇**，身上没有忆灵的天赋增伤），数据那一发排在它后面。这里读
        // **最后一个**实例 = 数据那一发，也就是这条用例原本要测的对象。规则实例的归属问题登记在 AGENT.md §3.7q。
        return settled.get(settled.size() - 1).breakdown().get("BoostArea");
    }
}
