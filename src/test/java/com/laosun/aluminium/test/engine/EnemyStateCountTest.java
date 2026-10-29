package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code enemy_state_count:<STATE>} - the whole-field count added for 1206 Sushang's trace 破敌.
 *
 * <p>⚠ Why this guard is driven through a character rather than by reading the variable: the variable only
 * exists inside a condition, and the corpus already has engine-level tests that use a shipped trace as the
 * carrier of a mechanism ({@code LowHpAggroTraceTest} guards 1206's 赤子 the same way). The rule being
 * exercised is real content, so this is close to the shape a future reader would copy.
 *
 * <p>⚠ What makes the assertion <b>about a count</b> rather than about a boolean: the variable skips the
 * <b>dead</b>. A broken corpse is still "an enemy in that state" to a {@code has_state} question, so an
 * implementation that merely asked "does any enemy have the state" would still pass a living-enemy case and
 * fail the dead one. That difference is the whole reason the spelling is a count and not a predicate.
 */
public class EnemyStateCountTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;

    /**
     * No broken enemy: her action is NOT advanced, because the condition is false.
     *
     * <p>⚠ The observable is her remaining wait on the action bar. Nothing else in her file can move it at a
     * Skill cast, so a change here can only be trace 破敌.
     */
    @Test
    public void withNoBrokenEnemyTheActionIsNotAdvanced() {
        Fixture f = new Fixture();
        Assertions.assertFalse(f.other.isBroken(), "precondition: the second enemy is healthy");
        double before = remainingWait(f, f.sushang);

        f.castSkill();

        Assertions.assertEquals(before, remainingWait(f, f.sushang), 1.0,
                "「若场上有敌方目标处于弱点击破状态」 - with none broken, nothing is advanced");
    }

    /**
     * ⭐ One broken enemy on the field is enough: the count is a whole-field question, and the broken one is
     * not even the unit she attacked.
     */
    @Test
    public void oneBrokenEnemyAnywhereOnTheFieldAdvancesHer() {
        Fixture f = new Fixture();
        // The OTHER enemy, not her target: "场上有" is about the field, not about what her Skill hit.
        f.other.breakEnemy(DamageElement.PHYSICAL);
        Assertions.assertTrue(f.other.isBroken(), "precondition: a broken enemy is on the field");
        double before = remainingWait(f, f.sushang);

        f.castSkill();

        Assertions.assertTrue(remainingWait(f, f.sushang) < before,
                "「若场上有敌方目标处于弱点击破状态，则素裳的行动提前 15%」: "
                        + before + " -> " + remainingWait(f, f.sushang));
    }

    /**
     * ⭐ A broken but DEAD enemy does not count - the half of the spelling that a {@code has_state}-style
     * predicate could not express.
     */
    @Test
    public void aBrokenCorpseDoesNotCount() {
        Fixture f = new Fixture();
        f.other.breakEnemy(DamageElement.PHYSICAL);
        Assertions.assertTrue(f.other.isBroken(), "precondition: it is broken");
        f.other.takeDamage(f.other.getCurrentHp() + 1);
        Assertions.assertTrue(f.other.isDeath(), "precondition: and it is dead");
        double before = remainingWait(f, f.sushang);

        f.castSkill();

        Assertions.assertEquals(before, remainingWait(f, f.sushang), 1.0,
                "\"on the field\" excludes the dead: a broken corpse must not satisfy the count");
    }

    /**
     * The same for the other cast slot the clause names, so "普攻**或**战技" is guarded on both sides.
     */
    @Test
    public void theBasicAttackHalfWorksToo() {
        Fixture f = new Fixture();
        f.other.breakEnemy(DamageElement.PHYSICAL);
        double before = remainingWait(f, f.sushang);

        f.castBasic();

        Assertions.assertTrue(remainingWait(f, f.sushang) < before,
                "「施放普攻…后」 must advance her as well: " + before + " -> " + remainingWait(f, f.sushang));
    }

    private static double remainingWait(Fixture f, Character unit) {
        return f.battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .findFirst()
                .map(f.battle.queue()::getTimeRemaining)
                .orElseThrow(() -> new AssertionError("no signal for " + unit.getName()));
    }

    private static final class Fixture {
        private final Character sushang = CharacterFactory.create(SUSHANG, LEVEL);
        private final Enemy target = EnemyFactory.create(1002011, 90, 1);
        private final Enemy other = EnemyFactory.create(1002011, 90, 1);
        private final Battle battle = new Battle(List.of(sushang), List.of(target, other),
                BattleFixture.alwaysRoll(0.0));

        private Fixture() {
            battle.startBattle();
        }

        private void castSkill() {
            battle.castImmediate(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(target));
        }

        private void castBasic() {
            battle.castImmediate(sushang.getSkills().get(SkillType.COMMON), sushang, List.of(target));
        }
    }
}
