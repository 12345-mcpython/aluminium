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
 * 1218 Jiaoqiu's ultimate: 「处于结界中时，敌方目标受到的终结技伤害提高 15.00%…结界持续 3 回合」.
 *
 * <p>Judged twice and both bind the number: the compiled rule (scope, share, turns) and the measured ratio between ultimate and normal damage.
 */
public class JiaoqiuUltTakenTest {
    private static final int WEARER = 1218;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleStatesTheUltimateScopeShareAndDuration() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double share = -1;
        String scope = null;
        int turns = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    share = effect.getPercent();
                    scope = effect.getDamageType();
                    turns = effect.getTurns();
                    System.out.println("[1218] rule id=" + rule.id() + " damageType=" + scope + " percent=" + share
                            + " turns=" + turns);
                }
            }
        }
        Assertions.assertEquals("ULTRA", scope, "scoped to ultimate damage");
        Assertions.assertEquals(0.15, share, 1e-9, "the clause states 15%");
        Assertions.assertEquals(3, turns, "and the domain lasts three turns");
    }

    @Test
    public void ultimateDamageIsAmplifiedAndNormalIsNot() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        double ultra = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.ULTRA, 1000));
        System.out.println("[1218] normal=" + normal + " ultra=" + ultra + " ratio=" + (ultra / normal));
        Assertions.assertEquals(1.15, ultra / normal, 1e-6, "ultimate damage rises by exactly the authored 15%");
    }
}
