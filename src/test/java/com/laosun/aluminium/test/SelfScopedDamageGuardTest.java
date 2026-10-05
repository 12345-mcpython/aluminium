package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Three DEALING_DAMAGE rules must NOT reach a teammate's damage (round 245).
 *
 * <p>The engine fires every event at every ally's table, so a missing `actor == self` silently broadens the sentence to the whole party. Each case here measures the same
 * ally's damage with and without the owner in the party: the two must be identical.
 */
public class SelfScopedDamageGuardTest {
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The ally's damage against an enemy already below half HP, optionally with the owner in the party. */
    private double allyDamage(int owner, boolean withOwner) {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(withOwner ? List.of(CharacterFactory.create(owner, LEVEL), ally) : List.of(ally),
                List.of(enemy), new Random(0));
        battle.startBattle();
        // Drain the enemy below half HP first, so the "low HP" condition is satisfied for anybody's damage.
        while (enemy.getCurrentHp() / enemy.getMaxHp() > 0.4) {
            battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL,
                    enemy.getMaxHp() * 0.05));
        }
        return battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
    }

    @Test
    public void theAllyDoesNotInheritTheLowHpRule() {
        double alone = allyDamage(1003, false);
        double watched = allyDamage(1003, true);
        System.out.println("[guard] 1003 low-hp rule: alone=" + alone + " withOwner=" + watched);
        Assertions.assertEquals(alone, watched, 1e-6, "1003's own damage rule must not reach a teammate");
    }

    @Test
    public void theAllyDoesNotInheritTheUltimateMultiplier() {
        double alone = allyDamage(1220, false);
        double watched = allyDamage(1220, true);
        System.out.println("[guard] 1220 ultimate rule: alone=" + alone + " withOwner=" + watched);
        Assertions.assertEquals(alone, watched, 1e-6, "1220's ultimate multiplier must not reach a teammate");
    }

    /** The owner's own damage: the ultimate multiplier must apply to an ULTRA-category instance and to nothing else. */
    @Test
    public void onlyAnUltimateCategoryInstanceGetsTheMultiplier() {
        Character owner = CharacterFactory.create(1220, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();
        double ordinary = battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.WIND, DamageType.NORMAL, 1000));
        double ultimate = battle.applyDamage(enemy, new Damage(owner, enemy, DamageElement.WIND, DamageType.NORMAL, 1000,
                com.laosun.aluminium.enums.SkillCategory.ULTRA));
        System.out.println("[guard] 1220 own damage: ordinary=" + ordinary + " ultimateCategory=" + ultimate
                + " ratio=" + (ultimate / ordinary));
        // MEASURED first, then pinned: the multiplier is a flat +30% on the ULTRA-category instance only.
        Assertions.assertEquals(1.3, ultimate / ordinary, 1e-6,
                "only the ULTRA-category instance is multiplied, by exactly 30%");
    }

    /** An ALLY's own ULTRA-category instance, with and without the owner in the party. */
    private double allyUltimate(int owner, boolean withOwner) {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(withOwner ? List.of(CharacterFactory.create(owner, LEVEL), ally) : List.of(ally),
                List.of(enemy), new Random(0));
        battle.startBattle();
        return battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.WIND, DamageType.NORMAL, 1000,
                com.laosun.aluminium.enums.SkillCategory.ULTRA));
    }

    /**
     * ⚠ The case that makes 1220's `actor == self` observable: her rule ALSO requires `from_skill ULTRA`, so a teammate's
     * ordinary swing never reaches it -- only a teammate's ULTRA-category instance does. Measured: without this test, removing
     * her self-guard turned nothing red.
     */
    @Test
    public void theAllyDoesNotInheritTheUltimateMultiplierOnAnUltimateInstance() {
        double alone = allyUltimate(1220, false);
        double watched = allyUltimate(1220, true);
        System.out.println("[guard] 1220 ally ULTRA instance: alone=" + alone + " withOwner=" + watched);
        Assertions.assertEquals(alone, watched, 1e-6, "her ultimate multiplier must not reach the ally's ultimate");
    }
}
