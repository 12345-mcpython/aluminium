package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1005 Kafka's talent: on a follow-up, the target takes 30% more DoT damage for two turns.
 *
 * <p>Two assertions that a magnitude or scope mutation cannot survive: the compiled rule's own scope and share, and the measured ratio between DoT and normal damage.
 */
public class KafkaDotTakenTest {
    private static final int WEARER = 1005;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleStatesTheDotScopeAndShare() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.FOLLOW_UP,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double share = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    share = effect.getPercent();
                    System.out.println("[1005] rule id=" + rule.id() + " damageType=" + effect.getDamageType()
                            + " percent=" + share + " turns=" + effect.getTurns());
                    Assertions.assertEquals("DOT", effect.getDamageType(), "the scope is the DoT type");
                }
            }
        }
        Assertions.assertEquals(0.3, share, 1e-9, "the talent states 30%");
    }

    @Test
    public void dotDamageIsAmplifiedByExactlyThirtyPercent() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        battle.fireTriggers(TriggerEvent.FOLLOW_UP, unit, enemy, 0, 0);
        double dot = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.DOT, 1000));
        System.out.println("[1005] normal=" + normal + " dot=" + dot + " ratio=" + (dot / normal));
        Assertions.assertEquals(1.3, dot / normal, 1e-6, "DoT damage rises by exactly the authored 30%");
    }
}
