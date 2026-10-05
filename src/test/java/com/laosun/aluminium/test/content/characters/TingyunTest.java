package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1202 Tingyun, from her own file (2026-09-29, round 156): Benediction with its cap, the ultimate's grant, and three traces.
 *
 * <p>Against her own listener-first logic: none of these needed a new engine ability. What is registered instead is the pair of
 * sentences where the BLESSED ALLY, not Tingyun, is the one dealing the extra damage.
 */
public class TingyunTest {
    private static final int TINGYUN = 1202;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note: "使其攻击力提高50%，最高不超过停云当前攻击力的25%": the gain must respect the ceiling. */
    @Test
    public void herSkillBlessesWithACappedGain() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.battle.castImmediate(f.tingyun.getSkills().get(SkillType.SKILL), f.tingyun, List.of(f.ally));

        double after = f.ally.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(f.ally.getBuffManager().hasState("赐福"), "「为指定我方单体提供【赐福】」");
        Assertions.assertTrue(after > before, "「使其攻击力提高50%」: " + before + " -> " + after);
        double ceiling = f.tingyun.getAttribute(AttributeType.ATTACK).get() * 0.25;
        Assertions.assertTrue(after - before <= ceiling + 1e-6,
                "「最高不超过停云当前攻击力的25%」 -- gain " + (after - before) + " vs ceiling " + ceiling);
    }

    /** Note: The ultimate grants energy AND a two-turn damage boost to the chosen ally. */
    @Test
    public void herUltimateGrantsEnergyAndABoost() {
        Fixture f = new Fixture();
        double energyBefore = f.ally.getCurrentEnergy();
        double boostBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        f.battle.castImmediate(f.tingyun.getSkills().get(SkillType.ULTRA), f.tingyun, List.of(f.ally));

        Assertions.assertTrue(f.ally.getCurrentEnergy() > energyBefore,
                "「为指定我方单体恢复50点能量」: " + energyBefore + " -> " + f.ally.getCurrentEnergy());
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > boostBefore,
                "「同时使目标造成的伤害提高50%，持续2回合」");
    }

    /** Census: five rules of hers plus the level convention. */
    @Test
    public void herFileCarriesTheClauses() {
        var table = TriggerTables.of(TINGYUN);
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.SKILL_CAST), "the Benediction and the trace's speed");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ULT_CAST), "the grant and the boost");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.DEALING_DAMAGE), "the basic-attack bonus trace");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.TURN_START), "the energy trace");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "the level convention");
    }

    private static final class Fixture {
        private final Character tingyun = CharacterFactory.create(TINGYUN, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(tingyun, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
