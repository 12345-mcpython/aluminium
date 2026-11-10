package com.laosun.aluminium.test.content.characters.mydei1404;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1404 万敌's Eidolons 2 / 4 / 6: the per-instance defence ignore and crit damage while in 【血仇】, the heal when he is
 * hit, and entering 【血仇】 at battle start.
 *
 * <p><b>Why per-instance modifiers.</b> 【血仇】 is a real state that ENDS (his lethal-blow rule removes it), so a
 * long-lived attribute would have to be taken back at exactly that moment. Written on {@code DEALING_DAMAGE} with
 * {@code instance: true}, the bonus belongs to the hit that is being settled and nothing outlives the state.
 *
 * <p>The two damage cases compare TOTAL damage at the same target with the same seed: a defence ignore and a crit
 * damage raise are both invisible in a panel reading, so they have to show up in the numbers.
 */
public class MydeiEidolonsTest {
    private static final int MYDEI = 1404;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "天赋充能";
    private static final String STATE = "血仇";

    /**
     * Eidolon 2: 「【血仇】期间，万敌造成的伤害无视敌方目标 15% 的防御力」 - the same hit does more.
     */
    @Test
    public void theSecondEidolonPiercesDefenceInBloodfeud() {
        double plain = damageInBloodfeud(1);
        double raised = damageInBloodfeud(2);
        System.out.println("[1404-E2] damage in 血仇: E1=" + plain + " ; E2=" + raised);
        Assertions.assertTrue(raised > plain,
                "「无视 15% 的防御力」 must land as more damage: E1=" + plain + " E2=" + raised);
    }

    /**
     * Eidolon 4, first half: 「【血仇】期间暴击伤害提高 30%」.
     */
    @Test
    public void theFourthEidolonCritsHarderInBloodfeud() {
        double plain = damageInBloodfeud(3);
        double raised = damageInBloodfeud(4);
        System.out.println("[1404-E4] critical damage in 血仇: E3=" + plain + " ; E4=" + raised);
        Assertions.assertTrue(raised > plain,
                "「暴击伤害提高 30%」 must land as more damage: E3=" + plain + " E4=" + raised);
    }

    /**
     * Eidolon 4, second half: a hit from an enemy heals him for 10% of his maximum HP.
     */
    @Test
    public void theFourthEidolonHealsWhenHeIsHit() {
        Run run = new Run(4);
        run.him.takeDamage(run.him.getAttribute(AttributeType.HEALTH).get() * 0.5);
        run.battle.processRequests();
        double maxHp = run.him.getAttribute(AttributeType.HEALTH).get();
        double before = run.him.getCurrentHp();

        run.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TAKING_HIT, run.enemy, run.him, 100, 0));
        run.battle.processRequests();

        Assertions.assertEquals(0.1 * maxHp, run.him.getCurrentHp() - before, 1e-6,
                "「受到来自敌方目标的攻击后回复等同于自身 10% 生命上限的生命值」");
    }

    /**
     * Eidolon 6: he starts the battle inside 【血仇】, with that state's own +50% maximum HP.
     */
    @Test
    public void theSixthEidolonEntersBloodfeudAtBattleStart() {
        Run e5 = new Run(5);
        Run e6 = new Run(6);
        Assertions.assertFalse(e5.him.getBuffManager().hasState(STATE), "E5 enters it the normal way: at 100 charge");
        Assertions.assertTrue(e6.him.getBuffManager().hasState(STATE), "「进入战斗时立刻进入【血仇】状态」");
        Assertions.assertEquals(1.5 * e5.him.getAttribute(AttributeType.HEALTH).get(),
                e6.him.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "the state carries its own 「生命上限 +50%」, so entering it at battle start includes it");
    }

    // ==================================================================

    private static final class Run {
        private final Character him;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        Run(int eidolon) {
            him = CharacterFactory.create(MYDEI, LEVEL);
            him.setEidolonRank(eidolon);
            // A fixed crit roll would make the numbers comparable; the seed alone is not enough because the two ranks
            // take different paths through the rules.
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(List.of(him, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
        }

        /** Puts him in 【血仇】 the way the talent does: 100 points of charge and the change announced. */
        void enterBloodfeud() {
            him.getResources().gain(CHARGE, 100);
            battle.fire(TriggerPayload.ResourceChanged.of(TriggerEvent.RESOURCE_CHANGED, him, CHARGE, 100));
            battle.processRequests();
        }
    }

    /** The Skill's damage against a fresh dummy, inside 【血仇】. */
    private static double damageInBloodfeud(int eidolon) {
        Run run = new Run(eidolon);
        run.enterBloodfeud();
        double before = run.enemy.getCurrentHp();
        run.battle.castImmediate(new DefaultSkill(MYDEI, 2, LEVEL), run.him, List.of(run.enemy));
        run.battle.processRequests();
        return before - run.enemy.getCurrentHp();
    }
}
