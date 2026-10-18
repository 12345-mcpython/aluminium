package com.laosun.compare;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import lombok.Getter;
import lombok.Setter;

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
 * Drives a whole-battle capture through {@link Battle}: one cast at a time, in the captured order.
 *
 * <h2>What this is for, and what {@link CaptureComparator} already covers</h2>
 * {@code compare-capture} replays a capture <b>one damage event at a time</b>: it states every modifier the payload
 * states and asks the engine for that event's settled number. That proves the engine's zone arithmetic. It does
 * <b>not</b> prove that the engine's own rules can <em>produce</em> those modifiers, and it cannot, because each
 * event is settled in a battle of its own.
 *
 * <p>This driver is the other half: it builds <b>one</b> battle out of what the capture states (the actors' panels,
 * the enemies' lives / defence / resistance), then hands the engine the captured <b>cast order</b> and lets the
 * engine's rules do the rest - states, resources, damage riders, derived hits. What comes out is a per-cast
 * reconciliation: <em>does the engine reach the game's number by itself?</em>
 *
 * <h2>The three pieces a replay needs, and where each comes from</h2>
 * <ol>
 *   <li><b>Which skill each captured cast used.</b> The payload says the <b>attack type</b> ({@code 1} basic,
 *       {@code 3} ultimate, {@code 12} a memosprite's skill) but not the slot, and a memosprite's skills are
 *       addressed by <b>data slot</b> ({@link Summon#skillAt(int)}). That mapping is the plan's {@code casts} table:
 *       a <b>manual</b> table, because the data does not state it and guessing it would be exactly the silent wrong
 *       answer this project refuses.</li>
 *   <li><b>The enemies.</b> {@link Enemy#fromAttributes} takes the capture's 生命上限 / 防御 directly and
 *       {@code setDamageResist} takes its per-element 抗性 - so a captured fight's targets are assembled, not
 *       guessed from a monster id.</li>
 *   <li><b>An entry that casts a named unit's named skill out of turn.</b> {@link Battle#castImmediate} already is
 *       that entry: it runs the whole cast (the pre-cast hook, the hits, every cast event, energy) for any unit,
 *       without asking the action bar who is next. A replay is exactly "the action bar is not in charge" - the
 *       capture is.</li>
 * </ol>
 *
 * <h2>What this driver does NOT do, and why the plan says so</h2>
 * A capture is a <b>window</b> of a fight already in progress. Whatever was standing before event 0 - a state, a
 * resource, a raised panel - is not in the payload, and inventing it would make the reconciliation meaningless.
 * The driver therefore states what it knows (panels, enemies, skill slots) and reports the rest as a difference,
 * once per cast, instead of approximating it. The measured causes are registered in {@code AGENT.md} §3.7zb.
 *
 * <p>Usage: {@code replay <plan.replay.json> [--strict]}. The exit code is {@code 0} when the replay ran and the
 * plan agreed with the capture, {@code 2} when it did not, and - with {@code --strict} - {@code 1} when any cast's
 * settled total differs from the game's.
 */
public final class BattleReplay {

    /** Rows match when they agree to this fraction of the game's number, the same convention as the comparators. */
    public static final double TOLERANCE = 1e-6;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BattleReplay() {
    }

    // ==================================================================
    // The plan: what the capture does not state, stated by hand
    // ==================================================================

    /**
     * A replay plan: the cast table, the party's panels and the enemies, for one capture.
     */
    @Getter
    @Setter
    public static class Plan {

        /** A short label, printed as the report's title. */
        private String name;

        /** Where the numbers came from, and what the plan deliberately does not state. */
        private String note;

        /** The capture this plan replays, relative to the working directory. */
        private String capture;

        /** Our side, one entry per captured attacker uid. */
        private List<MemberSpec> party = new ArrayList<>();

        /** The captured targets, in the order the plan's casts index them (1-based). */
        private List<EnemySpec> enemies = new ArrayList<>();

        /** The cast table: one entry per captured {@code cast} group. */
        private List<CastSpec> casts = new ArrayList<>();
    }

    /**
     * One of our units: the id the capture names it by, and the panel the capture states for it.
     */
    @Getter
    @Setter
    public static class MemberSpec {

        /** The uid the capture uses for this unit - the character's own cid here. */
        private Integer owner;

        /** The character id to build. Separate from {@link #owner} so a stand-in could be named. */
        private Integer cid;

        private Integer level = 80;

        /** 0-6 (星魂), the gates a kit's eidolon clauses read. */
        private Integer eidolon = 0;

        /**
         * Whether to place its memosprite on the field before the first cast.
         *
         * <p>A replay states this rather than waiting for the kit's own rule: the summon may hang on an ultimate
         * that happened in the window's unobserved prefix, and a memosprite that is not there cannot cast.
         */
        private Boolean memosprite;

        /** Absolute attribute values, so both sides use the panel the capture's numbers came from. */
        private Map<String, Double> attributes = new LinkedHashMap<>();
    }

    /**
     * One captured target: exactly the numbers the payload states about it.
     */
    @Getter
    @Setter
    public static class EnemySpec {

        private String name;

        /** The payload's {@code target_max_hp}. */
        private Double health;

        /** The payload's {@code target_defence}. */
        private Double defence;

        private Integer level = 95;

        private Double speed = 100.0;

        /** Element name ({@code FIRE}) to the payload's {@code resistance} entry. */
        private Map<String, Double> resistance = new LinkedHashMap<>();
    }

    /**
     * One cast of the capture, and the skill that produced it.
     */
    @Getter
    @Setter
    public static class CastSpec {

        /** The capture flow's 1-based cast index - the join key against {@link CaptureFlow}. */
        private Integer cast;

        /** The uid that cast it (the character's cid). */
        private Integer actor;

        /** {@code character} (the actor's own slot) or {@code memosprite} (its 忆灵's data slot). */
        private String caster = "character";

        /** A {@link SkillType} name for a character, a <b>data slot</b> for a memosprite. */
        private String slot;

        /** The data slot, spelled as a number when {@code caster} is {@code memosprite}. */
        private Integer slotNumber;

        /** Which of the plan's enemies was the main target, 1-based. */
        private Integer target;

        /** How many times this skill was cast back to back; absent = once. */
        private Integer repeat = 1;

        /** Why this slot is the one the capture used, or why it cannot be driven. */
        private String note;
    }

    // ==================================================================
    // Entry point
    // ==================================================================

    /**
     * @param args {@code [plan.json]} (+ optional {@code --strict})
     * @return 0 when the replay ran and agreed with the capture, 1 with {@code --strict} when a cast differed, 2 when
     * the plan could not be read or does not cover the capture
     */
    public static int run(String... args) {
        Path planPath = null;
        boolean strict = false;
        boolean detail = false;
        for (String arg : args) {
            if ("--strict".equals(arg)) {
                strict = true;
            } else if ("--detail".equals(arg)) {
                detail = true;
            } else if (!"replay".equals(arg) && !arg.startsWith("--")) {
                planPath = Path.of(arg);
            }
        }
        if (planPath == null) {
            System.out.println("usage: replay <plan.replay.json> [--strict] [--detail]");
            return 2;
        }
        Plan plan;
        try (Reader reader = Files.newBufferedReader(planPath, StandardCharsets.UTF_8)) {
            plan = GSON.fromJson(reader, Plan.class);
        } catch (IOException | RuntimeException e) {
            System.out.println("replay: cannot read " + planPath.toAbsolutePath() + " (" + e + ")");
            return 2;
        }
        if (plan == null || plan.getCapture() == null || plan.getCasts().isEmpty()) {
            System.out.println("replay: " + planPath + " needs a capture and at least one cast");
            return 2;
        }
        try {
            return replay(plan, planPath, strict, detail);
        } catch (RuntimeException e) {
            System.out.println("replay: the plan could not be driven: " + e);
            return 2;
        }
    }

    // ==================================================================
    // Replaying
    // ==================================================================

    /**
     * Builds the battle, drives every cast of the plan, and prints the reconciliation.
     *
     * @param plan     the plan
     * @param planPath the file it came from, for the header
     * @param strict   whether a difference is an exit code
     * @param detail   whether to print every settled instance under its cast
     * @return see {@link #run}
     */
    static int replay(Plan plan, Path planPath, boolean strict, boolean detail) {
        JsonObject capture = readCapture(plan.getCapture());
        List<CaptureFlow.Cast> flow = CaptureFlow.group(capture.getAsJsonArray("damage_events"));
        System.out.println("capture:  " + plan.getCapture());
        System.out.println("plan:     " + planPath.getFileName());
        System.out.println("scenario: " + (plan.getName() == null ? "(unnamed)" : plan.getName()));
        if (plan.getNote() != null && !plan.getNote().isBlank()) {
            System.out.println("note:     " + plan.getNote());
        }

        Map<Integer, CastSpec> byCast = new LinkedHashMap<>();
        for (CastSpec spec : plan.getCasts()) {
            if (spec.getCast() == null) {
                System.out.println("replay: a plan cast has no \"cast\" index");
                return 2;
            }
            if (byCast.put(spec.getCast(), spec) != null) {
                System.out.println("replay: cast " + spec.getCast() + " is named twice");
                return 2;
            }
        }

        // The capture is the authority on which casts existed; the plan may only say HOW each was cast.
        for (CaptureFlow.Cast group : flow) {
            if (!"cast".equals(group.classification())) {
                continue;
            }
            if (!byCast.containsKey(group.index())) {
                System.out.println("replay: the plan has no entry for cast " + group.index()
                        + " (actor " + group.actor() + ", " + group.kind() + ", " + group.hits() + " hit(s))");
                return 2;
            }
            CastSpec spec = byCast.get(group.index());
            if (spec.getActor() == null || spec.getActor() != group.actor()) {
                System.out.println("replay: cast " + group.index() + " is actor " + group.actor()
                        + " in the capture but " + spec.getActor() + " in the plan");
                return 2;
            }
            // The plan's `target` must be the unit the capture's cast was AIMED at - the payload's only target
            // identity is its `target_max_hp`, and the plan's enemies are stated with those same lives.
            int captured = enemyIndex(plan, group.firstTarget());
            if (captured < 0) {
                System.out.println("replay: cast " + group.index() + " hit a target with 生命上限 "
                        + group.firstTarget() + ", which is none of the plan's enemies");
                return 2;
            }
            if (spec.getTarget() != null && spec.getTarget() != captured) {
                System.out.println("replay: cast " + group.index() + " was aimed at enemy " + captured
                        + " in the capture but at " + spec.getTarget() + " in the plan");
                return 2;
            }
        }

        List<Character> party = new ArrayList<>();
        Map<Integer, Character> byCid = new LinkedHashMap<>();
        System.out.println();
        System.out.println("party (panels installed from the capture):");
        for (MemberSpec member : plan.getParty()) {
            Character character = CharacterFactory.create(member.getCid(), member.getLevel(), true, null, null,
                    member.getEidolon() == null ? 0 : member.getEidolon());
            applyOverrides(character, member.getAttributes());
            party.add(character);
            byCid.put(member.getOwner(), character);
            System.out.printf(Locale.ROOT, "  %-6d %-8s ATK %-14.6f HP %-14.6f DEF %-14.6f%n",
                    member.getOwner(), character.getName(),
                    character.getAttribute(AttributeType.ATTACK).get(),
                    character.getAttribute(AttributeType.HEALTH).get(),
                    character.getAttribute(AttributeType.DEFENCE).get());
        }

        List<Enemy> enemies = new ArrayList<>();
        System.out.println("enemies (assembled from the capture's own numbers):");
        for (EnemySpec spec : plan.getEnemies()) {
            Enemy enemy = Enemy.fromAttributes(spec.getName() == null ? "captured enemy" : spec.getName(),
                    spec.getHealth() == null ? 0 : spec.getHealth(),
                    spec.getDefence() == null ? 0 : spec.getDefence(),
                    100, spec.getSpeed() == null ? 100 : spec.getSpeed());
            enemy.setLevel(spec.getLevel() == null ? 95 : spec.getLevel());
            enemy.setDamageResist(resistances(spec.getResistance()));
            enemy.heal(enemy.getMaxHp());
            enemies.add(enemy);
            System.out.printf(Locale.ROOT, "  %-18s HP %-16.6f DEF %-14.6f level %d%n",
                    enemy.getName(), enemy.getMaxHp(),
                    enemy.getAttribute(AttributeType.DEFENCE).get(), enemy.getLevel());
        }

        Battle battle = new Battle(party, enemies, new Random(0L));
        battle.startBattle();
        for (MemberSpec member : plan.getParty()) {
            if (Boolean.TRUE.equals(member.getMemosprite())) {
                battle.summonMemosprite(byCid.get(member.getOwner()));
            }
        }
        battle.processRequests();

        List<Settled> window = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                window.add(new Settled(damage, applied)));

        List<Row> rows = rowsOf(flow, byCast);
        System.out.println();
        System.out.printf(Locale.ROOT, "%-4s %-6s %-10s %-10s %16s %16s %16s %10s%n",
                "cast", "actor", "kind", "capture", "capture damage", "engine inst", "engine damage", "verdict");
        System.out.println("-".repeat(104));
        int driven = 0;
        int matched = 0;
        int undrivable = 0;
        for (Row row : rows) {
            String captureCell = row.hits + "h/" + row.targets + "t";
            if (row.spec == null) {
                System.out.printf(Locale.ROOT, "%-4d %-6d %-10s %-10s %16.2f %16s %16s %10s%n",
                        row.cast, row.actor, row.kind, captureCell, row.captureDamage, "-", "-", "not in plan");
                continue;
            }
            Skill skill = skillOf(row.spec, battle, byCid);
            if (skill == null) {
                undrivable++;
                System.out.printf(Locale.ROOT, "%-4d %-6d %-10s %-10s %16.2f %16s %16s %10s%n",
                        row.cast, row.actor, row.kind, captureCell, row.captureDamage, "-", "-", "not drivable");
                System.out.println("     " + skillGap(row.spec));
                continue;
            }
            CanHit caster = casterOf(row.spec, battle, byCid);
            CanHit target = row.spec.getTarget() == null || row.spec.getTarget() < 1
                    || row.spec.getTarget() > enemies.size()
                    ? null : enemies.get(row.spec.getTarget() - 1);
            window.clear();
            int repeat = row.spec.getRepeat() == null ? 1 : Math.max(1, row.spec.getRepeat());
            for (int i = 0; i < repeat; i++) {
                battle.castImmediate(skill, caster, target == null ? List.of() : List.of(target));
            }
            driven++;
            double settled = window.stream().mapToDouble(Settled::applied).sum();
            boolean same = row.captureDamage > 0
                    && Math.abs(settled - row.captureDamage) <= Math.max(TOLERANCE, row.captureDamage * TOLERANCE);
            if (same) {
                matched++;
            }
            System.out.printf(Locale.ROOT, "%-4d %-6d %-10s %-10s %16.2f %16d %16.2f %10s%n",
                    row.cast, row.actor, row.kind, captureCell, row.captureDamage, window.size(), settled,
                    same ? "match" : "differs");
            if (detail) {
                // Which row the engine actually read: the first thing a reconciliation has to know, because a
                // multiplier that disagrees is either the wrong row or the wrong attribute and the two look alike.
                int level = caster.skillLevel(skill);
                System.out.printf(Locale.ROOT, "     skill %d slot %d cast by %-10s at level %d of %d row(s)%n",
                        skill.getCid(), skill.getSkillSlot(), caster.getName(), level,
                        skill.getData() == null || skill.getData().getSkills() == null
                                ? 0 : skill.getData().getSkills().size());
                for (Settled each : window) {
                    System.out.printf(Locale.ROOT, "     %-22s -> %-18s %-10s base %-14.4f settled %-14.4f%n",
                            each.damage().getAttacker() == null ? "-" : each.damage().getAttacker().getName(),
                            each.damage().getDefender() == null ? "-" : each.damage().getDefender().getName(),
                            each.damage().getType(),
                            each.damage().breakdown().getOrDefault("base", Double.NaN), each.applied());
                }
            }
        }
        System.out.println("-".repeat(104));
        System.out.printf(Locale.ROOT,
                "casts: %d in the capture ; driven: %d ; not drivable: %d ; reproduced: %d%n",
                rows.size(), driven, undrivable, matched);
        System.out.printf(Locale.ROOT, "groups: %d of %d capture groups accounted for (casts, derived hits, breaks)%n",
                rows.size(), flow.size());
        System.out.println();
        System.out.println("What a difference means is registered in AGENT.md §3.7zb: the window's unobserved prefix "
                + "(states and resources standing before event 0), the servants' own multiplier rows, and an enemy "
                + "with no toughness bar (so the capture's 10% unbroken reduction is absent here).");
        return strict && matched != driven ? 1 : 0;
    }

    /**
     * One row of the report: a captured cast (with the derived hits and breaks that followed it) and, when the plan
     * names it, the entry that drives it.
     */
    private record Row(int cast, long actor, String kind, double base, int hits, int targets, double captureDamage,
                       CastSpec spec) {
    }

    /**
     * One settled instance, kept with its {@code Damage} so {@code --detail} can name who dealt what to whom.
     */
    private record Settled(Damage damage, double applied) {
    }

    /**
     * Lays the capture's groups out as report rows, folding each {@code derived} / {@code break} group into the cast
     * before it - that is what it is (damage the previous cast produced), and keeping it as its own row would compare
     * an engine cast against a number the engine never books as a cast.
     */
    private static List<Row> rowsOf(List<CaptureFlow.Cast> flow, Map<Integer, CastSpec> byCast) {
        List<Row> rows = new ArrayList<>();
        int hits = 0;
        int targets = 0;
        double damage = 0;
        double base = 0;
        int cast = 0;
        long actor = 0;
        String kind = "";
        for (CaptureFlow.Cast group : flow) {
            if ("cast".equals(group.classification())) {
                if (cast != 0) {
                    rows.add(new Row(cast, actor, kind, base, hits, targets, damage, byCast.get(cast)));
                }
                cast = group.index();
                actor = group.actor();
                kind = group.kind();
                base = group.base();
                hits = group.hits();
                targets = group.targets();
                damage = group.damage() + group.companion();
                continue;
            }
            if (cast == 0) {
                // A derived hit before any cast: not attributable, and nothing to compare it against.
                continue;
            }
            damage += group.damage() + group.companion();
        }
        if (cast != 0) {
            rows.add(new Row(cast, actor, kind, base, hits, targets, damage, byCast.get(cast)));
        }
        return rows;
    }

    /**
     * Which of the plan's enemies has this 生命上限, 1-based - the capture's only target identity.
     *
     * @return the 1-based index, or {@code -1} when no enemy is stated with that life
     */
    private static int enemyIndex(Plan plan, double health) {
        List<EnemySpec> enemies = plan.getEnemies();
        for (int i = 0; i < enemies.size(); i++) {
            Double stated = enemies.get(i).getHealth();
            if (stated != null && Math.abs(stated - health) <= Math.max(1e-6, Math.abs(health) * 1e-9)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * The skill a plan entry names, or {@code null} when the data has no such row.
     */
    private static Skill skillOf(CastSpec spec, Battle battle, Map<Integer, Character> byCid) {
        if ("memosprite".equalsIgnoreCase(spec.getCaster())) {
            Summon summon = battle.memospriteOf(byCid.get(spec.getActor()));
            if (summon == null) {
                return null;
            }
            Integer slot = spec.getSlotNumber() != null ? spec.getSlotNumber() : parseSlot(spec.getSlot());
            return slot == null ? null : summon.skillAt(slot);
        }
        Character character = byCid.get(spec.getActor());
        if (character == null || spec.getSlot() == null) {
            return null;
        }
        try {
            return character.getSkills().get(SkillType.valueOf(spec.getSlot().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Who the cast belongs to: the character itself, or the memosprite it summoned.
     */
    private static CanHit casterOf(CastSpec spec, Battle battle, Map<Integer, Character> byCid) {
        Character character = byCid.get(spec.getActor());
        if (!"memosprite".equalsIgnoreCase(spec.getCaster())) {
            return character;
        }
        Summon summon = battle.memospriteOf(character);
        return summon == null ? character : summon;
    }

    /**
     * Why a named slot cannot be cast - printed under the row, so "not drivable" always says what is missing.
     */
    private static String skillGap(CastSpec spec) {
        if ("memosprite".equalsIgnoreCase(spec.getCaster())) {
            return "     the memosprite has no skill at data slot " + spec.getSlot()
                    + " - the data does not carry that row (registered, not approximated)";
        }
        return "     the character has no " + spec.getSlot() + " slot";
    }

    private static Integer parseSlot(String slot) {
        if (slot == null || slot.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(slot.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Installs absolute attribute values, so the engine's sheet is the one the capture's numbers came from.
     */
    private static void applyOverrides(Character unit, Map<String, Double> attributes) {
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
     * The capture's per-element resistance map, as the engine's own table.
     */
    private static Map<DamageElement, Double> resistances(Map<String, Double> stated) {
        Map<DamageElement, Double> table = new LinkedHashMap<>();
        if (stated == null) {
            return table;
        }
        for (Map.Entry<String, Double> entry : stated.entrySet()) {
            table.put(DamageElement.valueOf(entry.getKey().toUpperCase(Locale.ROOT)), entry.getValue());
        }
        return table;
    }

    private static JsonObject readCapture(String path) {
        try {
            JsonObject capture = JsonParser.parseString(
                    Files.readString(Path.of(path), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray events = capture.getAsJsonArray("damage_events");
            if (events == null || events.isEmpty()) {
                throw new IllegalArgumentException(path + " has no damage_events");
            }
            return capture;
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read " + path + ": " + e, e);
        }
    }
}
