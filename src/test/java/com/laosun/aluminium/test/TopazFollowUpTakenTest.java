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
 * 1112 Topaz, the skill's [负债证明]: "使其受到的追加攻击伤害提高 50%".
 *
 * <p>The engine's own follow-up path is {@code Battle.applyAdditionalDamage} (its comment calls DamageType.ADDITIONAL "the engine's one and only notion of a
 * follow-up attack"), so the two readings differ in exactly one thing: the damage type. Everything else in the fixture is identical.
 */
public class TopazFollowUpTakenTest {
    private static final int WEARER = 1112;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theRuleStatesTheFollowUpScopeAndShare() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        String scope = null;
        double share = -1;
        for (var rule : unit.getTriggerTable().matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scope = effect.getDamageType();
                    share = effect.getPercent();
                    System.out.println("[1112] rule id=" + rule.id() + " damageType=" + scope + " percent=" + share
                            + " turns=" + effect.getTurns());
                }
            }
        }
        Assertions.assertEquals("ADDITIONAL", scope, "the engine spells a follow-up attack ADDITIONAL");
        Assertions.assertEquals(0.5, share, 1e-9, "the clause states 50%");
    }

    @Test
    public void followUpDamageIsAmplifiedAndPlainDamageIsNot() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.SKILL_CAST, unit, enemy, 0, 0);
        double plain = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(unit, enemy, DamageElement.FIRE, 1000, null, null);
        System.out.println("[1112] plain=" + plain + " followUp=" + followUp + " ratio=" + (followUp / plain));
        Assertions.assertEquals(1.5, followUp / plain, 1e-6, "a follow-up instance rises by the authored 50%");
    }
}
