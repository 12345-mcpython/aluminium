package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * `allies_of_path:智识` and the clause it unlocks (2026-10-02): 「所有「智识」命途角色攻击力提高 #3(30)%」.
 *
 * ⭐ The party is chosen so the selector's REACH is what the reading shows: 那刻夏 (1405) and 景元 (1204) are Erudition, 缇宝 (1403) is Harmony, 昔涟 (1415) is Remembrance. A raise
 * aimed at the path must move exactly the first two.
 */
public class ReasonOdeEruditionAttackTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int JING_YUAN = 1204;
    private static final int TRIBBIE = 1403;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 18;

    @Test
    public void theRaiseReachesTheEruditionAllyOnly() {
        double[] with = run(true);
        double[] without = run(false);
        // ⭐ ROSTER order: [0] 昔涟 (Remembrance), [1] 那刻夏 (Erudition), [2] 景元 (Erudition), [3] 缇宝 (Harmony).
        System.out.println("[erudition_attack] gains: 那刻夏 " + (with[1] - without[1])
                + " ; 景元 " + (with[2] - without[2])
                + " ; 缇宝 " + (with[3] - without[3]) + " ; 昔涟 " + (with[0] - without[0]));
        System.out.println("[erudition_attack]   absolute with: " + java.util.Arrays.toString(with)
                + " ; without: " + java.util.Arrays.toString(without));
        // ⭐ What the selector must do: reach BOTH Erudition allies by the same amount, and neither of the others at all.
        double named = with[1] - without[1];
        double otherErudition = with[2] - without[2];
        Assertions.assertTrue(named > 0, "the one the ode names gains");
        Assertions.assertEquals(named, otherErudition, 1e-9, "and so does the OTHER Erudition ally, by the same amount");
        Assertions.assertEquals(0.0, with[3] - without[3], 1e-9, "a Harmony ally gains nothing");
        Assertions.assertEquals(0.0, with[0] - without[0], 1e-9, "and so does the Remembrance caster");
    }

    /** [那刻夏, 景元, 缇宝, 昔涟] attack values. */
    private static double[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Character jingYuan = CharacterFactory.create(JING_YUAN, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, anaxa, jingYuan, tribbie),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        if (castTheOde) {
            var sprite = battle.summonServant(battle.characters.get(0));
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 18");
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite,
                    List.of(battle.characters.get(1)));
            battle.processRequests();
        }
        double[] out = new double[4];
        for (int i = 0; i < 4; i++) {
            out[i] = battle.characters.get(i).getAttribute(AttributeType.ATTACK).get();
        }
        return out;
    }
}
