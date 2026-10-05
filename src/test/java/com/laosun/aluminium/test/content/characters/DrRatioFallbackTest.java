package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerInterpreter;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1305 Dr. Ratio: "若追加攻击施放前目标被消灭则对敌方随机单体发动" -- the fallback selector.
 *
 * <p><b>Two enemies.</b> With one, `target_else_random_enemy` and the plain `target` resolve to the same
 * unit, so the judge could not see the difference. <b>A is defeated first</b> via CanHit.perish(), which
 * fires no events and leaves the unit in Battle.enemies -- the window the clause is about.
 */
public class DrRatioFallbackTest {
    private static final int RATIO = 1305;
    private static final int LEVEL = 80;

    /** Returns how much HP each enemy lost when the follow-up fires with A (dead) as its target. */
    private static double[] lossesWithDeadTarget() {
        Character ratio = CharacterFactory.create(RATIO, LEVEL);
        Enemy a = EnemyFactory.create(1002011, 90, 1);
        Enemy b = EnemyFactory.create(1002011, 90, 2);
        Battle battle = new Battle(List.of(ratio), List.of(a, b), new Random(0));
        battle.startBattle();
        a.perish();
        Assertions.assertTrue(a.isDeath(), "the fixture must leave A defeated");
        Assertions.assertTrue(battle.enemies.contains(a),
                "isDeath() alone must not remove it -- that is the window the clause is about");
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(ratio, ratio, a, 1, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(RATIO).rulesFor(TriggerEvent.ALLY_ATTACK).stream()
                .filter(r -> "talent_chance_followup".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the follow-up rule must exist");
        double aBefore = a.getCurrentHp();
        double bBefore = b.getCurrentHp();
        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);
        return new double[]{aBefore - a.getCurrentHp(), bBefore - b.getCurrentHp()};
    }

    @Test
    public void aDeadTargetFallsBackToAnotherEnemy() {
        double[] loss = lossesWithDeadTarget();
        Assertions.assertEquals(0.0, loss[0], 1e-9,
                "the defeated preferred target must not be hit -- CanHit refuses damage on a dead unit");
        Assertions.assertTrue(loss[1] > 0,
                "with the preferred target dead the fallback must reach another enemy, got " + loss[1]);
        System.out.println("[1305] fallback ok: deadTargetLoss=" + loss[0] + " otherLoss=" + loss[1]);
    }
}
