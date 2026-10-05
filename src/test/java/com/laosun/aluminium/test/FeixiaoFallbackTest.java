package com.laosun.aluminium.test;

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
 * 1220 Feixiao: "若不存在可攻击的主目标，则攻击敌方随机单体" -- her follow-up fallback.
 *
 * <p>Two enemies, because with one the fallback and the preference pick the same unit. A is defeated first
 * with CanHit.perish(), which fires no events and leaves it in Battle.enemies.
 */
public class FeixiaoFallbackTest {
    private static final int FEIXIAO = 1220;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    private static double[] lossesWithDeadTarget() {
        Character feixiao = CharacterFactory.create(FEIXIAO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy a = EnemyFactory.create(1002011, 90, 1);
        Enemy b = EnemyFactory.create(1002011, 90, 2);
        Battle battle = new Battle(List.of(feixiao, ally), List.of(a, b), new Random(0));
        battle.startBattle();
        a.perish();
        Assertions.assertTrue(a.isDeath() && battle.enemies.contains(a),
                "the fixture must leave A defeated but still in the roster");
        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(feixiao, ally, a, 1, 0, null, battle,
                SkillCategory.UNSPECIFIED);
        var rules = TriggerTables.of(FEIXIAO).rulesFor(TriggerEvent.ALLY_ATTACK).stream()
                .filter(r -> "talent_followup_on_teammate_attack".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the follow-up rule must exist");
        double aBefore = a.getCurrentHp();
        double bBefore = b.getCurrentHp();
        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);
        return new double[]{aBefore - a.getCurrentHp(), bBefore - b.getCurrentHp()};
    }

    @Test
    public void aDeadMainTargetFallsBackToARandomEnemy() {
        double[] loss = lossesWithDeadTarget();
        Assertions.assertEquals(0.0, loss[0], 1e-9, "the defeated main target must not be hit");
        Assertions.assertTrue(loss[1] > 0,
                "the fallback must reach another enemy, got " + loss[1]);
        System.out.println("[1220] fallback ok: deadTargetLoss=" + loss[0] + " otherLoss=" + loss[1]);
    }
}
