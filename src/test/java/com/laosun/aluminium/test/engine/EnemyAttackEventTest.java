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

/** F-5: an enemy's attack raises ENEMY_ATTACK, and the event names the unit it hit. */
public class EnemyAttackEventTest {
    private static final int HIT = 1204;
    private static final int SPARED = 1205;

    @Test
    public void theEnemyAttackIsAnnouncedToTheUnitItHit() {
        Enemy enemy = EnemyFactory.create(1002011, 100, 1);
        Battle battle = new Battle(List.of(CharacterFactory.create(HIT, 80), CharacterFactory.create(SPARED, 80)),
                List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character hit = battle.characters.get(0);
        Character spared = battle.characters.get(1);

        // The same rule on both characters: "when an enemy attacks ME, my ATK rises". `target == self` is what
        // makes it "me" -- the event is delivered to every table, and its target is the victim.
        for (Character who : List.of(hit, spared)) {
            who.setTriggerTable(new TriggerTable(who.getCid(), List.of(
                    TriggerSpecs.rule("ENEMY_ATTACK", List.of("target == self"),
                            TriggerSpecs.modifyAttr("ATTACK", 0.5, 1)))));
        }
        double hitBefore = hit.getAttribute(AttributeType.ATTACK).get();
        double sparedBefore = spared.getAttribute(AttributeType.ATTACK).get();

        new EnemySkill(DamageElement.PHYSICAL, 1.0, 1, DamageType.NORMAL).execute(battle, enemy, List.of(hit));

        double hitAfter = hit.getAttribute(AttributeType.ATTACK).get();
        double sparedAfter = spared.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[enemy_attack] hit " + hitBefore + " -> " + hitAfter
                + " ; spared " + sparedBefore + " -> " + sparedAfter);
        Assertions.assertTrue(hitAfter > hitBefore,
                "the attacked unit reacts (ATK " + hitBefore + " -> " + hitAfter + ")");
        Assertions.assertEquals(sparedBefore, sparedAfter, 1e-9,
                "and the unit the enemy did not attack does not: the event carries the VICTIM as its target");
    }
}
