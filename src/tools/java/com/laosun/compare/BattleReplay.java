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
                + "of the same cast. None matches yet, and the two causes are different:");
        System.out.println("  · an \"Additional\" group is damage a RULE delivered, and the engine books that base as 0 "
                + "(the number enters through the rule's own scale, not through a skill row) - so there is nothing to "
                + "match until that path records its base;");
        System.out.println("  · a \"Break\" group's number is a break instance, whose base the engine derives from "
                + "toughness/level; the capture's 10883.71 sits between the engine's 6464.82 and 12929.64.");
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
}
