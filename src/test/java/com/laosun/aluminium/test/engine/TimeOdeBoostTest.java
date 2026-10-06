package com.laosun.aluminium.test.engine;
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
 * 1415's memosprite skill 24, first sentence -- BOTH halves.
 *
 * The capture half: `#1` is captured into a resource on her as the ode is cast. The boost half: when HER memosprite's data slot deals damage, that share raises its damage.
 *
 * Two-sided: with the ode the memosprite's boost is the ode's own #1; without it, nothing was captured and nothing is boosted.
 */
public class TimeOdeBoostTest {
    private static final int LEVEL = 80;
    private static final float EPS = 1e-9f;
    private static final int LONGNIGHT = 1413;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 24;
    private static final int DREAM_SLOT = 7;
    private static final String SHARE = "长夜的迷梦增伤";

    @Test
    public void theOdeCapturesAndThenBoostsTheDreamSkill() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[time_ode] row value " + with[2] + " ; captured " + with[1] + " ; boost " + with[0]
                + " ; without the ode captured " + without[1] + " boost " + without[0]);
        Assertions.assertEquals(Math.round(with[2] * 10000), with[1], "the captured value is #1 in basis points");
        Assertions.assertEquals(with[2], with[0], Math.abs(with[2]) * 1e-6,
                "\"when Evey (「长夜」) casts the memosprite skill [迷梦，流失，如露], the DMG it deals is raised by #1%\" (「「长夜」施放忆灵技【迷梦，流失，如露】时造成的伤害提高 #1%」) -- the captured share");
        Assertions.assertEquals(0, without[1], EPS, "without the ode nothing is captured");
        Assertions.assertEquals(0.0, without[0], EPS, "and nothing is boosted");
    }

    /** [the memosprite's boost after its dream skill lands, what she captured, the ode's row value] */
    private static double[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character longnight = CharacterFactory.create(LONGNIGHT, LEVEL);
        Battle battle = new Battle(List.of(cyrene, longnight),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        longnight = battle.characters.get(1);
        var sprite = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 24");
        double rowValue = ode.getData().getSkills().get(sprite.skillLevel(ode) - 1).get(0);
        if (castTheOde) {
            SkillExecutor.execute(battle, ode, sprite, List.of(longnight));
            battle.processRequests();
        }
        longnight = battle.characters.get(1);
        int captured = longnight.getResources().value(SHARE);
        var evey = battle.summonServant(longnight);
        battle.processRequests();
        Assertions.assertNotNull(evey, "precondition: her memosprite is out");
        var dream = evey.skillAt(DREAM_SLOT);
        Assertions.assertNotNull(dream, "precondition: data slot 7");
        SkillExecutor.execute(battle, dream, evey, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return new double[]{evey.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), captured, rowValue};
    }
}
