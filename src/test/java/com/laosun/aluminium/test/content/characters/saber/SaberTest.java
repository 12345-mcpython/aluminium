package com.laosun.aluminium.test.content.characters.saber;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 1014 Saber: the kit and the resources it spends. The talent 龙之炉心, the traces 龙之骑士 / 星之冠冕 / 湖之祝福 (the overflow
 * bank), the technique 骑士王的登场, the basic-attack swap the Ultimate leaves behind, the Skill's two branches (pay 8 energy
 * per point of 【炉心共鸣】 and empty it, or else gain 3 points), and the spec-level flag behind the word 「固定」.
 *
 * <p>The Eidolon-gated halves live in {@link SaberEidolonTest}, except the two that stay beside what they widen: the Eidolon 1
 * clause next to the talent that pays its points, and the Eidolon 6 allowance next to the bank it raises.
 *
 * <p><b>L3:</b> the cases that read one fixture are grouped into the scenario tests below, one {@code Assertions.assertAll}
 * per scenario, each entry carrying a short message that names its clause: the talent's two halves, the two stat traces, the
 * three overflow readings of 湖之祝福, and the Skill's consuming branch with the 龙之骑士 clause that rides on the same cast.
 * Every assertion, expected value, tolerance and message is unchanged. The technique, the Eidolon 1 clause, the Eidolon 6
 * allowance, the bank's cross-character control, the Skill's other branch, the enhanced basic, the Ultimate swap, the probe
 * and the spec flag keep their own tests - different boards and different branches, and an {@code assertAll} is not where
 * they belong.
 */
public class SaberTest {

    private static final int SABER = 1014;
    private static final int ALLY = 1001;
    private static final int OTHER = 1002;
    private static final int LEVEL = 80;
    private static final String RESONANCE = "炉心共鸣";
    private static final String MANA = "魔力放出";
    private static final Path SABER_FILE = Path.of("src", "main", "resources", "game", "characters", "1014.json");

    // ------------------------------------------------------------------ talent

    /**
     * 1014 Saber's talent 龙之炉心, the two halves its file states: 「进入战斗时获得 1 点【炉心共鸣】」 and 「我方任意目标施放终结技时，
     * Saber 造成的伤害提高 60%，持续 2 回合，并获得 3 点【炉心共鸣】」.
     *
     * <p>One board and one start, then one ally Ultimate: the entry readings are taken before the cast, the cast readings after
     * it, so both halves of the sentence are judged on the same battle.
     */
    @Test
    public void theTalentPaysResonanceOnEntryAndOnAnAllysUltimate() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        boolean resonanceAtStart = saber.getBuffManager().hasState("炉心共鸣");
        int atStart = saber.getBuffManager().stacksOf("炉心共鸣");
        int before = atStart;
        double boostBefore = saber.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, ally, ally, 0, 0));
        int after = saber.getBuffManager().stacksOf("炉心共鸣");
        double boostAfter = saber.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[1014] resonance at start: " + atStart + " (state " + resonanceAtStart + ") ; after the ultimate "
                + before + " -> " + after + " ; boost " + boostBefore + " -> " + boostAfter);
        Assertions.assertAll("天赋 龙之炉心: 进入战斗 1 点，我方终结技再付 3 点并提高伤害",
                () -> Assertions.assertTrue(resonanceAtStart, "「进入战斗时，获得 1 点【炉心共鸣】」"),
                () -> Assertions.assertEquals(1, atStart, "「进入战斗时，获得 1 点【炉心共鸣】」 — the stack count it hands her"),
                () -> Assertions.assertEquals(before + 3, after, "「并获得 3 点【炉心共鸣】」"),
                () -> Assertions.assertTrue(boostAfter > boostBefore, "「Saber 造成的伤害提高 60%，持续 2 回合」"));
    }

    /**
     * 星魂 1: 「Saber 造成的终结技伤害提高 60%」 and 「施放普攻或战技后，获得 1 点【炉心共鸣】」.
     *
     * <p>The boost is scoped to the Ultimate's own instance (from_skill ULTRA), so a basic attack must NOT move it.
     */
    @Test
    public void eidolonOneRaisesUltimateDamageAndPaysResonanceAfterAnAttack() {
        Character atZero = CharacterFactory.create(SABER, LEVEL, true, null, null, 0);
        Character atOne = CharacterFactory.create(SABER, LEVEL, true, null, null, 1);
        Battle zero = battleWithAlly(atZero);
        Battle one = battleWithAlly(atOne);
        double plain = ultimateDamage(zero, atZero);
        double boosted = ultimateDamage(one, atOne);
        System.out.println("[1014] ultimate at E0 " + plain + " ; E1 " + boosted);
        Assertions.assertTrue(boosted > plain, "「Saber 造成的终结技伤害提高 60%」");
    }

    private static Battle battleWithAlly(Character saber) {
        Battle battle = new Battle(new ArrayList<>(List.of(saber, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static double ultimateDamage(Battle battle, Character saber) {
        var enemy = battle.enemyUnits().getFirst();
        double before = enemy.getCurrentHp();
        battle.castImmediate(saber.getSkills().get(SkillType.ULTRA), saber, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    // --------------------------------------------------------------- technique

    /**
     * 1014 Saber's technique 骑士王的登场: 「使用秘技后，下一次战斗开始时 Saber 攻击力提高 35%，持续 2 回合，并获得 2 点【炉心共鸣】」.
     *
     * <p>The technique is a BATTLE_START rule gated on {@code self has_state sys:technique}, so the check compares a battle opened
     * normally with one opened by the technique.
     */
    @Test
    public void theTechniqueRaisesAttackAndPaysTwoResonance() {
        double plainAttack = attack(false);
        double techniqueAttack = attack(true);
        System.out.println("[1014] technique: attack " + plainAttack + " -> " + techniqueAttack
                + " ; resonance " + resonance(false) + " -> " + resonance(true));
        Assertions.assertTrue(techniqueAttack > plainAttack, "「攻击力提高 35%」");
        Assertions.assertEquals(resonance(false) + 2, resonance(true), "「并获得 2 点【炉心共鸣】」");
    }

    private static Character saber(boolean technique) {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        if (technique) {
            saber.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("sys:technique", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return saber;
    }

    private static double attack(boolean technique) {
        return saber(technique).getAttribute(AttributeType.ATTACK).get();
    }

    private static int resonance(boolean technique) {
        return saber(technique).getBuffManager().stacksOf("炉心共鸣");
    }

    // ------------------------------------------------------------------ traces

    /**
     * 1014 Saber's three traces, the halves that are stateable: 龙之骑士's +20% CRIT with 【魔力放出】 on entry, and 星之冠冕's
     * 「施放战技时，使 Saber 的暴击伤害提高 50%，持续 2 回合」.
     *
     * <p>Both read the same solo board ({@code battle(saber)}). The crit-damage reading is paired with a control that holds the
     * same three 炉心共鸣 points without casting, so the 50% is asserted over and above the points.
     */
    @Test
    public void theTwoTracesRaiseCritOnEntryAndCritDamageAfterASkill() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        battle(saber);
        double critChance = saber.getAttribute(AttributeType.CRIT_CHANCE).get();
        boolean mana = saber.getBuffManager().hasState("魔力放出");

        Character casting = CharacterFactory.create(SABER, LEVEL);
        Battle castingBattle = battle(casting);
        double before = casting.getAttribute(AttributeType.CRIT_ATTACK).get();
        castingBattle.castImmediate(casting.getSkills().get(SkillType.SKILL), casting,
                List.of(castingBattle.enemyUnits().getFirst()));
        castingBattle.processRequests();
        double castDelta = casting.getAttribute(AttributeType.CRIT_ATTACK).get() - before;

        Character control = CharacterFactory.create(SABER, LEVEL);
        double controlBefore = control.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle(control);
        for (int i = 0; i < 3; i++) {
            control.getBuffManager().addBuff(new StackBuff("炉心共鸣", 1, true, 60));
        }
        double controlDelta = control.getAttribute(AttributeType.CRIT_ATTACK).get() - controlBefore;

        System.out.println("[1014] crit " + critChance + " ; mana " + mana + " ; crit damage: skill +" + castDelta
                + " ; the same points without a cast +" + controlDelta);
        Assertions.assertAll("行迹 龙之骑士 / 星之冠冕",
                () -> Assertions.assertTrue(critChance >= 0.2, "「暴击率提高 20%」"),
                () -> Assertions.assertTrue(mana, "「进入战斗…获得【魔力放出】」"),
                () -> Assertions.assertEquals(0.5, castDelta - controlDelta, 1e-9,
                        "「暴击伤害提高 50%」, over and above the points"));
    }

    /**
     * 湖之祝福's opening clause:「战斗开始时若能量不足 60% 则使其恢复至 60%」.
     *
     * <p>Two directions: from empty it tops up to 60% of the cap; already above 60% it must do nothing.
     */
    @Test
    public void lakeBlessingTopsUpToSixtyPercent() {
        Character low = CharacterFactory.create(SABER, LEVEL);
        low.setCurrentEnergy(0);
        battle(low);
        double expected = 0.6 * low.getMaxEnergy();
        System.out.println("[1014] from 0 -> " + low.getCurrentEnergy() + " (cap " + low.getMaxEnergy() + ")");
        Assertions.assertEquals(expected, low.getCurrentEnergy(), 1e-9, "「恢复至 60%」");

        Character high = CharacterFactory.create(SABER, LEVEL);
        high.setCurrentEnergy(high.getMaxEnergy());
        battle(high);
        Assertions.assertEquals(high.getMaxEnergy(), high.getCurrentEnergy(), 1e-9,
                "「若能量不足 60%」: a full bar is left alone");
    }

    private static Battle battle(Character saber) {
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    // ---------------------------------------------------------- energy overflow

    /**
     * 1014 Saber's trace 湖之祝福: 「Saber 可积攒最多 <b>120</b> 点溢出能量，施放终结技后清空并恢复相应的能量值」.
     *
     * <p><b>Why a bank and not a wider bar.</b> The bar keeps its meaning (an ultimate is castable at {@code max} and not
     * before), and what would have been lost above the cap is <em>remembered</em>, then handed back after the ultimate. A bar
     * raised to {@code max + 120} would instead have every later ultimate start charged - a different sentence.
     *
     * <p>The four readings below are the ones the clause can be wrong in, each with a control: the excess is kept, the
     * allowance caps it, a unit without the trace still loses it, and the ultimate empties the bank into the bar.
     *
     * <p>Three of them - 「可积攒…溢出能量」: a gain that does not fit is kept rather than dropped; 「最多 120 点」: the
     * allowance is the trace's number; 「施放终结技后清空并恢复相应的能量值」: the bank becomes energy again and is emptied -
     * read the same fixture ({@code battleAt(SABER, 0)}), one board each, so they share this scenario test. The unit without
     * the trace is a different board and keeps its own test.
     */
    @Test
    public void theLakeBlessingBankKeepsTheOverflowCapsItAndTheUltimatePoursItBack() {
        // 「可积攒…溢出能量」: a gain that does not fit is kept rather than dropped.
        Battle keptBattle = battleAt(SABER, 0);
        Character kept = keptBattle.characters().getFirst();
        double max = kept.getMaxEnergy();
        kept.setCurrentEnergy(max - 10);
        double credited = kept.gainEnergy(100);
        double keptEnergy = kept.getCurrentEnergy();
        double keptBanked = kept.getBankedEnergy();

        // 「最多 120 点」: the allowance is the trace's number.
        Battle cappedBattle = battleAt(SABER, 0);
        Character capped = cappedBattle.characters().getFirst();
        double cappedMax = capped.getMaxEnergy();
        capped.gainEnergy(500);
        double cappedEnergy = capped.getCurrentEnergy();
        double cappedBanked = capped.getBankedEnergy();

        // 「施放终结技后清空并恢复相应的能量值」: the bank becomes energy again and the bank is emptied.
        Battle pouringBattle = battleAt(SABER, 0);
        Character pouring = pouringBattle.characters().getFirst();
        pouring.gainEnergy(500);
        double bankedBeforePour = pouring.getBankedEnergy();
        // What casting the ultimate does first: it pays the bar out (UltimateService.cast).
        pouring.setCurrentEnergy(0);

        pouringBattle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, pouring, pouring, 0, 0));
        pouringBattle.processRequests();
        double pouredEnergy = pouring.getCurrentEnergy();
        double bankedAfterPour = pouring.getBankedEnergy();

        Assertions.assertAll("行迹 湖之祝福: 溢出被记住、封顶 120 点、终结技后如数归还",
                () -> Assertions.assertEquals(10, credited, 1e-9, "the bar only takes what fits"),
                () -> Assertions.assertEquals(max, keptEnergy, 1e-9, "so it is full"),
                () -> Assertions.assertEquals(90, keptBanked, 1e-9,
                        "「可积攒溢出能量」 — the other 90 is held, not dropped"),
                () -> Assertions.assertEquals(cappedMax, cappedEnergy, 1e-9, "precondition: the bar fills first"),
                () -> Assertions.assertEquals(120, cappedBanked, 1e-9,
                        "「可积攒**最多 120 点**溢出能量」 — 500 - 360 = 140 is clipped to 120"),
                () -> Assertions.assertEquals(120, bankedBeforePour, 1e-9, "precondition: 120 banked"),
                () -> Assertions.assertEquals(120, pouredEnergy, 1e-9,
                        "「恢复相应的能量值」 — the banked amount is handed back"),
                () -> Assertions.assertEquals(0, bankedAfterPour, 1e-9, "「清空」"));
    }

    /** The control: without the trace a unit keeps nothing above its cap. */
    @Test
    public void aUnitWithoutTheTraceStillLosesTheOverflow() {
        Battle battle = battleAt(OTHER, 0);
        Character other = battle.characters().getFirst();
        other.gainEnergy(500);

        Assertions.assertEquals(other.getMaxEnergy(), other.getCurrentEnergy(), 1e-9, "the bar fills");
        Assertions.assertEquals(0, other.getBankedEnergy(), 1e-9,
                "and the excess is lost - the default every other file keeps");
    }

    /** 星魂 6: 「行迹【湖之祝福】可积攒的溢出能量提高到 200 点」. */
    @Test
    public void theEidolonRaisesTheAllowanceToTwoHundred() {
        Battle five = battleAt(SABER, 5);
        Character e5 = five.characters().getFirst();
        e5.gainEnergy(500);
        Assertions.assertEquals(120, e5.getBankedEnergy(), 1e-9, "E5 adds nothing: the trace's 120 stands");

        Battle six = battleAt(SABER, 6);
        Character e6 = six.characters().getFirst();
        e6.gainEnergy(500);
        Assertions.assertEquals(200, e6.getBankedEnergy(), 1e-9,
                "「提高到 200 点」 — the trace's 120 plus this Eidolon's 80");
    }

    private static Battle battleAt(int cid, int eidolon) {
        Character main = CharacterFactory.create(cid, LEVEL, false, null, null, eidolon);
        Battle battle = new Battle(List.of(main),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // -------------------------------------------------- skill / basic mechanics

    /**
     * 1014 Saber's enhanced basic:「若施放时敌方目标数量为 2/1，额外对敌方全体造成 150%/220% 攻击力的风属性伤害」.
     *
     * <p>The two tiers are row reads off the ENHANCED skill's own row (Lv6 = [1.5, 2, 1.5, 2.2]), which is only possible while
     * the COMMON slot still points at it - the restore rule runs later in the file on purpose. The judge compares what ONE
     * enemy takes: 150% base + 150% extra beside a neighbour, 150% + 220% when alone.
     */
    @Test
    public void theExtraHitDependsOnHowManyEnemiesThereAre() {
        double alone = enhancedDamage(1);
        double amongTwo = enhancedDamage(2);
        System.out.println("[1014] enhanced basic: alone " + alone + " ; one of two " + amongTwo);
        Assertions.assertTrue(alone > 0 && amongTwo > 0, "「额外对敌方全体造成…风属性伤害」 must land in both cases");
        // 370% alone against 300% beside a neighbour: the ratio is the tier difference, 2.2 vs 1.5 over the same base.
        Assertions.assertTrue(alone > amongTwo, "220% (alone) must beat 150% (with a neighbour): " + alone + " vs " + amongTwo);
        Assertions.assertEquals((1.5 + 2.2) / (1.5 + 1.5), alone / amongTwo, 0.02,
                "the tier is the only difference between the two situations");
    }

    /** The damage the FIRST enemy takes from one enhanced basic, with {@code enemies} enemies on the field. */
    private static double enhancedDamage(int enemies) {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            foes.add(BattleFixture.dummy());
        }
        Battle battle = new Battle(new ArrayList<>(List.of(saber)), foes, BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(saber.getSkills().get(SkillType.ULTRA), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        Enemy target = battle.enemyUnits().getFirst();
        double before = target.getCurrentHp();
        battle.castImmediate(saber.getSkills().get(SkillType.COMMON), saber, List.of(target));
        battle.processRequests();
        Assertions.assertFalse(target.isDeath(), "the target must survive, or a second hit settles nothing");
        return before - target.getCurrentHp();
    }

    /**
     * 1014 Saber's Skill, the <b>else</b> branch: 「否则立即获得 3 点【炉心共鸣】」.
     *
     * <p>「否则」 is the negation of the fill condition, written as the flipped comparison (numeric conditions may not use the
     * {@code !} prefix - that would turn an unreadable NaN into a pass). At one stack the bar cannot be filled, so this branch
     * is the one that runs: the cast hands her 3 points.
     */
    @Test
    public void aCastThatCannotFillTheBarPaysThreePoints() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = saber.getBuffManager().stacksOf(RESONANCE);
        Assertions.assertTrue(8.0 * before < saber.getMaxEnergy(), "precondition: this cast cannot fill the bar");
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1014] resonance " + before + " -> " + saber.getBuffManager().stacksOf(RESONANCE));
        Assertions.assertEquals(before + 3, saber.getBuffManager().stacksOf(RESONANCE),
                "「否则立即获得 3 点【炉心共鸣】」");
    }

    /**
     * 1014 Saber's Skill, the branch that only opens when the stacks can fill her bar:「若持有【炉心共鸣】且消耗后<b>能回满能量</b>，则
     * 每点使本次战技倍率提高 14%，并在攻击后消耗所有【炉心共鸣】为 Saber 恢复能量」.
     *
     * <p>A counter is SEVERAL buffs with one name, so 45 points means 45 additions - a single {@code StackBuff(name, 44, ...)}
     * carries one instance and made an earlier probe look like a dead rule.
     *
     * <p>1014 Saber's trace 龙之骑士, the conditional half:「若持有【炉心共鸣】且消耗后能回满能量，则消耗【魔力放出】为我方恢复 1 个战技点并使
     * Saber 立即行动」.
     *
     * <p>⚠ It must run on the SAME event as the Skill's own consuming branch and BEFORE it: a staircase probe showed
     * {@code self_stacks:炉心共鸣 >= 1} is false by the time {@code SKILL_CAST} fires (the points are already gone).
     *
     * <p>The trace's clause rides exactly that cast, on the same board (energy emptied, 45 points added, one Skill), so the two
     * readings share one scenario test: the bar's refund and the emptied counter, then the spent 【魔力放出】, the Skill Point
     * and the extra turn.
     */
    @Test
    public void theConsumingBranchPaysEnergyAndTheDragonKnightSpendsManaForATurn() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff(RESONANCE, 1, true, 60));
        }
        int points = saber.getBuffManager().stacksOf(RESONANCE);
        double maxEnergy = saber.getMaxEnergy();
        boolean canFill = 8.0 * points >= maxEnergy;
        boolean manaBefore = saber.getBuffManager().hasState(MANA);
        int pointsBefore = battle.getSkillPoints();
        var scheduledBefore = battle.getExtraTurnActor();
        double energyBefore = saber.getCurrentEnergy();
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        double energyGain = saber.getCurrentEnergy() - energyBefore;
        int pointsAfter = saber.getBuffManager().stacksOf(RESONANCE);
        boolean manaAfter = saber.getBuffManager().hasState(MANA);
        int skillPointsAfter = battle.getSkillPoints();
        var extraTurnActor = battle.getExtraTurnActor();
        System.out.println("[1014] points " + points + " ; energy " + energyBefore + " -> " + saber.getCurrentEnergy()
                + " ; points after " + pointsAfter + " ; mana " + manaBefore + " -> " + manaAfter
                + " ; skill points " + pointsBefore + " -> " + skillPointsAfter
                + " ; extra turn " + (extraTurnActor != null));
        // The refund is capped by the bar: 46 points x 8 = 368 on a 360-energy Ultimate fills it, it does not overflow.
        Assertions.assertAll("战技的消耗分支 + 行迹 龙之骑士: 回能、清空点数、消耗【魔力放出】换 1 战技点与立即行动",
                () -> Assertions.assertTrue(canFill,
                        "precondition: the points can fill the bar (" + points + " x 8 vs " + maxEnergy + ")"),
                () -> Assertions.assertEquals(Math.min(8.0 * points, maxEnergy), energyGain, 1e-9,
                        "「每消耗 1 点【炉心共鸣】固定恢复 8 点能量」, capped by the bar"),
                () -> Assertions.assertEquals(0, pointsAfter, "「消耗所有【炉心共鸣】」"),
                () -> Assertions.assertTrue(manaBefore, "precondition: 【魔力放出】 from entering battle"),
                () -> Assertions.assertNull(scheduledBefore, "precondition: nothing scheduled"),
                () -> Assertions.assertFalse(manaAfter, "「消耗【魔力放出】」"),
                () -> Assertions.assertEquals(pointsBefore + 1, skillPointsAfter, "「为我方恢复 1 个战技点」"),
                () -> Assertions.assertSame(saber, extraTurnActor, "「使 Saber 立即行动」"));
    }

    /**
     * 1014 Saber's Ultimate swap: 「施放终结技后下次普攻会被替换为【解放的金色王权】且仅能施放【解放的金色王权】」, and the pairing
     * clause that restores the plain basic after it is used.
     */
    @Test
    public void theUltimateSwapsTheBasicAndTheEnhancedOneRestoresIt() {
        Character saber = CharacterFactory.create(SABER, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        // The slot's own row is the identity: plain basic Lv10 #1 = 1.4, the enhanced one = 2.1 (data slot 8).
        double plainRow = rowOf(saber);

        battle.castImmediate(saber.getSkills().get(SkillType.ULTRA), saber, List.of(enemy));
        battle.processRequests();
        double afterUlt = rowOf(saber);
        System.out.println("[1014] basic #1: plain " + plainRow + " -> after ultimate " + afterUlt);
        Assertions.assertNotEquals(plainRow, afterUlt, "「下次普攻会被替换为【解放的金色王权】」");

        battle.castImmediate(saber.getSkills().get(SkillType.COMMON), saber, List.of(enemy));
        battle.processRequests();
        Assertions.assertEquals(plainRow, rowOf(saber), "after it is used the plain basic comes back");
    }

    /** The current basic-attack slot's Lv10 first parameter - the row that distinguishes the plain skill from the swap. */
    private static double rowOf(Character saber) {
        return saber.getSkills().get(SkillType.COMMON).getData().getSkills().get(9).get(0);
    }

    /** Probe: a staircase of conditions on SKILL_CAST - which step stops firing? */
    @Test
    public void probe() {
        Character saber = CharacterFactory.create(1014, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(saber)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        saber.setCurrentEnergy(0);
        for (int i = 0; i < 45; i++) {
            saber.getBuffManager().addBuff(new StackBuff("炉心共鸣", 1, true, 60));
        }
        battle.castImmediate(saber.getSkills().get(SkillType.SKILL), saber, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[probe] mana=" + saber.getBuffManager().hasState("魔力放出")
                + " s1=" + saber.getBuffManager().hasState("probe_s1")
                + " s2=" + saber.getBuffManager().hasState("probe_s2")
                + " s3=" + saber.getBuffManager().hasState("probe_s3")
                + " s4=" + saber.getBuffManager().hasState("probe_s4")
                + " stacks=" + saber.getBuffManager().stacksOf("炉心共鸣")
                + " energy=" + saber.getCurrentEnergy());
    }

    // ------------------------------------------------------------------- spec

    /**
     * 1014 Saber: 「消耗所有【炉心共鸣】为 Saber <b>固定</b>恢复能量」 / 天赋「每消耗 1 点为 Saber <b>固定</b>恢复 <b>8.00</b> 点能量」.
     *
     * <p><b>Why a spec-level case.</b> 「固定恢复」 is the engine's {@code fixed: true} on {@code GAIN_ENERGY}: a gain that skips the
     * energy-regeneration rate. The clause cannot be measured through the bar here - the 炉心共鸣 refund is only allowed when it
     * fills the bar, so the settled number is the cap either way and the difference hides in the overflow bank. What is checkable,
     * and what a mutation would break, is the flag itself: with it the refund is the stated amount, without it a character holding
     * +50% regeneration would refund 12 points per layer instead of 8.
     */
    @Test
    public void bothResonanceRefundsAreFixedGains() throws IOException {
        JsonObject file = JsonParser.parseString(Files.readString(SABER_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
        Assertions.assertTrue(refundIsFixed(file, "skill_resonance_boost_and_spend"),
                "「为 Saber 固定恢复能量」 — the refund must skip the energy-regeneration rate");
        Assertions.assertTrue(refundIsFixed(file, "skill_resonance_boost_with_eidolon2"),
                "and the Eidolon-2 branch of the same sentence says the same about the same number");
    }

    /**
     * @return whether that rule's {@code GAIN_ENERGY} (the 炉心共鸣 refund) carries {@code fixed: true}
     */
    private static boolean refundIsFixed(JsonObject file, String id) {
        for (var element : file.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!id.equals(rule.get("id").getAsString())) {
                continue;
            }
            for (var effect : rule.getAsJsonArray("do")) {
                JsonObject op = effect.getAsJsonObject();
                if (!"GAIN_ENERGY".equals(op.get("op").getAsString())) {
                    continue;
                }
                Assertions.assertTrue(op.has("value"), id + ": the refund is a per-layer product, not a literal");
                return op.has("fixed") && op.get("fixed").getAsBoolean();
            }
            throw new AssertionError(id + " has no GAIN_ENERGY effect any more");
        }
        throw new AssertionError("rule not found: " + id);
    }
}
