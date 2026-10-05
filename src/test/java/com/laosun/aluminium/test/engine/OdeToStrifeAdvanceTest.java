package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.content.memosprites.AglaeaMemospriteTest;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415 Cyrene (昔涟)'s memosprite the Demiurge (德谬歌), memosprite skill 8 "献予'纷争'之诗", the other half (2026-10-02):
 *
 * <p>"if Mydei (万敌) is <b>not</b> in the [血仇] state, then <b>advance Mydei's action by 100%</b>" -- `#2` is 1 at every level, i.e. 100%.
 *
 * <p>The predicate is the existing `!`: `HasState` already implements `PartyCondition`, so `!self has_state <state>` is an ordinary
 * negation and needed no new vocabulary. (An earlier round concluded the opposite from the loader's comments without trying it; the
 * class declaration is what settles it.)
 *
 * <p>The observable is the shipped one: `battle.queue.getTimeRemaining(signal)`, read exactly as `AglaeaMemospriteTest` reads it --
 * "how much action value the unit still has; zero means it acts now". The second case is the control: in [血仇] the OTHER branch runs
 * (the godslayer command) and his action value must not move.
 */
public class OdeToStrifeAdvanceTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final int ODE_TO_STRIFE = 16;
    private static final double EPS = 1e-6;
    private static final String BLOODFEUD = "血仇";

    /** Outside [血仇] (Bloodfeud) he is advanced: his remaining action value collapses. */
    @Test
    public void outsideBloodfeudTheOdeAdvancesHim() {
        Character mydei = scene(false);
        double before = timeRemaining(battle, mydei);
        Skill ode = lastSummon.skillAt(ODE_TO_STRIFE);

        SkillExecutor.execute(battle, ode, lastSummon, List.of(mydei));
        battle.processRequests();

        double after = timeRemaining(battle, mydei);
        System.out.println("[advance] outside bloodfeud: " + before + " -> " + after);
        Assertions.assertTrue(after < before,
                "「then advance Mydei's action by #2[i]%」-- 100%, so his action value collapses toward zero");
    }

    /** In [血仇] (Bloodfeud) the other branch runs instead -- the two halves are mutually exclusive. */
    @Test
    public void inBloodfeudItCommandsInsteadOfAdvancing() {
        Character mydei = scene(true);
        double before = timeRemaining(battle, mydei);
        Skill ode = lastSummon.skillAt(ODE_TO_STRIFE);

        SkillExecutor.execute(battle, ode, lastSummon, List.of(mydei));
        battle.processRequests();

        double after = timeRemaining(battle, mydei);
        System.out.println("[advance] in bloodfeud: " + before + " -> " + after
                + " (his SKILL slot = " + mydei.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL).getSkillSlot() + ")");
        Assertions.assertEquals(before, after, EPS,
                "「if Mydei is in [血仇]」-- that branch commands him instead of advancing him");
    }

    // ==================================================================

    private Battle battle;
    private Summon lastSummon;

    /** The scene both cases share: Cyrene (昔涟), Mydei (万敌) and her memosprite, with [血仇] stated or not. */
    private Character scene(boolean inBloodfeud) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        battle = new Battle(List.of(cyrene, mydei),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        lastSummon = battle.summonServant(cyrene);
        battle.processRequests();
        if (inBloodfeud) {
            StateBuff bloodfeud = new StateBuff(BLOODFEUD, 3, false);
            bloodfeud.setSource(mydei);
            mydei.getBuffManager().addBuff(bloodfeud);
        }
        return mydei;
    }

    /** How much action value the unit still has -- zero means "acts now" (the shipped reader). */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return Double.NaN;
    }
}
