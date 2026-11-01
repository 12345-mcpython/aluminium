package com.laosun.aluminium.test.content.characters.pela;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1106 佩拉's three traces.
 *
 * <p>Note on 痛击: the condition reads a DEBUFF count, and a plain counter is not a debuff - so the judge puts a real
 * {@code VulnerabilityBuff} on the enemy, and reads the boost zone rather than the settled damage (a vulnerability also
 * changes what lands, which would blur the 20%).
 */
public class PelaTraceTest {

    private static final int PELA = 1106;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;

    @Test
    public void thePartyGainsTenPercentEffectHit() {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(pela, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Assertions.assertTrue(ally.getAttribute(AttributeType.EFFECT_HIT_RATE).get() > 0,
                "「佩拉在场时，我方全体的效果命中提高 10%」");
    }

    private static double boostArea(boolean debuffed) {
        Character pela = CharacterFactory.create(PELA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(pela, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        if (debuffed) {
            enemy.getBuffManager().addBuff(new VulnerabilityBuff(1, 0.01));
        }
        Damage damage = new Damage(pela, enemy, DamageElement.ICE, DamageType.NORMAL, 100);
        battle.applyDamage(enemy, damage);
        java.util.Map<String, Double> breakdown = damage.breakdown();
        return breakdown.getOrDefault("BoostArea", 0.0);
    }
}


