package com.laosun.aluminium.test.content.characters;


import com.laosun.aluminium.test.engine.TalismanSavesAnAllyTest;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
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
 * 121 Huohuo (藿藿) Eidolon (星魂) 2's third sentence: "...<b>decreases [禳命]'s remaining turns by 1</b>".
 *
 * <p>THE SCENE IS COPIED VERBATIM from the shipped judge for the same sentence, `TalismanSavesAnAllyTest.afterLethalBlows`: the same
 * party, the same skill, and `SkillExecutor.execute(battle, skill, her,
 * List.of(battle.enemies.getFirst()))`, i.e. the skill is aimed at the ENEMY. Aiming it at the ally leaves her WITHOUT [禳命], so
 * every reading says "the rule never ran".
 *
 * <p>The reduction is the mechanism the game's own config uses: `Avatar_Huohuo_00_Rank02_Insert` does
 * `SetModifierValue{ModifierName: "MAvatar_Huohuo_Passive_HealMark", ModifyFunction: "Add", ValueType: "LifeTime"}`, beside a
 * `SetDynamicValueByAddValue{AddValue: -1, Min: 0}` on the same count. So it is a negative `turns` on the existing `EXTEND_BUFF` -- one
 * mechanism, not two dialects.
 */
public class HuohuoTalismanLosesATurnTest {
    private static final int HUOHUO = 1217;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "禳命";

    /** The save spends one turn of the talisman, and the ally survives. */
    @Test
    public void theSaveSpendsOneTurnOfTheTalisman() {
        Character her = CharacterFactory.create(HUOHUO, 80, false, null, null, 2);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(her, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // Her skill is what puts [禳命] on her -- and it is aimed at the ENEMY, exactly as the shipped judge drives it.
        Skill skill = her.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        SkillExecutor.execute(battle, skill, her, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        Assertions.assertTrue(her.getBuffManager().hasState(STATE), "precondition: 【禳命】 (Divine Provision) is on her");
        int before = her.getBuffManager().findBuff(StateBuff.class).duration();

        battle.applyTrueDamage(battle.enemies.getFirst(), ally, DamageElement.ICE, ally.getCurrentHp() * 2.0);
        battle.processRequests();

        int after = her.getBuffManager().findBuff(StateBuff.class).duration();
        System.out.println("[talisman] duration " + before + " -> " + after
                + " ; the saved ally survives = " + !ally.isDeath());

        Assertions.assertEquals(1, after,
                "「使【禳命】的持续回合数减 1」 (reduces the remaining turns of Divine Provision by 1)-- her skill granted 2, so the save leaves 1");
        Assertions.assertTrue(!ally.isDeath(),
                "「不会陷入无法战斗状态」 (does not fall into the unable-to-fight state)-- answering the lethal event is what cancels the death");
    }
}
