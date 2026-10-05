package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1222 Lingsha (灵砂)'s Ultimate "幔亭缭霞" (Pavilion in Rosy Clouds): the [醇醉] break-damage mark on every enemy, and the second party heal.
 *
 * <p>The heal is judged numerically -- 12% of her ATK plus 360, her own Lv10 row. The mark is judged by the CLASS the op attaches:
 * a `MODIFY_DAMAGE_TAKEN` carrying a `buff` name lands as a VulnerabilityBuff, not a StateBuff (`hasState` asks a different
 * registry and answers false), and `AbstractBuff` exposes no name getter, so the count is what can be observed. The NAME is what a
 * `REMOVE_STATE` by name matches, which the interpreter documents; noted rather than asserted.
 *
 * <p>1301's own test already pins what a break-damage increase does to a hit, so this one does not duplicate that measurement.
 */
public class LingshaUltimateTest {
    private static final int LINGSHA = 1222;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int OTHER_MONSTER = 1002012;
    private static final String STATE = "醇醉";

    @Test
    public void theUltimateHealsThePartyForHerOwnNumbers() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        ally.takeDamage(ally.getCurrentHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(enemy));
        double healed = ally.getCurrentHp() - before;
        double expected = 0.12 * lingsha.getAttribute(AttributeType.ATTACK).get() + 360;
        Assertions.assertTrue(healed > 0, "precondition: the ultimate healed the ally: " + healed);
        Assertions.assertEquals(expected, healed, Math.max(2.0, 0.02 * expected),
                "12% of her ATK (" + lingsha.getAttribute(AttributeType.ATTACK).get() + ") plus 360: " + healed);
    }

    @Test
    public void theUltimateMarksEveryEnemy() {
        Character lingsha = CharacterFactory.create(LINGSHA, LEVEL);
        Enemy first = EnemyFactory.create(MONSTER, 90, 1);
        Enemy second = EnemyFactory.create(OTHER_MONSTER, 90, 1);
        Battle battle = new Battle(List.of(lingsha), List.of(first, second), new Random(0));
        battle.startBattle();
        battle.castImmediate(lingsha.getSkills().get(SkillType.ULTRA), lingsha, List.of(first));
        for (Enemy enemy : List.of(first, second)) {
            Assertions.assertTrue(enemy.getBuffManager().countBuffs(VulnerabilityBuff.class) >= 1,
                    "the mark from 【" + STATE + "】 on " + enemy.getName());
        }
    }
}
