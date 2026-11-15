package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1107 克拉拉, from the character's own files: the talent's 10% and its 【反击标记】 (with the Skill's 「战技施放后所有【反击标记】
 * 失效」, which 星魂 1 keeps from firing), the Skill's extra hit on a marked target, the Ultimate's 25% and its enhanced
 * counter, Eidolon 4's 30% (with Eidolon 3/5's slot levels), and Eidolon 6's third charge plus its ally-triggered counter.
 *
 * <p>These were seven small classes in the same package; one class keeps the character together without changing any
 * assertion. The reduction sentences stay measured one by one: the talent alone (0.9), the talent and Eidolon 4
 * multiplied (0.9 x 0.7 = 0.63), and the Ultimate's own 25% on top of the talent. The probes use a rule-less dummy with
 * her DEF and base HP so the only difference is the zone; the marker and its clearing are state facts.
 *
 * <p><b>L3:</b> the single-clause cases that play one fixture and read one observed object are folded into the
 * scenario tests below, each assertion inside {@link Assertions#assertAll} keeping its message that names the clause.
 * The mark's clearing is read at E0 and at 星魂 1 on the same {@code Fixture}; the two reduction readings of Eidolon 4
 * (armed after the hit, multiplied onto the talent) come off one fixture sequence; the Eidolon 6 counter is read with
 * an ally hit and with her own hit, which is the control for the same clause. Nothing was dropped: every expected
 * value, tolerance and message the separate cases carried is still asserted.
 *
 * <p>Her trace (行迹) rules live next door in {@code ClaraTraceTest}.
 */
public class ClaraTest {

    private static final int CLARA = 1107;
    private static final int LEVEL = 80;
    private static final String MARK = "反击标记";
    private static final String CHARGES = "强化反击";

    /**
     * The 10% protection: the same probe hit is 10% smaller on her than on a bare dummy with her DEF.
     */
    @Test
    public void theTalentProtectsHer() {
        Fixture f = new Fixture();
        double claraLoss = f.probeClara();
        double dummyLoss = f.probeDummy();
        System.out.println("[1107] clara=" + claraLoss + " dummy=" + dummyLoss + " ratio=" + (claraLoss / dummyLoss));
        Assertions.assertEquals(0.9, claraLoss / dummyLoss, 1e-6,
                "「受到敌方目标攻击的伤害降低 10%」");
    }

    /**
     * The attacker carries 【反击标记】, and the Skill clears every mark - except at 星魂 1.
     *
     * <p>星魂 1 高大的背影:「施放战技后，**不会**清除敌方目标的【反击标记】」. The base Skill DOES clear every mark, so
     * the Eidolon half is the same act with the Eidolon on: the mark must survive. The rule states it the honest way -
     * the clearing rule itself is gated {@code eidolon < 1}, so at rank 1 nothing clears rather than something
     * "un-clearing". Both ranks are read on the one {@link Fixture}: the same hit, then the same Skill cast, and the
     * only difference is the observed mark.
     */
    @Test
    public void theSkillClearsTheMarkUnlessEidolonOneKeepsIt() {
        Fixture base = new Fixture();
        base.claraLosesHp();
        boolean baseMarked = base.enemy.getBuffManager().hasState(MARK);
        base.castSkill();
        boolean baseCleared = base.enemy.getBuffManager().hasState(MARK);

        Fixture e1 = new Fixture(1);
        e1.claraLosesHp();
        boolean e1Marked = e1.enemy.getBuffManager().hasState(MARK);
        e1.castSkill();
        boolean e1Kept = e1.enemy.getBuffManager().hasState(MARK);

        Assertions.assertAll("「战技施放后所有【反击标记】失效」 - and 星魂 1's exception to it",
                () -> Assertions.assertTrue(baseMarked,
                        "「攻击克拉拉的敌方目标会被史瓦罗标上【反击标记】」"),
                () -> Assertions.assertFalse(baseCleared,
                        "「战技施放后所有【反击标记】失效」"),
                () -> Assertions.assertTrue(e1Marked, "precondition: the attacker is marked"),
                () -> Assertions.assertTrue(e1Kept,
                        "「施放战技后，不会清除敌方目标的【反击标记】」 - with the Eidolon the mark stays"));
    }

    /**
     * 1107 克拉拉's Skill: 「对所有被史瓦罗标上【反击标记】的敌方目标额外造成 120% 攻击力物理伤害」.
     */
    @Test
    public void onlyTheMarkedEnemiesTakeTheExtraHit() {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy marked = BattleFixture.monster();
        Enemy plain = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(marked, plain)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        marked.getBuffManager().addBuff(new StackBuff(MARK, 1, true, 5));

        double markedBefore = marked.getCurrentHp();
        double plainBefore = plain.getCurrentHp();
        battle.castImmediate(clara.getSkills().get(SkillType.SKILL), clara, List.of(marked, plain));
        double markedLoss = markedBefore - marked.getCurrentHp();
        double plainLoss = plainBefore - plain.getCurrentHp();
        System.out.println("[1107] marked loss=" + markedLoss + " plain loss=" + plainLoss);
        Assertions.assertTrue(markedLoss > plainLoss, "「对标记目标**额外**造成一次伤害」");
    }

    /**
     * The two reduction sentences - the talent's 10% and Eidolon 4's 30% - measured in the order they are armed.
     *
     * <p>Eidolon 4: 「受到攻击后，克拉拉受到的伤害降低 30%，效果持续到自己的下个回合开始」. Two things are load-bearing
     * and both are measured: the reduction must NOT cover the attack that granted it (「受到攻击<b>后</b>」), and it must
     * survive until her own next turn ({@code NEXT_TURN_START}, not "one turn" - {@code turns: 1} would drop it at the
     * end of a turn, one beat too early).
     *
     * <p>Measured against a bare dummy (her DEF, no rules) rather than against E0: the talent already reduces 10%, and
     * this engine REPLACES same-kind reductions instead of adding them, so an E4/E0 ratio would read 7/9 for a reason
     * unrelated to the 30%. {@code ClaraReductionStackingGapTest} pins that defect.
     *
     * <p>The canon's reduction zone is a <b>product</b> (HSR.md: 减伤区 = Π(1 - 减伤)), so once both sentences are in the
     * zone the total is 0.9 x 0.7 = 0.63 - not 0.6 (that would be additive) and not 0.7 (that is what a same-kind
     * <b>replacement</b> gives, which silently loses the talent's sentence). {@code ReductionBuff.isSameKind} now
     * compares the creating rule, so two rules coexist while a re-application of the same rule still refreshes.
     *
     * <p>One fixture sequence reads both clauses: the same {@code Fixture(4)} is probed before the hit (the talent alone,
     * 0.9) and after it (both zones, 0.63), so the timing and the multiplication are two readings of one board. The E0
     * board is beside it as the control that the 30% zone is the Eidolon's at all.
     */
    @Test
    public void theReductionStartsAfterTheHitAndStacksOnTheTalent() {
        Fixture e0 = new Fixture(0);
        double dummy = e0.probeDummy();
        double e0TalentOnly = e0.probeClara() / dummy;

        Fixture e0After = new Fixture(0);
        e0After.probeClara();
        e0After.fireAttackFinishedOnClara();
        double e0AfterHit = e0After.probeClara() / dummy;

        Fixture e4 = new Fixture(4);
        double e4TalentOnly = e4.probeClara() / dummy;
        e4.fireAttackFinishedOnClara();
        double after = e4.probeClara();
        System.out.println("[1107] e4 after=" + after + " dummy=" + dummy + " ratio=" + (after / dummy));
        System.out.println("[1107] stacking: after=" + after / dummy);

        Assertions.assertAll("the Eidolon 4 zone: armed after the hit, multiplied onto the talent",
                () -> Assertions.assertEquals(0.9, e0TalentOnly, 1e-6, "E0: only the talent's 10% protects her"),
                () -> Assertions.assertEquals(0.9, e0AfterHit, 1e-6, "E0: no 30% zone at all"),
                () -> Assertions.assertEquals(0.9, e4TalentOnly, 1e-6,
                        "the attack that triggers it is NOT reduced by the Eidolon (「受到攻击<b>后</b>」)"),
                () -> Assertions.assertEquals(0.9, e4TalentOnly, 1e-6, "the talent alone"),
                // The talent 10% and the Eidolon 30% are two zones, multiplied (0.9 x 0.7 = 0.63).
                () -> Assertions.assertEquals(0.63, after / dummy, 1e-6,
                        "「受到的伤害降低 30%」 - and it is still up until her next turn starts"),
                () -> Assertions.assertEquals(0.63, after / dummy, 1e-6,
                        "both sentences are in the zone now (a same-kind replacement would give 0.7)"));
    }

    /**
     * Eidolon 3 and 5: the two level clauses the data spells as {@code skill_add_level}.
     */
    @Test
    public void theLevelEidolonsRaiseTheirSlots() {
        Assertions.assertAll("Eidolon 3 and 5's 等级 +2 / +1",
                () -> Assertions.assertEquals(level(0, SkillType.SKILL) + 2, level(3, SkillType.SKILL), "E3: 战技等级 +2"),
                () -> Assertions.assertEquals(level(0, SkillType.COMMON) + 1, level(3, SkillType.COMMON), "E3: 普攻等级 +1"),
                () -> Assertions.assertEquals(level(0, SkillType.ULTRA) + 2, level(5, SkillType.ULTRA), "E5: 终结技等级 +2"),
                () -> Assertions.assertEquals(level(0, SkillType.TALENT) + 2, level(5, SkillType.TALENT), "E5: 天赋等级 +2"));
    }

    /**
     * 1107 克拉拉's Ultimate, first clause: 「施放终结技后克拉拉受到的伤害额外降低 25%…持续 2 回合」.
     */
    @Test
    public void theUltimateCutsTheDamageSheTakes() {
        double before = taken(false);
        double after = taken(true);
        System.out.println("[1107] damage taken " + before + " -> " + after);
        Assertions.assertEquals(0.75, after / before, 1e-6, "「受到的伤害额外降低 25%」");
    }

    /**
     * 1107 克拉拉's Ultimate, second clause: the enhanced counter fires when an ENEMY finishes an attack, twice per Ultimate.
     */
    @Test
    public void theUltimateArmsTwoCounters() {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        Assertions.assertEquals(2, clara.getBuffManager().stacksOf(CHARGES), "「强化效果可生效 2 次」");

        double hp = enemy.getCurrentHp();
        enemyAttack(battle, clara, enemy);
        Assertions.assertTrue(enemy.getCurrentHp() < hp, "an ENEMY attack triggers Svarog's counter");
        Assertions.assertEquals(1, clara.getBuffManager().stacksOf(CHARGES), "one charge spent");

        enemyAttack(battle, clara, enemy);
        Assertions.assertEquals(0, clara.getBuffManager().stacksOf(CHARGES), "two charges spent");

        double after = enemy.getCurrentHp();
        enemyAttack(battle, clara, enemy);
        Assertions.assertEquals(after, enemy.getCurrentHp(), 1e-9, "no charges left: no counter");
    }

    /**
     * 1107 克拉拉's Eidolon 6: 「终结技额外增加 1 次强化反击」 and 「我方其他目标遭到攻击后，史瓦罗有 50% 的固定概率发动反击」.
     */
    @Test
    public void theUltimateArmsThreeCountersAtEidolonSix() {
        Assertions.assertEquals(3, charges(6), "「终结技额外增加 1 次强化反击」");
        Assertions.assertEquals(2, charges(0), "control: two without the Eidolon");
    }

    /**
     * The Eidolon 6 clause names 「我方**其他**目标」: another ally being hit arms Svarog, while Clara's own being hit is
     * the Talent's clause and must not reach this counter.
     *
     * <p>Both halves play the same board - Clara at 星魂 6, one ally, one enemy, a generator that always answers 0 so
     * the 50% fixed chance lands - and read the same object, the enemy's HP after one {@code ATTACK_FINISHED}. Only the
     * victim of the hit differs, which is exactly what the clause is about, so the control belongs in this scenario.
     */
    @Test
    public void onlyAnotherAlliesBeingHitTriggersTheCounter() {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, 6);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));   // always roll 0 => the 50% chance lands
        battle.startBattle();
        double hp = enemy.getCurrentHp();
        battle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, enemy, ally, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(ally)));
        double allyHitHp = enemy.getCurrentHp();
        boolean marked = enemy.getBuffManager().hasState(MARK);
        System.out.println("[1107] enemy hp " + hp + " -> " + allyHitHp);

        Character ownClara = CharacterFactory.create(CLARA, LEVEL, true, null, null, 6);
        Enemy ownEnemy = BattleFixture.monster();
        Battle ownBattle = new Battle(new ArrayList<>(List.of(ownClara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(ownEnemy)), BattleFixture.alwaysRoll(0.0));
        ownBattle.startBattle();
        double ownHp = ownEnemy.getCurrentHp();
        ownBattle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, ownEnemy, ownClara, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(ownClara)));

        Assertions.assertAll("「我方其他目标遭到攻击后…发动反击」 - her own hit is not that clause",
                () -> Assertions.assertTrue(allyHitHp < hp, "「我方其他目标遭到攻击后…发动反击」"),
                () -> Assertions.assertTrue(marked,
                        "「…并对攻击目标添加【反击标记】」 - the counter marks the attacker it hit back"),
                () -> Assertions.assertEquals(ownHp, ownEnemy.getCurrentHp(), 1e-9,
                        "「我方**其他**目标」 - her own hit is the Talent's clause, and that one is not unconditional"));
    }

    // ==================================================================

    /**
     * Levels are raised by BATTLE_START rules, so the battle must have started before the slot reads its level.
     */
    private static int level(int eidolon, SkillType slot) {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
        Battle battle = new Battle(new ArrayList<>(List.of(clara)), new ArrayList<>(List.of(BattleFixture.monster())),
                BattleFixture.seeded());
        battle.startBattle();
        return 1 + clara.skillLevelBonus(slot);
    }

    /**
     * The damage a 1000-strength hit deals her, before and after the Ultimate.
     */
    private static double taken(boolean afterUlt) {
        Character clara = CharacterFactory.create(CLARA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (afterUlt) {
            battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        }
        return battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
    }

    /**
     * The Ultimate's charges for a given Eidolon rank.
     */
    private static int charges(int eidolon) {
        Character clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(clara.getSkills().get(SkillType.ULTRA), clara, List.of(enemy));
        return clara.getBuffManager().stacksOf(CHARGES);
    }

    /**
     * Finishes an enemy attack on Clara and lets the trigger table answer it.
     */
    private static void enemyAttack(Battle battle, Character clara, Enemy enemy) {
        battle.fire(new TriggerPayload.Cast(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 0.0,
                SkillCategory.NORMAL, 0, 0, List.of(clara)));
    }

    // ==================================================================

    /**
     * One battle with Clara at the given Eidolon rank plus a rule-less dummy carrying her DEF and base HP (so a damage
     * ratio isolates the reduction zone), and the same battle's mark helpers.
     *
     * <p>The no-arg form is the base rank, which is what the talent's own probes want.
     */
    private static final class Fixture {
        private final Character clara;
        private final Character dummy;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private final Battle dummyBattle;

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            clara = CharacterFactory.create(CLARA, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();

            double defence = clara.getAttribute(AttributeType.DEFENCE).get();
            double health = clara.getMaxHp();
            dummy = Character.fromAttributes("Bare Dummy", health, defence, 100, 100);
            dummyBattle = new Battle(new ArrayList<>(List.of(dummy)),
                    new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.99));
            dummyBattle.startBattle();
        }

        private double probeClara() {
            double before = clara.getCurrentHp();
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - clara.getCurrentHp();
        }

        private double probeDummy() {
            double before = dummy.getCurrentHp();
            dummyBattle.applyDamage(dummy, new Damage(dummyBattle.enemies().getFirst(), dummy,
                    DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - dummy.getCurrentHp();
        }

        private void claraLosesHp() {
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 100.0));
        }

        private void castSkill() {
            battle.castImmediate(clara.getSkills().get(SkillType.SKILL), clara, List.of(enemy));
        }

        private void fireAttackFinishedOnClara() {
            battle.fire(TriggerPayload.Cast.of(TriggerEvent.ATTACK_FINISHED, enemy, clara, 1, 200.0,
                    com.laosun.aluminium.enums.SkillCategory.UNSPECIFIED, 0, 0, List.of(clara)));
        }
    }
}
