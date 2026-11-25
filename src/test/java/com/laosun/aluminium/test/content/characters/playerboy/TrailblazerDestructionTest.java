package com.laosun.aluminium.test.content.characters.playerboy;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8001/8002 Trailblazer (Destruction): the ATK that stacks on Weakness Breaks, the trace's per-stack DEFENCE, and
 * Eidolon 4's crit-rate boost against a Weakness-Broken target.
 *
 * <p>These were three small files in the same package; one class keeps the character together without changing any
 * assertion.
 */
public class TrailblazerDestructionTest {
    private static final int TB = 8001;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String COUNTER_8001 = "char:8001/pinch_steal";
    private static final String COUNTER_8002 = "char:8002/pinch_steal";

    /**
     * Note: "每次击破敌方目标的弱点后，攻击力提高20%…最多叠加2层".
     */
    @Test
    public void hisTalentStacksAttackOnBreaksAndStopsAtTwo() {
        Character tb = CharacterFactory.create(TB, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();
        double before = tb.getAttribute(AttributeType.ATTACK).get();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, tb, enemy, 0, 0));
        double afterOne = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, tb, enemy, 0, 0));
        double afterTwo = tb.getAttribute(AttributeType.ATTACK).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, tb, enemy, 0, 0));
        double afterThree = tb.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertTrue(afterOne > before, "\"after each time an enemy target's Weakness is Broken, ATK is raised by 20%\" (「每次击破敌方目标的弱点后，攻击力提高20%」): " + before + " -> " + afterOne);
        Assertions.assertTrue(afterTwo > afterOne, "the second layer must add again");
        Assertions.assertEquals(afterTwo, afterThree, 1e-9,
                "「该效果最多叠加2层」 (this effect stacks at most 2 times) -- a third break must not add a third layer");
    }

    /**
     * Trailblazer - Destruction (开拓者(毁灭))'s trace Tenacity (坚韧): "each layer of the talent's effect also raises the Trailblazer's DEF by 10%" -- the counter scale again, on a counter the
     * character's own talent marks.
     */
    @Test
    public void eachLayerAddsTenPercentOfBaseDefence() {
        assertDelta(8001, COUNTER_8001, 1, 0.10);
        assertDelta(8001, COUNTER_8001, 2, 0.20);
        assertDelta(8002, COUNTER_8002, 2, 0.20);
    }

    /**
     * Trailblazer (Destruction) (开拓者 (毁灭))'s Eidolon 4: "击中处于弱点击破状态的敌方目标时，暴击率提高25%" -- a state-gated boost for that hit.
     */
    @Test
    public void theBoostIsStatedOnlyAgainstABrokenTarget() {
        for (int cid : new int[]{8001, 8002}) {
            // Note: A difference, not an absolute: an intact unit still reads the game's inherent +0.05 crit chance
            // (measured: 0.30 where the clause owns 0.25).
            Assertions.assertEquals(0.25, critChance(cid, true) - critChance(cid, false), EPS,
                    "cid " + cid + ": the clause's own 25%");
            Assertions.assertEquals(0.05, critChance(cid, false), EPS,
                    "cid " + cid + ": against an intact target only the inherent remains");
        }
    }

    private static void assertDelta(int cid, String counter, int layers, double shareOfBase) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(trailblazer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double base = trailblazer.getAttribute(AttributeType.DEFENCE).baseValue();
        double before = trailblazer.getAttribute(AttributeType.DEFENCE).get();
        for (int i = 0; i < layers; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, trailblazer, enemy, 1, 1000));
        }
        Assertions.assertEquals(layers, trailblazer.getBuffManager().stacksOf(counter),
                "precondition: the talent marked exactly " + layers + " layer(s)");
        double after = trailblazer.getAttribute(AttributeType.DEFENCE).get();
        Assertions.assertEquals(shareOfBase * base, after - before, EPS,
                "\"each layer of the talent's effect also raises the Trailblazer's DEF by 10%\" with " + layers + " layer(s)");
    }

    private static double critChance(int cid, boolean broken) {
        Character trailblazer = CharacterFactory.create(cid, LEVEL, true, null, null, 4);
        DamageElement element = trailblazer.getSkills().get(SkillType.COMMON).getData().getElement();
        // Note: A monster the element can actually break: breaking requires a weakness, and `reduceToughness` on a monster
        // that is not weak to the element leaves the weakness break state off entirely (measured: hasState false).
        Enemy enemy = null;
        for (int id = 1002010; id < 1002100 && enemy == null; id++) {
            try {
                Enemy candidate = EnemyFactory.create(id, 90, 1);
                if (candidate.isWeakTo(element)) {
                    enemy = candidate;
                }
            } catch (RuntimeException ignored) {
                // not in the data
            }
        }
        Assertions.assertNotNull(enemy, "no enemy weak to " + element + " in the probed range");
        Battle battle = new Battle(List.of(trailblazer), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (broken) {
            battle.reduceToughness(trailblazer, enemy, element, 999);
            Assertions.assertTrue(enemy.getBuffManager().hasState("break:weakness"),
                    "precondition: emptying the bar of a weak enemy puts it in Weakness Break (弱点击破)");
        }
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, trailblazer, enemy, 1, 1000));
        return trailblazer.getAttribute(AttributeType.CRIT_CHANCE).get()
                - trailblazer.getAttribute(AttributeType.CRIT_CHANCE).baseValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
