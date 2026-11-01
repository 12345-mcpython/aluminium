package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A share read out of the skill row must be the RULE HOLDER's row, on every event - not whoever happens to be the actor.
 *
 * <p><b>Why this exists.</b> 2026-10-31: converting Hyacine (风堇)'s Eidolon 1 heal to {@code percent_from_skill_param}
 * gave 0.1 where her Skill's row states 0.08, and her Eidolon 2 speed gave 0.33 where the Ultimate's row states 0.3. The
 * slot mapping was verified correct, so the question is whose row is being read. This test isolates it: a hand-built rule
 * on HER, fired by an ALLY's attack, with the heal scaled off her own Max HP.
 */
public class SkillRowOwnerTest {

    private static final int HYACINE = 1409;

    private static EffectSpec levelRaise(String slot) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "RAISE_SKILL_LEVEL");
        TriggerSpecs.set(effect, "skill", slot);
        TriggerSpecs.set(effect, "amount", 9.0);
        return effect;
    }

    @Test
    public void theRowReadIsTheRuleHoldersOwnRow() {
        Character hyacine = CharacterFactory.create(HYACINE, 80);
        Character ally = CharacterFactory.create(1001, 80);
        Battle battle = new Battle(List.of(hyacine, ally),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.0));

        EffectSpec heal = new EffectSpec();
        TriggerSpecs.set(heal, "op", "HEAL");
        TriggerSpecs.set(heal, "scale", "owner_max_hp");
        TriggerSpecs.set(heal, "percentFromSkillParam", "SKILL:0");
        TriggerSpecs.set(heal, "target", "attacker");
        // The hand-built table still needs the file's own level convention: without it the row read is row 1, which is
        // exactly the trap this test is about.
        EffectSpec raiseCommon = levelRaise("COMMON");
        EffectSpec raiseSkill = levelRaise("SKILL");
        EffectSpec raiseUltra = levelRaise("ULTRA");
        EffectSpec raiseTalent = levelRaise("TALENT");
        hyacine.setTriggerTable(new TriggerTable(HYACINE, List.of(
                TriggerSpecs.rule("BATTLE_START", null, raiseCommon, raiseSkill, raiseUltra, raiseTalent),
                TriggerSpecs.rule("ALLY_ATTACK", null, heal))));

        battle.startBattle();
        Assertions.assertEquals(10, hyacine.skillLevel(hyacine.getSkills().get(SkillType.SKILL)),
                "precondition: the file pins the Skill to the Lv10 row");

        ally.takeDamage(ally.getMaxHp() * 0.5);
        double before = ally.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, ally, 1, 0));
        double healed = ally.getCurrentHp() - before;

        double row10 = hyacine.getSkills().get(SkillType.SKILL).getData().getSkills().get(9).get(0);
        System.out.println("[owner-probe] row10#1=" + row10 + " row1#1="
                + hyacine.getSkills().get(SkillType.SKILL).getData().getSkills().get(0).get(0)
                + " maxHp=" + hyacine.getMaxHp() + " healed=" + healed
                + " implied share=" + (healed / (hyacine.getMaxHp() * 1.0)));
        Assertions.assertEquals(row10 * hyacine.getMaxHp(), healed, hyacine.getMaxHp() * 1e-6,
                "the heal is HER Skill row's #1 of HER Max HP");

        // …and the same rule WITHOUT the convention reads row 1: the two halves of the trap, measured.
        Character bare = CharacterFactory.create(HYACINE, 80);
        Character other = CharacterFactory.create(1001, 80);
        Battle second = new Battle(List.of(bare, other), List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.0));
        EffectSpec healAgain = new EffectSpec();
        TriggerSpecs.set(healAgain, "op", "HEAL");
        TriggerSpecs.set(healAgain, "scale", "owner_max_hp");
        TriggerSpecs.set(healAgain, "percentFromSkillParam", "SKILL:0");
        TriggerSpecs.set(healAgain, "target", "attacker");
        bare.setTriggerTable(new TriggerTable(HYACINE, List.of(TriggerSpecs.rule("ALLY_ATTACK", null, healAgain))));
        Assertions.assertEquals(1, bare.skillLevel(bare.getSkills().get(SkillType.SKILL)),
                "precondition: no convention in this table, so the Skill is at its base level");
        other.takeDamage(other.getMaxHp() * 0.5);
        double beforeAgain = other.getCurrentHp();
        second.startBattle();
        second.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, other, other, 1, 0));
        double readRow1 = (other.getCurrentHp() - beforeAgain) / bare.getMaxHp();
        System.out.println("[owner-probe] without the convention the share is " + readRow1 + " (row 1)");
        Assertions.assertEquals(0.04, readRow1, 1e-9,
                "a table without the convention reads row 1 - which is why a rule may only read a row the file pins");
    }
}