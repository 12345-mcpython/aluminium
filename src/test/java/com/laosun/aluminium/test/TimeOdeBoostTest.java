package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 24, first sentence -- the CAPTURE half (2026-10-02).
 *
 * 「对长夜月施放后，「长夜」施放忆灵技【迷梦，流失，如露】时造成的伤害提高 #1%」. The number lives in slot 24's own row, which no share can read later (a memosprite's skill row is
 * unreachable), so it is captured as the ode is cast -- in basis points, because the share is below 1 and a resource holds an integer.
 *
 * \u2b50 Two-sided: with the ode cast at her, the captured value is exactly round(#1 * 10000); without it, the resource stays empty. The \u300c\u52a0\u6210\u90a3\u534a\u300d (spending it when
 * data slot 7 lands) is registered -- measured, a memosprite's damage does not reach the master's table through `DEALING_DAMAGE`, and gating `DAMAGE_SETTLED` by
 * `actor is_summon` + `from_skill_id == 7` still read nothing.
 */
public class TimeOdeBoostTest {
    private static final int LEVEL = 80;
    private static final int LONGNIGHT = 1413;
    private static final int CYRENE = 1415;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 24;
    private static final String SHARE = "\u957f\u591c\u7684\u8ff7\u68a6\u589e\u4f24";

    @Test
    public void theOdeCapturesItsOwnShareOnHer() {
        double[] with = run(true);
        double[] without = run(false);
        System.out.println("[time_ode] row value " + with[1] + " ; captured " + with[0]
                + " ; without the ode " + without[0]);
        Assertions.assertEquals(Math.round(with[1] * 10000), with[0],
                "the captured value is #1 in basis points -- the row read out of the engine");
        Assertions.assertEquals(0, without[0], "and without the ode she has captured nothing");
    }

    /** [what she captured, the ode's row value] */
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
        return new double[]{battle.characters.get(1).getResources().value(SHARE), rowValue};
    }
}
