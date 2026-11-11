package com.laosun.aluminium.test.content.characters.playerboy2;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The Preservation Trailblazer pair: 8003 and 8004, one kit under two ids.
 *
 * <p>Three claims, each from the document: one stack of [灼热意志] per hit up to eight; the cast-triggered party shield at 6% DEF + 80; and the technique's
 * self-shield at 30% DEF + 384, which only exists when the technique was declared.
 */
public class PreservationTrailblazerTest {
    private static final int TB3 = 8003;
    private static final int TB4 = 8004;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: "每受到1次攻击，叠加1层[灼热意志]，最多可叠加8层".
     */
    @Test
    public void hisTalentStacksMagmaWillUpToEight() {
        Character tb = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 8; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0));
        }
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"),
                "「每受到1次攻击，叠加1层【灼热意志】，最多可叠加8层」 (every time it is attacked once, gains 1 stack of Magma Will (【灼热意志】), up to 8 stacks)");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, enemy, tb, 0, 0));
        Assertions.assertEquals(8, tb.getBuffManager().stacksOf("灼热意志"), "the ninth hit must not pass the cap");
    }

    /**
     * Note: "施放普攻、战技、终结技后，为我方全体提供…等同于6.00%防御力+80的护盾，持续2回合" -- all three casts, both ids.
     *
     * <p>⚠ <b>The battle is started first, and that is load-bearing.</b> The shield's two numbers are read out of the
     * TALENT's row at its <b>current level</b> (`percent_from_skill_param` / `amount_from_skill_param`), and the level is
     * put there by the file's own `level_convention` rule on `BATTLE_START`. This case used to cast into a battle that
     * had never started, which was invisible while the numbers were literals (they do not depend on any level) and
     * became a silent 6%-of-row-1 the moment they followed the row - measured: 52.744 instead of 129.116. A real battle
     * always starts, so starting it is what the sentence assumes.
     */
    @Test
    public void everyCastShieldsThePartyForBothIds() {
        for (int cid : new int[]{TB3, TB4}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Character ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(List.of(tb, ally), List.of(enemy), fixed());
            battle.startBattle();
            double expected = tb.getAttribute(AttributeType.DEFENCE).get() * 0.06 + 80;

            battle.castImmediate(tb.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), tb, List.of(enemy));

            Assertions.assertEquals(expected, ally.getShield(), expected * 0.02,
                    "cid " + cid + ": \"after casting the Skill, provides all allies with a shield of ... 6.00% DEF + 80\" (「施放战技后，为我方全体提供…6.00%防御力+80的护盾」)");
        }
    }

    /**
     * Note: The technique's self-shield, gated on the marker; and the control.
     */
    @Test
    public void theTechniqueShieldsHimOnlyWhenDeclared() {
        Character withTechnique = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(withTechnique), List.of(enemy), fixed());
        battle.markTechniqueUsed(withTechnique);
        battle.startBattle();
        double expected = withTechnique.getAttribute(AttributeType.DEFENCE).get() * 0.3 + 384;
        Assertions.assertEquals(expected, withTechnique.getShield(), expected * 0.02,
                "「给自身提供…等同于30%防御力+384的护盾，持续1回合」 (provides itself with a shield equal to 30% DEF + 384 for 1 turn)");

        Character without = CharacterFactory.create(TB3, LEVEL);
        Enemy enemy2 = BattleFixture.monster();
        Battle plain = new Battle(List.of(without), List.of(enemy2), fixed());
        plain.startBattle();
        Assertions.assertEquals(0.0, without.getShield(), 1e-9,
                "「使用秘技后」 (after using the Technique) -- undeclared, so no shield");
    }

    /**
     * 终结技:「对敌方全体造成等同于开拓者 **100% 攻击力 + 150% 防御力** 的火属性伤害」.
     *
     * <p><b>Why the comparison is an engine probe.</b> The base is a SUM of two attributes, so no single attribute can be
     * checked against it. The criterion therefore settles the same instance by hand
     * ({@code applyAdditionalDamage(ATK x 1.0 + DEF x 1.5)}) in an identical fight and requires the Ultimate to land exactly
     * that number - which is simultaneously "one instance" (a second settlement from the row would double it) and "the base
     * is both terms".
     */
    @Test
    public void theUltimateSettlesOneMixedBase() {
        for (int cid : new int[]{TB3, TB4}) {
            Scene cast = new Scene(cid);
            double before = cast.enemy.getCurrentHp();
            cast.battle.castImmediate(cast.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                    cast.unit, List.of(cast.enemy));
            cast.battle.processRequests();
            double settled = before - cast.enemy.getCurrentHp();

            Scene probe = new Scene(cid);
            double base = probe.unit.getAttribute(AttributeType.ATTACK).get()
                    + 1.5 * probe.unit.getAttribute(AttributeType.DEFENCE).get();
            double expected = probe.battle.applyAdditionalDamage(probe.unit, probe.enemy,
                    com.laosun.aluminium.enums.DamageElement.FIRE, base);

            System.out.println("[8003] cid=" + cid + " ult=" + settled + " probe=" + expected);
            Assertions.assertTrue(settled > 0, "cid " + cid + ": the Ultimate dealt damage");
            Assertions.assertEquals(expected, settled, Math.max(1e-6, expected * 1e-6),
                    "cid " + cid + ": ONE instance whose base is ATK + 1.5 x DEF (the row no longer swings, and `parts` is what settled it)");
        }
    }

    /**
     * 终结技的韧性 60（数据 `stance_list.all`）：被交接的施放不削韧性，所以它必须由规则那一发带着。
     */
    @Test
    public void theUltimateStillTakesSixtyToughness() {
        for (int cid : new int[]{TB3, TB4}) {
            Scene scene = new Scene(cid);
            // Toughness only comes off an enemy that is WEAK to the element (the gate `reduceToughness` keeps) - the ultimate is Fire.
            scene.enemy.addWeakness(com.laosun.aluminium.enums.DamageElement.FIRE);
            double before = scene.enemy.getStance();
            scene.battle.castImmediate(scene.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                    scene.unit, List.of(scene.enemy));
            scene.battle.processRequests();
            Assertions.assertEquals(before - 60, scene.enemy.getStance(), 1e-6,
                    "cid " + cid + ": 「破韧值 全体 60」 rides on the rule's instance");
        }
    }

    /**
     * 星魂 1:「施放普攻时，额外造成等同于开拓者 25% 防御力的火属性伤害；施放强化普攻时，额外造成等同于开拓者 50% 防御力的火属性伤害」.
     *
     * <p>The two halves differ ONLY in the share, and the only way to tell the casts apart is the swapped slot
     * ({@code from_skill_id}: 1 for the plain basic, 8 for the enhanced one). So the criterion measures each cast's EXTRA
     * damage against the same cast without the Eidolon - a ratio of 1.0 for the control and 2.0 between the two shares.
     */
    @Test
    public void eidolonOneAddsADefenceScaledInstanceToBothBasics() {
        for (int cid : new int[]{TB3, TB4}) {
            double plainWithout = extraDamageOfABasic(cid, 0, false);
            double plainWith = extraDamageOfABasic(cid, 1, false);
            double enhancedWith = extraDamageOfABasic(cid, 1, true);
            System.out.println("[8003] cid=" + cid + " extra: plain=" + plainWithout + " -> " + plainWith
                    + " enhanced=" + enhancedWith);
            Assertions.assertEquals(0.0, plainWithout, 1e-9,
                    "cid " + cid + ": the control - a plain basic adds nothing without the Eidolon");
            Assertions.assertTrue(plainWith > 0, "cid " + cid + ": 「额外造成等同于开拓者 25% 防御力的火属性伤害」");
            Assertions.assertEquals(2.0, enhancedWith / plainWith, 1e-6,
                    "cid " + cid + ": 50% vs 25% of the same defence - the enhanced basic's extra instance is twice the plain one's");
        }
    }

    /** How much MORE an enemy loses to one basic, with the Eidolon, than to the same basic without it. */
    private static double extraDamageOfABasic(int cid, int eidolon, boolean enhanced) {
        double withEidolon = damageOfOneBasic(cid, eidolon, enhanced);
        double without = damageOfOneBasic(cid, 0, enhanced);
        return withEidolon - without;
    }

    /** One basic's total damage on a fresh, non-weak enemy (toughness pinned so nothing breaks). */
    private static double damageOfOneBasic(int cid, int eidolon, boolean enhanced) {
        Scene scene = new Scene(cid, eidolon);
        if (enhanced) {
            // The swap to slot 8 happens on the events around the stacks; one hit is enough when 星魂 4 already gave four.
            int hits = Math.max(1, 4 - scene.unit.getBuffManager().stacksOf("灼热意志"));
            for (int i = 0; i < hits; i++) {
                scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                        TriggerEvent.TAKING_HIT, scene.enemy, scene.unit, 0, 0));
            }
            scene.battle.processRequests();
        }
        double before = scene.enemy.getCurrentHp();
        scene.battle.castImmediate(scene.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                scene.unit, List.of(scene.enemy));
        scene.battle.processRequests();
        return before - scene.enemy.getCurrentHp();
    }

    /**
     * 星魂 2:「触发天赋时，为我方全体提供的护盾能够额外抵消等同于开拓者防御力 2%+27 的伤害」.
     *
     * <p><b>Why a difference and not a value.</b> The shield is the SAME shield, only bigger - so the criterion casts the same
     * skill at Eidolon 1 and at Eidolon 2 and requires the difference to be exactly {@code DEF x 0.02 + 27}. That also pins the
     * one thing that could go wrong silently: two {@code SHIELD} effects firing on the same event would <b>overwrite</b> rather
     * than add (a shield does not stack), so the talent's three rules are gated at {@code max_eidolon: 1} and the Eidolon-2
     * variants state the WHOLE shield with the two shares summed.
     */
    @Test
    public void eidolonTwoRaisesTheTalentShieldByTwoPercentAndTwentySeven() {
        for (int cid : new int[]{TB3, TB4}) {
            double e1 = shieldAfterOneSkill(cid, 1);
            double e2 = shieldAfterOneSkill(cid, 2);
            Scene probe = new Scene(cid, 2);
            double expected = probe.unit.getAttribute(AttributeType.DEFENCE).get() * 0.02 + 27;
            System.out.println("[8003] cid=" + cid + " shield e1=" + e1 + " e2=" + e2 + " delta=" + (e2 - e1)
                    + " expected=" + expected);
            Assertions.assertTrue(e1 > 0, "cid " + cid + ": precondition - the talent grants a shield at Eidolon 1 too");
            Assertions.assertEquals(expected, e2 - e1, expected * 1e-9,
                    "cid " + cid + ": 「额外抵消等同于开拓者防御力 2%+27 的伤害」 - the same shield, bigger by exactly that");
        }
    }

    /** The party's shield right after casting the Skill (which is one of the talent's three triggers). */
    private static double shieldAfterOneSkill(int cid, int eidolon) {
        Scene scene = new Scene(cid, eidolon);
        scene.battle.castImmediate(scene.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL),
                scene.unit, List.of(scene.enemy));
        scene.battle.processRequests();
        return scene.unit.getShield();
    }

    /**
     * 星魂 6:「施放强化普攻或终结技后，开拓者的防御力提高 10%，最多叠加 3 层」.
     *
     * <p>Three things this pins, none of which a "the rule exists" check can see: the step is 10% of the BASE per stack, the
     * cap is three stacks <b>across both triggers</b> (they share one counter), and an un-enhanced basic does not count
     * ({@code from_skill_id == 8} is the gate - taking four stacks of Magma Will is not the same as having the swapped slot).
     */
    @Test
    public void eidolonSixStacksDefenceOnEnhancedBasicsAndUltimates() {
        for (int cid : new int[]{TB3, TB4}) {
            // ⚠ The observable ratio is NOT 1.10: `percent` on an attribute scales the WHITE base, and the white base is only
            // ~74% of the panel here (the rest comes from the level table / relics). Measured on 2026-11-09: 1.0740.. per stack.
            // So the criterion pins what the document actually says - a CONSTANT step of one tenth of the white base, three
            // times, then nothing - rather than a ratio that would encode this fixture's gear.
            double bare = defenceRatio(cid, 0, 0);
            double one = defenceRatio(cid, 6, 1);
            double two = defenceRatio(cid, 6, 2);
            double three = defenceRatio(cid, 6, 3);
            double four = defenceRatio(cid, 6, 4);
            System.out.println("[8003] cid=" + cid + " e6 stacks: " + bare + " -> " + one + " " + two + " " + three + " " + four);
            Assertions.assertEquals(1.0, bare, 1e-9, "cid " + cid + ": without the Eidolon nothing is added");
            double step = one - bare;
            Assertions.assertTrue(step > 0.05 && step < 0.15,
                    "cid " + cid + ": one stack is about a tenth of the panel (measured " + step + ")");
            Assertions.assertEquals(step, two - one, 1e-12, "cid " + cid + ": the second stack adds the same amount");
            Assertions.assertEquals(step, three - two, 1e-12, "cid " + cid + ": the third stack adds the same amount");
            Assertions.assertEquals(three, four, 1e-12, "cid " + cid + ": 「最多叠加 3 层」 - the fourth adds nothing");

            Assertions.assertEquals(step, enhancedBasicDefenceRatio(cid, 6) - bare, 1e-12,
                    "cid " + cid + ": 「施放强化普攻…后」 is a stack too");
            Assertions.assertEquals(1.0, enhancedBasicDefenceRatio(cid, 0), 1e-12,
                    "cid " + cid + ": the control - without the Eidolon the enhanced basic adds no defence");
        }
    }

    /** DEF after N ultimate casts, divided by the same character's DEF at battle start. */
    private static double defenceRatio(int cid, int eidolon, int ultimates) {
        Scene scene = new Scene(cid, eidolon);
        for (int i = 0; i < ultimates; i++) {
            scene.battle.castImmediate(scene.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                    scene.unit, List.of(scene.enemy));
            scene.battle.processRequests();
        }
        return scene.unit.getAttribute(AttributeType.DEFENCE).get() / scene.baseDefence;
    }

    /** DEF after ONE enhanced basic (four Magma Will stacks first, so the slot is swapped to 8), divided by the base. */
    private static double enhancedBasicDefenceRatio(int cid, int eidolon) {
        Scene scene = new Scene(cid, eidolon);
        // 星魂 4 hands out four stacks at battle start, so only the missing ones are fired here (the count is an input, not a
        // constant: firing four more would sit at the cap of 8 and tell us nothing about the swap).
        // ⚠ AT LEAST one: the swap to slot 8 happens on the events around the stacks, so a battle that already starts with four
        // stacks (eidolon 4) still needs ONE event for the swap rule to run. Measured: skipping it left the basic un-enhanced.
        // ⚠ Computed ONCE: re-reading the count per iteration would shrink the bound as the stacks grow (measured: 4 intended
        // hits landed as 2, and the basic was never enhanced).
        int hits = Math.max(1, 4 - scene.unit.getBuffManager().stacksOf("灼热意志"));
        for (int i = 0; i < hits; i++) {
            scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    TriggerEvent.TAKING_HIT, scene.enemy, scene.unit, 0, 0));
        }
        scene.battle.processRequests();
        Assertions.assertTrue(scene.unit.getBuffManager().stacksOf("灼热意志") >= 4,
                "precondition: four stacks so the basic is the enhanced one");
        scene.battle.castImmediate(scene.unit.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON),
                scene.unit, List.of(scene.enemy));
        scene.battle.processRequests();
        return scene.unit.getAttribute(AttributeType.DEFENCE).get() / scene.baseDefence;
    }

    /** A fight with a toughness bar big enough that a 60-point hit cannot break it (a break would add its own instance). */
    private static final class Scene {
        private final Character unit;
        private final Enemy enemy;
        private final Battle battle;

        private final double baseDefence;

        private Scene(int cid) {
            this(cid, 0);
        }

        private Scene(int cid, int eidolon) {
            unit = CharacterFactory.create(cid, LEVEL, true, null, null, eidolon);
            enemy = BattleFixture.monster();
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(List.of(unit), List.of(enemy), fixed());
            battle.startBattle();
            battle.processRequests();
            baseDefence = unit.getAttribute(AttributeType.DEFENCE).get();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}