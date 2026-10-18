package com.laosun.compare;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Compares one scenario's numbers against the original game, <b>zone by zone</b>.
 *
 * <p><b>What "zone by zone" means.</b> This engine settles a hit as a chain of multipliers, and {@link Damage#breakdown()}
 * reports every one of them in the order it is applied:
 * <pre>
 *   base x BoostArea x FinalBoostArea x ComboArea x BaseMultiArea x MonsterTakenArea
 *        x VulnerableArea x ReductionArea x WeaknessArea x CritArea x DefenceArea x ResistArea - absorb = final
 * </pre>
 * A scenario's {@code expect} names the zones it knows the game's numbers for. A row that is absent is <b>not compared</b>
 * (reported as such, not as a failure), and a row the engine does not have is an <b>error</b>: that is a typo or a
 * vocabulary drift, and silently ignoring it would make the comparison look complete when it is not.
 *
 * <h2>Usage</h2>
 * <pre>
 *   gradlew run --args="compare scenarios/1002-skill.json"
 *   gradlew run --args="compare"          (lists the scenarios folder)
 * </pre>
 * The exit code is 1 when any compared row differs by more than its tolerance, so the comparison can be used as a gate.
 */
public final class DamageComparator {

    /** Rows match when they agree to this fraction of the game's number. */
    public static final double DEFAULT_TOLERANCE = 1e-6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Where a scenario lives when no path is given. */
    private static final Path SCENARIOS = Path.of("scenarios");

    private DamageComparator() {
    }

    /**
     * Entry point for {@code Main}: compares one scenario file (or lists what there is) and reports.
     *
     * @param args the command line, {@code args[0] == "compare"}, {@code args[1]} an optional scenario path
     * @return process exit code: 0 everything compared matched, 1 otherwise
     */
    public static int run(String... args) {
        if (args.length < 2) {
            System.out.println("compare: needs a scenario file, for example");
            System.out.println("  gradlew run --args=\"compare scenarios/1002-skill.json\"");
            listScenarios();
            return 2;
        }
        Path file = Path.of(args[1]);
        DamageScenario scenario;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            scenario = GSON.fromJson(reader, DamageScenario.class);
        } catch (IOException e) {
            System.out.println("compare: cannot read " + file.toAbsolutePath() + " (" + e.getMessage() + ")");
            return 2;
        }
        if (scenario == null) {
            System.out.println("compare: " + file + " is empty");
            return 2;
        }
        return report(scenario);
    }

    /**
     * Runs the scenario and prints the table.
     *
     * @param scenario the parsed scenario
     * @return 0 when every compared row matched, 1 otherwise
     */
    public static int report(DamageScenario scenario) {
        System.out.println("scenario: " + text(scenario.getName()));
        if (scenario.getNote() != null && !scenario.getNote().isBlank()) {
            System.out.println("note:     " + scenario.getNote());
        }
        Result result;
        try {
            result = measure(scenario);
        } catch (RuntimeException e) {
            System.out.println("compare: the scenario could not be run: " + e);
            return 2;
        }
        System.out.println("inputs:   " + result.inputs);
        System.out.printf(Locale.ROOT, "settled:  %d instance(s)%n", result.instances.size());
        if (result.instances.size() <= result.reported) {
            System.out.println("compare: the scenario asks for instance " + result.reported + " but the cast settled "
                    + result.instances.size());
            return 2;
        }
        Map<String, Double> engine = result.instances.get(result.reported).breakdown();
        System.out.println("(instance " + result.reported + " of the cast)");

        if (scenario.getExpect().isEmpty()) {
            // No expectations yet: this IS the useful output then - the engine's own numbers, ready to be pasted into
            // `expect` and replaced by the game's.
            System.out.println("no expectations stated; the engine's own numbers are:");
            engine.forEach((zone, value) -> System.out.printf(Locale.ROOT, "  %-20s %18.6f%n", zone, value));
            return 0;
        }
        double tolerance = scenario.getTolerance() == null ? DEFAULT_TOLERANCE : scenario.getTolerance();
        System.out.printf(Locale.ROOT, "%-20s %18s %18s %14s %10s%n", "zone", "game", "engine", "diff", "relative");
        System.out.println("-".repeat(84));
        int compared = 0;
        int mismatched = 0;
        for (Map.Entry<String, Double> expected : scenario.getExpect().entrySet()) {
            String zone = expected.getKey();
            Double game = expected.getValue();
            Double mine = engine.get(zone);
            if (mine == null) {
                System.out.printf(Locale.ROOT, "%-20s %18s %18s %14s %10s%n", zone, fmt(game), "MISSING", "-", "-");
                System.out.println("  ! the engine has no zone named \"" + zone + "\"; its zones are " + engine.keySet());
                mismatched++;
                continue;
            }
            compared++;
            double diff = mine - game;
            double relative = game == 0 ? (diff == 0 ? 0 : Double.POSITIVE_INFINITY) : diff / Math.abs(game);
            boolean ok = Math.abs(relative) <= tolerance || Math.abs(diff) <= tolerance;
            if (!ok) {
                mismatched++;
            }
            System.out.printf(Locale.ROOT, "%-20s %18.6f %18.6f %14.6f %9.4f%% %s%n",
                    zone, game, mine, diff, relative * 100, ok ? "" : "  <-- differs");
        }
        int notCompared = Math.max(0, engine.size() - compared);
        System.out.println("-".repeat(84));
        System.out.printf(Locale.ROOT, "zones reported by the engine: %d ; compared: %d ; engine-only (not compared): %d%n",
                engine.size(), compared, notCompared);
        if (scenario.getInstances() != null && scenario.getInstances() != result.instances.size()) {
            System.out.printf(Locale.ROOT, "! expected %d instance(s), the cast settled %d%n",
                    scenario.getInstances(), result.instances.size());
            mismatched++;
        }
        System.out.println(mismatched == 0
                ? "RESULT: every compared row matches"
                : "RESULT: " + mismatched + " row(s) differ (tolerance " + tolerance + ")");
        return mismatched == 0 ? 0 : 1;
    }

    // ==================================================================
    // Running a scenario
    // ==================================================================

    static Result measure(DamageScenario scenario) {
        DamageScenario.Attacker stated = scenario.getAttacker();
        DamageScenario.Target targetSpec = scenario.getTarget();
        if (stated == null || stated.getCid() == null || stated.getLevel() == null) {
            throw new IllegalArgumentException("attacker.cid and attacker.level are required");
        }
        if (targetSpec == null || targetSpec.getMonsterId() == null || targetSpec.getLevel() == null) {
            throw new IllegalArgumentException("target.monsterId and target.level are required");
        }
        SkillType slot = SkillType.valueOf(required(stated.getSkill(), "attacker.skill").toUpperCase(Locale.ROOT));
        Integer dataSlot = Constant.SKILL_SLOT.get(slot);
        if (dataSlot == null) {
            throw new IllegalArgumentException("attacker.skill " + slot + " has no data slot");
        }

        Character attacker = CharacterFactory.create(stated.getCid(), stated.getLevel(), true, null, null,
                stated.getEidolon() == null ? 0 : stated.getEidolon());

        Enemy enemy = EnemyFactory.create(targetSpec.getMonsterId(), targetSpec.getLevel(),
                targetSpec.getHardLevelGroup() == null ? 1 : targetSpec.getHardLevelGroup());

        if (Boolean.TRUE.equals(stated.getSuppressContent())) {
            // A stand-in, not a character: an empty trigger table keeps the kit out of a hit the capture already states.
            attacker.setTriggerTable(com.laosun.aluminium.models.trigger.TriggerTable.EMPTY);
        }
        Battle battle = new Battle(new ArrayList<>(List.of(attacker)), new ArrayList<>(List.of(enemy)),
                roll(scenario.getCrit()));
        battle.startBattle();
        // The skill is installed AFTER the battle starts, on purpose: a character file's `level_convention` raises the
        // slots at BATTLE_START (1002's is +9, i.e. "the document quotes Lv10"), so installing the scenario's level before
        // that would let the raise win and the cast would use another row - measured: 4.8 instead of 4.0 on his ultimate,
        // a 20% error that showed up as a base 20% above the capture's `damage_value`.
        int level = stated.getSkillLevel() == null
                ? (int) attacker.skillLevel(attacker.getSkills().get(slot))
                : stated.getSkillLevel();
        attacker.setSkill(slot, new DefaultSkill(stated.getCid(), dataSlot, level));
        // What the engine will cast with, raises included - reported below so the header cannot lie about the row.
        int effectiveLevel = (int) attacker.skillLevel(attacker.getSkills().get(slot));
        applyOverrides(attacker, stated.getAttributes());
        if (stated.getBase() != null) {
            // Solve ATK from the game's own base: the comparison must start where the capture starts.
            //
            // The multiplier comes from the row the engine will ACTUALLY cast with - `skillLevel(skill)` includes the
            // per-battle raises, and a character file's `level_convention` raises every slot at BATTLE_START (1002's is
            // +9, "the document quotes Lv10"). Measured: using the requested level instead read row 10's 4.0 while the cast
            // used row 15's 4.8, and the base came out 20% above the capture - a wrong ATK that looks like a wrong formula.
            Double statedMultiplier = multiplierOf(attacker.getSkills().get(slot), effectiveLevel);
            if (statedMultiplier == null || statedMultiplier == 0) {
                throw new IllegalArgumentException("cannot read the multiplier of " + slot + " at level " + effectiveLevel);
            }
            double multiplier = statedMultiplier;
            attacker.setAttribute(AttributeType.ATTACK, new DoubleValue(stated.getBase() / multiplier));
        }
        applyOverrides(enemy, targetSpec.getAttributes());
        if (Boolean.TRUE.equals(targetSpec.getBroken())) {
            // Before the cast: an unbroken enemy carries the extra toughness reduction, and a capture that reports a
            // single reduction number has already accounted for it.
            enemy.breakEnemy(skillElement(attacker.getSkills().get(slot)));
        }
        applyModifiers(enemy, targetSpec.getBuffs());

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        Skill skill = attacker.getSkills().get(slot);
        SkillExecutor.execute(battle, skill, attacker, List.of(enemy));

        String inputs = "attacker=" + stated.getCid() + "@" + stated.getLevel()
                + (stated.getEidolon() == null || stated.getEidolon() == 0 ? "" : " E" + stated.getEidolon())
                + " " + slot + " Lv" + effectiveLevel
                + " ATK=" + num(attacker.getAttribute(AttributeType.ATTACK).get())
                + " multiplier=" + num(multiplierOf(skill, effectiveLevel))
                + " | enemy=" + targetSpec.getMonsterId() + "@" + targetSpec.getLevel()
                + " DEF=" + num(enemy.getAttribute(AttributeType.DEFENCE).get())
                + " crit=" + (scenario.getCrit() == null ? "left to the dice" : scenario.getCrit());
        return new Result(inputs, settled, scenario.getInstance() == null ? 0 : scenario.getInstance());
    }

    /**
     * Installs the capture's damage-side state on the target: vulnerability and reduction.
     */
    private static void applyModifiers(Enemy enemy, java.util.List<DamageScenario.Target.Modifier> buffs) {
        if (buffs == null) {
            return;
        }
        for (DamageScenario.Target.Modifier buff : buffs) {
            int turns = buff.getTurns() == null ? 1 : buff.getTurns();
            double value = buff.getValue() == null ? 0 : buff.getValue();
            switch (String.valueOf(buff.getKind()).toLowerCase(Locale.ROOT)) {
                case "vulnerable" -> enemy.getBuffManager().addBuff(
                        new com.laosun.aluminium.models.buff.VulnerabilityBuff(turns, value));
                case "reduction" -> enemy.getBuffManager().addBuff(
                        new com.laosun.aluminium.models.buff.ReductionBuff(turns, value));
                default -> throw new IllegalArgumentException("unknown buff kind \"" + buff.getKind()
                        + "\": use \"vulnerable\" or \"reduction\"");
            }
        }
    }

    /**
     * The element a skill's damage carries - what the break state and the resistance zone need.
     */
    private static com.laosun.aluminium.enums.DamageElement skillElement(Skill skill) {
        return skill.getData() == null ? null : skill.getData().getElement();
    }

    /**
     * Installs absolute attribute values, so the scenario's panel is the one the game's numbers came from (this engine's
     * sheet has no light cone / relics unless one is handed in).
     */
    private static void applyOverrides(com.laosun.aluminium.models.CanHit unit, Map<String, Double> attributes) {
        if (attributes == null) {
            return;
        }
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            AttributeType type = AttributeType.fromString(entry.getKey());
            if (type == null) {
                throw new IllegalArgumentException("unknown attribute \"" + entry.getKey() + "\"");
            }
            unit.setAttribute(type, new DoubleValue(entry.getValue()));
        }
    }

    /**
     * A {@link Random} that forces the crit roll: {@code true} rolls 0.0 (a crit for any positive crit rate), {@code
     * false} rolls 1.0 (never a crit). {@code null} leaves the engine's own dice alone.
     */
    private static Random roll(Boolean crit) {
        if (crit == null) {
            return new Random(0L);
        }
        return new Random(0L) {
            @Override
            public double nextDouble() {
                return crit ? 0.0 : 1.0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private static Double multiplierOf(Skill skill, int level) {
        if (skill == null || skill.getData() == null || !skill.getData().isLoaded()) {
            return null;
        }
        List<List<Double>> rows = skill.getData().getSkills();
        if (rows.isEmpty()) {
            return null;
        }
        int row = Math.max(1, Math.min(level, rows.size())) - 1;
        return rows.get(row).isEmpty() ? null : rows.get(row).getFirst();
    }

    private static void listScenarios() {
        if (!Files.isDirectory(SCENARIOS)) {
            System.out.println("(no " + SCENARIOS.toAbsolutePath() + " folder)");
            return;
        }
        System.out.println("scenarios in " + SCENARIOS.toAbsolutePath() + ":");
        try (var files = Files.list(SCENARIOS)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> System.out.println("  " + SCENARIOS.resolve(path.getFileName())));
        } catch (IOException e) {
            System.out.println("  (cannot list: " + e.getMessage() + ")");
        }
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " is required");
        }
        return value;
    }

    private static String text(String value) {
        return value == null ? "(unnamed)" : value;
    }

    private static String fmt(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.6f", value);
    }

    private static String num(Double value) {
        return value == null ? "-" : String.format(Locale.ROOT, "%.6f", value);
    }

    /** What one run produced: the inputs (for the header line) and every settled instance. */
    record Result(String inputs, List<Damage> instances, int reported) {
    }

    /** Unused, kept so a future "compare every scenario in a folder" loop has the shape it needs. */
    @SuppressWarnings("unused")
    private static Map<String, Double> empty() {
        return new LinkedHashMap<>();
    }
}
