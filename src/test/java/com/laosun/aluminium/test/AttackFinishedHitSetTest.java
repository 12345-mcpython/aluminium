package com.laosun.aluminium.test;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * \\u300ca random one of the enemies this attack HIT\\u300d reads the ATTACK-LEVEL set (2026-09-30).
 *
 * <p>\\u2b50 The reader for the capability behind cone 21029: {@code ATTACK_FINISHED} carries the attack's FROZEN hit set,
 * while a per-hit context only has its instance's snapshot. This judge drives real casts through the engine and looks at
 * an effect that LANDS ON THE CHOSEN ENEMY, so the evidence is behavioural rather than a read of an internal field.
 *
 * <p>\\u26a0 Part two varies the battle\\u2019s seed: if the candidates were only \\u300cthe one hit being settled\\u300d, the picks could
 * never cover every target of a multi-target attack -- so covering all of them is what proves the set is the whole one.
 */
public class AttackFinishedHitSetTest {
    private static final int CASTER = 1003;          // Himeko: slot 2 is Fire + Blast, as 21040's judge uses
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final int LEVEL = 80;
    private static final String MARK = "\\u547d\\u4e2d\\u6807\\u8bb0";

    private EffectSpec mark() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "ADD_STACK");
        TriggerSpecs.set(effect, "buff", MARK);
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "permanent", true);
        TriggerSpecs.set(effect, "target", "random_hit_enemy");
        return effect;
    }

    /** Casts at {@code targets} enemies with the given seed, and reports which of them were marked. */
    private Set<Integer> markedByOneCast(int targets, long seed) {
        Character caster = CharacterFactory.create(CASTER, LEVEL);
        caster.setTriggerTable(new TriggerTable(CASTER, List.of(
                TriggerSpecs.rule("ATTACK_FINISHED", List.of("actor == self"), mark()))));
        List<Enemy> foes = new ArrayList<>();
        for (int i = 0; i < targets; i++) {
            foes.add(EnemyFactory.create(MONSTER, 90, 1));
        }
        Battle fight = new Battle(List.of(caster, CharacterFactory.create(ALLY, LEVEL)), foes, new Random(seed));
        fight.startBattle();
        var skill = caster.getSkills().values().stream()
                .filter(candidate -> candidate.getData() != null
                        && candidate.getData().getCategory() == SkillCategory.BPSKILL)
                .findFirst().orElseThrow();
        fight.castImmediate(skill, caster, List.copyOf(foes));
        Set<Integer> marked = new LinkedHashSet<>();
        for (int i = 0; i < foes.size(); i++) {
            if (foes.get(i).getBuffManager().stacksOf(MARK) > 0) {
                marked.add(i);
            }
        }
        return marked;
    }

    @Test
    public void exactlyOneOfTheHitTargetsIsMarked() {
        for (int seed = 0; seed < 5; seed++) {
            Set<Integer> marked = markedByOneCast(3, seed);
            System.out.println("[ATTACK_FINISHED] seed=" + seed + " marked=" + marked);
            Assertions.assertEquals(1, marked.size(), "one pick per attack (seed " + seed + ")");
        }
    }

    @Test
    public void theCandidatesAreTheWholeHitSetNotTheSingleInstance() {
        Set<Integer> ever = new LinkedHashSet<>();
        for (int seed = 0; seed < 40; seed++) {
            ever.addAll(markedByOneCast(3, seed));
        }
        System.out.println("[ATTACK_FINISHED] over 40 seeds, targets ever marked = " + ever);
        Assertions.assertEquals(Set.of(0, 1), ever,
                "the blast reaches two of the three foes, and BOTH must be reachable -- a single-instance snapshot could only ever mark one");
    }
}
