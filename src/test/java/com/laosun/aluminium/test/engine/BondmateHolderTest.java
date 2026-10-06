package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The <b>state-holder</b> selector: {@code "target": "holder_of:同袍"} - "持有[同袍]的角色").
 *
 * <p><b>Why the vocabulary had to exist.</b> 1414's kit is built on one marker: his skill designates one ally as
 * [同袍], and then two other clauses speak about <i>whoever holds it</i> - the Shenxiu (神秀) trace that raises that ally's ATK,
 * and (next) the technique that re-aims his skill at that ally. A condition cannot say it (conditions decide whether a
 * <b>rule</b> runs, not which units an effect reaches), and no fixed selector could either: the holder is not a slot in
 * the roster, it is whoever the state landed on.
 *
 * <p>What each case pins:
 * <ul>
 *   <li>the holder is read - and it is <b>only</b> the holder that is buffed, with the other ally as the control;</li>
 *   <li>nobody holding it reaches <b>nobody</b> (a legal state of the world for these clauses, unlike a missing
 *       {@code summon}, which throws);</li>
 *   <li>an empty state name is refused where the file is read;</li>
 *   <li>and his three shipped clauses actually run: the technique's [同袍], the trace that buffs whoever just became
 *       the Bondmate, the trace's "行动提前 40%" and the trace's "[同袍]施放攻击时回复 6 点能量".</li>
 * </ul>
 */
public class BondmateHolderTest {
    private static final int DHPT = 1414;
    private static final int ALLY = 1002;
    private static final int OTHER_ALLY = 1202;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String BONDMATE = "同袍";
    private static final double EPS = 1e-6;

    // ==================================================================
    // 1. The selector itself
    // ==================================================================

    /**
     * Only the unit that <b>holds</b> the state is reached - and when nobody holds it, nobody is.
     *
     * <p>The control matters twice: the other ally must stay untouched (a resolver that picked the first ally, or the
     * owner, would move it), and the amount must be the owner's own 15% (so a wrong <i>magnitude</i> source shows up
     * as a number, not as a pass).
     */
    @Test
    public void onlyTheHolderIsReached() {
        Character him = CharacterFactory.create(DHPT, LEVEL);
        him.setTriggerTable(new TriggerTable(DHPT, List.of(
                TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), holderBuff()))));
        Character first = CharacterFactory.create(ALLY, LEVEL);
        Character second = CharacterFactory.create(OTHER_ALLY, LEVEL);
        Battle battle = new Battle(List.of(him, first, second), List.of(dummy()), new Random(0));
        battle.startBattle();

        double firstBefore = first.getAttribute(AttributeType.ATTACK).get();
        double secondBefore = second.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, him, first, 0, 0);
        Assertions.assertEquals(firstBefore, first.getAttribute(AttributeType.ATTACK).get(), EPS,
                "nobody holds " + BONDMATE + " yet, so the effect must reach nobody (and must not throw)");
        Assertions.assertEquals(secondBefore, second.getAttribute(AttributeType.ATTACK).get(), EPS,
                "and certainly not the other ally");

        second.getBuffManager().addBuff(new StateBuff(BONDMATE, 3, false));
        double hisAttack = him.getAttribute(AttributeType.ATTACK).get();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, him, first, 0, 0);

        Assertions.assertEquals(secondBefore + 0.15 * hisAttack, second.getAttribute(AttributeType.ATTACK).get(), EPS,
                "the holder is buffed by 15% of HIS attack (" + hisAttack + ")");
        Assertions.assertEquals(firstBefore, first.getAttribute(AttributeType.ATTACK).get(), EPS,
                "and the ally who does NOT hold it is still untouched");
        Assertions.assertEquals(hisAttack, him.getAttribute(AttributeType.ATTACK).get(), EPS,
                "nor is the rule's owner (the sentence is about the holder, not about me)");
    }

    /** Note: The prefix with no state after it is refused while the file is read. */
    @Test
    public void anEmptyHolderStateIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "percent", 0.15);
        TriggerSpecs.set(effect, "target", "holder_of:");

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(DHPT,
                        List.of(TriggerSpecs.rule("SKILL_CAST", List.of("actor == self"), effect))));

        Assertions.assertTrue(refused.getMessage().contains("holder_of"), refused.getMessage());
    }

    // ==================================================================
    // 2. His shipped clauses
    // ==================================================================

    /** technique (秘技) "使用秘技后获得[同袍]" - and its control: without the technique there is no Bondmate. */
    @Test
    public void hisTechniqueGivesHimTheBondmate() {
        Character without = CharacterFactory.create(DHPT, LEVEL);
        Battle plain = new Battle(List.of(without), List.of(dummy()), new Random(0));
        plain.startBattle();
        Assertions.assertFalse(without.getBuffManager().hasState(BONDMATE),
                "no technique was used, so nothing may be granted");

        Character him = CharacterFactory.create(DHPT, LEVEL);
        Battle battle = new Battle(List.of(him), List.of(dummy()), new Random(0));
        battle.markTechniqueUsed(him);
        battle.startBattle();

        Assertions.assertTrue(him.getBuffManager().hasState(BONDMATE),
                "「使用秘技后获得【同袍】」: the technique's own marker (秘技) is what the rule is gated on");
    }

    /**
     * trace (行迹) Shenxiu (神秀) "施放战技时，使成为[同袍]的目标攻击力提高，等同于丹恒-腾荒15%攻击力" - no scaffold.
     *
     * <p>Two things at once, and both are the reason the selector exists: the ally who was <b>just</b> designated by
     * this very cast is the one buffed (which is also an assertion about rule ORDER inside the file - the marking has
     * to happen first), and the owner is not.
     */
    @Test
    public void hisTraceBuffsWhoeverJustBecameTheBondmate() {
        Character him = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(him, ally), List.of(dummy()), new Random(0));
        battle.startBattle();
        double himBefore = him.getAttribute(AttributeType.ATTACK).get();
        double allyBefore = ally.getAttribute(AttributeType.ATTACK).get();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, him, ally, 0, 0);

        Assertions.assertTrue(ally.getBuffManager().hasState(BONDMATE),
                "precondition: the cast designated the aimed ally as " + BONDMATE);
        Assertions.assertEquals(allyBefore + 0.15 * himBefore, ally.getAttribute(AttributeType.ATTACK).get(), EPS,
                "the trace buffed the ally who holds it, by 15% of 1414's ATK (" + himBefore + ")");
        Assertions.assertEquals(himBefore, him.getAttribute(AttributeType.ATTACK).get(), EPS,
                "and not its own owner");
    }

    /** trace (行迹) Weirui (葳蕤) "战斗开始时，丹恒-腾荒行动提前40%" - measured on the action bar, against no rules at all. */
    @Test
    public void hisTraceAdvancesHimAtBattleStart() {
        Character plain = CharacterFactory.create(DHPT, LEVEL);
        plain.setTriggerTable(TriggerTable.EMPTY);
        Battle baseline = new Battle(List.of(plain), List.of(dummy()), new Random(0));
        baseline.startBattle();

        Character him = CharacterFactory.create(DHPT, LEVEL);
        Battle battle = new Battle(List.of(him), List.of(dummy()), new Random(0));
        battle.startBattle();

        double untouched = timeRemaining(baseline, plain);
        Assertions.assertTrue(untouched > 0, "precondition: there was a wait to shorten (" + untouched + ")");
        Assertions.assertEquals(untouched * 0.6, timeRemaining(battle, him), 1e-9,
                "「行动提前40%」: " + untouched + " -> " + timeRemaining(battle, him));
    }

    /** trace (行迹) Weirui (葳蕤)'s second half "[同袍]施放攻击时，丹恒-腾荒恢复6点能量" - gated on the ATTACKER holding it. */
    @Test
    public void hisTracePaysEnergyWhenTheBondmateAttacks() {
        Character him = CharacterFactory.create(DHPT, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(him, ally), List.of(enemy), new Random(0));
        battle.startBattle();

        double before = him.getCurrentEnergy();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, him, enemy, 0, 0);
        Assertions.assertEquals(before, him.getCurrentEnergy(), EPS,
                "the attacker does not hold " + BONDMATE + ", so nothing is paid");

        ally.getBuffManager().addBuff(new StateBuff(BONDMATE, 3, false));
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0);
        Assertions.assertEquals(before + 6, him.getCurrentEnergy(), EPS,
                "and when the Bondmate attacks, he gets 6");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** "使<持有某个状态的目标>攻击力提高，等同于我自己攻击力的 15%" - the shape the trace is written in. */
    private static EffectSpec holderBuff() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "ATTACK");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", 0.15);
        TriggerSpecs.set(effect, "turns", 3);
        TriggerSpecs.set(effect, "target", "holder_of:" + BONDMATE);
        return effect;
    }

    /** How much action value the unit still has - the action bar's own answer. */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
