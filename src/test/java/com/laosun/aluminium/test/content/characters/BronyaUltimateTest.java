package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/** 1101 Bronya (布洛妮娅)'s ultimate: an all-allies ATK share and a DERIVED CRIT DMG amount, both judged on a teammate. */
public class BronyaUltimateTest {
    private static final int BRONYA = 1101;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theUltimateRaisesThePartysAttackAndDerivedCritDamage() {
        Character bronya = CharacterFactory.create(BRONYA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(bronya, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        double attackBefore = ally.getAttribute(AttributeType.ATTACK).get();
        double critBefore = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        double herCritDamage = bronya.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.castImmediate(bronya.getSkills().get(SkillType.ULTRA), bronya, List.of(enemy));
        double attackAfter = ally.getAttribute(AttributeType.ATTACK).get();
        double critAfter = ally.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertEquals(0.55 * attackBefore, attackAfter - attackBefore, Math.max(0.5, 0.02 * attackBefore),
                "55% of the ally's base ATK: " + attackBefore + " -> " + attackAfter);
        Assertions.assertEquals(0.16 * herCritDamage + 0.20, critAfter - critBefore, 1e-6,
                "16% of her CRIT DMG (" + herCritDamage + ") plus 20%: " + critBefore + " -> " + critAfter);
    }
}
