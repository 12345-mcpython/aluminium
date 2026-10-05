package com.laosun.aluminium.test;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;

/**
 * Capability ② and the clause it unlocks (2026-10-02): "…造成的战技伤害提高 #2(20)%".
 *
 * The control is RNG-identical: the ode is cast in BOTH readings, aimed at him in one and at 昔涟 in the other, so the same casts draw the same random numbers and the only difference is
 * whether [真知] landed on him. (Measured: comparing against "no ode at all" moved the basic attack by 0.11% of pure RNG drift, which swamped the gate.)
 *
 * And one attack per battle, because [真知] is spent by the next attack.
 *
 * Note: The share comes from the LEVEL row (measured: 5600 bp). The ratio it produces (1.3948 rather than 1.56) implies a baseline of 0.4184 this round did not trace; registered, not guessed.
 */
public class TrueKnowledgeSkillDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int MONSTER = 1002011;
    private static final int ODE_SLOT = 18;
    private static final String CAPTURED = "真知的战技增伤";

    @Test
    public void theStateRaisesHisSkillDamageAndNothingElse() {
        double[] skillAtHim = hit(true, SkillType.SKILL);
        double[] skillElsewhere = hit(false, SkillType.SKILL);
        double[] basicAtHim = hit(true, SkillType.COMMON);
        double[] basicElsewhere = hit(false, SkillType.COMMON);
        System.out.println("[true_knowledge_dmg] SKILL: " + skillAtHim[0] + " with 【真知】 on him, "
                + skillElsewhere[0] + " without it (captured " + skillAtHim[1] + " bp)");
        System.out.println("[true_knowledge_dmg] COMMON: " + basicAtHim[0] + " with it, " + basicElsewhere[0]
                + " without it");
        double skillRatio = skillAtHim[0] / skillElsewhere[0];
        double basicRatio = basicAtHim[0] / basicElsewhere[0];
        Assertions.assertTrue(skillRatio > 1.05, "his SKILL hits much harder with 【真知】 up: " + skillRatio);
        // The BASIC's move is the OTHER clause of the same sentence (the +30% attack for Erudition, which is +0.84 on ~5= +0.111%), NOT a gate leak. A boost that lost its
        // `from_skill SKILL` gate would move this ratio to the skill's own ~1.39 and fail here.
        Assertions.assertTrue(basicRatio < 1.01,
                "and his BASIC attack is essentially untouched: " + basicRatio + " (the attack-raise's own +0.111%)");
    }

    /** [the damage that one attack dealt, the captured #2 in basis points] */
    private static double[] hit(boolean atHim, SkillType slot) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        var sprite = battle.summonServant(battle.characters.get(0));
        battle.processRequests();
        var ode = sprite.skillAt(ODE_SLOT);
        Assertions.assertNotNull(ode, "precondition: slot 18");
        // the SAME cast either way; only the aim moves
        Character aimed = battle.characters.get(atHim ? 1 : 0);
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, ode, sprite, List.of(aimed));
        battle.processRequests();
        him = battle.characters.get(1);
        double captured = him.getResources().has(CAPTURED) ? him.getResources().value(CAPTURED) : 0;
        var skill = him.getSkills().get(slot);
        Assertions.assertNotNull(skill, "precondition: " + slot);
        double before = battle.enemies.getFirst().getCurrentHp();
        com.laosun.aluminium.models.skill.SkillExecutor.execute(battle, skill, him,
                List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return new double[]{before - battle.enemies.getFirst().getCurrentHp(), captured};
    }
}
