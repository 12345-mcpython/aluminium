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
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.state.Resource;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
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

        /**
         * Cast index to the row level the capture used, for the casts whose row is NOT the skill's own level.
         *
         * <p>Read off the capture the way the plan used to state it: the row a cast was read at. The payload cannot supply
         * this one - its {@code damage_percentage} is the instance's effective share (see {@code deriveCasts}) - so it is
         * declared here instead of scattered over hand-written cast entries.
         */
        private Map<Integer, Integer> levels = new LinkedHashMap<>();

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

        /**
         * States the member already holds when the window opens, by name (e.g. 1415's 【结界】) - the same
         * "unobserved prefix" idea as {@link CastSpec#getResources()}, for a buff rather than a counter.
         */
        private java.util.List<String> states;

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

        /**
         * The light cone it wears. When stated, the character's panel is <b>computed</b> from it plus the account dump's
         * relics - no absolute panel is written into the plan, so the panel itself is part of what the replay proves.
         */
        private ConeSpec cone;

        /**
         * Absolute attribute values, an <b>override</b> for a unit whose loadout is not in the dump. Superseded by
         * {@link #cone} when both are stated.
         */
        private Map<String, Double> attributes = new LinkedHashMap<>();
    }

    /**
     * One light cone: the id, the level and the 叠影 rank.
     */
    @Getter
    @Setter
    public static class ConeSpec {
        private Integer id;
        private Integer level = 80;
        private Integer rank = 1;
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

        /**
         * The payload's {@code enemies[uid].properties.MaxStance} - the bar a break empties.
         *
         * <p>Absent = 0, i.e. no bar: an enemy that cannot be broken. The older payload carried no toughness at all,
         * which is why break damage could only be checked by back-solving its number.
         */
        private Double toughness;
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

        /**
         * The row to read this cast at. Absent = the skill's own level: for a memosprite that is the level its spec
         * states (10), while the capture's fight may have been played at a lower one (measured: 长夜's slot 1 is the
         * Lv8 row, 晴空乐手's is Lv6), so the plan states it per cast rather than the tool guessing.
         */
        private Integer level;

        /** Which of the plan's enemies was the main target, 1-based. */
        private Integer target;

        /** How many times this skill was cast back to back; absent = once. */
        private Integer repeat = 1;

        /**
         * Counters the fight already held when this cast happened, as {@code {resource: value}} - the window's
         * <b>unobserved prefix</b>, stated per cast instead of invented once for the whole fight.
         *
         * <p>Reader: cast 4 of the committed fixture. The game's 忆灵技1 reads ten points of 【忆质】 there
         * ({@code 0.6 + (10/4) x 0.12 = 0.9} against the row's 0.6), while a replay that starts from zero earns only the
         * single point its first cast granted. The number is <b>derived from the capture's own arithmetic</b>, not
         * guessed - which is why it may be written here and why the tool prints it (see the row's `seeded:` line).
         *
         * <p>{@code double}: one of the captures' tallies (1409's 「本场战斗累计治疗数值」) is a sum of heals - 36907.2364
         * for cast 2 - and {@code Resource.setExactValue} stores it at that precision.
         */
        private Map<String, Double> resources;

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
        boolean derive = false;
        for (String arg : args) {
            if ("--strict".equals(arg)) {
                strict = true;
            } else if ("--detail".equals(arg)) {
                detail = true;
            } else if ("--derive".equals(arg)) {
                derive = true;
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
        if (plan == null || plan.getCapture() == null || (plan.getCasts().isEmpty() && !derive)) {
            System.out.println("replay: " + planPath + " needs a capture and at least one cast (or --derive)");
            return 2;
        }
        try {
            return replay(plan, planPath, strict, detail, derive);
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
        return replay(plan, planPath, strict, detail, false);
    }

    /**
     * The same, optionally deriving the cast table (and the enemy list) from the payload instead of the plan.
     *
     * <p>Why: a payload that states {@code cast_index} and {@code skill_id} already names every cast and the row it used
     * ({@code DerivedCasts}), and one that states each enemy's {@code properties} already names its panel and its toughness.
     * The plan then only has to say WHO is fighting - the party - and what the tool drives is a read of the capture.
     *
     * @param derive whether to derive {@code casts} (and, when the plan names none, the enemies) from the payload
     */
    static int replay(Plan plan, Path planPath, boolean strict, boolean detail, boolean derive) {
        JsonObject capture = readCapture(plan.getCapture());
        List<CaptureFlow.Cast> flow = CaptureFlow.group(capture.getAsJsonArray("damage_events"));
        System.out.println("capture:  " + plan.getCapture());
        System.out.println("plan:     " + planPath.getFileName());
        System.out.println("scenario: " + (plan.getName() == null ? "(unnamed)" : plan.getName()));
        if (plan.getNote() != null && !plan.getNote().isBlank()) {
            System.out.println("note:     " + plan.getNote());
        }

        if (derive) {
            deriveEnemies(plan, capture);
            deriveCasts(plan, flow, capture);
            System.out.println("derived:  " + plan.getCasts().size() + " cast(s) from the payload's cast_index; "
                    + plan.getEnemies().size() + " enemy(ies) from its enemy map");
        }

        java.util.Set<Integer> seenGroups = new java.util.LinkedHashSet<>();
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
            // One plan entry names one cast; a payload that states cast_index may still settle SEVERAL cast-classified
            // groups under one index (1413's ultimate: the servant's swing, then the ultimate's own hit). The entry's
            // target belongs to the first of them, so the check only applies there - the others are driven at the same
            // enemy, and a mismatch on them would compare two different groups against one number.
            if (seenGroups.add(group.index()) && spec.getTarget() != null && spec.getTarget() != captured) {
                System.out.println("replay: cast " + group.index() + " was aimed at enemy " + captured
                        + " in the capture but at " + spec.getTarget() + " in the plan");
                return 2;
            }
        }

        List<Character> party = new ArrayList<>();
        Map<Integer, Character> byCid = new LinkedHashMap<>();
        System.out.println();
        System.out.println("party (panels derived from cid + cone + the account dump's relics):");
        for (MemberSpec member : plan.getParty()) {
            Character character;
            if (member.getCone() != null) {
                character = AccountLoadout.assemble(new AccountLoadout.Build(
                        member.getCid(),
                        member.getLevel() == null ? 80 : member.getLevel(),
                        member.getEidolon() == null ? 0 : member.getEidolon(),
                        member.getCone().getId(),
                        member.getCone().getLevel() == null ? 80 : member.getCone().getLevel(),
                        member.getCone().getRank() == null ? 1 : member.getCone().getRank()));
            } else {
                character = CharacterFactory.create(member.getCid(),
                        member.getLevel() == null ? 80 : member.getLevel(), true, null, null,
                        member.getEidolon() == null ? 0 : member.getEidolon());
                applyOverrides(character, member.getAttributes());
            }
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
            if (spec.getToughness() != null) {
                // The bar a break empties: the newer payload states it per enemy (properties.MaxStance), and without it a
                // break can only be checked by back-solving the number the capture reports.
                enemy.setStance(spec.getToughness());
                enemy.setMaxStance(spec.getToughness());
            }
            enemy.heal(enemy.getMaxHp());
            enemies.add(enemy);
            System.out.printf(Locale.ROOT, "  %-18s HP %-16.6f DEF %-14.6f level %d%n",
                    enemy.getName(), enemy.getMaxHp(),
                    enemy.getAttribute(AttributeType.DEFENCE).get(), enemy.getLevel());
        }

        Battle battle = new Battle(party, enemies, new Random(0L));
        battle.startBattle();
        for (MemberSpec member : plan.getParty()) {
            if (member.getStates() == null || member.getStates().isEmpty()) {
                continue;
            }
            Character unit = byCid.get(member.getOwner());
            if (unit == null) {
                continue;
            }
            for (String state : member.getStates()) {
                // A duration past the window's end: the capture says the state was up, not when it expires.
                unit.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(state, 99));
            }
            // Printed like a seeded counter: a reader must be able to tell a state the capture already had from one the
            // replay earned.
            System.out.println("seeded states for " + member.getOwner() + ": " + member.getStates());
        }
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
        int differed = 0;
        int notComparable = 0;
        int foldedMatched = 0;
        int foldedUnmatched = 0;
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
            // The capture's own panel for THIS cast: a window is a fight already in progress, and a panel that moves
            // inside it (1413's 生命上限 2960 -> 4526 -> 4309) makes one static sheet wrong for two thirds of the rows.
            applyCastPanel(row, battle, byCid);
            seedResources(row, battle, byCid);
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
            // The capture's group lists what the SKILL's row dealt; a rule-delivered rider (an ADDITIONAL instance such as
            // cone 23042's burst, or a TRUE one such as 1415's zone) is booked by the engine under the same cast but is not
            // one of the skill's own hits, so it must not turn a matching row into DIFFERS. It is counted, printed, and
            // matched separately as the folded groups are.
            List<Double> engineBases = window.stream()
                    .filter(each -> each.damage().getType() == com.laosun.aluminium.enums.DamageType.NORMAL)
                    .map(each -> each.damage().breakdown().getOrDefault("base", 0.0))
                    .toList();
            long ruleInstances = window.stream()
                    .filter(each -> each.damage().getType() != com.laosun.aluminium.enums.DamageType.NORMAL)
                    .count();
            // The folded groups (derived hits, breaks) are matched against EVERY instance: they ARE the rule-delivered
            // ones the row comparison just excluded.
            List<Double> allBases = window.stream()
                    .map(each -> each.damage().breakdown().getOrDefault("base", 0.0))
                    .toList();
            BaseVerdict verdict = compareBases(row.group, engineBases);
            // The folded groups (derived hits, breaks) carry their own bases: match each against the engine instances
            // this cast settled, so "18 groups" means 18 numbers checked rather than 13 checked and 5 summed.
            List<Double> spare = new ArrayList<>(allBases);
            row.group().hitBases().forEach(spare::remove);
            int derivedOk = 0;
            int derivedMissing = 0;
            for (CaptureFlow.Cast derived : row.derived()) {
                int found = -1;
                double tolerance = Math.max(1e-6, Math.abs(derived.base()) * 1e-9);
                for (int i = 0; i < spare.size(); i++) {
                    if (Math.abs(spare.get(i) - derived.base()) <= tolerance) {
                        found = i;
                        break;
                    }
                }
                if (found >= 0) {
                    spare.remove(found);
                    derivedOk++;
                    foldedMatched++;
                } else {
                    derivedMissing++;
                    foldedUnmatched++;
                    System.out.printf(Locale.ROOT, "     %-9s %-10s capture base %-14.6f -> no engine instance (engine had %s)%n",
                            derived.classification(), derived.kind(), derived.base(), describeBases(spare));
                }
            }
            if (verdict.agrees()) {
                matched++;
            } else if (verdict.comparable()) {
                differed++;
            } else {
                notComparable++;
            }
            System.out.printf(Locale.ROOT, "%-4d %-6d %-10s %-10s %16.2f %16d %16.2f %10s%n",
                    row.cast, row.actor, row.kind, captureCell, row.captureDamage, window.size(), settled,
                    verdict.label());
            if (verdict.comparable() && !verdict.agrees() && row.spec.getLevel() != null
                    && !row.group.hitBases().isEmpty() && !engineBases.isEmpty()) {
                // The factor is measured, not read off the verdict's label: that one is rounded to two decimals, which is
                // too coarse to tell two adjacent row levels apart.
                double factor = row.group.hitBases().getFirst() / engineBases.getFirst();
                Integer hint = levelThatFits(row.spec, factor);
                if (hint != null && !hint.equals(row.spec.getLevel())) {
                    System.out.printf(Locale.ROOT, "     level:  the capture reads this row at %d, not %d (the engine is "
                            + "linear in the row's parameter, so one measured base is enough to say which level fits)%n",
                            hint, row.spec.getLevel());
                }
            }
            System.out.printf(Locale.ROOT, "     bases: capture %s%n", describeBases(row.group.hitBases()));
            System.out.printf(Locale.ROOT, "            engine  %s%s%n", describeBases(engineBases),
                    ruleInstances == 0 ? "" : "  (+" + ruleInstances + " rule instance(s))");
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
                "casts: %d in the capture ; driven: %d ; not drivable: %d ; bases reproduced: %d%n",
                rows.size(), driven, undrivable, matched);
        System.out.printf(Locale.ROOT, "bases: %d agree, %d differ, %d not comparable, of %d driven%n",
                matched, differed, notComparable, driven);
        System.out.printf(Locale.ROOT, "groups: %d in the capture = %d cast rows (%d base-ok, %d n/a) + %d folded "
                        + "(derived/break: %d matched, %d without a comparable engine base)%n",
                flow.size(), rows.size(), matched, notComparable, flow.size() - rows.size(),
                foldedMatched, foldedUnmatched);
        System.out.println();
        System.out.println("What is compared is the BASE (pre-zone), not the settled total: the capture is a window of a "
                + "fight already in progress and the plan deliberately installs no crit/boost, so a settled total is "
                + "not comparable by construction (AGENT.md §3.7zb). A differing base has one of three causes:");
        System.out.println("  · the row's SERVANT reads 「%生命上限」 off its own HP while the capture's arithmetic "
                + "follows the summoner's panel (长夜 50%, 晴空乐手 70% - the payload cannot say whose HP the game read);");
        System.out.println("  · the row is read at a level the plan does not state (a memosprite's spec says 10);");
        System.out.println("  · the base did not come from a row at all (damage_percentage 0: a tally the window "
                + "does not carry) - those rows say \"base n/a\" instead of pretending to compare.");
        System.out.println("A folded group (derived hit / break) is matched by BASE against the engine's own instances "
                + "of the same cast. None matches yet, and what has been RULED OUT is worth more than what is left:");
        System.out.println("  · an \"Additional\" group is NOT the rule damage the cast itself dealt (that instance is "
                + "already matched as the row's own hit), and it is NOT the 24% rider of any hit the capture lists: the "
                + "engine's rider follows only instances that count as attacks, and rule-delivered damage does not "
                + "(which is what the game does too). Its parent is therefore a hit the payload does not carry;");
        System.out.println("  · a \"Break\" group's number is NOT a formula gap: fed the toughness the bar actually gave "
                + "(25 points, level 80, break effect 0.15552 from the payload itself) the engine answers 10883.708573 "
                + "against the capture's 10883.708128 - 4e-8 relative, pinned by BreakBaseAgainstCaptureTest. The payload "
                + "carries no toughness bar, so the window's BAR STATE is what cannot be reproduced: the replay's own "
                + "breaks land at 30 and 15 consumed because that is where its bars empty.");
        return strict && differed > 0 ? 1 : 0;
    }

    /**
     * One row of the report: a captured cast (with the derived hits and breaks that followed it) and, when the plan
     * names it, the entry that drives it.
     */
    private record Row(int cast, long actor, String kind, double base, int hits, int targets, double captureDamage,
                       CaptureFlow.Cast group, CastSpec spec, List<CaptureFlow.Cast> derived) {
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
        CaptureFlow.Cast last = null;
        List<CaptureFlow.Cast> folded = new ArrayList<>();
        for (CaptureFlow.Cast group : flow) {
            if ("cast".equals(group.classification())) {
                if (cast != 0) {
                    rows.add(new Row(cast, actor, kind, base, hits, targets, damage, last, byCast.get(cast),
                            List.copyOf(folded)));
                }
                cast = group.index();
                actor = group.actor();
                kind = group.kind();
                base = group.base();
                hits = group.hits();
                targets = group.targets();
                damage = group.damage() + group.companion();
                last = group;
                folded.clear();
                continue;
            }
            if (cast == 0) {
                // A derived hit before any cast: not attributable, and nothing to compare it against.
                continue;
            }
            damage += group.damage() + group.companion();
            // Kept rather than only summed: each folded group carries its OWN base, and a base is exactly what this
            // tool exists to check. Folding the money while dropping the number would leave those groups unverified.
            folded.add(group);
        }
        if (cast != 0) {
            rows.add(new Row(cast, actor, kind, base, hits, targets, damage, last, byCast.get(cast),
                    List.copyOf(folded)));
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
        Skill atSpecLevel = skillAtSpecLevel(spec, battle, byCid);
        if (atSpecLevel == null || spec.getLevel() == null) {
            return atSpecLevel;
        }
        // The plan states the level the capture's numbers were taken at; the row is read at THAT level, not at the one
        // the spec happens to declare (a memosprite's all say 10).
        return new DefaultSkill(atSpecLevel.getCid(), atSpecLevel.getSkillSlot(), Math.max(1, spec.getLevel()));
    }

    /**
     * The skill a plan entry names, at whatever level the data gives it, or {@code null} when the data has no such row.
     */
    private static Skill skillAtSpecLevel(CastSpec spec, Battle battle, Map<Integer, Character> byCid) {
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
     * Installs the panel the capture's <b>own arithmetic</b> used for this cast.
     *
     * <p>A replay is a window of a fight already in progress, and a panel can move inside it (1413's 生命上限 reads
     * 2960.0287, then 4526.4984, then 4309.8792 - her own ultimate's base is 2.0 x that number, so one static sheet
     * cannot be right for all three). The payload states the panel per event, so each cast is driven at the panel its
     * own events carry.
     *
     * <p>For a <b>memosprite</b> cast the panel that matters is the servant's, which is frozen as a share of its
     * master's at summon time - so the share is re-stated against this cast's panel rather than inherited from the
     * sheet the battle started with. Its current life is left alone: nothing damages it in a scripted replay, and
     * healing it would fire events this window never saw.
     */
    private static void applyCastPanel(Row row, Battle battle, Map<Integer, Character> byCid) {
        Character master = byCid.get((int) row.actor);
        if (master == null || row.group == null) {
            return;
        }
        applyOverrides(master, Map.of(
                "ATTACK", row.group.attack(),
                "HEALTH", row.group.health(),
                "DEFENCE", row.group.defence()));
        // A memosprite's sheet is a share of its master's, so it moves with the master EVEN when the cast is the
        // character's own: 1413's ultimate is delivered by 长夜, and a stale servant sheet would price it at the panel of
        // an earlier cast (measured: 4526 instead of 4309 for cast 13).
        Summon servant = battle.memospriteOf(master);
        if (servant != null) {
            servant.setAttribute(AttributeType.HEALTH,
                    new DoubleValue(servantHealthRatio((int) row.actor) * row.group.health()));
        }
    }

    /**
     * Writes the counters the capture already held when this cast happened - the window's <b>prefix</b>, which the
     * payload does not carry and a replay cannot earn.
     *
     * <p>Printed as {@code seeded:} so a reader can tell a reproduced number from one the plan declared, and so the
     * fixture's own comments can say where each number was derived from (cast 4's ten points come from
     * {@code (0.09 - 0.06) x 4 / 0.12}).
     */
    private static void seedResources(Row row, Battle battle, Map<Integer, Character> byCid) {
        Character actor = byCid.get((int) row.actor);
        if (actor == null || row.spec.getResources() == null || row.spec.getResources().isEmpty()) {
            return;
        }
        row.spec.getResources().forEach((id, value) -> {
            Resource resource = actor.getResources().get(id);
            if (resource == null) {
                // The CAP only has to be at least the seed; a counter's real cap comes from the character's own
                // declaration when there is one. `register` takes an int cap, the seed itself stays exact.
                resource = actor.getResources().register(id, Math.max(1, (int) Math.ceil(value)), 0);
            }
            resource.setExactValue(value);
        });
        System.out.printf(Locale.ROOT, "     seeded: %s (the window's unobserved prefix)%n",
                row.spec.getResources());
    }

    /**
     * What share of its summoner's Max HP a memosprite's own sheet takes, from the same file the engine reads.
     */
    private static double servantHealthRatio(int ownerCid) {
        com.laosun.aluminium.beans.MemospriteSpec spec = com.laosun.aluminium.data.Memosprites.of(ownerCid);
        if (spec == null || spec.panel() == null) {
            return 1.0;
        }
        for (com.laosun.aluminium.beans.MemospriteSpec.Panel panel : spec.panel()) {
            if ("HEALTH".equalsIgnoreCase(panel.attribute()) && panel.percent() != null) {
                return panel.percent();
            }
        }
        return 1.0;
    }

    /**
     * The verdict on one cast's bases: whether they agree, and - when they do not - whether the difference is the
     * <b>servant's own HP share</b> (every engine instance exactly {@code ratio ×} the capture's).
     *
     * <p>That last shape is worth naming rather than printing a bare "differs": a memosprite's row says 「%生命上限」 and
     * the engine reads the <b>memorprite's</b> sheet (half of 长夜月's, 70% of 知更鸟•晴歌's), while every capture row's
     * base is {@code share × the summoner's panel} to the last digit. A run that reports the factor says which of the
     * two readings produced it; a run that reports "differs" leaves the next reader to re-derive it.
     */
    private static BaseVerdict compareBases(CaptureFlow.Cast group, List<Double> engineBases) {
        if (group == null || group.percentage() == 0) {
            return new BaseVerdict("base-n/a", false, false);
        }
        List<Double> capture = group.hitBases();
        if (capture.isEmpty() || engineBases.isEmpty()) {
            return new BaseVerdict("DIFFERS", false, true);
        }
        if (sameValues(capture, engineBases)) {
            boolean sameCount = capture.size() == engineBases.size();
            return new BaseVerdict(sameCount ? "base-ok" : "base-ok/count", true, true);
        }
        Double factor = uniformFactor(capture, engineBases);
        return new BaseVerdict(factor == null ? "DIFFERS"
                : String.format(Locale.ROOT, "DIFFERS-x%.2f", factor), false, true);
    }

    /**
     * The same <b>distinct</b> base values on both sides - so a cast driven against three living enemies where the
     * capture had one still reads as the same row, with the count reported separately.
     */
    private static boolean sameValues(List<Double> capture, List<Double> engine) {
        List<Double> left = new ArrayList<>(distinct(capture));
        for (double each : distinct(engine)) {
            int at = -1;
            for (int i = 0; i < left.size(); i++) {
                if (Math.abs(left.get(i) - each) <= Math.max(TOLERANCE, Math.abs(each) * TOLERANCE)) {
                    at = i;
                    break;
                }
            }
            if (at < 0) {
                return false;
            }
            left.remove(at);
        }
        return left.isEmpty();
    }

    private static List<Double> distinct(List<Double> values) {
        List<Double> unique = new ArrayList<>();
        for (double value : values) {
            boolean seen = false;
            for (double kept : unique) {
                if (Math.abs(kept - value) <= Math.max(TOLERANCE, Math.abs(value) * TOLERANCE)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                unique.add(value);
            }
        }
        return unique;
    }

    /**
     * The single factor every engine base carries over its capture counterpart, or {@code null} when there is none.
     *
     * <p>Compared over the <b>distinct</b> values, so a cast driven against more living enemies than the capture had
     * still reports its factor (casts 5 and 13 hit two enemies in the window and three on the replayed field).
     */
    private static Double uniformFactor(List<Double> capture, List<Double> engine) {
        List<Double> captureValues = distinct(capture);
        List<Double> engineValues = distinct(engine);
        if (captureValues.isEmpty() || captureValues.size() != engineValues.size()
                || Math.abs(captureValues.get(0)) <= TOLERANCE) {
            return null;
        }
        double factor = engineValues.get(0) / captureValues.get(0);
        for (int i = 0; i < captureValues.size(); i++) {
            if (Math.abs(engineValues.get(i) - factor * captureValues.get(i))
                    > Math.max(TOLERANCE, Math.abs(engineValues.get(i)) * 1e-6)) {
                return null;
            }
        }
        return factor;
    }

    /**
     * One cast's base verdict: what to print, whether it counts as reproduced, and whether it was comparable at all.
     */
    private record BaseVerdict(String label, boolean agrees, boolean comparable) {
    }

    /**
     * The bases as {@code "177.6017×5, 888.0086"}, so one line says both the value and the instance shape.
     */
    private static String describeBases(List<Double> bases) {
        if (bases == null || bases.isEmpty()) {
            return "(none)";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (double base : bases) {
            counts.merge(String.format(Locale.ROOT, "%.4f", base), 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder();
        counts.forEach((value, count) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(value);
            if (count > 1) {
                text.append('×').append(count);
            }
        });
        return text.toString();
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
    private static void applyOverrides(CanHit unit, Map<String, Double> attributes) {
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

    // ==================================================================
    // Derivation: the payload states the cast table and the enemy panels
    // ==================================================================

    /**
     * Fills the plan's enemies from the payload's own {@code enemies} map, for the ones that were actually fought.
     *
     * <p>Only enemies a damage event names are used: the map also lists units that never took a hit, and a plan that
     * carried them would make the action bar fight a crowd the capture never engaged.
     *
     * @param plan    the plan to fill
     * @param capture the parsed capture
     */
    private static void deriveEnemies(Plan plan, com.google.gson.JsonObject capture) {
        com.google.gson.JsonObject enemies = capture.getAsJsonObject("enemies");
        if (enemies == null) {
            return;
        }
        java.util.LinkedHashMap<Long, EnemySpec> derived = new java.util.LinkedHashMap<>();
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            long uid = element.getAsJsonObject().getAsJsonObject("target").get("uid").getAsLong();
            if (derived.containsKey(uid) || !enemies.has(String.valueOf(uid))) {
                continue;
            }
            com.google.gson.JsonObject unit = enemies.getAsJsonObject(String.valueOf(uid));
            com.google.gson.JsonObject properties = unit.getAsJsonObject("properties");
            EnemySpec spec = new EnemySpec();
            spec.setName(unit.has("name") ? unit.get("name").getAsString() : "enemy " + uid);
            spec.setHealth(numberOr(properties, "MaxHP", 0));
            spec.setDefence(numberOr(properties, "Defence", 0));
            spec.setSpeed(numberOr(properties, "Speed", 100));
            spec.setLevel((int) numberOr(properties, "Level", 95));
            spec.setToughness(numberOr(properties, "MaxStance", 0));
            for (String name : new String[]{"Physical", "Fire", "Ice", "Thunder", "Wind", "Quantum", "Imaginary"}) {
                double value = numberOr(properties, name + "Resistance", 0);
                if (value != 0) {
                    spec.getResistance().put(name.toUpperCase(java.util.Locale.ROOT), value);
                }
            }
            derived.put(uid, spec);
        }
        plan.setEnemies(new java.util.ArrayList<>(derived.values()));
        // A cast's "target" is an index into this list, so keep the uids in the same order for {@link #deriveCasts}.
        TARGET_UIDS.clear();
        TARGET_UIDS.addAll(derived.keySet());
    }

    /** The order {@link #deriveCasts} turns a target uid into a 1-based index (filled by {@link #deriveEnemies}). */
    private static final java.util.List<Long> TARGET_UIDS = new java.util.ArrayList<>();

    /**
     * Fills the plan's casts from the payload: {@code cast_index} says which cast, {@code skill_id} names the row, and the
     * row's {@code (cid, slot)} says whether it is the character's own slot or its memosprite's data slot.
     *
     * @param plan    the plan to fill
     * @param capture the parsed capture
     */
    private static void deriveCasts(Plan plan, List<CaptureFlow.Cast> flow, com.google.gson.JsonObject capture) {
        java.util.Map<Integer, Integer> servantOwner = servantOwners(plan);
        // The target is read off the GROUP the replay compares against, not off the cast's first event: a cast's value
        // of "which enemy was aimed at" is its group's first hit, and an AoE cast's first event may hit a different one.
        java.util.Map<Integer, Double> firstTargetByCast = new java.util.HashMap<>();
        for (CaptureFlow.Cast cast : flow) {
            if ("cast".equals(cast.classification())) {
                firstTargetByCast.putIfAbsent(cast.index(), cast.firstTarget());
            }
        }
        java.util.List<CastSpec> casts = new java.util.ArrayList<>();
        for (DerivedCasts.Entry entry : DerivedCasts.of(capture)) {
            CastSpec spec = new CastSpec();
            spec.setCast(entry.castIndex());
            boolean servant = servantOwner.containsKey(entry.cid());
            // A cast that names no skill row (the payload states skill_id 0 for 1413's ultimate) still names its caster;
            // the slot is then inferred from the event's own kind, which is what the payload does state.
            spec.setActor(servant ? servantOwner.get(entry.cid())
                    : entry.cid() != 0 ? entry.cid() : (int) entry.actor());
            spec.setCaster(servant ? "memosprite" : "character");
            if (servant) {
                spec.setSlotNumber(entry.slot());
                spec.setSlot(String.valueOf(entry.slot()));
            } else {
                int slot = entry.slot();
                if (slot < 1) {
                    slot = switch (entry.kind() == null ? "" : entry.kind()) {
                        case "Ultimate" -> 3;
                        case "Basic" -> 1;
                        case "Skill" -> 2;
                        default -> 0;
                    };
                }
                spec.setSlot(switch (slot) {
                    case 1 -> "COMMON";
                    case 2 -> "SKILL";
                    case 3 -> "ULTRA";
                    case 4 -> "TALENT";
                    default -> null;                        // an unknown slot is refused by the driver, loudly
                });
            }
            // The ROW LEVEL comes from the ACCOUNT, not from the payload: a capture never states which row level a hit
            // was read at, while the archive does (`skills` for the character's own slots, `memosprite` for its 忆灵's).
            // The plan's `levels` map still wins when it names one - a plan may know better than the archive.
            if (plan.getLevels() != null && plan.getLevels().containsKey(entry.castIndex())) {
                spec.setLevel(plan.getLevels().get(entry.castIndex()));
            } else {
                spec.setLevel(accountLevel(spec.getActor(), spec.getCaster(), entry.slot()));
            }
            Double firstTarget = firstTargetByCast.get(entry.castIndex());
            spec.setTarget(firstTarget == null ? null : enemyByHealth(plan, firstTarget));
            spec.setNote("derived from cast_index " + entry.castIndex() + " / skill " + entry.skillId());
            casts.add(spec);
        }
        plan.setCasts(casts);
    }

    /**
     * The 1-based plan index of the enemy whose 生命上限 the capture used, or {@code null} when none matches.
     *
     * @param plan  the plan
     * @param health the capture's {@code target_max_hp}
     * @return the 1-based index, or {@code null}
     */
    private static Integer enemyByHealth(Plan plan, double health) {
        for (int i = 0; i < plan.getEnemies().size(); i++) {
            Double stated = plan.getEnemies().get(i).getHealth();
            if (stated != null && Math.abs(stated - health) <= Math.max(1e-6, Math.abs(health) * 1e-9)) {
                return i + 1;
            }
        }
        return null;
    }

    /**
     * Which of the party's memosprites each servant id belongs to - read off the memosprite specs, so a servant row is
     * attributed to its summoner without a hand-written table.
     *
     * @param plan the plan
     * @return servant id to owner cid
     */
    private static java.util.Map<Integer, Integer> servantOwners(Plan plan) {
        java.util.Map<Integer, Integer> owners = new java.util.LinkedHashMap<>();
        for (MemberSpec member : plan.getParty()) {
            var spec = com.laosun.aluminium.data.Memosprites.of(member.getCid());
            if (spec != null && spec.servantId() != null) {
                owners.put(spec.servantId(), member.getCid());
            }
        }
        return owners;
    }

    private static double numberOr(com.google.gson.JsonObject object, String key, double fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsDouble();
    }


    /**
     * Which level of a skill row carries the multiplier the capture used.
     *
     * <p>Read from {@code skills.json}'s per-level parameter rows: a level whose row states the capture's
     * {@code damage_percentage} is the level the game read. Ambiguity (a parameter identical across levels) keeps the
     * lowest level, and no match at all leaves {@code null} - the driver then uses the skill's own level, as before.
     *
     * @param cid        the character or servant id
     * @param slot       the data slot
     * @param percentage the capture's multiplier for this cast
     * @return the 1-based level, or {@code null} when no row states that number
     */
    private static Integer rowLevelOf(int cid, int slot, double percentage) {
        if (percentage <= 0) {
            return null;
        }
        com.google.gson.JsonArray rows = parameterRows(cid, slot);
        if (rows == null) {
            return null;
        }
        for (int level = 1; level <= rows.size(); level++) {
            for (com.google.gson.JsonElement value : rows.get(level - 1).getAsJsonArray()) {
                double stated = value.getAsDouble();
                if (Math.abs(stated - percentage) <= Math.max(1e-9, Math.abs(percentage) * 1e-6)) {
                    return level;
                }
            }
        }
        return null;
    }

    /** The per-level parameter rows of one skill, or {@code null}. */
    private static com.google.gson.JsonArray parameterRows(int cid, int slot) {
        com.google.gson.JsonObject root = SKILLS.get();
        if (root == null || !root.has(String.valueOf(cid))) {
            return null;
        }
        com.google.gson.JsonObject slots = root.getAsJsonObject(String.valueOf(cid));
        if (!slots.has(String.valueOf(slot))) {
            return null;
        }
        com.google.gson.JsonElement params = slots.getAsJsonObject(String.valueOf(slot)).get("param_list");
        return params == null || !params.isJsonArray() ? null : params.getAsJsonArray();
    }

    /** {@code skills.json}, read once. */
    private static final class SkillsHolder {
        private static final com.google.gson.JsonObject VALUE = load();

        private static com.google.gson.JsonObject load() {
            try (java.io.InputStream stream = BattleReplay.class.getResourceAsStream("/game/data/skills.json")) {
                if (stream == null) {
                    return null;
                }
                return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException e) {
                return null;
            }
        }
    }

    private static final java.util.function.Supplier<com.google.gson.JsonObject> SKILLS = () -> SkillsHolder.VALUE;


    /**
     * The row level the account states for this slot: {@code skills.basic/skill/ult/talent} for a character's own slot,
     * {@code memosprite.skill} for its 忆灵's data slots.
     *
     * <p>Read from {@code game/data/account.json} (a digest of the account archive). {@code null} when the account does not
     * name the character - the driver then uses the skill's own level, as before.
     *
     * @param actor  the casting character's cid
     * @param caster {@code character} or {@code memosprite}
     * @param slot   the row's slot
     * @return the level, or {@code null}
     */
    private static Integer accountLevel(Integer actor, String caster, int slot) {
        com.google.gson.JsonObject account = ACCOUNT.get();
        if (account == null || actor == null || !account.has("characters")
                || !account.getAsJsonObject("characters").has(String.valueOf(actor))) {
            return null;
        }
        com.google.gson.JsonObject who = account.getAsJsonObject("characters").getAsJsonObject(String.valueOf(actor));
        String group = "memosprite".equals(caster) ? "memosprite" : "skills";
        if (!who.has(group) || !who.getAsJsonObject(group).isJsonObject()) {
            return null;
        }
        com.google.gson.JsonObject levels = who.getAsJsonObject(group);
        String key = "memosprite".equals(caster)
                ? (slot == 1 ? "skill" : slot == 4 ? "talent" : null)
                : switch (slot) {
                    case 1 -> "basic";
                    case 2 -> "skill";
                    case 3 -> "ult";
                    case 4 -> "talent";
                    default -> null;
                };
        if (key == null || !levels.has(key)) {
            return null;
        }
        // The archive's number is the BASE level. Whether the game reads higher (1413's 「长夜」 is 6 in the archive
        // while the capture reads its row at 8, and 1415's 「德谬歌」 is 6 and the capture reads 6) is per character and
        // per slot, so the tool does not guess: it prints the level the capture implies (see `levelThatFits`) and the
        // plan states it. Measured the hard way - a blanket "+2 for ascension 6" fixed 1413 and broke 1415.
        return levels.get(key).getAsInt();
    }

    /** {@code account.json}, read once. */
    private static final class AccountHolder {
        private static final com.google.gson.JsonObject VALUE = load();

        private static com.google.gson.JsonObject load() {
            try (java.io.InputStream stream = BattleReplay.class.getResourceAsStream("/game/data/account.json")) {
                if (stream == null) {
                    return null;
                }
                return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,
                        java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException e) {
                return null;
            }
        }
    }

    private static final java.util.function.Supplier<com.google.gson.JsonObject> ACCOUNT = () -> AccountHolder.VALUE;


    /**
     * The row level whose parameter explains the capture's base, given that the engine is <b>linear</b> in that parameter
     * (every other factor - the panel, the segment ratios, the zones - is the same at any level).
     *
     * <p>Measured, not assumed: the engine's own base at the level it used gives {@code K = base / param(level)}, and the
     * capture's base divided by {@code K} is the parameter the row must have had. That is how 1413's 忆灵技1 is pinned to
     * Lv8 in the recorded fight even though the account archive states 6 - the two disagree, and the capture decides.
     *
     * @param spec   the cast being driven
     * @param factor the capture's first base divided by the engine's first base
     * @return the level that would fit, or {@code null} when the numbers cannot say
     */
    private static Integer levelThatFits(CastSpec spec, double factor) {
        if (factor <= 0 || Math.abs(factor - 1) < 1e-9) {
            return null;
        }
        // A memosprite's row lives under its own servant id, not under its summoner's: read the servant id off the
        // memosprite spec, the same way the casts were attributed.
        int rowCid = spec.getActor() == null ? 0 : spec.getActor();
        if ("memosprite".equals(spec.getCaster())) {
            var summon = com.laosun.aluminium.data.Memosprites.of(rowCid);
            if (summon == null || summon.servantId() == null) {
                return null;
            }
            rowCid = summon.servantId();
        }
        com.google.gson.JsonArray rows = parameterRows(rowCid,
                spec.getSlotNumber() != null ? spec.getSlotNumber() : slotNumber(spec.getSlot()));
        if (rows == null || spec.getLevel() == null || spec.getLevel() < 1 || spec.getLevel() > rows.size()) {
            return null;
        }
        double usedParam = rows.get(spec.getLevel() - 1).getAsJsonArray().get(0).getAsDouble();
        if (usedParam <= 0) {
            return null;
        }
        // The capture's base is `factor` times what this level produced, and the row's parameter scales that base
        // linearly, so the parameter it used is `usedParam * factor`.
        double needed = usedParam * factor;
        for (int level = 1; level <= rows.size(); level++) {
            double stated = rows.get(level - 1).getAsJsonArray().get(0).getAsDouble();
            if (Math.abs(stated - needed) <= Math.max(1e-9, needed * 1e-6)) {
                return level;
            }
        }
        return null;
    }

    /**
     * The data slot a {@link SkillType} name stands for, or {@code 0} for an unknown spelling.
     */
    private static int slotNumber(String slot) {
        if (slot == null) {
            return 0;
        }
        return switch (slot) {
            case "COMMON" -> 1;
            case "SKILL" -> 2;
            case "ULTRA" -> 3;
            case "TALENT" -> 4;
            default -> 0;
        };
    }


    /**
     * The levels a character's ascension bonuses add to its 忆灵's skill rows: 0 at ascension 0-1, then one per unlocked
     * node, i.e. 2 at ascension 6.
     *
     * <p>Measured, not assumed: 1413's archive level is 6 and the capture's row is 8 (level 8 reproduces the cast, level 6
     * is 0.83x off), and her ascension is 6.
     *
     * @param ascension the character's ascension
     * @return the bonus
     */
    private static int ascensionBonus(int ascension) {
        return Math.min(2, Math.max(0, ascension / 2));
    }

}
