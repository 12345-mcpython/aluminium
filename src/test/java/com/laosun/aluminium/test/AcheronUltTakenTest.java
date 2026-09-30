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
 * 1308 Acheron's talent: enemies enter battle with 「受到的终结技伤害提高 8%」, which DamageType.ULTRA scopes.
 *
 * <p>Judged twice, and both bind the number: the compiled rule's scope and share, and the measured ratio between ultimate and normal damage.
 */
public class AcheronUltTakenTest {
    private static final int WEARER = 1308;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleStatesTheUltimateScopeAndShare() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        var rules = unit.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double share = -1;
        String scope = null;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    share = effect.getPercent();
                    scope = effect.getDamageType();
                    System.out.println("[1308] rule id=" + rule.id() + " damageType=" + scope + " percent=" + share);
                }
            }
        }
        Assertions.assertEquals("ULTRA", scope, "scoped to ultimate damage");
        Assertions.assertEquals(0.08, share, 1e-9, "the talent states 8%");
    }

    @Test
    public void ultimateDamageIsAmplifiedAndNormalIsNot() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.NORMAL, 1000));
        double ultra = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.THUNDER, DamageType.ULTRA, 1000));
        System.out.println("[1308] normal=" + normal + " ultra=" + ultra + " ratio=" + (ultra / normal));
        Assertions.assertEquals(1.08, ultra / normal, 1e-6, "ultimate damage rises by exactly the authored 8%");
    }
}
