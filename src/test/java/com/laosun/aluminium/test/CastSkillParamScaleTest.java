package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `cast_skill_param:<index>`: a magnitude that is a parameter of the skill that produced the event, at its CURRENT level (2026-10-02).
 *
 * <p>Reader: 1415's memosprite skill 10 「献予「创世」之诗」 -- 「使开拓者•记忆的攻击力提高，提高数值等同于德谬歌生命上限的 #1%」, where #1 runs
 * with the skill level. A literal `percent` would have frozen one level.
 *
 * <p>⚠⚠ <b>The lesson this judge cost, and why it now reads the level it reads.</b> The first version expected ROW 1 (`0.36`) while the
 * engine reads the row for the caster's CURRENT level -- and this memosprite's skill sits at level 10 (`rows = 10`). The mismatch surfaced as
 * the number `1.008`, which I first recorded as a mysterious downstream factor of 2.8 (`0.36 × 2.8`, and `0.18 × 2.8` in the mutant run).
 * There was never a factor: `70` -- the index-0 reading -- <b>is the same in every row</b>, which is exactly why the first case looked right
 * while the second did not. Both cases now assert against the row the ENGINE uses, read through the same accessor `multiplierOf` uses.
 */
public class CastSkillParamScaleTest {
    private static final int LEVEL = 80;
    private static final int CYRENE = 1415;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int ODE_OF_ROMANCE = 14;

    /** The magnitude is the skill's own parameter, out of the row of the caster's current level. */
    @Test
    public void theMagnitudeIsTheCastingSkillsOwnParameter() {
        Scene scene = scene();
        var rows = scene.ode().getData().getSkills();
        int level = scene.demiurge().skillLevel(scene.ode());
        var used = rows.get(level - 1);

        double gained = scene.measure("cast_skill_param:0");
        System.out.println("[skill_param] level = " + level + " of " + rows.size() + " ; the row used = " + used
                + " ; the gain = " + gained);
        Assertions.assertEquals(used.get(0), gained, 1e-6,
                "\u300c\u63d0\u9ad8\u6570\u503c\u7b49\u540c\u4e8e\u2026\u7684 #1%\u300d-- #1 is the skill own parameter, at the level the cast is at");
    }

    /** A different index gives a different member of the same row, so the reading really is the index. */
    @Test
    public void aDifferentIndexYieldsADifferentMember() {
        Scene scene = scene();
        var used = scene.ode().getData().getSkills().get(scene.demiurge().skillLevel(scene.ode()) - 1);
        Assumptions.assumeTrue(used.size() > 1 && used.get(0) != used.get(1),
                "the shipped row must differ at index 1 for this reading to say anything");

        double gained = scene.measure("cast_skill_param:1");
        System.out.println("[skill_param] the row used = " + used + " ; index 1 = " + used.get(1)
                + " ; the gain = " + gained);
        Assertions.assertEquals(used.get(1), gained, 1e-6, "index 1 of the same row is the second member");
        Assertions.assertNotEquals(used.get(0), gained, 1e-6, "and it is not the first member");
    }

    // ---------------------------------------------------------------- the scene, built once

    private record Scene(Battle battle, Character owner, Summon demiurge, Skill ode) {
        double measure(String scale) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
            TriggerSpecs.set(effect, "attribute", "ATTACK");
            TriggerSpecs.set(effect, "scale", scale);
            TriggerSpecs.set(effect, "percent", 1.0);
            TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
            TriggerSpecs.set(effect, "target", "self");
            owner.setTriggerTable(new TriggerTable(CYRENE, List.of(
                    TriggerSpecs.rule("CAST_SETUP", List.of("actor is_summon"), effect))));
            double before = owner.getAttribute(AttributeType.ATTACK).get();
            SkillExecutor.execute(battle, ode, demiurge, List.of(battle.characters.get(1)));
            battle.processRequests();
            return owner.getAttribute(AttributeType.ATTACK).get() - before;
        }
    }

    private static Scene scene() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(cyrene, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Summon demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        Skill ode = demiurge.skillAt(ODE_OF_ROMANCE);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 14");
        return new Scene(battle, cyrene, demiurge, ode);
    }
}
