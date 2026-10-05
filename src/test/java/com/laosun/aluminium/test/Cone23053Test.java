package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23053: every skill point the wearer SPENDS makes its elation damage ignore 5% more defence, up to 4 layers.
 *
 * <p>`SKILL_POINT_SPENT` is already a trigger event (fired by the policy's own listener, so a spend at the cap -- which
 * credits nothing -- does not fire it), and the instance route already supports DEFENCE_IGNORE with per_stack. The judge
 * therefore spends for real and re-reads the settled damage, and it also checks that a NON-elation hit is untouched.
 */
public class Cone23053Test {
    private static final int CONE = 23053;
    private static final int WEARER = 1205;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CAP = 4;
    private static final double PER_POINT = 0.05;

    private Character wearer;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230531));
        return battle;
    }

    private double hit(Battle battle, DamageType type) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, type, 1000));
    }

    /**
     * Spend through a REAL Skill cast: the camp check lives in the policy ({@code Battle.applySkillPointCost}'s doc says
     * so), and a bare {@code spendSkillPoint()} therefore refuses when no actor is set up -- measured.
     */
    private void spend(Battle battle, int times) {
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == com.laosun.aluminium.enums.SkillCategory.BPSKILL)
                .findFirst().orElseThrow(() -> new AssertionError("the wearer has no Skill to cast"));
        for (int i = 0; i < times; i++) {
            battle.gainSkillPoint(1);   // keep the pool from capping out: a spend at the cap credits nothing
            Assertions.assertTrue(battle.applySkillPointCost(skill, wearer),
                    "the pool must have room to spend (point " + (i + 1) + ")");
        }
    }

    @Test
    public void eachSpentPointLowersTheEnemysDefenceForElationDamage() {
        Battle battle = battle(true);
        double none = hit(battle, DamageType.ELATION);
        spend(battle, 1);
        double one = hit(battle, DamageType.ELATION);
        spend(battle, 3);
        double four = hit(battle, DamageType.ELATION);
        spend(battle, 2);
        double capped = hit(battle, DamageType.ELATION);
        System.out.println("[23053] none=" + none + " one=" + one + " four=" + four + " after six=" + capped
                + " layers=" + wearer.getBuffManager().stacksOf("消耗层数"));
        Assertions.assertTrue(one > none, "one spent point must raise the settled elation damage");
        // The VALUE, not just the direction (discipline 189): the settled damage is `base / (effDef + 200 + 10L)`, and
        // the ignore multiplies effDef. Everything here comes from the engine's own numbers, so a wrong `percent` has to
        // move this line -- a monotonicity-only judge let `5% -> 2.5%` pass (measured, 0 red).
        double defence = enemy.getAttribute(AttributeType.DEFENCE).get();
        double level = wearer.getLevel();
        double zoneNone = 1.0 / (defence + 200 + 10 * level);
        double zoneFour = 1.0 / (defence * (1 - 4 * PER_POINT) + 200 + 10 * level);
        double expectedFour = none * zoneFour / zoneNone;
        System.out.println("[23053] defence=" + defence + " level=" + level + " expectedFour=" + expectedFour
                + " measured=" + four);
        if (Math.abs(expectedFour - none) > 1e-9) {
            Assertions.assertEquals(expectedFour, four, Math.abs(expectedFour) * 1e-6,
                    "four layers must ignore exactly " + (4 * PER_POINT) + " of the defence");
        }
        Assertions.assertTrue(four > one, "and four must raise it further (" + PER_POINT + " per layer)");
        Assertions.assertEquals(four, capped, 1e-9, "but the clause caps at " + CAP + " layers");
        Assertions.assertEquals(CAP, wearer.getBuffManager().stacksOf("消耗层数"), "counter at the cap");
    }

    @Test
    public void aNonElationHitIsUntouched() {
        Battle battle = battle(true);
        double before = hit(battle, DamageType.NORMAL);
        spend(battle, 4);
        double after = hit(battle, DamageType.NORMAL);
        System.out.println("[23053] ordinary damage before=" + before + " after four spends=" + after);
        Assertions.assertEquals(before, after, 1e-9, "the clause names ELATION damage only (false case)");
    }

    @Test
    public void withoutTheConeSpendingChangesNothing() {
        Battle battle = battle(false);
        double none = hit(battle, DamageType.ELATION);
        spend(battle, 4);
        System.out.println("[23053] without the cone: " + none + " -> " + hit(battle, DamageType.ELATION));
        Assertions.assertEquals(none, hit(battle, DamageType.ELATION), 1e-9, "no cone, no ignore (false case)");
    }
}
