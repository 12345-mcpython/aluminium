package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 12 "献予'浪漫'之诗" (data slot 14, SkillID 1141514), the half that is expressible today (2026-10-02).
 *
 * <p>"单次生效，对阿格莱雅施放时，<b>使阿格莱雅获得[浪漫]</b>并使衣匠忆灵天赋的速度提高效果层数立即叠加至上限。…"
 *
 * <p>TWO readings, because this skill needed TWO things before it could exist at all:
 * <ul>
 *   <li>the state is applied to the ally the ode is aimed at;</li>
 *   <li>and the skill has a `skill_effects.json` entry, without which `SkillExecutor.canDeliver` refuses it and the ode can NEVER be
 *       cast -- a skill that cannot be cast is a rule that can never run, which is the silence this project refuses.</li>
 * </ul>
 */
public class OdeOfRomanceTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int AGLAEA = 1402;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;
    private static final String STATE = "浪漫";

    /** The ode is deliverable, and reaching Aglaea leaves [浪漫] on her. */
    @Test
    public void theOdeIsDeliverableAndMarksAglaea() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, aglaea),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        Skill ode = demiurge.skillAt(ODE_OF_ROMANCE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries its own skill 14");
        Assertions.assertTrue(SkillExecutor.canDeliver(ode),
                "「单次生效」-- the Rules entry is what makes this skill deliverable at all");

        SkillExecutor.execute(battle, ode, demiurge, List.of(aglaea));
        battle.processRequests();

        boolean marked = aglaea.getBuffManager().hasState(STATE);
        System.out.println("[romance] canDeliver = true ; Aglaea carries " + STATE + " = " + marked);
        Assertions.assertTrue(marked,
                "「对阿格莱雅施放时，使阿格莱雅获得【" + STATE + "】」");
    }

    /** And the other ally is untouched -- the ode is aimed at ONE unit. */
    @Test
    public void anotherAllyIsNotMarked() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Character bystander = CharacterFactory.create(1002, LEVEL);
        Battle battle = new Battle(List.of(cyrene, aglaea, bystander),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        SkillExecutor.execute(battle, demiurge.skillAt(ODE_OF_ROMANCE), demiurge, List.of(aglaea));
        battle.processRequests();

        System.out.println("[romance] bystander carries it = " + bystander.getBuffManager().hasState(STATE));
        Assertions.assertFalse(bystander.getBuffManager().hasState(STATE),
                "「对阿格莱雅施放时」-- one aim, not the whole party");
    }
}
