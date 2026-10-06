package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23053, its [推流] clause: spending 4 skill points WITHIN ONE TURN grants the state, and it lifts the whole party's
 * elation damage by 20%.
 *
 * <p>"同一回合内" is the new {@code until: turn_end} lifetime, swept at the carrier's own turn end. The
 * discriminating reading spreads the same four spends over TWO turns: three, then a turn boundary, then one must NOT pay out.
 */
public class DazzledByAFloweryWorldPushTest {
    private static final int CONE = 23053;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int THRESHOLD = 4;
    private static final double BURST = 0.2;
    private static final String SPENT = "本回合消耗";
    private static final String PUSH = "推流";

    private Character wearer;
    private Character ally;
    private Enemy enemy;

    private Battle battle(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private void spend(Battle battle, int times) {
        var skill = wearer.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        for (int i = 0; i < times; i++) {
            battle.gainSkillPoint(1);
            Assertions.assertTrue(battle.applySkillPointCost(skill, wearer), "room to spend");
        }
    }

    private void endTurn() {
        wearer.getBuffManager().afterMove();
    }

    @Test
    public void fourSpendsInsideOneTurnGrantPush() {
        Battle battle = battle(true);
        double allyBase = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        spend(battle, 4);
        double allyDelta = ally.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get() - allyBase;
        System.out.println("[23053push] after four spends in one turn: push=" + wearer.getBuffManager().hasState(PUSH)
                + " party elation +" + allyDelta + " counter=" + wearer.getBuffManager().stacksOf(SPENT));
        Assertions.assertTrue(wearer.getBuffManager().hasState(PUSH), "four spends in one turn grant 【推流】 (Stream Promo)");
        Assertions.assertEquals(BURST, allyDelta, 1e-9, "and the whole party gains 20% elation damage");
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(SPENT), "the counter is cleared by the payout");
    }

    @Test
    public void theSameFourSpendsAcrossTwoTurnsDoNot() {
        Battle battle = battle(true);
        spend(battle, 3);
        endTurn();
        int afterBoundary = wearer.getBuffManager().stacksOf(SPENT);
        spend(battle, 1);
        System.out.println("[23053push] three, then a turn boundary (counter=" + afterBoundary + "), then one: push="
                + wearer.getBuffManager().hasState(PUSH));
        Assertions.assertEquals(0, afterBoundary, "the turn boundary clears the counter -- within the same turn (同一回合内)");
        Assertions.assertFalse(wearer.getBuffManager().hasState(PUSH),
                "so the fourth spend in ANOTHER turn must not pay out (false case)");
    }

    @Test
    public void withoutTheConeSpendingGrantsNothing() {
        Battle battle = battle(false);
        spend(battle, 4);
        System.out.println("[23053push] without the cone: push=" + wearer.getBuffManager().hasState(PUSH));
        Assertions.assertFalse(wearer.getBuffManager().hasState(PUSH), "no cone, no state (false case)");
    }
}
