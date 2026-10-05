package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 21, the ever-burning clause (2026-10-02): 「且在变身时获得【永续的燃烧】…持有【永续的燃烧】时，卡厄斯兰那的暴击率提高 #1%」 (#1 = 0.08).
 *
 * ⭐ Two-sided: his ultimate grants the state AND raises his CRIT RATE by exactly 0.08; before he transforms he has neither.
 */
public class WorldOdeEverburningTest {
    private static final int LEVEL = 80;
    private static final int PHAINON = 1408;
    private static final int MONSTER = 1002011;
    private static final String STATE = "永续的燃烧";

    @Test
    public void theTransformationGrantsTheStateAndItsCritRate() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[everburning] after his ultimate the state is " + with[2] + " and CRIT RATE gained +" + with[0]
                + " ; before it the state is " + without[2] + " and the gain is +" + without[0]);
        Assertions.assertEquals(0.08, with[0], 1e-6, "holding it raises his CRIT RATE by #1");
        Assertions.assertTrue(with[2] > 0, "and the state itself is there");
        Assertions.assertEquals(0.0, without[0], 1e-9, "before the transformation there is no gain");
        Assertions.assertEquals(0, without[2], "and no state");
    }

    /** [the CRIT RATE his ultimate added, that rate, the state's stacks] */
    private static double[] run(boolean castUlt) {
        Character him = CharacterFactory.create(PHAINON, LEVEL);
        Battle battle = new Battle(List.of(him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        him = battle.characters.getFirst();
        him = battle.characters.getFirst();
        if (castUlt) {
        // ⭐ The ode grants 【永续的燃烧】 (2026-10-02), so it must land before his transformation.
        var odeSprite = battle.summonServant(CharacterFactory.create(1415, LEVEL));
        battle.processRequests();
        var ode = odeSprite.skillAt(21);
        Assertions.assertNotNull(ode, "precondition: slot 21");
        SkillExecutor.execute(battle, ode, odeSprite, List.of(battle.characters.getFirst()));
        battle.processRequests();
        }
        him = battle.characters.getFirst();
        double before = him.getAttribute(AttributeType.CRIT_CHANCE).get();
        if (castUlt) {
            var ult = him.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA);
            Assertions.assertNotNull(ult, "precondition: he has an ultimate");
            SkillExecutor.execute(battle, ult, him, List.of(battle.enemies.getFirst()));
            battle.processRequests();
        }
        him = battle.characters.getFirst();
        return new double[]{him.getAttribute(AttributeType.CRIT_CHANCE).get() - before,
                him.getAttribute(AttributeType.CRIT_CHANCE).get(),
                him.getBuffManager().stacksOf(STATE)};
    }
}
