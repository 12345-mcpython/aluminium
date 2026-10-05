package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1415's memosprite skill 19, THIRD sentence: "风堇施放战技/终结技后，消耗1层[献予'天空'之诗]" (2026-10-02).
 *
 * <p>The reading discriminates the SLOT gate three ways in one battle: casting her SKILL (slot 2) spends a layer, casting her ULTIMATE (slot 3) spends another, and casting
 * her BASIC (slot 1) spends none. A rule that fired on any cast -- or on a wrong slot -- cannot pass all three.
 */
public class SkyOdeSpendTest {
    private static final int LEVEL = 80;
    private static final int HYACINE = 1409;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String MARK = "献予「天空」之诗";

    @Test
    public void aSkillAndAnUltimateSpendOneLayerEachAndABasicSpendsNone() {
        Character hyacine = CharacterFactory.create(HYACINE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(hyacine, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        hyacine = battle.characters.get(0);
        ally = battle.characters.get(1);

        // Note:Note: The layers are placed by ANOTHER character rule, NOT by replacing Hyacine own table: the two rules under test live in that table, and
        // `setTriggerTable` would have deleted them. This exact mistake has been made six times in this project; it is discipline #1 in GAPS.md.
        EffectSpec layers = new EffectSpec();
        TriggerSpecs.set(layers, "op", "ADD_STACK");
        TriggerSpecs.set(layers, "buff", MARK);
        TriggerSpecs.set(layers, "amount", 3.0);
        TriggerSpecs.set(layers, "maxStacks", 99999);
        TriggerSpecs.set(layers, "permanent", Boolean.TRUE);
        TriggerSpecs.set(layers, "target", "ally_cid:" + HYACINE);
        ally.setTriggerTable(new TriggerTable(ALLY, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), layers))));
        battle.fireTriggers(TriggerEvent.BATTLE_START);
        Assertions.assertEquals(3, hyacine.getBuffManager().stacksOf(MARK), "precondition: three layers");

        System.out.println("[sky_spend] the engine slot ids: common="
                + hyacine.getSkills().get(SkillType.COMMON).getSkillSlot()
                + " skill=" + hyacine.getSkills().get(SkillType.SKILL).getSkillSlot()
                + " ultra=" + hyacine.getSkills().get(SkillType.ULTRA).getSkillSlot());
        int afterBasic = cast(battle, hyacine, 1);
        int afterSkill = cast(battle, hyacine, 2);
        int afterUlt = cast(battle, hyacine, 3);
        System.out.println("[sky_spend] layers after basic = " + afterBasic + " ; after skill = " + afterSkill
                + " ; after ultimate = " + afterUlt);

        Assertions.assertEquals(3, afterBasic, "「战技/终结技」-- a BASIC is neither, so it must not spend");
        Assertions.assertEquals(2, afterSkill, "战技 (slot 2) spends one");
        Assertions.assertEquals(1, afterUlt, "终结技 (slot 3) spends another");
    }

    private static int cast(Battle battle, Character who, int slot) {
        // Note: `skillAt` is the MEMOSPRITE's API; a character's skills are keyed by slot enum
        SkillType type = switch (slot) {
            case 1 -> SkillType.COMMON;
            case 2 -> SkillType.SKILL;
            default -> SkillType.ULTRA;
        };
        var skill = who.getSkills().get(type);
        Assertions.assertNotNull(skill, "precondition: slot " + slot + " exists");
        SkillExecutor.execute(battle, skill, who, List.of(battle.enemies.getFirst()));
        battle.processRequests();
        return who.getBuffManager().stacksOf(MARK);
    }
}
