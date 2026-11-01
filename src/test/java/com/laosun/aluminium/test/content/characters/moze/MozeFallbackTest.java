package com.laosun.aluminium.test.content.characters.moze;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerInterpreter;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1223 貊泽's ultimate: 「并对该目标发动天赋的追加攻击。**若本次追加攻击施放前目标被消灭则对敌方随机单体发动追加攻击**」.
 *
 * <p>Two enemies, and the preferred one is dead first: with a single enemy `target_else_random_enemy` and the plain
 * {@code target} resolve to the same unit, so the fixture must show the fallback. The dead unit stays on the roster
 * ({@code isDeath()} alone removes nobody) - that is the window the clause is about.
 */
public class MozeFallbackTest {

    private static final int MOZE = 1223;
    private static final int LEVEL = 80;

    @Test
    public void aDeadTargetFallsBackToAnotherEnemy() {
        Character moze = CharacterFactory.create(MOZE, LEVEL);
        Enemy a = EnemyFactory.create(1002011, 90, 1);
        Enemy b = EnemyFactory.create(1002011, 90, 2);
        Battle battle = new Battle(List.of(moze), List.of(a, b), BattleFixture.seeded());
        battle.startBattle();
        a.perish();
        Assertions.assertTrue(a.isDeath(), "the fixture must leave A defeated");
        Assertions.assertTrue(battle.enemies().contains(a), "the defeated unit stays on the roster");

        var rules = TriggerTables.of(MOZE).rulesFor(TriggerEvent.ULT_CAST).stream()
                .filter(r -> "ult_triggers_talent_followup".equals(r.id())).toList();
        Assertions.assertEquals(1, rules.size(), "the ultimate follow-up rule must exist");

        TriggerTable.TriggerContext ctx = new TriggerTable.TriggerContext(moze, moze, a, 1, 0, null, battle,
                SkillCategory.ULTRA);
        double aBefore = a.getCurrentHp();
        double bBefore = b.getCurrentHp();
        TriggerInterpreter.apply(battle, rules.getFirst(), ctx);

        Assertions.assertEquals(0.0, aBefore - a.getCurrentHp(), 1e-9,
                "the defeated preferred target is not hit");
        Assertions.assertTrue(bBefore - b.getCurrentHp() > 0,
                "the fallback reaches another enemy, got " + (bBefore - b.getCurrentHp()));
    }
}
