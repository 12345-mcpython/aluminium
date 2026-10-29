package com.laosun.aluminium.test.content.memosprites;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * `allies_of_path:智识` and the clause it unlocks: "所有'智识'命途角色攻击力提高 #3(30)%" (all characters on the Erudition path gain #3(30)% ATK).
 * <p>
 * The party is chosen so the selector's REACH is what the reading shows: Anaxa (1405) and Jing Yuan (1204) are Erudition, Tribbie (1403) is Harmony, Cyrene (1415) is Remembrance. A raise
 * aimed at the path must move exactly the first two.
 */
public class ReasonOdeEruditionAttackTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int JING_YUAN = 1204;
    private static final int TRIBBIE = 1403;
    private static final int ODE_SLOT = 18;

    /** Each character's base attack, captured by the last {@link #run(boolean)} (the same in both runs). */
    private static final ThreadLocal<double[]> BASE = new ThreadLocal<>();

    @Test
    public void theRaiseReachesTheEruditionAllyOnly() {
        double[] with = run(true);
        double[] without = run(false);
        double[] base = BASE.get();
        // ROSTER order: [0] Cyrene (Remembrance), [1] Anaxa (Erudition), [2] Jing Yuan (Erudition), [3] Tribbie (Harmony).
        System.out.println("[erudition_attack] gains: Anaxa " + (with[1] - without[1])
                + " ; Jing Yuan " + (with[2] - without[2])
                + " ; Tribbie " + (with[3] - without[3]) + " ; Cyrene " + (with[0] - without[0]));
        System.out.println("[erudition_attack]   absolute with: " + java.util.Arrays.toString(with)
                + " ; without: " + java.util.Arrays.toString(without));
        // What the selector must do: reach BOTH Erudition allies, and neither of the others at all.
        //
        // ⚠ CORRECTED (2026-10-17): the observable is the SHARE, not "the same absolute amount". The sentence is 「攻击力
        // 提高 #3(84)%」, so each ally gains 84% of ITS OWN base attack - two allies with different bases gain different
        // point amounts by construction, which is what this case used to compare. The old equality only held because the
        // engine handed the share to the modifier as an ABSOLUTE number (+0.84 attack, identical for everyone), i.e. the
        // assertion was pinning the bug.
        // The gain is a SHARE OF EACH ALLY'S OWN BASE - so the invariant is `gain == share x base`, not "the same
        // percentage of the current attack": an ally whose kit already carries attack percentages (Jing Yuan's traces do)
        // would report a smaller ratio even though the raise is exactly the same rule.
        double share = com.laosun.aluminium.data.SkillData.init(11415, ODE_SLOT).getSkills().get(9).get(2);
        Assertions.assertEquals(share * base[1], with[1] - without[1], 1e-9,
                "the one the ode names gains the row's #3 of its own base: " + share);
        Assertions.assertEquals(share * base[2], with[2] - without[2], 1e-9,
                "and so does the OTHER Erudition ally, by the same SHARE");
        Assertions.assertEquals(0.0, with[3] - without[3], 1e-9, "a Harmony ally gains nothing");
        Assertions.assertEquals(0.0, with[0] - without[0], 1e-9, "and so does the Remembrance caster");
    }

    /**
     * [Anaxa, Jing Yuan, Tribbie, Cyrene] attack values.
     */
    private static double[] run(boolean castTheOde) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character anaxa = CharacterFactory.create(ANAXA, LEVEL);
        Character jingYuan = CharacterFactory.create(JING_YUAN, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Battle battle = new Battle(List.of(cyrene, anaxa, jingYuan, tribbie),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        if (castTheOde) {
            var sprite = battle.summonServant(battle.characters().get(0));
            battle.processRequests();
            var ode = sprite.skillAt(ODE_SLOT);
            Assertions.assertNotNull(ode, "precondition: slot 18");
            com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite,
                    List.of(battle.characters().get(1)));
            battle.processRequests();
        }
        double[] out = new double[4];
        BASE.set(new double[]{
                battle.characters().get(0).getAttribute(AttributeType.ATTACK).baseValue(),
                battle.characters().get(1).getAttribute(AttributeType.ATTACK).baseValue(),
                battle.characters().get(2).getAttribute(AttributeType.ATTACK).baseValue(),
                battle.characters().get(3).getAttribute(AttributeType.ATTACK).baseValue()});
        for (int i = 0; i < 4; i++) {
            out[i] = battle.characters().get(i).getAttribute(AttributeType.ATTACK).get();
        }
        return out;
    }
}
