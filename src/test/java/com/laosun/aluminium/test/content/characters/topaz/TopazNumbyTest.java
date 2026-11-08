package com.laosun.aluminium.test.content.characters.topaz;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1112 托帕&账账: 账账 (Numby) is a <b>panel-less</b> summon with its own action - 「战斗开始时召唤账账…初始拥有 80 点速度，行动时…造成等同于托帕 150% 攻击力的火属性伤害」.
 *
 * <p>It has no skill row in the data, so the share is the sentence's own number; the action point is its turn, exactly as 1204's 神君.
 */
public class TopazNumbyTest {
    private static final int TOPAZ = 1112;
    private static final int LEVEL = 80;

    @Test
    public void numbyEntersAtEightySpeed() {
        Battle battle = scene();
        Summon numby = battle.summonsOf(battle.characters().getFirst()).getFirst();
        Assertions.assertEquals("账账", numby.getName());
        Assertions.assertEquals(80, numby.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「账账初始拥有 80 点速度」");
    }

    /**
     * 「行动时…**对陷入【负债证明】状态下的敌方单体**造成…」 - so the state has to be there first. Without it the strike has no
     * legal target at all, which is the clause's own reading (and the reason `random_enemy` was wrong here).
     */
    @Test
    public void numbyStrikesOnlyADebtMarkedTarget() {
        Battle battle = scene();
        Character topaz = battle.characters().getFirst();
        Summon numby = battle.summonsOf(topaz).getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(before, enemy.getCurrentHp(), 1e-9,
                "nobody holds 【负债证明】, so there is no target the sentence allows");

        battle.castImmediate(topaz.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), topaz,
                List.of(enemy));
        battle.processRequests();
        Assertions.assertTrue(enemy.getBuffManager().hasState("负债证明"), "precondition: the Skill marks the target");
        before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
        battle.processRequests();
        Assertions.assertTrue(enemy.getCurrentHp() < before, "「行动时发动追加攻击…造成火属性伤害」");
    }

    /**
     * 天赋:「…陷入【负债证明】状态下的敌方目标受到我方的追加攻击时，账账行动提前 50%。在账账自身回合内，无法触发行动提前效果」.
     *
     * <p>An advance is visible in exactly one place - the action bar - so this reads the summon's own signal before and after
     * (`queue().snapshot()` + `getTimeRemaining`, the probe 1222 introduced for the same clause family). The three controls are
     * the conditions the sentence itself states, each of which would otherwise be an over-trigger with no symptom:
     * <ol>
     *   <li>the target does not carry 【负债证明】;</li>
     *   <li>the attacker is not ours (an enemy's follow-up);</li>
     *   <li>the attacker IS the summon - 「在账账自身回合内，无法触发」.</li>
     * </ol>
     */
    @Test
    public void anAllyFollowUpOnADebtMarkedTargetAdvancesNumbyByHalf() {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL);
        Enemy marked = BattleFixture.monster();
        Enemy plain = BattleFixture.monster();
        for (Enemy each : List.of(marked, plain)) {
            each.setMaxStance(10_000);
        }
        Battle battle = new Battle(List.of(topaz), List.of(marked, plain), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon numby = battle.summonsOf(topaz).getFirst();
        battle.castImmediate(topaz.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), topaz, List.of(marked));
        battle.processRequests();
        Assertions.assertTrue(marked.getBuffManager().hasState("负债证明"), "precondition: the Skill marks the target");

        double before = remaining(battle, numby);
        Assertions.assertTrue(before > 0, "precondition: it has time left to skip (" + before + ")");
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, topaz, marked, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(before * 0.5, remaining(battle, numby), 1e-6,
                "「…账账行动提前 50%」 - half of its remaining wait is skipped");

        // Control 1: the same follow-up on a target WITHOUT the mark.
        double armed = remaining(battle, numby);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, topaz, plain, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(armed, remaining(battle, numby), 1e-9,
                "「陷入【负债证明】状态下的敌方目标」 - a target without the mark does not advance it");

        // Control 2: the summon's own follow-up - 「在账账自身回合内，无法触发行动提前效果」.
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, numby, marked, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(armed, remaining(battle, numby), 1e-9,
                "「…自身回合内，无法触发」 - its own attack must not advance it (that is what `!actor is_summon` is for)");

        // Control 3: an enemy's follow-up is not 「我方」.
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.FOLLOW_UP, marked, marked, 1, 0));
        battle.processRequests();
        Assertions.assertEquals(armed, remaining(battle, numby), 1e-9,
                "「受到**我方**追加攻击时」 - the enemy's own follow-up is not ours");
    }

    /**
     * 星魂 2:「账账自身行动并发动攻击后，托帕恢复 5 点能量」 (param [5]).
     *
     * <p>Judged as the DIFFERENCE between E2 and E1 on the same action: the attack itself settles energy of its own, so the
     * absolute bar cannot be the criterion - the Eidolon's five points are what has to be there on top.
     *
     * <p>⚠ The gate 「**并发动攻击**」 is real: Numby only strikes enemies that carry 【负债证明】, so with no mark on the field
     * it never swung and the Eidolon must not pay (a clause that paid for a turn nobody took would be an over-trigger with no
     * symptom).
     */
    @Test
    public void theSecondEidolonPaysAfterNumbysAttack() {
        Assertions.assertEquals(5.0, energyAfterOneAction(2) - energyAfterOneAction(1), 1e-9,
                "「账账自身行动并发动攻击后，托帕恢复 5 点能量」");
        Assertions.assertEquals(0.0, energyAfterOneAction(2, false) - energyAfterOneAction(1, false), 1e-9,
                "no 【负债证明】 on the field ⇒ no attack ⇒ no energy");
    }

    /**
     * 星魂 4:「账账自身回合开始时，托帕的行动提前 20%」 (param [0.2]) - and unlike 星魂 2 it does NOT need the attack: the
     * sentence says "when its turn starts", so an empty turn still advances her.
     */
    @Test
    public void theFourthEidolonAdvancesHerOnNumbysTurn() {
        Assertions.assertEquals(0.8, topazRemainingRatio(4), 1e-6,
                "「账账自身回合开始时，托帕的行动提前 20%」 - a fifth of her remaining wait is skipped");
        Assertions.assertEquals(1.0, topazRemainingRatio(3), 1e-9, "before 星魂 4 nothing happens");
    }

    /**
     * 秘技:「主动施放秘技使下一场战斗中账账施放首次攻击后，托帕恢复 60 点能量」 (param [60, 10000]) - 「首次」 is
     * {@code once_per_battle}, so the second attack pays nothing.
     */
    @Test
    public void theTechniquePaysSixtyOnTheFirstAttack() {
        Fixture guarded = new Fixture(0, true, true);
        double first = guarded.act();
        Assertions.assertEquals(60.0, first, 1e-9, "「账账施放首次攻击后，托帕恢复 60 点能量」");
        Assertions.assertEquals(0.0, guarded.act(), 1e-9, "「首次」 - the second attack pays nothing");

        Fixture control = new Fixture(0, true, false);
        Assertions.assertEquals(0.0, control.act(), 1e-9, "control: no technique, no energy");
    }

    /** Energy 托帕 gains from one of Numby's actions, at the given Eidolon level. */
    private static double energyAfterOneAction(int eidolon) {
        return energyAfterOneAction(eidolon, true);
    }

    private static double energyAfterOneAction(int eidolon, boolean markTheTarget) {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon numby = battle.summonsOf(topaz).getFirst();
        if (markTheTarget) {
            battle.castImmediate(topaz.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), topaz, List.of(enemy));
            battle.processRequests();
        }
        topaz.setCurrentEnergy(0);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
        battle.processRequests();
        return topaz.getCurrentEnergy();
    }

    /** 托帕's remaining wait after Numby's turn start, as a share of what it was. */
    private static double topazRemainingRatio(int eidolon) {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        Summon numby = battle.summonsOf(topaz).getFirst();
        double before = remaining(battle, topaz);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
        battle.processRequests();
        return remaining(battle, topaz) / before;
    }

    /** A fight where 「负债证明」 may or may not be up and the technique may or may not have been declared. */
    private static final class Fixture {
        private final Character topaz;
        private final Summon numby;
        private final Battle battle;

        private Fixture(int eidolon, boolean markTheTarget, boolean technique) {
            topaz = CharacterFactory.create(TOPAZ, LEVEL, false, null, null, eidolon);
            if (technique) {
                // The engine stamps this permanent state before any BATTLE_START rule runs; it is the gate her Technique reads.
                topaz.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("秘技", 1, true));
            }
            Enemy enemy = BattleFixture.monster();
            enemy.setMaxStance(10_000);
            battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            numby = battle.summonsOf(topaz).getFirst();
            if (markTheTarget) {
                battle.castImmediate(topaz.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), topaz,
                        List.of(enemy));
                battle.processRequests();
            }
            topaz.setCurrentEnergy(0);
        }

        /** One of Numby's actions; returns the energy it paid 托帕. */
        private double act() {
            topaz.setCurrentEnergy(0);
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, numby, numby, 1, 0));
            battle.processRequests();
            return topaz.getCurrentEnergy();
        }
    }

    /** How long until that unit acts, read off the action bar. */
    private static double remaining(Battle battle, CanHit unit) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException(unit.getName() + " is not in the action order");
    }

    private static Battle scene() {
        Character topaz = CharacterFactory.create(TOPAZ, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        Battle battle = new Battle(List.of(topaz), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
