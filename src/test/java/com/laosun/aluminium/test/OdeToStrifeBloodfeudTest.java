package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
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
 * 1415 昔涟's memosprite 德谬歌, 忆灵技能 8 「献予「纷争」之诗 / Ode to Strife」, the 血仇 branch (2026-10-02).
 *
 * <p>The game's own words for this skill (data slot 16, SkillID 1141516):
 * 「单次生效，对万敌施放时解除万敌陷入的所有控制类负面状态，若万敌处于【血仇】状态，则使其自动施放1次不消耗充能的【弑神登神】，
 * 本次攻击中万敌的暴击伤害提高 #1[i]%，若施放前目标被消灭则对新入场的敌方目标施放。若万敌不处于【血仇】状态，则使万敌行动提前 #2[i]%。」
 *
 * <p>⭐ The effect side is 1404's own shipped rule for the same skill -- `REPLACE_SKILL{skill: SKILL, skill_id: 11, turns: 1}` beside
 * `CAST_SKILL{skill: SKILL}` -- minus the `SPEND_RESOURCE{天赋充能, 150}` his hundred-and-fifty rule adds, which is exactly the
 * 「不消耗充能」 the sentence states.
 *
 * <p>⭐ The trigger became expressible only now: `from_skill_id` carries the SLOT, so on its own it also matches any other unit's slot-16
 * skill aimed at the same target; `actor is_summon` names the caster's nature. And the scene became possible only now, because a
 * memosprite whose speed the game keeps at zero could not be put into the battle at all.
 */
public class OdeToStrifeBloodfeudTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int MYDEI = 1404;
    private static final int MONSTER = 1002011;
    private static final int ODE_TO_STRIFE = 16;
    private static final int GODSLAYER = 11;
    private static final String BLOODFEUD = "血仇";

    /** In 【血仇】 the command lands: his SKILL slot becomes 【弑神登神】. */
    @Test
    public void inBloodfeudTheOdeCommandsTheGodslayer() {
        Character mydei = scene(true);
        Skill ode = lastSummon.skillAt(ODE_TO_STRIFE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries its own skill 16");
        int before = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();

        SkillExecutor.execute(battle, ode, lastSummon, List.of(mydei));
        battle.processRequests();

        int slot = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[ode] his SKILL slot " + before + " -> " + slot + " (want " + GODSLAYER + ")");
        Assertions.assertEquals(GODSLAYER, slot,
                "「使其自动施放 1 次【弑神登神】」-- the swap his own rule performs");
    }

    /** Outside 【血仇】 the same cast leaves him alone -- the other half of 「若…处于…则…」. */
    @Test
    public void outsideBloodfeudTheOdeDoesNothing() {
        Character mydei = scene(false);
        Skill ode = lastSummon.skillAt(ODE_TO_STRIFE);
        int before = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();

        SkillExecutor.execute(battle, ode, lastSummon, List.of(mydei));
        battle.processRequests();

        int slot = mydei.getSkills().get(SkillType.SKILL).getSkillSlot();
        System.out.println("[ode] outside bloodfeud his SKILL slot " + before + " -> " + slot + " (want it unchanged)");
        Assertions.assertEquals(before, slot, "「若万敌处于【血仇】」-- this branch needs it");
    }

    // ==================================================================

    private Battle battle;
    private Summon lastSummon;

    /** The scene every case shares: 昔涟, 万敌 and her memosprite, with 【血仇】 stated or not. */
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
            Assertions.assertTrue(mydei.getBuffManager().hasState(BLOODFEUD), "precondition: he is in it");
        } else {
            Assertions.assertFalse(mydei.getBuffManager().hasState(BLOODFEUD), "precondition: he is out of it");
        }
        return mydei;
    }
}
