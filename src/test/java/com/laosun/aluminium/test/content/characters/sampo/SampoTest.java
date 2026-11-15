package com.laosun.aluminium.test.content.characters.sampo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1108 桑博 (Sampo), from the character's own file: Eidolons 1-6, the 秘技 and the three 行迹.
 *
 * <p><b>Eidolon 1.</b> 「施放战技时，对敌方随机单体额外造成 1 次伤害」. The skill already deals FOUR extra instances (the
 * engine reads that count from the sentence), so the Eidolon is one more instance on the same slot - measured as the number
 * of settled instances, not as a ratio, because the main hit is part of the same count.
 *
 * <p><b>Eidolon 2.</b> 「风化状态下的敌方目标被消灭时，使敌方全体有 100% 的基础概率叠加 1 层等同于天赋的
 * 风化状态」. The dead unit still carries its buffs at {@code KILL}, the windshear lands on every LIVING opponent,
 * and a clean corpse pays nothing.
 *
 * <p><b>Eidolons 3 and 5.</b> The two level clauses the data spells as {@code skill_add_level}.
 *
 * <p><b>Eidolon 4.</b> 「战技击中风化状态大于等于 5 层的敌方目标时，使其当前承受的风化状态立即产生
 * 相当于原伤害 8% 的伤害」. The threshold reads the DOT's own layers ({@code target_stacks:dot:wind_shear}). The expected
 * extra is derived from a measured full tick rather than from the DOT's raw base: the settled tick passes the target's DEF
 * and resistance like any damage.
 *
 * <p><b>Eidolon 6.</b> The talent's windshear hits 15% harder - a FACTOR (0.52 x 1.15), not +0.15 points (which would be
 * +28.8%).
 *
 * <p><b>秘技.</b> 「进入战斗时有 100% 固定概率使敌方每个单体目标行动延后 25%」. The judge reads the action bar's remaining
 * wait, which is what 行动延后 shortens/extends - not a buff count.
 *
 * <p><b>The three 行迹.</b> 圈套 (longer windshear), 后手 (+10 energy on the Ultimate) and 加料 (windsheared attackers hit
 * him for 15% less).
 *
 * <p><b>L3.</b> The single-clause cases are folded into the scenario tests below, one {@code Assertions.assertAll} per
 * fixture, so a failing clause still names itself; Eidolon 6's ratio keeps a test of its own, being one reading of one
 * fixture. Every expected value, tolerance and message is unchanged, and the two gates of Eidolon 2 are read beside the
 * positive case they guard instead of as a rejection test.
 */
public class SampoTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;
    private static final double TICK = 100.0;

    // ==================================================================
    // Eidolon 1
    // ==================================================================

    @Test
    public void theEidolonAddsOneInstanceToTheSkill() {
        double e0Hits = instances(0);
        double e1Hits = instances(1);
        System.out.println("[1108] e0=" + e0Hits + " e1=" + e1Hits);
        Assertions.assertAll("星魂 1: the Skill's own four extra instances plus the Eidolon's one",
                () -> Assertions.assertEquals(5.0, e0Hits, 1e-9,
                        "the Skill itself: one hit on the target + 「额外造成 4 次伤害」"),
                () -> Assertions.assertEquals(e0Hits + 1, e1Hits, 1e-9,
                        "「额外造成 1 次」 is one more instance on the same slot, not a new attack"));
    }

    /**
     * @return how many separate damage instances the skill settles
     */
    private static double instances(int eidolon) {
        Character sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        double[] count = new double[1];
        battle.addDamageObserver((damage, applied, hpLoss, shield, died) -> {
            if (damage.getDefender() == enemy) {
                count[0] += 1;
            }
        });
        battle.castImmediate(sampo.getSkills().get(SkillType.SKILL), sampo, List.of(enemy));
        return count[0];
    }

    // ==================================================================
    // Eidolon 2
    // ==================================================================

    /**
     * Eidolon 2, one {@code EidolonTwoFixture} board per reading: the windshear lands on every LIVING opponent, and the
     * two gates it needs are what makes that happen - without the rank, or without the windshear the clause names, a
     * corpse spreads nothing. All three readings are stated in the one {@code assertAll}.
     */
    @Test
    public void aWindshearedDeathSpreadsOneLayerOnlyUnderTheEidolon() {
        EidolonTwoFixture spread = new EidolonTwoFixture(2);
        spread.windtorn(spread.corpse, 1);
        int otherLayers = spread.stacks(spread.other);
        spread.kill(spread.corpse);
        int otherAfter = spread.stacks(spread.other);

        EidolonTwoFixture withoutEidolon = new EidolonTwoFixture(0);
        withoutEidolon.windtorn(withoutEidolon.corpse, 1);
        int before = withoutEidolon.stacks(withoutEidolon.other);
        withoutEidolon.kill(withoutEidolon.corpse);
        int afterE0 = withoutEidolon.stacks(withoutEidolon.other);

        EidolonTwoFixture noWindshear = new EidolonTwoFixture(2);
        int otherBefore = noWindshear.stacks(noWindshear.other);
        noWindshear.kill(noWindshear.corpse);
        int otherClean = noWindshear.stacks(noWindshear.other);

        System.out.println("[1108] corpse dead=" + spread.corpse.isDeath() + " other=" + otherAfter
                + " ; e0=" + afterE0 + " ; clean=" + otherClean);
        Assertions.assertAll("星魂 2: 「风化状态下的敌方目标被消灭时…叠加 1 层」",
                () -> Assertions.assertEquals(otherLayers + 1, otherAfter,
                        "「使敌方全体…叠加 1 层」 - the living enemy gains one layer"),
                () -> Assertions.assertEquals(before, afterE0, "E0: no spread"),
                () -> Assertions.assertEquals(otherBefore, otherClean,
                        "「风化状态下的」 - a clean corpse spreads nothing"));
    }

    private static final class EidolonTwoFixture {
        private final Character sampo;
        private final Enemy corpse = BattleFixture.monster();
        private final Enemy other = BattleFixture.monster();
        private final Battle battle;

        private EidolonTwoFixture(int eidolon) {
            sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(corpse, other)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }

        private void windtorn(Enemy enemy, int layers) {
            for (int i = 0; i < layers; i++) {
                enemy.getBuffManager().addBuff(new DotBuff(sampo, DamageElement.WIND, 100, 3, 5, "dot:wind_shear"));
            }
        }

        private int stacks(Enemy enemy) {
            return enemy.getBuffManager().stacksOf("dot:wind_shear");
        }

        private void kill(Enemy enemy) {
            // Killed by the OTHER ally: if Sampo dealt the fatal hit, his talent would land windshear first and the
            // "clean corpse" case could never exist.
            battle.applyTrueDamage(battle.characters().get(1), enemy, DamageElement.PHYSICAL,
                    enemy.getCurrentHp() * 10);
            battle.processRequests();
        }
    }

    // ==================================================================
    // Eidolons 3 and 5
    // ==================================================================

    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertAll("星魂 3 / 星魂 5: the two skill_add_level clauses",
                () -> Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(3, SkillType.SKILL), "E3: 战技 +2"),
                () -> Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(3, SkillType.COMMON), "E3: 普攻 +1"),
                () -> Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(5, SkillType.ULTRA), "E5: 终结技 +2"),
                () -> Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(5, SkillType.TALENT), "E5: 天赋 +2"));
    }

    /**
     * Eidolon 6: the talent's windshear hits 15% harder - a FACTOR (0.52 x 1.15), not +0.15 points (which would be +28.8%).
     */
    @Test
    public void eidolonSixScalesTheTalentWindtorn() {
        double base = windtornDamage(0);
        double boosted = windtornDamage(6);
        System.out.println("[1108] windtorn base=" + base + " e6=" + boosted + " ratio=" + (boosted / base));
        Assertions.assertEquals(1.15, boosted / base, 1e-6, "「伤害倍率提高 15%」 - multiplicative");
    }

    private static double windtornDamage(int eidolon) {
        Character sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        // The talent rolls 65% for the windshear; a zero roll makes the fixture about the multiplier, not the dice.
        Battle battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(sampo.getSkills().get(SkillType.COMMON), sampo, List.of(enemy));
        return enemy.getBuffManager().activeBuffs().stream()
                .filter(buff -> buff instanceof DotBuff)
                .map(DotBuff.class::cast)
                .mapToDouble(DotBuff::getBaseDamage)
                .max().orElseThrow();
    }

    private static int level(int eidolon, SkillType slot) {
        // Levels are raised by BATTLE_START rules, so the battle must have started before the slot reads its level.
        Character sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(sampo)), new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded());
        battle.startBattle();
        return 1 + sampo.skillLevelBonus(slot);
    }

    // ==================================================================
    // Eidolon 4
    // ==================================================================

    @Test
    public void fiveLayersTickOnceAndFourDoNot() {
        double fullTick = tickOnly(5);
        // Compare WITHIN one Eidolon rank: rank 4 also raises the Skill by two levels, so E4-vs-E0 would include that.
        double extra = skillLoss(4, 5) - skillLoss(4, 4);
        double withoutEidolon = skillLoss(0, 5) - skillLoss(0, 4);
        System.out.println("[1108] fullTick=" + fullTick + " extra=" + extra);
        Assertions.assertAll("星魂 4: 「风化状态大于等于 5 层…立即产生相当于原伤害 8% 的伤害」",
                () -> Assertions.assertEquals(0.08 * fullTick, extra, 1e-6,
                        "「相当于原伤害 8%」 - one extra tick at 8% of the whole windshear stack"),
                () -> Assertions.assertEquals(0.0, withoutEidolon, 1e-6,
                        "「5 层」 is a threshold, and without the Eidolon nothing ticks"));
    }

    /**
     * One full windshear tick on five layers (the settled number, DEF and resistance included).
     */
    private static double tickOnly(int layers) {
        EidolonFourFixture f = new EidolonFourFixture(0, layers);
        return f.battle.tickDotStateNow(f.enemy, DamageElement.WIND, 1.0);
    }

    private static double skillLoss(int eidolon, int layers) {
        EidolonFourFixture f = new EidolonFourFixture(eidolon, layers);
        double before = f.enemy.getCurrentHp();
        f.battle.castImmediate(f.sampo.getSkills().get(SkillType.SKILL), f.sampo, List.of(f.enemy));
        return before - f.enemy.getCurrentHp();
    }

    private static final class EidolonFourFixture {
        private final Character sampo;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private EidolonFourFixture(int eidolon, int layers) {
            sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    // 0.99 fails the talent's 65% roll: otherwise the Skill itself adds a windshear layer and the
                    // threshold (and the tick it triggers) would move between the two runs.
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();
            for (int i = 0; i < layers; i++) {
                enemy.getBuffManager().addBuff(new DotBuff(sampo, DamageElement.WIND, TICK, 3, 0, "dot:wind_shear"));
            }
            Assertions.assertEquals(layers, enemy.getBuffManager().stacksOf("dot:wind_shear"),
                    "precondition: the windshear layers are readable by name");
        }
    }

    // ==================================================================
    // 秘技
    // ==================================================================

    @Test
    public void theTechniqueDelaysEveryEnemy() {
        double with = wait(true);
        double without = wait(false);
        System.out.println("[1108] enemy wait: with technique " + with + " ; without " + without);
        // 「行动延后 25%」 pushes the bar by 25% of the target's PERIOD (10000 / speed), not by 25% of its remaining wait -
        // measured: a period of 75.76 gets exactly 18.94 added, while the wait itself went 113.64 -> 132.58.
        double period = 10000.0 / BattleFixture.monster().getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        Assertions.assertAll("秘技: 「进入战斗时…使敌方每个单体目标行动延后 25%」",
                () -> Assertions.assertTrue(with > without, "「行动延后 25%」 must lengthen the wait"),
                () -> Assertions.assertEquals(0.25 * period, with - without, 0.01, "25% of the action period"));
    }

    private static double wait(boolean technique) {
        Character him = CharacterFactory.create(1108, 80);
        if (technique) {
            him.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(him)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // The action bar owns the wait, so the judge reads its own snapshot ({@code remainingWaitOf} in the Hyacine tests).
        for (var signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == enemy) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new AssertionError("the enemy is not on the action bar");
    }

    // ==================================================================
    // 行迹: 圈套, 后手, 加料
    // ==================================================================

    /**
     * The three 行迹, each clause on its own {@code TraceFixture} board - 圈套 (the talent's windshear lasts one turn
     * longer, 3 -> 4), 后手 (casting the Ultimate pays 10 energy on top of whatever the cast itself restores) and 加料
     * (a windsheared enemy hits him for 15% less). One board per reading, so no clause can move another, and all three
     * readings are stated in the one {@code assertAll} with the message that names its clause.
     */
    @Test
    public void theThreeTracesEachHoldTheirOwnClause() {
        int turns = windshearTurns();

        TraceFixture ultimate = new TraceFixture();
        double before = ultimate.sampo.getCurrentEnergy();
        ultimate.battle.castImmediate(ultimate.sampo.getSkills().get(SkillType.ULTRA), ultimate.sampo,
                List.of(ultimate.enemy));
        double gained = ultimate.sampo.getCurrentEnergy() - before;

        double fromWindsheared = lossFromAttacker(true);
        double fromClean = lossFromAttacker(false);

        System.out.println("[1108] 圈套 turns=" + turns + " ; 后手 energy delta=" + gained
                + " ; 加料 windsheared=" + fromWindsheared + " clean=" + fromClean);
        Assertions.assertAll("行迹 圈套 / 后手 / 加料",
                () -> Assertions.assertEquals(3 + 1, turns,
                        "「天赋使敌方陷入风化状态的持续时间延长 1 回合」"),
                () -> Assertions.assertEquals(10.0, gained, 1e-9, "「施放终结技时，额外恢复 10 点能量」"),
                () -> Assertions.assertEquals(0.85, fromWindsheared / fromClean, 1e-6, "「造成的伤害降低 15%」"));
    }

    private static int windshearTurns() {
        TraceFixture f = new TraceFixture();
        f.battle.castImmediate(f.sampo.getSkills().get(SkillType.SKILL), f.sampo, List.of(f.enemy));
        return f.enemy.getBuffManager().activeBuffs().stream()
                .filter(DotBuff.class::isInstance)
                .map(DotBuff.class::cast)
                .mapToInt(DotBuff::remainingTurns)
                .max().orElseThrow();
    }

    /**
     * The trace's energy is measured against a fixture whose trace rule is not firing otherwise: same character without
     * the Ultimate's own energy is impossible to separate, so the delta is taken between two battles that differ ONLY in
     * the Ultimate being cast.
     */
    private static double traceDelta() {
        TraceFixture cast = new TraceFixture();
        TraceFixture idle = new TraceFixture();
        double beforeCast = cast.sampo.getCurrentEnergy();
        cast.battle.castImmediate(cast.sampo.getSkills().get(SkillType.ULTRA), cast.sampo, List.of(cast.enemy));
        return cast.sampo.getCurrentEnergy() - beforeCast + idle.sampo.getCurrentEnergy();
    }

    private static double lossFromAttacker(boolean windsheared) {
        TraceFixture f = new TraceFixture();
        if (windsheared) {
            f.enemy.getBuffManager().addBuff(new DotBuff(f.enemy, DamageElement.WIND, 100, 3, 0, "dot:wind_shear"));
        }
        double before = f.sampo.getCurrentHp();
        f.battle.applyDamage(f.sampo, new Damage(f.enemy, f.sampo, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
        return before - f.sampo.getCurrentHp();
    }

    private static final class TraceFixture {
        private final Character sampo;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private TraceFixture() {
            sampo = CharacterFactory.create(SAMPO, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }
    }
}
