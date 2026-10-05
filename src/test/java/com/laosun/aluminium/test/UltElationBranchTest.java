package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
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
 * 8009/8010's ultimate, the branch that needs an Elation skill on the target (2026-10-02):
 * "Gains 5 Punchline point(s) ... if the target has an Elation skill, the target additionally gains 10 points of
 * Bondmate-of-Appreciation, and immediately uses one Elation skill".
 *
 * <p>The caster is 8009 and the target is 1505 -- one of the nine Elation-skill holders (data slot 20), and its own file
 * declares Bondmate-of-Appreciation, so every half of the sentence has somewhere to land.
 *
 * <p>Note: The ultimate deals NO damage of its own (its first parameter is the crit-damage share it grants), so the enemy's HP
 * loss below is the commanded Elation cast and nothing else -- which is also what the mutation removes.
 */
public class UltElationBranchTest {
    private static final int CASTER = 8009;
    private static final int TARGET = 1505;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "笑点";
    private static final String GIFT = "好活当赏";

    /** "Gains 5 Punchline point(s)" -- onto the SHARED party counter. */
    @Test
    public void theUltimateGivesFiveLaughs() {
        Scene scene = fight();
        int before = scene.battle.partyResourceValue(LAUGH);
        cast(scene);
        Assertions.assertEquals(5, scene.battle.partyResourceValue(LAUGH) - before,
                "the ultimate grants five laughs to the shared counter");
    }

    /** "the target additionally gains 10 points" -- on the TARGET, not on the caster. */
    @Test
    public void theTargetGainsTenGift() {
        Scene scene = fight();
        int targetBefore = scene.target.getResources().value(GIFT);
        int casterBefore = scene.caster.getResources().value(GIFT);
        cast(scene);
        Assertions.assertEquals(10, scene.target.getResources().value(GIFT) - targetBefore,
                "the TARGET gains ten -- read as a delta, because her own technique already granted twenty");
        Assertions.assertEquals(casterBefore, scene.caster.getResources().value(GIFT),
                "and not the caster's own copy");
    }

    /** "and immediately uses one Elation skill" -- the commanded cast really lands on the enemy. */
    @Test
    public void theCommandedElationCastLands() {
        Scene scene = fight();
        double before = scene.enemy.getCurrentHp();
        cast(scene);
        double lost = before - scene.enemy.getCurrentHp();
        System.out.println("[8009] the commanded Elation cast took " + lost + " HP from the enemy");
        Assertions.assertTrue(lost > 0, "the commanded Elation cast has to reach the enemy");
    }

    // ==================================================================

    private static final class Scene {
        final Battle battle;
        final Character caster;
        final Character target;
        final Enemy enemy;

        Scene(Battle battle, Character caster, Character target, Enemy enemy) {
            this.battle = battle;
            this.caster = caster;
            this.target = target;
            this.enemy = enemy;
        }
    }

    private static Scene fight() {
        Character caster = CharacterFactory.create(CASTER, LEVEL, false, null, null, 0);
        Character target = CharacterFactory.create(TARGET, LEVEL, false, null, null, 0);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(caster, target), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return new Scene(battle, caster, target, enemy);
    }

    private static void cast(Scene scene) {
        scene.battle.castImmediate(scene.caster.getSkills().get(SkillType.ULTRA), scene.caster,
                List.of(scene.target));
        scene.battle.processRequests();
    }
}
