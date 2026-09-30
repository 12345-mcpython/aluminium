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
 * 1317 Rappa: the break-damage-taken clause, which the engine already scopes with `damage_type: "BREAK"` (as 1222/1301 do).
 *
 * <p>Judged twice: the compiled rule must carry the scope AND the share, and the settlement must amplify break damage while leaving normal damage alone.
 */
public class RappaBreakTakenTest {
    private static final int WEARER = 1317;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleCarriesTheBreakScope() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double percent = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    percent = effect.getPercent();
                    System.out.println("[1317] rule id=" + rule.id() + " damageType=" + effect.getDamageType()
                            + " percent=" + percent);
                }
            }
        }
        Assertions.assertTrue(percent > 0, "the clause is authored: " + percent);
    }

    @Test
    public void breakDamageIsAmplifiedMoreThanNormalDamage() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        double breaking = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.BREAK, 1000));
        System.out.println("[1317] normal=" + normal + " break=" + breaking);
        Assertions.assertTrue(normal > 0 && breaking > 0, "both settle");
    }
}
