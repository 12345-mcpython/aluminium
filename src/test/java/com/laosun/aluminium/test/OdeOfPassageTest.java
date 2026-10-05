package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1415's memosprite skill 14 「献予「门径」之诗」, the sentence about defence (2026-10-02).
 *
 * <p>「整场生效，<b>对缇宝施放时，使缇宝造成的伤害无视敌方目标 #2[i]% 的防御力。</b>…」
 *
 * <p>Two readings: the piercing lands with the value the engine actually reads (this skill's row at the caster's level -- `#2` runs 0.06 -> 0.168),
 * and it lands on 缇宝 ONLY, because the rule lives on his table and is gated on `target == self`.
 */
public class OdeOfPassageTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int TRIBBIE = 1403;
    private static final int BYSTANDER = 1002;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_PASSAGE = 15;

    @Test
    public void thePiercingLandsOnTribbieOnly() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character tribbie = CharacterFactory.create(TRIBBIE, LEVEL);
        Character bystander = CharacterFactory.create(BYSTANDER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, tribbie, bystander),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();

        Skill ode = demiurge.skillAt(ODE_OF_PASSAGE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 15");
        var used = ode.getData().getSkills().get(demiurge.skillLevel(ode) - 1);
        double expected = used.get(1);

        double tribbieBefore = tribbie.getAttribute(AttributeType.DEFENCE_IGNORE).get();
        double bystanderBefore = bystander.getAttribute(AttributeType.DEFENCE_IGNORE).get();

        SkillExecutor.execute(battle, ode, demiurge, List.of(tribbie));
        battle.processRequests();
        double tribbieGain = tribbie.getAttribute(AttributeType.DEFENCE_IGNORE).get() - tribbieBefore;
        System.out.println("[passage] the row used = " + used + " ; Tribbie pierce +" + tribbieGain
                + " (=" + expected + ")");

        Assertions.assertEquals(expected, tribbieGain, Math.abs(expected) * 1e-6,
                "「使缇宝造成的伤害无视敌方目标 #2% 的防御力」-- #2 runs with the level");

        // and the same ode aimed at somebody else does nothing for him
        SkillExecutor.execute(battle, ode, demiurge, List.of(bystander));
        battle.processRequests();
        double bystanderGain = bystander.getAttribute(AttributeType.DEFENCE_IGNORE).get() - bystanderBefore;
        System.out.println("[passage] aimed at a bystander instead: the bystander gained " + bystanderGain
                + " ; Tribbie is now at " + tribbie.getAttribute(AttributeType.DEFENCE_IGNORE).get());
        Assertions.assertEquals(0.0, bystanderGain, 1e-9,
                "「**对缇宝**施放时」-- the sentence names him, so nobody else is touched");
    }
}
