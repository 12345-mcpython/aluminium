package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The technique gate's first harvest (2026-09-29, round 179): Dan Heng's opening ATK, declared only when the technique was used.
 *
 * <p>A pair again: declaring the technique raises his ATK at battle start, and not declaring it leaves his ATK at its plain value.
 */
public class TechniqueHarvestTest {
    private static final int DANHENG = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ 「使用秘技后，下一次战斗开始时丹恒攻击力提高40%」. */
    @Test
    public void aDeclaredTechniqueRaisesHisAttackAtBattleStart() {
        Character plain = CharacterFactory.create(DANHENG, LEVEL);
        double untouched = plain.getAttribute(AttributeType.ATTACK).get();

        Character withTechnique = CharacterFactory.create(DANHENG, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(withTechnique), List.of(enemy), fixed());
        battle.markTechniqueUsed(withTechnique);
        battle.startBattle();

        double boosted = withTechnique.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(boosted > untouched,
                "「使用秘技后，下一次战斗开始时丹恒攻击力提高40%」: "
                        + untouched + " -> " + boosted);
        // Measured: the gain is 40% of the BASE attack (the engine's `add_percent` convention), not of the current total — 645.2712 -> 864.0072
        // is a gain of 218.736 = 0.4 x 546.84. `baseValue()` reads the base, so the claim is stated in the engine's own terms.
        double base = withTechnique.getAttribute(AttributeType.ATTACK).baseValue();
        double expectedGain = base * 0.4;
        Assertions.assertEquals(expectedGain, boosted - untouched, expectedGain * 0.02,
                "「攻击力提高40%」 of the BASE: base " + base + " -> expected gain " + expectedGain
                        + ", actual gain " + (boosted - untouched));
    }

    /** ⚠ The control: without the marker his ATK is untouched at battle start. */
    @Test
    public void withoutTheTechniqueHisAttackIsUntouched() {
        Character plain = CharacterFactory.create(DANHENG, LEVEL);
        double before = plain.getAttribute(AttributeType.ATTACK).get();
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(plain), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(before, plain.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "「使用秘技后」 -- without the technique nothing happens");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
