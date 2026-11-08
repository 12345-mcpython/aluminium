package com.laosun.aluminium.test.content.characters.topaz;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.enemy.Enemy;
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

    /** How long until the summon's next action, read off the action bar. */
    private static double remaining(Battle battle, Summon numby) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == numby) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("账账 is not in the action order");
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
