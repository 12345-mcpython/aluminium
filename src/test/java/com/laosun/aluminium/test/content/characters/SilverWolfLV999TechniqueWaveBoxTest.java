package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999): her technique's wave clause.
 *
 * <p>「【怪怪怪味豆】在场时，进入战斗后**每个波次开始时触发 1 次**【怪怪怪味豆】对应的【头号补给盲盒】，本次造成的欢愉伤害**固定计入 99 点**
 * 【好活当赏】。」 (her page, 秘技 朋友，这才是T0级秘技)
 *
 * <p>⚠ Three things this file has to state, because each is a way the clause could be quietly wrong:
 * <ul>
 *   <li><b>it is a WAVE_START rule</b> - the wave has a real event, and the technique state is in place before every
 *       BATTLE_START (the caller marks the technique with {@code markTechniqueUsed}, and {@code applyTechniqueStates} turns
 *       that into a plain state named 「秘技」), so the gate is vocabulary the project already has;</li>
 *   <li><b>it is DETERMINISTIC</b> - 「触发 1 次」, with no fixed-probability sentence attached. The chance/decay pair belongs
 *       to the skill-point route, and the engine keeps that state PER RULE, so this rule simply states none and the two
 *       sources cannot disturb each other;</li>
 *   <li><b>it counts 99 laughs for that firing</b> - the field the engine gained for exactly this sentence
 *       ({@code counted_laughs}), read off the Elation instance so the pool's snapshot cannot reach it.</li>
 * </ul>
 *
 * <p>Read structurally, and the reason is measured: a behavioural case would have to advance a real wave through the queue,
 * which is a different fixture (the same wall the Eidolon 2 case documented - firing turn events by hand does not drive
 * those clocks). What this pins is the shipped shape, which is the whole clause.
 */
public class SilverWolfLV999TechniqueWaveBoxTest {
    private static final int WOLF = 1506;
    private static final String RULE = "p1506_technique_box_each_wave";

    /** The wave rule exists, hangs on the technique, fires the same box, and counts 99 laughs for that firing. */
    @Test
    public void theTechniqueFiresTheBoxEachWaveCounting99Laughs() {
        var table = CharacterFactory.create(WOLF, 80).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.WAVE_START).stream()
                .filter(candidate -> candidate.id().equals(RULE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the technique's wave clause is not on her table: " + RULE));
        List<String> conditions = rule.conditions().stream().map(condition -> condition.source()).toList();
        var effects = rule.effects();
        System.out.println("[1506-tech] " + RULE + " when=" + conditions + " effects=" + effects.size()
                + " countedLaughs=" + effects.stream()
                        .filter(e -> "ELATION".equalsIgnoreCase(e.getDamageType() == null ? "" : e.getDamageType().trim()))
                        .findFirst().map(e -> e.getCountedLaughs()).orElse(null));

        Assertions.assertTrue(conditions.contains("self has_state 秘技"),
                "「【怪怪怪味豆】在场时」 is the technique state the engine applies before every BATTLE_START");
        Assertions.assertEquals(4, effects.size(),
                "the same box as the skill-point route: the Elation hit, the sword, the skill points and the laughs");
        var elation = effects.stream()
                .filter(e -> "ELATION".equalsIgnoreCase(e.getDamageType() == null ? "" : e.getDamageType().trim()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the wave clause has no Elation instance"));
        Assertions.assertEquals(99.0, elation.getCountedLaughs(), 1e-9,
                "「本次造成的欢愉伤害固定计入 99 点【好活当赏】」 - the instance states 99 rather than reading the pool");
        Assertions.assertEquals("all_enemies", elation.getTarget(),
                "and it is still the box's own hit on every enemy");
    }
}
