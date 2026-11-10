package com.laosun.aluminium.test.content.characters.robin;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1309 知更鸟's Eidolons 1 / 2 / 4: the party-wide modifiers that ride her 【协奏】 state, the talent energy point Eidolon 2
 * adds, and Eidolon 4's control-class dispel.
 *
 * <p><b>Why one criterion per half.</b> All three modifiers are granted on the Ultimate - the same cast that ENTERS
 * 【协奏】 - and they are grouped by that name ({@code buff: 协奏}), which is what one {@code REMOVE_STATE 协奏} takes off.
 * So each case reads the attribute on an ALLY (not on her), and one case removes the state and expects the whole group to
 * go: a modifier that survived its state would be a silent, permanent buff.
 */
public class RobinEidolonsTest {
    private static final int ROBIN = 1309;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    /** 「冻结」: a control-class state - the thing Eidolon 4 removes. */
    private static final String CONTROL_MARK = "冻结";

    /**
     * Eidolon 1: 「处于【协奏】状态时，我方全体全属性抗性穿透提高 24%」.
     */
    @Test
    public void theFirstEidolonPutsPenetrationOnTheWholeParty() {
        Run e0 = new Run(0);
        Run e1 = new Run(1);
        double plain = e0.penetration();
        double raised = e1.penetration();
        System.out.println("[1309-E1] ally penetration " + plain + " -> " + raised);
        Assertions.assertEquals(0.0, plain, 1e-9, "E0 states no such aura");
        Assertions.assertEquals(0.24, raised - plain, 1e-9, "「全属性抗性穿透提高 24%」");
    }

    /**
     * Eidolon 2: party speed +16%, and the talent's energy restore is raised by 1.
     */
    @Test
    public void theSecondEidolonAddsSpeedAndOneMoreEnergy() {
        Run e1 = new Run(1);
        Run e2 = new Run(2);
        double base = e2.ally.getAttribute(AttributeType.SPEED).baseValue();
        double plainSpeed = e1.ally.getAttribute(AttributeType.SPEED).get();
        double raisedSpeed = e2.ally.getAttribute(AttributeType.SPEED).get();
        Assertions.assertEquals(0.16 * base, raisedSpeed - plainSpeed, 1e-6,
                "「我方全体速度提高 16%」 of the base speed");

        Assertions.assertEquals(2.0, e1.talentEnergy(), 1e-9, "the talent pays 2 by default");
        Assertions.assertEquals(3.0, e2.talentEnergy(), 1e-9,
                "「天赋的能量恢复效果额外提高 1 点」 - `MODIFY_RULE effect_amount: 1`");
    }

    /**
     * Eidolon 4: party effect RES +50% while 【协奏】 holds - and the whole group goes with the state.
     */
    @Test
    public void theFourthEidolonAddsEffectResistanceAndItLeavesWithTheState() {
        Run e4 = new Run(4);
        Assertions.assertEquals(0.5, e4.effectResistance(), 1e-9, "「效果抵抗提高 50%」");

        // The content removes the state from EVERY ally (`REMOVE_STATE 协奏 all_allies`): the state sits on her, while the
        // auras carrying the same name sit on each carrier - so the group comes off one carrier at a time.
        e4.robin.getBuffManager().removeState(Run.STATE);
        e4.ally.getBuffManager().removeState(Run.STATE);
        e4.battle.processRequests();

        Assertions.assertEquals(0.0, e4.effectResistance(), 1e-9,
                "the modifier is grouped by the state name, so one REMOVE_STATE takes the whole group off");
        Assertions.assertEquals(0.0, e4.penetration(), 1e-9, "and the Eidolon 1 aura with it");
    }

    /**
     * Eidolon 4, first half: 「解除我方全体的**控制类**负面状态」 - by CLASS, so a non-control debuff stays.
     *
     * <p>⚠ The registry used to call this unwritable (「`DISPEL` only knows all debuffs」). That was stale: the op takes a
     * {@code kind} (the engine's {@code DebuffClass}) and, with no amount, means "all of that class".
     */
    @Test
    public void theFourthEidolonDispelsControlButKeepsOtherDebuffs() {
        Run run = debuffedRun(4);
        Assertions.assertTrue(run.ally.getBuffManager().hasState(CONTROL_MARK),
                "the fixture starts with a control state on the ally");
        Assertions.assertTrue(run.ally.getAttribute(AttributeType.DEFENCE).get() < run.allyBareDefence,
                "and a non-control stat debuff, which must survive");

        run.ult();

        Assertions.assertFalse(run.ally.getBuffManager().hasState(CONTROL_MARK),
                "「解除我方全体的控制类负面状态」");
        Assertions.assertTrue(run.ally.getAttribute(AttributeType.DEFENCE).get() < run.allyBareDefence,
                "a stat debuff is NOT control-class, so it survives");
    }

    /**
     * The control: at E3 nothing dispels it.
     */
    @Test
    public void withoutTheFourthEidolonTheControlStays() {
        Run run = debuffedRun(3);
        run.ult();
        Assertions.assertTrue(run.ally.getBuffManager().hasState(CONTROL_MARK),
                "E3 states no dispel at all");
    }

    // ==================================================================

    private static final class Run {
        private static final String STATE = "协奏";
        private final Character robin;
        private final Character ally;
        private final Battle battle;
        private final double allyPenetrationBefore;
        private final double allyEffectResistanceBefore;
        private double allyBareDefence;

        Run(int eidolon) {
            this(eidolon, null, true);
        }

        Run(int eidolon, TriggerTable allyTable, boolean castNow) {
            robin = CharacterFactory.create(ROBIN, LEVEL);
            robin.setEidolonRank(eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            if (allyTable != null) {
                ally.setTriggerTable(allyTable);
            }
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(List.of(robin, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            allyPenetrationBefore = ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get();
            allyEffectResistanceBefore = ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
            if (castNow) {
                // Her Ultimate is what enters 【协奏】 and, with it, what grants the three auras.
                ult();
            }
        }

        void ult() {
            battle.castImmediate(robin.getSkills().get(SkillType.ULTRA), robin,
                    List.of(battle.enemies().getFirst()));
            battle.processRequests();
        }

        double penetration() {
            return ally.getAttribute(AttributeType.DAMAGE_PENETRATION).get() - allyPenetrationBefore;
        }

        double effectResistance() {
            return ally.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - allyEffectResistanceBefore;
        }

        /** What one ally attack pays her, read as the energy delta. */
        double talentEnergy() {
            double before = robin.getCurrentEnergy();
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, battle.enemies().getFirst(), 1, 0));
            battle.processRequests();
            return robin.getCurrentEnergy() - before;
        }
    }

    /**
     * A run whose ally carries a control state and a plain stat debuff, both applied at battle start by a hand-built table -
     * and whose Ultimate has NOT been cast yet, so the test can look at the board before it.
     */
    private static Run debuffedRun(int eidolon) {
        TriggerTable table = new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", null, control()),
                TriggerSpecs.rule("BATTLE_START", null, statDebuff())));
        Run run = new Run(eidolon, table, false);
        run.allyBareDefence = CharacterFactory.create(ALLY, LEVEL).getAttribute(AttributeType.DEFENCE).get();
        return run;
    }

    private static EffectSpec control() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "APPLY_CONTROL");
        TriggerSpecs.set(effect, "control", CONTROL_MARK);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "baseChance", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static EffectSpec statDebuff() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "DEFENCE");
        TriggerSpecs.set(effect, "percent", -0.1);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }
}
