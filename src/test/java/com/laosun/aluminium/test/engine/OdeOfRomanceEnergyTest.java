package com.laosun.aluminium.test.engine;

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
 * 1415 memosprite skill 12 (the ode of romance), the clause with no duration attached.
 *
 * <p>Why the gain is measured TWICE. The first version read the energy across ONE attack and got 90, not 0: casting a skill earns energy on its
 * own, so the clause 0 and the cast own gain are merged in one number. The difference between an attack with the ode on her and the same attack
 * without it isolates the clause -- the only reading that can tell 0 from "0 plus whatever the cast paid".
 */
public class OdeOfRomanceEnergyTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int AGLAEA = 1402;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;
    private static final double ENERGY = 70.0;
    private static final String STATE = "浪漫";

    @Test
    public void anAttackSpendsTheStateAndPaysSeventy() {
        double withOde = attack(battleWithTheOde());
        double without = attack(battleWithoutTheOde());
        System.out.println("[romance_energy] an attack with the ode gains " + withOde
                + " ; the same attack without it gains " + without
                + " ; the clause pays " + (withOde - without));
        Assertions.assertEquals(ENERGY, withOde - without, 1e-6,
                "the clause pays 70, and 70 is #1 at EVERY level of the data");
    }

    @Test
    public void theStateIsSpent() {
        Battle battle = battleWithTheOde();
        Character aglaea = battle.characters.get(1);
        Assertions.assertTrue(aglaea.getBuffManager().hasState(STATE), "the ode reaches her and leaves the state");
        attack(battle);
        boolean stillThere = aglaea.getBuffManager().hasState(STATE);
        System.out.println("[romance_energy] after the attack the state is still on her = " + stillThere);
        Assertions.assertFalse(stillThere, "it is CONSUMED, not kept");
    }

    private static Battle battleWithTheOde() {
        Battle battle = freshBattle();
        Character aglaea = battle.characters.get(1);
        Summon demiurge = battle.summonServant(battle.characters.getFirst());
        battle.processRequests();
        SkillExecutor.execute(battle, demiurge.skillAt(ODE_OF_ROMANCE), demiurge, List.of(aglaea));
        battle.processRequests();
        return battle;
    }

    private static Battle battleWithoutTheOde() {
        Battle battle = freshBattle();
        battle.summonServant(battle.characters.getFirst());
        battle.processRequests();
        return battle;
    }

    private static Battle freshBattle() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, aglaea),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    private static double attack(Battle battle) {
        Character aglaea = battle.characters.get(1);
        Skill skill = aglaea.getSkills().values().stream()
                .filter(s -> s != null && s.getData() != null && s.getData().getEffect().isDamaging())
                .findFirst().orElse(null);
        Assertions.assertNotNull(skill, "precondition: Aglaea has a damaging skill");
        double before = aglaea.getCurrentEnergy();
        SkillExecutor.execute(battle, skill, aglaea, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return aglaea.getCurrentEnergy() - before;
    }
}
