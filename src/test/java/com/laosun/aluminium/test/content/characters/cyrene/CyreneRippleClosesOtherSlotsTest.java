package com.laosun.aluminium.test.content.characters.cyrene;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 「普攻强化为【向着爱与明天♪】且<b>仅能使用该普攻</b>」 - 1415's ultimate's last clause.
 *
 * <p><b>How it is expressed.</b> The clause is put on the rows it excludes ({@code castable_when} on the skill and on the
 * ultimate: "not while 【往昔的涟漪】 holds") rather than as a whitelist the engine would have to enforce: the enhanced
 * basic is installed by {@code REPLACE_SKILL}, so "the only basic is the enhanced one" is already true, and what needed
 * saying is that the other slots are closed during the state.
 *
 * <p>⚠ <b>A reading, registered.</b> The sentence names only the basic attack; whether the ripple form keeps a skill at all
 * is not stated in the rows this repo has. The reading is therefore written as data - one line per slot - so a fuller
 * skill table can overturn it without touching the engine.
 */
public class CyreneRippleClosesOtherSlotsTest {

    private static final int CYRENE = 1415;
    private static final String RIPPLE = "往昔的涟漪";

    private static Battle battle() {
        Character cyrene = CharacterFactory.create(CYRENE, 80);
        Battle battle = new Battle(List.of(cyrene), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static void enterRipple(Battle battle, Character cyrene) {
        cyrene.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(RIPPLE, 99));
    }

    /**
     * Inside the ripple the skill is refused - and, being refused, it settles nothing.
     */
    @Test
    public void theSkillIsRefusedInsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        enterRipple(battle, cyrene);
        Skill skill = cyrene.getSkills().get(SkillType.SKILL);

        Assertions.assertFalse(battle.castImmediate(skill, cyrene, List.of(battle.enemies().getFirst())),
                "「仅能使用该普攻」: the skill slot is closed while the ripple holds");
    }

    /**
     * Outside the ripple the same skill is castable - the gate is the state, not the slot.
     */
    @Test
    public void theSkillIsCastableOutsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        Skill skill = cyrene.getSkills().get(SkillType.SKILL);

        Assertions.assertTrue(battle.castImmediate(skill, cyrene, List.of(battle.enemies().getFirst())),
                "outside the ripple the skill works as before");
    }

    /**
     * The enhanced basic is the one thing still usable inside the ripple.
     */
    @Test
    public void theEnhancedBasicStillWorksInsideTheRipple() {
        Battle battle = battle();
        Character cyrene = battle.characters().getFirst();
        enterRipple(battle, cyrene);
        // Install the enhanced basic the way the ultimate does (REPLACE_SKILL), then cast it.
        SkillExecutor.execute(battle, cyrene.getSkills().get(SkillType.ULTRA), cyrene,
                List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Skill enhanced = cyrene.getSkills().get(SkillType.COMMON);
        Assertions.assertTrue(enhanced.getData().getDescription().contains("向着爱与明天"),
                "precondition: the enhanced basic is installed");

        Assertions.assertTrue(battle.castImmediate(enhanced, cyrene, List.of(battle.enemies().getFirst())),
                "「普攻强化为【向着爱与明天♪】」: the enhanced basic is castable inside the ripple");
    }
}
