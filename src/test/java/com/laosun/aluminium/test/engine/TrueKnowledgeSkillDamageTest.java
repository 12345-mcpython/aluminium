package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Capability (2) and the clause it unlocks: "... raise the Skill damage dealt by #2(20)%".
 * <p>
 * The control is RNG-identical: the ode is cast in BOTH readings, aimed at him in one and at Cyrene (昔涟) in the other, so the same casts draw the same random numbers and the only difference is
 * whether True Knowledge (真知) landed on him. (Measured: comparing against "no ode at all" moved the basic attack by 0.11% of pure RNG drift, which swamped the gate.)
 * <p>
 * And one attack per battle, because True Knowledge (真知) is spent by the next attack.
 * <p>
 * Note: The share comes from the LEVEL row (measured: 5600 bp). The ratio it produces (1.3948 rather than 1.56) implies a baseline of 0.4184 this round did not trace; registered, not guessed.
 */
public class TrueKnowledgeSkillDamageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ANAXA = 1405;
    private static final int ODE_SLOT = 18;
    private static final String CAPTURED = "真知的战技增伤";

    @Test
    public void theStateRaisesHisSkillDamageAndNothingElse() {
        double[] skillAtHim = hit(true, SkillType.SKILL);
        double[] skillElsewhere = hit(false, SkillType.SKILL);
        double[] basicAtHim = hit(true, SkillType.COMMON);
        double[] basicElsewhere = hit(false, SkillType.COMMON);
        System.out.println("[true_knowledge_dmg] SKILL: " + skillAtHim[0] + " with [真知] (True Knowledge) on him, "
                + skillElsewhere[0] + " without it (captured " + skillAtHim[1] + " bp)");
        System.out.println("[true_knowledge_dmg] COMMON: " + basicAtHim[0] + " with it, " + basicElsewhere[0]
                + " without it");
        double skillRatio = skillAtHim[0] / skillElsewhere[0];
        double basicRatio = basicAtHim[0] / basicElsewhere[0];
        double attackRatio = basicAtHim[2] / basicElsewhere[2];
        Assertions.assertTrue(skillRatio > 1.05, "his SKILL hits much harder with [真知] (True Knowledge) up: " + skillRatio);
        // The BASIC's move is the OTHER clause of the same sentence (「所有智识命途角色攻击力提高 #3」), NOT a gate leak: so
        // it must move by exactly what the ATTACK raise did and no more.
        //
        // ⚠ CORRECTED (2026-10-17): this used to assert `basicRatio < 1.01` with the comment "the attack-raise's own
        // +0.111%". That number was the signature of a BUG, not of the rule: the share was handed to the modifier as an
        // ABSOLUTE value (+0.84 attack, ~0.111% of his ATK) instead of a fraction of it (row #3 = 84%). Now that the
        // magnitude is a share, the basic really does move - so the assertion is "it moves exactly as much as the ATTACK
        // raise", which is the property that matters and does not depend on the share's size.
        Assertions.assertEquals(attackRatio, basicRatio, 1e-6,
                "the basic attack's move must be exactly the attack raise (" + attackRatio + "), i.e. the skill gate "
                        + "did not leak");
        Assertions.assertTrue(attackRatio > 1.5,
                "and the attack raise is the row's 84%, not 0.84 points of attack: " + attackRatio);
    }

    /**
     * [the damage that one attack dealt, the captured #2 in basis points, his ATTACK]
     */
    private static double[] hit(boolean atHim, SkillType slot) {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character him = CharacterFactory.create(ANAXA, LEVEL);
        Battle battle = new Battle(List.of(cyrene, him),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
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
        return new double[]{before - battle.enemies.getFirst().getCurrentHp(), captured,
                him.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get()};
    }
}
