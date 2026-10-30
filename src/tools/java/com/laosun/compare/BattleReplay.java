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

        /**
         * Cast index to the states that were already up when it was cast, as {@code {cast: [state, ...]}}.
         *
         * <p>Why per cast and not per member: a state's window is a fact about the moment. 1415's 【往昔的涟漪】 is up only
         * after she has cast her ultimate, and the capture shows her normal basic before that and the enhanced one after;
         * a battle-start seed would give her the enhanced row from turn one.
         */
        private Map<Integer, List<String>> statesAt = new LinkedHashMap<>();

        /**
         * Casts the fight performed that the capture cannot show, because they deal no damage - run before a named cast and
         * <b>not</b> compared.
         *
         * <p>Reader: 1415's ultimate. It installs her enhanced basic into the {@code COMMON} slot ({@code REPLACE_SKILL}) and
         * opens 【往昔的涟漪】, and it settles no damage, so no event names it - yet the capture's casts 59/76 use the row it
         * installs. Seeding the state alone is not enough (measured: still one instance where the capture has seven).
         */
        private List<PreludeSpec> preludes = new ArrayList<>();

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

        /**
         * The capture cast index at or after which this enemy is on the field; absent = it is there from the start.
         *
         * <p>Read off the capture (the first cast that hits it), because a recorded fight brings its enemies in waves - the
         * 2026-10-30 one over six moments - and an AoE's share depends on how many targets exist when it lands.
         */
        private Integer arrivesAt;
    }

    /**
     * A cast to perform before another one, without comparing it - see {@link Plan#getPreludes()}.
     */
    @Getter
    @Setter
    public static class PreludeSpec {

        /** The cast index this runs before. */
        private Integer before;

        /** The character that casts it. */
        private Integer actor;

        /** A {@link com.laosun.aluminium.enums.SkillType} name. */
        private String slot;

        /** Which of the plan's enemies to aim at, 1-based; absent = no target (a buff/install cast). */
        private Integer target;

        /** Why the capture cannot show this cast. */
        private String note;
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

        /**
         * The payload's {@code type} of this group ({@code Servant} / {@code Ultimate} / {@code Basic} / ...).
         *
         * <p>Why it is part of the key: one {@code cast_index} may settle MORE than one skill - 1413's cast 16 is her
         * servant's swing and her ultimate in the same index - and a plan keyed by the index alone could only state one.
         * Absent = the entry matches any kind, which is what every older plan does.
         */
        private String kind;

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

        java.util.Set<String> seenGroups = new java.util.LinkedHashSet<>();
        Map<String, CastSpec> byCast = new LinkedHashMap<>();
        for (CastSpec spec : plan.getCasts()) {
            if (spec.getCast() == null) {
                System.out.println("replay: a plan cast has no \"cast\" index");
                return 2;
            }
            if (byCast.put(specKey(spec.getCast(), spec.getKind()), spec) != null) {
                System.out.println("replay: cast " + spec.getCast()
                        + (spec.getKind() == null ? "" : " (" + spec.getKind() + ")") + " is named twice");
                return 2;
            }
        }

        // The capture is the authority on which casts existed; the plan may only say HOW each was cast.
        for (CaptureFlow.Cast group : flow) {
            if (!"cast".equals(group.classification())) {
                continue;
            }
            CastSpec spec = specFor(byCast, group);
            if (spec == null) {
                System.out.println("replay: the plan has no entry for cast " + group.index()
                        + " (actor " + group.actor() + ", " + group.kind() + ", " + group.hits() + " hit(s))");
                return 2;
            }
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
            // The plan's target must be one the cast actually hit - not necessarily the FIRST one: an AoE's row can pay
            // its main target double, and the tool drives such a row at the enemy the capture dealt the most to.
            boolean hit = spec.getTarget() != null && spec.getTarget() >= 1 && spec.getTarget() <= plan.getEnemies().size()
                    && groupHitHealths(capture, group)
                    .contains(plan.getEnemies().get(spec.getTarget() - 1).getHealth());
            if (seenGroups.add(specKey(group.index(), group.kind()))
                    && spec.getTarget() != null && !hit && spec.getTarget() != captured) {
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

        // Only the enemies that were there on the capture's first cast; the rest join before the cast they first appear in.
        List<Enemy> present = new ArrayList<>();
        List<Enemy> latecomers = new ArrayList<>();
        int firstCast = flow.isEmpty() ? 0 : flow.get(0).index();
        for (int i = 0; i < enemies.size(); i++) {
            Integer arrivesAt = plan.getEnemies().get(i).getArrivesAt();
            if (arrivesAt == null || arrivesAt <= firstCast) {
                present.add(enemies.get(i));
            } else {
                latecomers.add(enemies.get(i));
            }
        }
        Battle battle = new Battle(party, present, new Random(0L));
        CURRENT_BATTLE = battle;
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
            // A 忆灵 can leave the field and come back (1413's slot 7 dismisses 长夜, and the capture shows it acting again
            // before cast 69), so a memosprite cast whose summon is missing summons it first. The arrival event is buffered
            // until the next settle, so `Battle.fireSummoned` now skips an arrival whose unit already left - without that,
            // this re-summon fired "when summoned" for a corpse and every rule naming `summon` threw.
            if ("memosprite".equalsIgnoreCase(row.spec.getCaster())) {
                Character owner = byCid.get(row.spec.getActor());
                if (owner != null && battle.memospriteOf(owner) == null) {
                    battle.summonMemosprite(owner);
                    System.out.println("     re-summoned: the 忆灵 of " + row.spec.getActor()
                            + " had left the field, and the capture shows it acting again before cast " + row.cast);
                }
            }
            for (Enemy latecomer : latecomers) {
                int index = enemies.indexOf(latecomer);
                Integer arrivesAt = index < 0 ? null : plan.getEnemies().get(index).getArrivesAt();
                if (arrivesAt != null && arrivesAt <= row.cast && !battle.enemyUnits().contains(latecomer)) {
                    battle.addEnemy(latecomer);
                    System.out.printf(Locale.ROOT, "     arrived: %s joins before cast %d (the capture first hits it here)%n",
                            latecomer.getName(), row.cast);
                }
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
            runPreludes(row, battle, byCid, plan, enemies);
            applyStates(row, battle, byCid, plan);
            applyCastPanel(row, battle, byCid);
            // The bars are put where the capture had them BEFORE this cast, not only after it: an add that left the field
            // between two casts must be gone from this cast's target list, and only a pre-cast sync can say that.
            syncBars(row, capture, enemies, plan, true);
            seedResources(row, battle, byCid);
            CanHit target = row.spec.getTarget() == null || row.spec.getTarget() < 1
                    || row.spec.getTarget() > enemies.size()
                    ? null : enemies.get(row.spec.getTarget() - 1);
            window.clear();
            int repeat = row.spec.getRepeat() == null ? 1 : Math.max(1, row.spec.getRepeat());
            for (int i = 0; i < repeat; i++) {
                battle.castImmediate(skill, caster, target == null ? List.of() : List.of(target));
            }
            syncBars(row, capture, enemies, plan);
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
    private static List<Row> rowsOf(List<CaptureFlow.Cast> flow, Map<String, CastSpec> byCast) {
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
                    rows.add(new Row(cast, actor, kind, base, hits, targets, damage, last,
                            specFor(byCast, last), List.copyOf(folded)));
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
            rows.add(new Row(cast, actor, kind, base, hits, targets, damage, last, specFor(byCast, last),
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
            spec.setArrivesAt(firstHitCast(capture, uid));
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
        // One entry per GROUP, not per cast index: a single index may settle two different skills (1413's cast 16 is her
        // servant's swing and her ultimate), and the entry is keyed by (cast, kind) so both can be driven.
        java.util.Map<String, com.google.gson.JsonObject> firstEvent = new java.util.HashMap<>();
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            firstEvent.putIfAbsent(event.get("cast_index").getAsInt() + "|" + event.get("type").getAsString(), event);
        }
        java.util.Set<String> seenGroups = new java.util.LinkedHashSet<>();
        java.util.Map<Integer, CastSpec> byCast = new java.util.LinkedHashMap<>();
        java.util.List<CastSpec> casts = new java.util.ArrayList<>();
        for (CaptureFlow.Cast group : flow) {
            if (!"cast".equals(group.classification())) {
                continue;
            }
            com.google.gson.JsonObject event = firstEvent.get(group.index() + "|" + group.kind());
            if (event == null) {
                continue;
            }
            long skillId = event.has("skill_id") ? event.get("skill_id").getAsLong() : 0;
            int[] row = skillId == 0 ? null : DerivedCasts.resolve(skillId);
            int cid = row == null ? 0 : row[0];
            int slot = row == null ? -1 : row[1];
            com.google.gson.JsonObject zones = event.getAsJsonObject("zones");
            double percentage = zones.has("damage_percentage") ? zones.get("damage_percentage").getAsDouble() : 0;

            CastSpec spec = new CastSpec();
            spec.setCast(group.index());
            spec.setKind(group.kind());
            boolean servant = servantOwner.containsKey(cid);
            spec.setActor(servant ? servantOwner.get(cid) : cid != 0 ? cid : (int) group.actor());
            spec.setCaster(servant ? "memosprite" : "character");
            if (servant) {
                spec.setSlotNumber(slot);
                spec.setSlot(String.valueOf(slot));
            } else {
                int resolved = slot >= 1 ? slot : switch (group.kind() == null ? "" : group.kind()) {
                    case "Ultimate" -> 3;
                    case "Basic" -> 1;
                    case "Skill" -> 2;
                    default -> 0;
                };
                spec.setSlot(switch (resolved) {
                    case 1 -> "COMMON";
                    case 2 -> "SKILL";
                    case 3 -> "ULTRA";
                    case 4 -> "TALENT";
                    // Slot 8 is 1415's 强化普攻: her ultimate REPLACE_SKILLs it into COMMON, which is the slot the engine
                    // finds it in while 【往昔的涟漪】 is up. The capture reads it at the character's basic level.
                    case 8 -> "COMMON";
                    default -> null;
                });
            }
            if (plan.getLevels() != null && plan.getLevels().containsKey(group.index())) {
                spec.setLevel(plan.getLevels().get(group.index()));
            } else {
                spec.setLevel(accountLevel(spec.getActor(), spec.getCaster(), slot));
            }
            Long main = mainTargetOf(capture, group);
            double mainPercentage = mainPercentageOf(capture, group, main);
            // A `per_resource_share` row states its share as `parameter x points`, so the capture's own percentage is the
            // points divided by the parameter - and the LEVEL is whichever row makes that a whole number. Both are read
            // off the capture; a row that states nothing leaves them alone.
            int[] pool = perResourcePoolOf(skillId == 0 ? -1 : cid, slot, mainPercentage, spec.getLevel());
            if (pool != null) {
                spec.setLevel(pool[0]);
                spec.setResources(java.util.Map.of(resourceNameOf(skillId == 0 ? -1 : cid, slot), (double) pool[1]));
            } else {
                Integer stepped = resourcePoolOf(skillId == 0 ? -1 : cid, slot, spec.getLevel(), percentage);
                if (stepped != null) {
                    spec.setResources(java.util.Map.of(resourceNameOf(skillId == 0 ? -1 : cid, slot),
                            (double) stepped));
                }
            }
            // The cast is DRIVEN at the enemy the capture dealt the most to - its "main" target. For a row whose shares
            // differ per target (11413's slot 7 pays the main target double), driving at an underling halves every base,
            // measured on cast 58.
            Integer targetIndex = main == null ? null : TARGET_UIDS.indexOf(main);
            spec.setTarget(targetIndex == null || targetIndex < 0 || targetIndex >= plan.getEnemies().size()
                    ? null : targetIndex + 1);
            spec.setNote("derived from cast_index " + group.index() + " (" + group.kind() + ") / skill " + skillId);
            casts.add(spec);
            seenGroups.add(specKey(group.index(), group.kind()));
        }
        plan.setCasts(casts);
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
        // The archive names two memosprite rows - `skill` and `talent` - and every damaging 忆灵 row (1 and 7 alike) is
        // read at the `skill` level; only slot 4 is the talent.
        String key = "memosprite".equals(caster)
                ? (slot == 4 ? "talent" : "skill")
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


    /**
     * The points of the row's own counter that the capture's multiplier implies, or {@code null} when the row states no
     * {@code resource_share} or the arithmetic is not an integer.
     *
     * @param entry the derived cast (its {@code percentage} is the capture's instance share)
     * @param spec  the cast being built (its level is the row level to read)
     * @return the points, or {@code null}
     */
    private static Integer resourcePoolOf(DerivedCasts.Entry entry, CastSpec spec) {
        com.google.gson.JsonObject spec0 = skillEffectOf(entry.cid(), entry.slot());
        if (spec0 == null || !spec0.has("resource_share") || spec.getLevel() == null) {
            return null;
        }
        com.google.gson.JsonObject shareSpec = spec0.getAsJsonObject("resource_share");
        com.google.gson.JsonArray rows = parameterRows(entry.cid(), entry.slot());
        if (rows == null || spec.getLevel() < 1 || spec.getLevel() > rows.size()) {
            return null;
        }
        com.google.gson.JsonArray row = rows.get(spec.getLevel() - 1).getAsJsonArray();
        int stepIndex = shareSpec.get("step_param").getAsInt();
        int shareIndex = shareSpec.get("share_param").getAsInt();
        if (stepIndex >= row.size() || shareIndex >= row.size()) {
            return null;
        }
        double base = row.get(0).getAsDouble();
        double stepSize = row.get(stepIndex).getAsDouble();
        double share = row.get(shareIndex).getAsDouble();
        if (stepSize <= 0 || share <= 0) {
            return null;
        }
        double ratio = spec0.has("segments") ? 1.0 : 1.0;
        if (spec0.has("segments")) {
            for (com.google.gson.JsonElement each : spec0.getAsJsonArray("segments")) {
                ratio = Math.min(ratio, each.getAsDouble());
            }
        }
        if (entry.percentage() <= 0) {
            return null;
        }
        double steps = (entry.percentage() / ratio - base) / share;
        double rounded = Math.rint(steps);
        if (rounded < 0 || Math.abs(steps - rounded) > 1e-6) {
            return null;
        }
        return (int) Math.round(rounded * stepSize);
    }

    /**
     * The name of the counter a row's {@code resource_share} reads.
     *
     * @param entry the derived cast
     * @return the counter's id
     */
    private static String resourceNameOf(DerivedCasts.Entry entry) {
        com.google.gson.JsonObject spec = skillEffectOf(entry.cid(), entry.slot());
        return spec.getAsJsonObject("resource_share").get("resource").getAsString();
    }

    /** The {@code skill_effects.json} row of one skill, or {@code null}. */
    private static com.google.gson.JsonObject skillEffectOf(int cid, int slot) {
        com.google.gson.JsonObject root = EFFECTS.get();
        if (root == null || cid <= 0 || slot <= 0 || !root.has(String.valueOf(cid))) {
            return null;
        }
        com.google.gson.JsonObject slots = root.getAsJsonObject(String.valueOf(cid));
        return slots.has(String.valueOf(slot)) ? slots.getAsJsonObject(String.valueOf(slot)) : null;
    }

    /** {@code skill_effects.json}, read once. */
    private static final class EffectsHolder {
        private static final com.google.gson.JsonObject VALUE = load();

        private static com.google.gson.JsonObject load() {
            try (java.io.InputStream stream =
                         BattleReplay.class.getResourceAsStream("/game/data/skill_effects.json")) {
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

    private static final java.util.function.Supplier<com.google.gson.JsonObject> EFFECTS = () -> EffectsHolder.VALUE;


    /**
     * The key one cast entry is stored under: the index plus the group's kind when the entry names one.
     *
     * @param cast the payload's cast index
     * @param kind the entry's kind, or {@code null}
     * @return the key
     */
    private static String specKey(int cast, String kind) {
        return kind == null || kind.isBlank() ? String.valueOf(cast) : cast + "|" + kind.trim();
    }

    /**
     * The plan entry for one capture group: its own kind first, then the kind-less entry every older plan uses.
     *
     * @param byCast the plan's entries
     * @param group  the capture's group
     * @return the entry, or {@code null}
     */
    private static CastSpec specFor(Map<String, CastSpec> byCast, CaptureFlow.Cast group) {
        CastSpec exact = byCast.get(specKey(group.index(), group.kind()));
        return exact != null ? exact : byCast.get(specKey(group.index(), null));
    }


    /**
     * The 1-based plan index of the enemy whose 生命上限 the capture used, or {@code null} when none matches.
     *
     * @param plan   the plan
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
     * The points of the row's own counter that the capture's multiplier implies, or {@code null} when the row states no
     * {@code resource_share} or the arithmetic is not an integer.
     *
     * @param cid        the row's character or servant id
     * @param slot       the data slot
     * @param level      the row level, or {@code null}
     * @param percentage the capture's instance share
     * @return the points, or {@code null}
     */
    private static Integer resourcePoolOf(int cid, int slot, Integer level, double percentage) {
        com.google.gson.JsonObject spec = skillEffectOf(cid, slot);
        if (spec == null || !spec.has("resource_share") || level == null || percentage <= 0) {
            return null;
        }
        com.google.gson.JsonObject shareSpec = spec.getAsJsonObject("resource_share");
        com.google.gson.JsonArray rows = parameterRows(cid, slot);
        if (rows == null || level < 1 || level > rows.size()) {
            return null;
        }
        com.google.gson.JsonArray row = rows.get(level - 1).getAsJsonArray();
        int stepIndex = shareSpec.get("step_param").getAsInt();
        int shareIndex = shareSpec.get("share_param").getAsInt();
        if (stepIndex >= row.size() || shareIndex >= row.size()) {
            return null;
        }
        double base = row.get(0).getAsDouble();
        double stepSize = row.get(stepIndex).getAsDouble();
        double share = row.get(shareIndex).getAsDouble();
        if (stepSize <= 0 || share <= 0) {
            return null;
        }
        double ratio = 1.0;
        if (spec.has("segments")) {
            for (com.google.gson.JsonElement each : spec.getAsJsonArray("segments")) {
                ratio = Math.min(ratio, each.getAsDouble());
            }
        }
        double steps = (percentage / ratio - base) / share;
        double rounded = Math.rint(steps);
        if (rounded < 0 || Math.abs(steps - rounded) > 1e-6) {
            return null;
        }
        return (int) Math.round(rounded * stepSize);
    }

    /**
     * The name of the counter a row's {@code resource_share} reads.
     *
     * @param cid  the row's character or servant id
     * @param slot the data slot
     * @return the counter's id
     */
    private static String resourceNameOf(int cid, int slot) {
        com.google.gson.JsonObject spec = skillEffectOf(cid, slot);
        com.google.gson.JsonObject share = spec.has("resource_share")
                ? spec.getAsJsonObject("resource_share")
                : spec.getAsJsonObject("per_resource_share");
        return share.get("resource").getAsString();
    }


    /**
     * The {@code (level, points)} a {@code per_resource_share} row implies, or {@code null} when the row is not that shape
     * or no level makes the points a whole number.
     *
     * <p>Why the level comes from here: the share is {@code parameter x points}, so a level whose parameter divides the
     * capture's percentage into an integer is a candidate, and the capture's three casts of 11413's slot 7 all land on
     * Lv1 (25 / 34 / 60 points) - the account's memosprite level 6 would need 12.5 points for cast 18, which is not a
     * number of points the game can hold.
     *
     * @param cid        the row's character or servant id
     * @param slot       the data slot
     * @param percentage the capture's instance share
     * @return {@code {level, points}}, or {@code null}
     */
    private static int[] perResourcePoolOf(int cid, int slot, double percentage, Integer preferred) {
        com.google.gson.JsonObject spec = skillEffectOf(cid, slot);
        if (spec == null || !spec.has("per_resource_share") || percentage <= 0) {
            return null;
        }
        com.google.gson.JsonObject share = spec.getAsJsonObject("per_resource_share");
        int main = share.get("main_param").getAsInt();
        int other = share.get("other_param").getAsInt();
        com.google.gson.JsonArray rows = parameterRows(cid, slot);
        if (rows == null) {
            return null;
        }
        // The capture's event may be the MAIN target's instance or another one's: the row prices them differently
        // (11413's slot 7 pays #1 on the main and #2 on the rest), so both parameters are candidates. The account's level is
        // tried first - it is the game's own number - and only then the rest, because a level other than the one the game
        // read can also divide the share into a whole number of points.
        java.util.List<Integer> order = new java.util.ArrayList<>();
        if (preferred != null && preferred >= 1 && preferred <= rows.size()) {
            order.add(preferred);
        }
        for (int level = 1; level <= rows.size(); level++) {
            if (!order.contains(level)) {
                order.add(level);
            }
        }
        for (int level : order) {
            com.google.gson.JsonArray row = rows.get(level - 1).getAsJsonArray();
            if (level == order.getFirst()) {
                // The share came from the MAIN instance, so the row's main parameter is the reading to try first.
                if (main < row.size() && row.get(main).getAsDouble() > 0) {
                    double points = percentage / row.get(main).getAsDouble();
                    double rounded = Math.rint(points);
                    if (rounded >= 1 && Math.abs(points - rounded) <= 1e-6) {
                        return new int[]{level, (int) Math.round(rounded)};
                    }
                }
            }
            for (int index : new int[]{main, other}) {
                if (index < 0 || index >= row.size()) {
                    continue;
                }
                double param = row.get(index).getAsDouble();
                if (param <= 0) {
                    continue;
                }
                double points = percentage / param;
                double rounded = Math.rint(points);
                if (rounded >= 1 && Math.abs(points - rounded) <= 1e-6) {
                    return new int[]{level, (int) Math.round(rounded)};
                }
            }
        }
        return null;
    }


    /**
     * The cast index of the first event that lands on one enemy - when it joined the fight, as far as the capture can say.
     *
     * @param capture the parsed capture
     * @param uid     the enemy's uid
     * @return the 1-based cast index, or {@code null} when the payload never hits it
     */
    private static Integer firstHitCast(com.google.gson.JsonObject capture, long uid) {
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            if (event.get("cast_index") == null) {
                return null;
            }
            if (event.getAsJsonObject("target").get("uid").getAsLong() == uid) {
                return event.get("cast_index").getAsInt();
            }
        }
        return null;
    }


    /**
     * Moves every enemy the capture's cast touched to the bar the capture states afterwards.
     *
     * <p>Why: the payload carries {@code hp_before} / {@code hp_after} per event, so a replay can keep the recorded fight's
     * casualties instead of producing its own. Measured on 1413's AoE (cast 18 / 58): with all nine enemies laid out at turn
     * one the row faced nine targets while the capture faced two.
     *
     * @param row     the cast that was just driven
     * @param capture the parsed capture
     * @param enemies the plan's enemies, in plan order
     * @param plan    the plan
     */
    private static void syncBars(Row row, com.google.gson.JsonObject capture, List<Enemy> enemies, Plan plan) {
        syncBars(row, capture, enemies, plan, false);
    }

    /**
     * The same, optionally reading the bars as they stood <b>before</b> this cast.
     *
     * <p>Why both sides: a bar the capture takes to 0 <em>during</em> this cast must not be emptied before it - that would
     * leave the cast with no target (measured: cast 51 lost its victim and settled nothing) - while a unit whose recorded
     * bars stop earlier has left the field and must be gone before it.
     *
     * @param beforeCast whether to read the bars strictly before this cast
     */
    private static void syncBars(Row row, com.google.gson.JsonObject capture, List<Enemy> enemies, Plan plan,
                                 boolean beforeCast) {
        for (java.util.Map.Entry<Long, java.util.TreeMap<Integer, Double>> each : timeline(capture).entrySet()) {
            int index = TARGET_UIDS.indexOf(each.getKey());
            if (index < 0 || index >= enemies.size() || index >= plan.getEnemies().size()) {
                continue;
            }
            java.util.Map.Entry<Integer, Double> bar = beforeCast
                    ? each.getValue().lowerEntry(row.cast)
                    : each.getValue().floorEntry(row.cast);
            if (bar == null) {
                continue;                               // it had not been hit yet
            }
            Enemy enemy = enemies.get(index);
            if (CURRENT_BATTLE == null || !CURRENT_BATTLE.enemyUnits().contains(enemy)) {
                continue;                               // it has not joined yet
            }
            if (row.cast > each.getValue().lastKey() && bar.getValue() > 0) {
                // The capture HIT it for the last time before this cast and never brings it back, with its bar still above
                // zero: it left the field (an add that retreats, not a corpse). Keeping it alive would put it in this
                // cast's target list - measured: cast 69 hit 1 enemy in the capture and 3 in the replay, the extra two
                // being 100032 / 100035, whose recorded bars stop at cast 59 with 313916 / 337805 left. It is retired the
                // same way the replay retires anything else, by emptying its bar, so every later target list leaves it out.
                if (!enemy.isDeath()) {
                    enemy.takeDamage(enemy.getCurrentHp(), true);
                }
                continue;
            }
            double delta = bar.getValue() - enemy.getCurrentHp();
            if (delta > 0) {
                enemy.heal(delta);
            } else if (delta < 0) {
                // `mayDie: true`: the capture's `hp_after` is 0 for what it KILLED, and a bar merely set to 0 while the unit
                // stays on the field would keep it in every later cast's target list (measured: cast 69 faced 3 enemies in
                // the replay and 1 in the capture).
                enemy.takeDamage(-delta, true);
            }
        }
    }

    /**
     * Every enemy's bar over the recorded fight: uid to {@code (cast index -> hp_after)}.
     *
     * <p><b>Why the whole timeline, not just the cast that was driven.</b> A cast only touches the enemies it hits, so a
     * replay that moves only those bars lets units the capture had already killed live on - and every later AoE then faces
     * a crowd the capture did not have. Reading each enemy's latest recorded bar at every cast keeps "who is on the field"
     * a fact of the capture.
     *
     * @param capture the parsed capture
     * @return the timelines
     */
    private static java.util.Map<Long, java.util.TreeMap<Integer, Double>> timeline(com.google.gson.JsonObject capture) {
        if (TIMELINE != null) {
            return TIMELINE;
        }
        java.util.Map<Long, java.util.TreeMap<Integer, Double>> lines = new java.util.HashMap<>();
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            if (event.get("cast_index") == null || !event.has("hp_after") || event.get("hp_after").isJsonNull()) {
                continue;
            }
            long uid = event.getAsJsonObject("target").get("uid").getAsLong();
            lines.computeIfAbsent(uid, key -> new java.util.TreeMap<>())
                    .put(event.get("cast_index").getAsInt(), event.get("hp_after").getAsDouble());
        }
        TIMELINE = lines;
        return lines;
    }

    /** The capture's bar timelines, computed once per run. */
    private static java.util.Map<Long, java.util.TreeMap<Integer, Double>> TIMELINE;

    /** The battle the loop is driving, so {@link #syncBars} can tell which enemies have joined. */
    private static Battle CURRENT_BATTLE;


    /**
     * The uid of the enemy the capture's cast hit hardest - the row's main target.
     *
     * @param capture the parsed capture
     * @param group   the capture group
     * @return the uid, or {@code null} when no event of the group carries a base
     */
    private static Long mainTargetOf(com.google.gson.JsonObject capture, CaptureFlow.Cast group) {
        Long best = null;
        double most = Double.NEGATIVE_INFINITY;
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            if (event.get("cast_index") == null || event.get("cast_index").getAsInt() != group.index()
                    || !group.kind().equals(event.get("type").getAsString())) {
                continue;
            }
            com.google.gson.JsonObject zones = event.getAsJsonObject("zones");
            if (!zones.has("raw_damage_before_modifiers")) {
                continue;
            }
            double base = zones.get("raw_damage_before_modifiers").getAsDouble();
            if (base > most) {
                most = base;
                best = event.getAsJsonObject("target").get("uid").getAsLong();
            }
        }
        return best;
    }


    /**
     * Every target the capture's group hit, identified by 生命上限 - the payload's only per-target identity.
     *
     * @param capture the parsed capture
     * @param group   the capture group
     * @return the lives it touched
     */
    private static java.util.Set<Double> groupHitHealths(com.google.gson.JsonObject capture, CaptureFlow.Cast group) {
        java.util.Set<Double> lives = new java.util.LinkedHashSet<>();
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            if (event.get("cast_index") == null || event.get("cast_index").getAsInt() != group.index()
                    || !group.kind().equals(event.get("type").getAsString())) {
                continue;
            }
            com.google.gson.JsonObject zones = event.getAsJsonObject("zones");
            if (zones.has("target_max_hp")) {
                lives.add(zones.get("target_max_hp").getAsDouble());
            }
        }
        return lives;
    }


    /**
     * The capture's {@code damage_percentage} on the instance that hit the row's main target - the one the row prices with
     * its {@code main_param}.
     *
     * <p>Why the main instance: a {@code per_resource_share} row prices the main and the rest differently, so the share of
     * an arbitrary event can be read two ways (measured on cast 58, 2.04 is either {@code 0.06 x 34} as an underling or
     * {@code 0.12 x 17} as the main). Taking the main instance's share and dividing by {@code main_param} leaves one reading.
     *
     * @param capture the parsed capture
     * @param group   the capture group
     * @param main    the main target's uid, or {@code null}
     * @return the main instance's percentage, or {@code 0} when it cannot be found
     */
    private static double mainPercentageOf(com.google.gson.JsonObject capture, CaptureFlow.Cast group, Long main) {
        if (main == null) {
            return 0;
        }
        for (com.google.gson.JsonElement element : capture.getAsJsonArray("damage_events")) {
            com.google.gson.JsonObject event = element.getAsJsonObject();
            if (event.get("cast_index") == null || event.get("cast_index").getAsInt() != group.index()
                    || !group.kind().equals(event.get("type").getAsString())
                    || event.getAsJsonObject("target").get("uid").getAsLong() != main) {
                continue;
            }
            com.google.gson.JsonObject zones = event.getAsJsonObject("zones");
            if (zones.has("damage_percentage")) {
                return zones.get("damage_percentage").getAsDouble();
            }
        }
        return 0;
    }


    /**
     * Seeds the states the capture says were up when this cast happened.
     *
     * @param row   the cast being driven
     * @param battle the battle
     * @param byCid  the party
     * @param plan   the plan, which declares them per cast
     */
    private static void applyStates(Row row, Battle battle, Map<Integer, Character> byCid, Plan plan) {
        if (plan.getStatesAt() == null) {
            return;
        }
        List<String> states = plan.getStatesAt().get(row.cast);
        if (states == null || states.isEmpty()) {
            return;
        }
        CanHit caster = casterOf(row.spec, battle, byCid);
        CanHit holder = caster == null ? byCid.get(row.actor) : caster;
        if (holder == null) {
            return;
        }
        for (String state : states) {
            // A duration past the window's end: the capture says the state was up, not when it expires.
            holder.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff(state, 99));
        }
        System.out.println("     states:   " + states + " up for cast " + row.cast);
    }


    /**
     * Performs the casts that precede this one and are not compared.
     *
     * @param row     the cast about to be driven
     * @param battle  the battle
     * @param byCid   the party
     * @param plan    the plan, which declares them
     * @param enemies the enemies, so a prelude may name a target
     */
    private static void runPreludes(Row row, Battle battle, Map<Integer, Character> byCid, Plan plan, List<Enemy> enemies) {
        if (plan.getPreludes() == null) {
            return;
        }
        for (PreludeSpec prelude : plan.getPreludes()) {
            if (prelude.getBefore() == null || prelude.getBefore() != row.cast) {
                continue;
            }
            Character actor = byCid.get(prelude.getActor());
            Skill skill = actor == null || prelude.getSlot() == null ? null
                    : actor.getSkills().get(com.laosun.aluminium.enums.SkillType.valueOf(prelude.getSlot()));
            if (skill == null) {
                System.out.println("     prelude:  SKIPPED - " + prelude.getActor() + " has no " + prelude.getSlot());
                continue;
            }
            CanHit target = prelude.getTarget() == null || prelude.getTarget() < 1
                    || prelude.getTarget() > enemies.size() ? null : enemies.get(prelude.getTarget() - 1);
            battle.castImmediate(skill, actor, target == null ? List.of() : List.of(target));
            System.out.printf(Locale.ROOT, "     prelude:  %d casts %s before cast %d (settles no damage, so the capture "
                    + "cannot name it) - not compared%n", prelude.getActor(), prelude.getSlot(), row.cast);
        }
    }

}
