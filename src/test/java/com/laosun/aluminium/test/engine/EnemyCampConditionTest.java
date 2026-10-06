package com.laosun.aluminium.test.engine;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/** F-5: the camp vocabulary now has both sides -- `actor is_enemy` is the mirror of `actor is_ally`. */
public class EnemyCampConditionTest {
    private static final int HERO = 1204;

    @Test
    public void theEnemySideIsAskableAndTheAllySideIsNotIt() {
        Enemy enemy = EnemyFactory.create(1002011, 100, 1);
        Battle battle = new Battle(List.of(CharacterFactory.create(HERO, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character hero = battle.characters.getFirst();

        // Told apart by the attribute each grants: the `is_enemy` rule grants ATTACK, the `is_ally` rule grants
        // CRIT_ATTACK. An enemy attack must satisfy the first and not the second.
        hero.setTriggerTable(new TriggerTable(HERO, List.of(
                TriggerSpecs.rule("ENEMY_ATTACK", List.of("actor is_enemy"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 1)),
                TriggerSpecs.rule("ENEMY_ATTACK", List.of("actor is_ally"), TriggerSpecs.modifyAttr("CRIT_ATTACK", 0.5, 1)))));

        double attackBefore = hero.getAttribute(AttributeType.ATTACK).get();
        double critBefore = hero.getAttribute(AttributeType.CRIT_ATTACK).get();

        new EnemySkill(DamageElement.PHYSICAL, 1.0, 1, DamageType.NORMAL).execute(battle, enemy, List.of(hero));

        double attackAfter = hero.getAttribute(AttributeType.ATTACK).get();
        double critAfter = hero.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[is_enemy] ATTACK " + attackBefore + " -> " + attackAfter
                + " ; CRIT_ATTACK " + critBefore + " -> " + critAfter);
        Assertions.assertTrue(attackAfter > attackBefore,
                "`actor is_enemy` matched the enemy that attacked (ATK " + attackBefore + " -> " + attackAfter + ")");
        Assertions.assertEquals(critBefore, critAfter, 1e-9,
                "and `actor is_ally` did not: the two sides are now distinguishable");
    }
}
