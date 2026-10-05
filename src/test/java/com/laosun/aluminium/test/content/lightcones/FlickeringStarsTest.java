package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
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
 * Light cone 23061: ANY of our characters spending 4 skill points inside ITS OWN turn grants the wearer [闪耀王冠].
 *
 * <p>This is the first consumer of two new pieces: {@code SKILL_POINT_SPENT} names its spender as the actor, and
 * {@code actor_stacks:<NAME>} reads that spender's counter from the WEARER's own rule. The discriminating readings are (a) the
 * spender being the ALLY rather than the wearer and (b) the same four spends split across two of the ally's turns.
 */
public class FlickeringStarsTest {
    private static final int CONE = 23061;
    private static final int WEARER = 1205;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int THRESHOLD = 4;
    private static final String SPENT = "本回合消耗";
    private static final String CROWN = "闪耀王冠";

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

    private void spendAs(Battle battle, Character who, int times) {
        var skill = who.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        for (int i = 0; i < times; i++) {
            battle.gainSkillPoint(1);
            Assertions.assertTrue(battle.applySkillPointCost(skill, who), "room to spend");
        }
    }

    @Test
    public void anAllysFourSpendsInItsOwnTurnCrownTheWearer() {
        Battle battle = battle(true);
        spendAs(battle, ally, THRESHOLD);
        System.out.println("[23061] after the ALLY's four spends: crown=" + wearer.getBuffManager().hasState(CROWN)
                + " ally counter=" + ally.getBuffManager().stacksOf(SPENT));
        Assertions.assertTrue(wearer.getBuffManager().hasState(CROWN), "any of our characters counts");
        Assertions.assertEquals(0, ally.getBuffManager().stacksOf(SPENT), "and the spender's counter is cleared");
    }

    @Test
    public void theWearersOwnSpendsCountToo() {
        Battle battle = battle(true);
        spendAs(battle, wearer, THRESHOLD);
        System.out.println("[23061] after the WEARER's own four spends: crown="
                + wearer.getBuffManager().hasState(CROWN));
        Assertions.assertTrue(wearer.getBuffManager().hasState(CROWN), "我方任意角色 includes the wearer");
    }

    @Test
    public void fourSpendsAcrossTwoOfTheSpendersTurnsDoNot() {
        Battle battle = battle(true);
        spendAs(battle, ally, THRESHOLD - 1);
        ally.getBuffManager().afterMove();          // the SPENDER's own turn ends: its counter goes away
        int afterBoundary = ally.getBuffManager().stacksOf(SPENT);
        spendAs(battle, ally, 1);
        System.out.println("[23061] ally: three, then its turn boundary (counter=" + afterBoundary
                + "), then one: crown=" + wearer.getBuffManager().hasState(CROWN));
        Assertions.assertEquals(0, afterBoundary, "the spender's turn boundary clears ITS counter");
        Assertions.assertFalse(wearer.getBuffManager().hasState(CROWN),
                "so a fourth spend in another turn must not grant the crown (false case)");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        Battle battle = battle(false);
        spendAs(battle, ally, THRESHOLD);
        System.out.println("[23061] without the cone: crown=" + wearer.getBuffManager().hasState(CROWN));
        Assertions.assertFalse(wearer.getBuffManager().hasState(CROWN), "no cone, no crown (false case)");
    }
}
