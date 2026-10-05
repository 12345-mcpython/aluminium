package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `DEALING_DAMAGE` carries the skill key (2026-09-28, round 121), verified through the shipped clause it was built for.
 *
 * <p>Gallagher's enhanced basic attack says "并使目标攻击力降低15.00%，持续2回合". That sentence cannot live on
 * `ALLY_ATTACK` - that event deliberately carries no aim - so it needed an event with <b>both</b> a target and the skill key.
 * `DEALING_DAMAGE` has the target, and this round taught its instance to carry the key.
 *
 * <p>Note: Both preconditions are asserted first (round 109's lesson), and the deeper one too (round 113's): the swapped skill
 * must actually LOAD, otherwise the attack does nothing at all and a failure below would be blamed on the wrong thing.
 */
public class GallagherEnhancedAttackTest {
    private static final int GALLAGHER = 1301;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theEnhancedAttackLowersTheTargetsAttack() {
        Fixture f = new Fixture();
        f.battle.castImmediate(f.gallagher.getSkills().get(SkillType.ULTRA), f.gallagher, List.of(f.enemy));

        var enhanced = f.gallagher.getSkills().get(SkillType.COMMON);
        Assertions.assertEquals(SkillCategory.NORMAL, enhanced.getData().getCategory(),
                "⚠ deep precondition: the swapped skill LOADS (a data-row id would have been EMPTY)");
        Assertions.assertTrue(enhanced.getSkillSlot() == 8, "the enhanced row lives under loader key 8");

        double before = f.enemy.getAttribute(AttributeType.ATTACK).get();
        f.battle.castImmediate(enhanced, f.gallagher, List.of(f.enemy));
        f.battle.fireAfterAttack(f.gallagher, f.enemy, List.of(f.enemy), 1.0);

        Assertions.assertTrue(f.enemy.getAttribute(AttributeType.ATTACK).get() < before,
                "「并使目标攻击力降低 15.00%，持续 2 回合」 — an ordinary basic attack would not have done this");
    }

    /** Note: The negative control: the ordinary basic attack must NOT lower anything. */
    @Test
    public void theOrdinaryBasicAttackDoesNothing() {
        Fixture f = new Fixture();
        double before = f.enemy.getAttribute(AttributeType.ATTACK).get();

        f.battle.castImmediate(f.gallagher.getSkills().get(SkillType.COMMON), f.gallagher, List.of(f.enemy));
        f.battle.fireAfterAttack(f.gallagher, f.enemy, List.of(f.enemy), 1.0);

        Assertions.assertEquals(before, f.enemy.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "row 130101 is not the enhanced one, so `from_skill_id == 8` must not match it");
    }

    private static final class Fixture {
        private final Character gallagher = CharacterFactory.create(GALLAGHER, LEVEL);
        private final Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        private final Battle battle = new Battle(List.of(gallagher), List.of(enemy), new Random() {
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
