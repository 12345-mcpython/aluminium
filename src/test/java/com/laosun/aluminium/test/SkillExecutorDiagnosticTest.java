package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * The **undispatched diagnostic** for non-damaging skills (item 3 of the P8-2 plan).
 *
 * <p>Background: {@code SkillExecutor.resolveHits} **silently returns** when "the skill is not a
 * damaging one" — after a heal/shield/buff/control/summon is cast **nothing at all happens** (only
 * the energy gain is still given). That is very hard to notice in a real team: in the log the skill
 * "went off", there was just no effect.
 *
 * <p>So a toggleable diagnostic log was added (labelling the owning phase as P6-2 / P10-3 / P10-6 /
 * P9-4). The plan originally wanted a bare {@code IO.println}, but the demo casts heals and shields
 * every turn — that would flood the output by default, so it was changed to an explicit switch. This
 * class verifies both "it prints when enabled" and "it does not print by default".
 */
public class SkillExecutorDiagnosticTest {
    private static final double EPS = 1e-9;

    @AfterEach
    public void restore() {
        SkillExecutor.setLogNotDispatched(false);      // do not leak the switch into other tests
    }

    /**
     * With the switch turned on, casting a healing skill (Natasha's (娜塔莎) skill = {@code Restore})
     * prints the undispatched notice and labels its owner as P6-2.
     */
    @Test
    public void diagnosticReportsNonDamagingSkillsWhenEnabled() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha));
        });

        Assertions.assertTrue(out.contains("NOT DISPATCHED"), "it should print the undispatched notice; actual output: " + out);
        Assertions.assertTrue(out.contains("RESTORE"), "it should carry the effect type; actual: " + out);
        Assertions.assertTrue(out.contains("Natasha"),
                "it should carry the caster; actual: " + out);
        Assertions.assertTrue(out.contains("P6-2"), "it should label the owning phase; actual: " + out);
    }

    /**
     * **Off by default**: nothing is printed. The demo heals/shields every turn, so having it on by
     * default would flood the output.
     */
    @Test
    public void diagnosticIsOffByDefault() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);

        String out = capture(() ->
                battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha)));

        Assertions.assertFalse(out.contains("NOT DISPATCHED"),
                "the diagnostic should not be printed by default; actual output: " + out);
    }

    /**
     * A shield skill (March 7th's (三月七) skill = {@code Defence}) goes through the same silent path
     * and is labelled P10-3.
     */
    @Test
    public void shieldSkillIsAlsoReported() {
        Character march7th = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(march7th);

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1001, 2, 1), march7th, List.of(march7th));
        });

        Assertions.assertTrue(out.contains("DEFENCE"), "it should report DEFENCE; actual: " + out);
        Assertions.assertTrue(out.contains("P10-3"), "it should label P10-3; actual: " + out);
    }

    /**
     * A damaging skill does **not** trigger the diagnostic (it goes down the normal dispatch path).
     */
    @Test
    public void damagingSkillsDoNotTriggerTheDiagnostic() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        String out = capture(() -> {
            SkillExecutor.setLogNotDispatched(true);
            battle.castImmediate(new DefaultSkill(1204, 1, 1), jingYuan, List.of(enemy));
        });

        Assertions.assertFalse(out.contains("NOT DISPATCHED"), "a damaging skill should not report; actual: " + out);
        Assertions.assertTrue(enemy.getCurrentHp() < enemy.getMaxHp(), "it really did fire");
    }

    /**
     * The diagnostic **does not affect behaviour**: a non-damaging skill is still "energy only, no
     * effect".
     *
     * <p>This pins the current real state — turning the log on is not the same as implementing the effect.
     */
    @Test
    public void diagnosticDoesNotChangeBehaviour() {
        Character natasha = CharacterFactory.create(1105, 80);
        Battle battle = newBattle(natasha);
        natasha.takeDamage(natasha.getMaxHp() / 2);      // currentHp has no setter, so take damage to create a gap
        double hpBefore = natasha.getCurrentHp();
        double energyBefore = natasha.getCurrentEnergy();
        Assertions.assertTrue(hpBefore < natasha.getMaxHp(), "HP really did drop, so there is something to heal");

        SkillExecutor.setLogNotDispatched(true);
        battle.castImmediate(new DefaultSkill(1105, 2, 1), natasha, List.of(natasha));

        Assertions.assertEquals(hpBefore, natasha.getCurrentHp(), EPS,
                "the healing skill still has no effect (the effect is another path in P6-2 and does not go through this executor)");
        Assertions.assertTrue(natasha.getCurrentEnergy() > energyBefore,
                "but the energy gain is still given (P3-2: skill energy gain is independent of whether there is damage)");
    }

    // ==================================================================

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** Captures {@code System.out} ({@code IO.println} writes to stdout). */
    private static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
