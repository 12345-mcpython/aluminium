package com.laosun.aluminium;

import com.google.gson.reflect.TypeToken;
import com.laosun.aluminium.beans.*;
import com.laosun.aluminium.beans.CharacterData;
import com.laosun.aluminium.data.RelicSets;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.utils.JSONReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global constants and static game data loaded at startup.
 *
 * <p>All game data files under {@code resources/data/} are deserialized via
 * {@link JSONReader} when this class is first loaded. The following data sets are available:
 * <ul>
 *   <li>{@link #RELIC_MAIN_ATTRIBUTES} — main attribute value tables by star level</li>
 *   <li>{@link #RELIC_SUB_ATTRIBUTES} — sub-attribute value tables by star level</li>
 *   <li>{@link #RELIC_SETS} — relic set definitions (the 2-piece / 4-piece bonus table)</li>
 *   <li>{@link #WEAPONS} — weapon (light cone) data by ID</li>
 *   <li>{@link #CHARACTERS} — character base stats by ID</li>
 *   <li>{@link #SKILL_TRACES} — trace tree data (行迹) by character ID</li>
 * </ul>
 *
 * <p>{@link #PERCENT_TO_BASE} maps percentage-type attributes to their corresponding
 * base attributes for modifier redirection during calculation.
 */
public final class Constant {
    /**
     * Relic main attribute value tables (keyed by star level 2-5).
     * <star level> part attribute <base bonus> <double value>
     */
    public static final RelicMainAttribute RELIC_MAIN_ATTRIBUTES;
    /**
     * Relic sub-attribute value tables (keyed by star level 2-5).
     */
    public static final RelicSubAttribute RELIC_SUB_ATTRIBUTES;
    /**
     * Relic set definitions ({@code relic_sets.json}): set id → {@link RelicSet}, i.e. the 2-piece /
     * 4-piece bonus table (60 sets / 92 bonuses).
     *
     * <p>Loaded through {@link com.laosun.aluminium.data.RelicSets} rather than {@link JSONReader}: the
     * file's top level is a map keyed by the set id as a string, which the {@code Class}-based overload
     * cannot express, and — like {@code stage.json} — a <b>missing file yields an empty table instead of
     * taking down the static initializer</b>. Without it the engine simply cannot apply set bonuses.
     *
     * <p>{@link #RELIC_SET_NONE} is the id that means "this relic belongs to no set".
     */
    public static final Map<Integer, RelicSet> RELIC_SETS;
    /**
     * Weapon base data indexed by weapon ID.
     */
    public static final Map<Integer, WeaponData> WEAPONS;
    /**
     * Character base data indexed by character ID.
     */
    public static final Map<Integer, CharacterData> CHARACTERS;
    /**
     * Skill point tree data indexed by character ID.
     */
    public static final Map<Integer, List<SkillTraceData>> SKILL_TRACES;

    public static final Map<Integer, Map<Integer, Skill>> SKILLS;

    /**
     * Monster template base attributes ({@code monster_template_config.json}): template_id → base value.
     */
    public static final Map<Integer, MonsterTemplate> MONSTER_TEMPLATES;
    /**
     * Monster instance data ({@code monster_config.json}): monster_id → instance ratios / weakness / resistance.
     * Missing ratios are already filled in at load time by {@link #normalizeMonsterConfigs}, so downstream code
     * is guaranteed to get non-null values.
     */
    public static final Map<Integer, MonsterConfig> MONSTER_CONFIGS;
    /**
     * Level group ratios ({@code hard_level_group.json}): group number → level → ratio.
     * The group number and level come from the stage (StageConfig); at the P2 stage they are passed in
     * explicitly by the caller.
     */
    public static final Map<Integer, Map<Integer, HardLevelGroup>> HARD_LEVEL_GROUPS;

    /**
     * Enemy skill table ({@code enemy_skills.json}, self-built in P5-3): {@code monster instance id → skill}.
     *
     * <p>⚠ The **multipliers in this table are guessed** (the data source has no enemy skill table);
     * every entry carries a {@code guessed} flag. See
     * {@link com.laosun.aluminium.beans.EnemySkillData}.
     */
    public static final Map<Integer, EnemySkillData> ENEMY_SKILLS;

    /**
     * Stage table ({@code stage.json}): {@code stage_id → }{@link StageBean}. **Lazily loaded**.
     *
     * <p>Why it is not shoved into the static block like the other data tables: {@code stage.json} is
     * 9 MB / roughly 29,000 stages, larger than all the other data put together, and the vast majority of
     * tests and demos never touch stages at all. Putting it in the static block would mean paying
     * ~35 MB of heap + tens of milliseconds on **every** {@code Constant} initialization.
     *
     * <p>⚠ The second difference from the other data tables: when {@code stage.json} is missing this
     * returns an **empty table** instead of throwing. It only serves stage-driven code (P7-4/P7-5), and
     * the static block of `Constant` is the place where "one touch brings down the entire test suite" —
     * an optional feature must not drag the whole suite down with it. When the stages cannot be loaded,
     * the caller ({@code StageFactory.load}) produces a self-explanatory error.
     *
     * @return the stage table; an empty table when the data file is missing
     */
    public static Map<Integer, StageBean> stages() {
        return StageHolder.LOADED;
    }

    /**
     * How many times the stage table has been **parsed** (0 or 1) — for tests to observe lazy loading only (P7-4).
     *
     * <p>Why it is needed: Java exposes no public API to ask "has this class been initialized" without
     * triggering initialization, so the statement "nobody calls {@link #stages()} ⇒ stage.json must not be
     * read" needs an observable point in tests. What is counted is the **parse attempt** (a failure caused by
     * a missing file counts too) — that is exactly the work being deferred.
     *
     * <p>Deliberately placed in a **separate class** rather than inside {@link StageHolder}: the static fields
     * of {@code StageHolder} are initialized in declaration order, so putting the counter after the callee
     * would read the default value 0.
     */
    public static int stageLoadAttempts() {
        return StageProbe.LOAD_ATTEMPTS.get();
    }

    /**
     * See {@link #stageLoadAttempts()}: a counter separate from {@link StageHolder}, so that static field
     * initialization order cannot read the count as 0.
     */
    private static final class StageProbe {
        private static final AtomicInteger LOAD_ATTEMPTS = new AtomicInteger();
    }

    /**
     * The lazy-loading carrier for the stage table.
     *
     * <p>The key is that {@code LOADED} is a static field of {@link StageHolder}: **a nested class is
     * initialized only when it is first referenced**, so the static block of {@code Constant} finishes
     * without parsing {@code stage.json}, until somebody actually calls {@link #stages()}.
     */
    private static final class StageHolder {
        private static final Map<Integer, StageBean> LOADED = load();

        private static Map<Integer, StageBean> load() {
            StageProbe.LOAD_ATTEMPTS.incrementAndGet();
            try {
                return Map.copyOf(JSONReader.fromJSON("stage.json",
                        new TypeToken<Map<Integer, StageBean>>() {
                        }.getType()));
            } catch (IllegalStateException e) {
                // Data was not generated → empty table. Anyone who really needs stages gets a clear error in StageFactory.load.
                return Map.of();


            }
        }
    }


    /**
     * {@link SkillType} → the **skill slot number** in {@code skills.json} (P8-2).
     *
     * <p>The slot convention in the data: <b>1 basic attack / 2 skill / 3 ultimate / 4 talent / 5 (none) /
     * 6 overworld basic attack / 7 technique</b>, and {@code skill_id = character id × 100 + slot}
     * (all 638 skills satisfy this; verified).
     *
     * <p>⚠ Slot 5 does not exist in the data (none of the 93 characters' skill sets has one), so there is no
     * entry for it here either.
     *
     * <p>⚠ Although {@code MAZE} / {@code TECHNIQUE} are the character's own skills, they are **not equipped
     * when building the character** (see {@link SkillType#isIntrinsic()}): they are overworld skills, attached
     * at battle start by {@code Battle.startBattle()}. This table is used by both places.
     *
     * <p>⚠ This table **must exist in exactly one copy**: before the fix, {@code Character.Builder.build()}
     * wrote {@code new DefaultSkill(cid, 1, level)} for every slot, so basic attack / skill / ultimate / talent
     * **all** resolved to slot 1; the consequence was that all six slots had the multipliers, toughness
     * reduction, element and {@code sp_need} of the basic attack.
     */
    public static final Map<SkillType, Integer> SKILL_SLOT = Map.of(
            SkillType.COMMON, 1,
            SkillType.SKILL, 2,
            SkillType.ULTRA, 3,
            SkillType.TALENT, 4,
            SkillType.MAZE, 6,
            SkillType.TECHNIQUE, 7);

    /**
     * Maps percentage-type attributes to their corresponding base-type attributes.
     * E.g. HEALTH_PERCENT → HEALTH means health percentage bonuses are merged
     * into the HEALTH attribute's modifier list.
     */
    public static final Map<AttributeType, AttributeType> PERCENT_TO_BASE = Map.of(
            AttributeType.HEALTH_PERCENT, AttributeType.HEALTH,
            AttributeType.ATTACK_PERCENT, AttributeType.ATTACK,
            AttributeType.DEFENCE_PERCENT, AttributeType.DEFENCE,
            AttributeType.SPEED_PERCENT, AttributeType.SPEED
    );

    /**
     * The {@code setId} of a relic that belongs to no set.
     *
     * <p>{@code 0} because that is what {@link com.laosun.aluminium.models.Relic} defaults to for relics
     * built by hand or from a setting (there is no set id in {@code Setting}), and because the game's own
     * set ids all start above it. {@link com.laosun.aluminium.models.RelicSuit} skips relics with this id
     * when it counts pieces — without it, every hand-built relic in the tests would count as one piece of
     * "set 0" and the lookup would fail.
     */
    public static final int RELIC_SET_NONE = 0;

    /**
     * How many times one stackable modifier may accumulate before further applications are dropped.
     *
     * <p>This is the cap of the <b>primitive</b>, not of any particular effect: a rule that wants a
     * smaller cap states it (the trigger table's {@code max_stacks}), and a rule that omits the
     * argument gets exactly one stack, i.e. the historical replace-on-same-kind behaviour. The bound
     * exists so that a typo cannot grow the buff list without limit.
     */
    public static final int MAX_STACKS_LIMIT = 999;

    /**
     * Upper bound of the vulnerability zone (易伤区) multiplier.
     */
    public static final double VULNERABLE_CAP = 3.5;
    /**
     * Lower bound of the damage-reduction zone (减伤区) multiplier.
     */
    public static final double REDUCTION_MIN = 0.01;
    /**
     * Lower bound of the weakness zone (虚弱区) multiplier.
     */
    public static final double WEAKNESS_MIN = 0.2;
    /**
     * Lower bound of the raw resistance value (-100%).
     */
    public static final double RESIST_MIN = -1.0;
    /**
     * Upper bound of the raw resistance value (90%).
     */
    public static final double RESIST_MAX = 0.9;
    /**
     * Level-independent term of the defence zone formula.
     */
    public static final double DEFENCE_CONST = 200.0;
    /**
     * Per-level term of the defence zone formula.
     */
    public static final double DEFENCE_PER_LEVEL = 10.0;
    /**
     * Whether true damage skips every damage zone (真伤跳过乘区开关).
     */
    public static final boolean TRUE_DMG_SKIP_ZONES = true;

    /**
     * Regular basic-attack energy gain (P3 fallback value).
     *
     * <p>Taken from the regular tier of tbgd {@code AvatarSkillConfig.SPBase} (ROADMAP P3-0 definition 2):
     * basic attack 20 / skill 30 / ultimate 5 are universal values for all characters, and **the data for
     * multi-hit (bouncing) skills is the "per-hit value"** (Asta / Sampo / Anaxa / Harmony Trailblazer 6×5,
     * Welt 10×3); the total is still 30, do not multiply by the number of hits again.
     * After P3-4 puts skill data into the database, this only serves as the fallback for "when there is no
     * skill data".
     */
    public static final double ENERGY_GAIN_BASIC = 20;
    /**
     * Regular skill energy gain (P3 fallback value). See {@link #ENERGY_GAIN_BASIC}.
     */
    public static final double ENERGY_GAIN_SKILL = 30;
    /**
     * Regular ultimate energy gain (P3 fallback value). Ultimates are always 5 (Dan Heng • Imbibitor Lunae
     * has 3 hits, Misha has multiple hits, Argenti bounces 6 times — all 5), with no multiplication by hit
     * count; on cast the gauge is zeroed first and then these 5 points are gained.
     */
    public static final double ENERGY_GAIN_ULTRA = 5;
    /**
     * Baseline energy gain on being hit (P3). The document gives no direct number; it is inferred backwards
     * from "Natasha E4 recovers **an extra** 5 points after being attacked" and "Yunli recovers **an extra**
     * 15 points after being attacked" that a baseline value of 10 exists; to be calibrated with data in P9.
     */
    public static final double ENERGY_GAIN_HIT = 10;
    /**
     * Baseline energy gain on kill (P3, pending calibration). It only ever appears in the documents in the
     * form "extra recovery".
     */
    public static final double ENERGY_GAIN_KILL = 5;
    /**
     * Baseline energy gain on weakness break (P3, pending calibration). Called on weakness break in P4-4.
     */
    public static final double ENERGY_GAIN_BREAK = 5;

    /**
     * Skill point (SP) cap (P8-4): **one pool shared by the whole team**, not one bar per character.
     *
     * <p>The value comes from the game rules rather than a data table — {@code skills.json} has **no**
     * skill point field, and of the 638 skills measured, the {@code sp_need} of all 122 basic attacks and all
     * 109 skills is **null** (the 99 entries that do have a value are all ultimates, which is the ultimate
     * energy threshold, see §9.4). So skill points can only come from the rules: cap 5, 3 at the start,
     * +1 per basic attack, -1 per skill.
     *
     * <p>⚠ <b>The cap is not always 5</b>: Sparkle's talent "cap +2" and a light cone's "for each character
     * on the Path of Elation +1", and some light cones even trigger on "cap ≥ 6". The engine currently has
     * **no** hook for "changing a team-level resource cap" — registered as <b>F-1</b> in
     * {@code DOC_VS_CODE.md} §F, to be handled around P8-7.
     */
    public static final int SKILL_POINT_MAX = 5;

    /**
     * Skill points at the start of battle (P8-4). See {@link #SKILL_POINT_MAX}.
     *
     * <p>⚠ <b>The start value is not always 3 either</b>: {@code RELICS.md} gives the 4-piece Passerby set
     * "at the start of battle immediately recover 1 skill point for our side" → start at 4 (5 if two
     * characters wear it). That bonus is an <b>ability</b> rather than stats — {@link #RELIC_SETS} now
     * carries it as {@code Ability51011} with an empty property list — and the engine has no ability
     * interpreter, so it is still not applied: registered as <b>F-2</b> in §F.
     */
    public static final int SKILL_POINT_START = 3;

    /**
     * Skill points recovered by one basic attack (P8-4). See {@link #SKILL_POINT_MAX}.
     */
    public static final int SKILL_POINT_GAIN_BASIC = 1;

    /**
     * How many Eidolon ranks (星魂) a character can have. {@code 0} means "none active"; the data carries
     * exactly the ranks {@code 1}…{@code 6} for every character in {@code character_data.json}.
     *
     * <p>It is a constant here because it is the bound the <b>assembly point</b> validates against
     * ({@code CharacterFactory.create}) and the bound a trigger rule's {@code min_eidolon} is checked against at
     * load time — two places that must agree, which is exactly the kind of number that must not be typed twice.
     */
    public static final int EIDOLON_MAX_RANK = 6;

    /**
     * Weakness break base value table: level → base value (P4-3). **The values in the data file are scaled
     * by 10×**, so use them with {@code /10} (level 80 = 3767.5535 → 376.75535).
     *
     * <p>See the unit note in {@code models/BreakDamageCalculator}: this project uniformly uses the "point"
     * scale for toughness reduction values.
     */
    public static final Map<Integer, Double> BREAKING_RATE;

    /**
     * Weakness break delay ratio (P4-4): at the instant of the break, push the target's action bar back by
     * 25% (unit = that target's own action period).
     */
    public static final double BREAK_DELAY_RATIO = 0.25;

    /**
     * Weakness break duration in turns (P4-4): after an enemy is broken it skips this many of its own turns,
     * then toughness is restored to full.
     */
    public static final int BROKEN_REMAIN_TURNS = 2;

    /**
     * Super break independent DMG boost (P4-6): {@code 1 + SUPER_BREAK_BOOST} is multiplied into super break
     * damage.
     *
     * <p>It is **unrelated** to the regular DMG boost zone — super break does not take elemental / attack-type
     * DMG boosts (blocked by {@code DamageType.SUPER_BREAK}'s {@code isBoostable() == false}), so it is an
     * **independent damage zone** and can only be read from here.
     *
     * <p>**Example value, TODO data**: the document only says "in version 2.2 only Harmony Trailblazer
     * provides it" (their traces give 20%~60% depending on the number of enemies on the field), and there is
     * no lookup table available, so 0.4 is used as a placeholder.
     */
    public static final double SUPER_BREAK_BOOST = 0.4;

    /**
     * Base damage of one weakness break DOT tick = break base value × this ratio (**example value, TODO data**:
     * HSR.md §2 only says "the base multiplier is determined by level and break effect (look it up in the value
     * table)" — the per-element multipliers have not been obtained yet).
     */
    public static final double DOT_RATIO = 0.5;

    /**
     * Number of weakness break DOT ticks (**example value, TODO data**).
     */
    public static final int DOT_TURNS = 3;

    /**
     * One element's weakness-break effect (P10-1 structure, P10-2 control).
     *
     * <p>⚠ <b>Still structure over data.</b> The four damaging elements reuse {@link #DOT_RATIO} /
     * {@link #DOT_TURNS} so this table reproduces the pre-table behaviour exactly, and the numbers of the
     * three control elements are <b>example values marked {@code TODO data}</b> — see
     * {@link #CONTROL_EFFECTS}. What is real here is the <b>shape</b>: every element now says what it does
     * (a DOT, an extra delay, a control state, or none of those) in one place, instead of the call site
     * testing set membership and then reaching for loose scalars.
     *
     * @param dotRatio     per-tick DOT damage as a fraction of the break base value (0 = no DOT)
     * @param dotTurns     how many ticks it lasts (0 = no DOT)
     * @param delayPercent extra action delay on top of the fixed {@link #BREAK_DELAY_RATIO}
     *                     (0 = the plain 25% only)
     * @param control      the key of a {@link #CONTROL_EFFECTS} entry, or {@code null} when the element has
     *                     no control part. A string rather than an enum on purpose: the key is also what the
     *                     data's own {@code STAT_*} resistance vocabulary is keyed by.
     */
    public record BreakEffect(double dotRatio, int dotTurns, double delayPercent, String control) {
        /**
         * Whether this element attaches a damage-over-time when it breaks.
         */
        public boolean hasDot() {
            return dotRatio > 0 && dotTurns > 0;
        }

        /**
         * Whether this element leaves a control state behind.
         */
        public boolean hasControl() {
            return control != null;
        }

        /**
         * The control state this element leaves behind, or {@code null}.
         *
         * @throws IllegalStateException when {@link #control} names an entry that is not in
         *                               {@link #CONTROL_EFFECTS} — a typo must not degrade into "no control at all"
         */
        public ControlEffect controlEffect() {
            if (control == null) {
                return null;
            }
            ControlEffect effect = CONTROL_EFFECTS.get(control);
            if (effect == null) {
                throw new IllegalStateException(
                        "break effect names the control state '" + control
                                + "' but CONTROL_EFFECTS has no such entry");
            }
            return effect;
        }
    }

    /**
     * A control state (P10-2): what sits on the victim while a break's control lasts.
     *
     * <p><b>Why this is a table and not a class per state.</b> The three states differ only in numbers and
     * in one boolean, and the engine already has a primitive for each part — so a control is
     * <b>composed</b> from them rather than given a class of its own (the P8-0 rule, and the same reason
     * {@code StatModifierBuff} covers every "attribute X becomes X ⊕ v" buff):
     *
     * <table border="1">
     *   <caption>which existing primitive implements which part</caption>
     *   <tr><th>part</th><th>primitive</th></tr>
     *   <tr><td>{@link #blocksAct}</td><td>{@code StunBuff} ({@code canAct() == false})</td></tr>
     *   <tr><td>{@link #slowPercent}</td><td>{@code StatModifierBuff.percentDebuff(SPEED, …)}</td></tr>
     *   <tr><td>the delay</td><td>{@code Battle.delayMovePercent} — not a buff, it is an instant push</td></tr>
     *   <tr><td>{@link #resistKey}</td><td>{@code Battle.hitChance}'s 4th argument (skill-applied only)</td></tr>
     * </table>
     *
     * <p><b>{@link #resistKey} is deliberately not consulted when a <i>break</i> applies the control.</b>
     * A weakness break is not a resisted debuff: it happens because the toughness bar emptied, and no
     * monster's {@code STAT_CTRL_*} resistance may cancel it. The key is here because the whole point of
     * the table is that the same state can also be applied by a skill, and that path goes through
     * {@code Battle.tryApplyDebuff(…, resistKey)} — where the resistance really does apply.
     *
     * @param resistKey   the data's specific-resistance key for this state (what a <i>skill</i> must beat)
     * @param turns       how many of the victim's turns it lasts
     * @param blocksAct   {@code true} = the victim cannot act at all; {@code false} = it acts, just slower
     *                    or later (this is the difference between 冻结 and 禁锢/纠缠)
     * @param slowPercent SPEED reduction as a decimal (0.2 = −20%), 0 = no slow
     */
    public record ControlEffect(String resistKey, int turns, boolean blocksAct, double slowPercent) {
    }

    /**
     * The control states a break can leave behind (P10-2), keyed by the name {@link BreakEffect#control}
     * uses.
     *
     * <p>⚠ <b>Every number here is an example value, {@code TODO data}.</b> The data was probed and it does
     * <b>not</b> contain a break-control table: {@code breaking_rate.json} is level → break base value, and
     * the only descriptions of these states live in the encyclopedia text, which states the mechanics but
     * not the numbers. What the text <i>did</i> settle is the mechanics, and two of them contradicted the
     * plan (recorded in {@code ROADMAP.md} P10-2):
     * <ul>
     *   <li>冻结 = <b>不能行动</b>（+ 每回合冰属性伤害）—— the plan said "冻结期受伤害 +30%",
     *       which nothing in the data supports;</li>
     *   <li>禁锢 / 纠缠 = <b>行动延后 + 速度降低</b>, and the victim still acts.</li>
     * </ul>
     *
     * <p>{@code turns} is 1 for all three because none of the sources states a break-applied duration, and
     * 1 is the value that makes the state last exactly the victim's next turn — the smallest thing that is
     * observably a control. Do not read it as data.
     *
     * <p><b>One number did turn out to be traceable after all</b> (P10-6, reading the descriptions):
     * 瓦尔特's 画地为牢 (1004/7) writes {@code 禁锢状态下，敌方目标行动延后#2%，速度降低#3%} with
     * {@code param_list = [1, 0.2, 0.1, 15, 0.5]} — i.e. 行动延后 20%, 速度降低 10%. The delay matches
     * {@link #IMPRISON_EXTRA_DELAY} by coincidence (it was a guess), and {@code slowPercent} for
     * {@code IMPRISONED} was guessed as 0.2 and is <b>corrected to 0.1</b> from that text. The same
     * caveat as 冻结 applies: that is a <i>technique</i>-applied 禁锢, not a break-applied one, and no
     * source gives the latter — so this is the closest available evidence, not a verified break value.
     * {@code ENTANGLED}'s 0.2 has no source at all and stays a plain guess.
     */
    public static final Map<String, ControlEffect> CONTROL_EFFECTS = Map.of(
            "FROZEN", new ControlEffect("STAT_CTRL_Frozen", 1, true, 0.0),
            "ENTANGLED", new ControlEffect("STAT_Entangle", 1, false, 0.2),
            "IMPRISONED", new ControlEffect("STAT_Confine", 1, false, 0.1));

    /**
     * Extra action delay of a Freeze break, on top of {@link #BREAK_DELAY_RATIO} (**example value,
     * TODO data**). The encyclopedia text says a 冻结 "行动延后" but gives no break-applied number.
     */
    public static final double FREEZE_EXTRA_DELAY = 0.5;

    /**
     * Extra action delay of an Entanglement break (**example value, TODO data**).
     */
    public static final double ENTANGLE_EXTRA_DELAY = 0.2;

    /**
     * Extra action delay of an Imprisonment break (**example value, TODO data**).
     */
    public static final double IMPRISON_EXTRA_DELAY = 0.2;

    /**
     * The seven weakness-break effects, by element — the single place the break behaviour is described.
     *
     * <p>Before this, {@code Battle.attachBreakDot} tested membership in a set and then used two loose
     * scalars, so nothing said what Ice / Quantum / Imaginary do and a reader of the call site could
     * believe the three were merely "a different DOT". They are not: they carry a <b>control state</b>
     * ({@link #CONTROL_EFFECTS}) and an extra delay instead.
     *
     * <p>The four damaging elements keep {@code delayPercent = 0} and {@code control = null}, which is what
     * makes this table reproduce their pre-table behaviour exactly.
     */
    public static final java.util.Map<DamageElement, BreakEffect> BREAK_EFFECTS =
            java.util.Map.ofEntries(
                    java.util.Map.entry(DamageElement.FIRE, new BreakEffect(DOT_RATIO, DOT_TURNS, 0, null)),
                    java.util.Map.entry(DamageElement.THUNDER, new BreakEffect(DOT_RATIO, DOT_TURNS, 0, null)),
                    java.util.Map.entry(DamageElement.PHYSICAL, new BreakEffect(DOT_RATIO, DOT_TURNS, 0, null)),
                    java.util.Map.entry(DamageElement.WIND, new BreakEffect(DOT_RATIO, DOT_TURNS, 0, null)),
                    java.util.Map.entry(DamageElement.ICE,
                            new BreakEffect(0, 0, FREEZE_EXTRA_DELAY, "FROZEN")),
                    java.util.Map.entry(DamageElement.QUANTUM,
                            new BreakEffect(0, 0, ENTANGLE_EXTRA_DELAY, "ENTANGLED")),
                    java.util.Map.entry(DamageElement.IMAGINARY,
                            new BreakEffect(0, 0, IMPRISON_EXTRA_DELAY, "IMPRISONED")));

    /**
     * Break elements that carry a damage-over-time effect: fire = burn, lightning = shock, physical = bleed,
     * wind = wind shear (GLOSSARY_EXTRA 10000012).
     *
     * <p><b>Derived from {@link #BREAK_EFFECTS}</b> rather than listed a second time, so "which elements
     * have a DOT" cannot disagree with the effect table.
     *
     * <p>⚠ <b>No engine code reads this any more.</b> {@code Battle.attachBreakDot} asks
     * {@link BreakEffect#hasDot()} of the element's own entry, which is the single judgement point —
     * and since this set is derived from that same table, the two can never disagree. It survives as a
     * convenience for callers that want the list (tests do: {@code BreakEffectTableTest}); do not
     * reintroduce it as a decision input at a call site, or the "one judgement point" property is lost
     * again. (P10-0 note: the DOT itself is an ordinary {@code DotBuff} in the buff system now, so
     * "which elements get one" is the only thing this constant is about.)
     */
    public static final Set<DamageElement> DOT_ELEMENTS = BREAK_EFFECTS.entrySet().stream()
            .filter(entry -> entry.getValue().hasDot())
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * Action value of one round (P7-1): **100** for every round after that.
     *
     * <p>In this project "action value (AV)" is a **time dimension**: a unit with speed 100 covers
     * 100 action value in one period, so the {@code elapsed} advanced by
     * {@link com.laosun.aluminium.Queue#move()} is accumulated action value, and
     * {@link com.laosun.aluminium.Queue#getRound()} splits it into rounds directly.
     */
    public static final double ROUND_ACTION_VALUE = 100;

    /**
     * First-round action value multiplier (P7-1): the first round totals **150** action value, every round
     * after that **100**.
     *
     * <p>So a unit with speed 100 has to wait 150 to act in the first round, then acts every 100 from the
     * second lap on; a unit with speed 200 waits 75 in the first round. This is not "the whole first round is
     * delayed" but rather each unit's **first period** being stretched 1.5× — in the first round fast units
     * can act more often (a speed-240 unit has a period of 41.67 and can act 3 times within the first round's
     * 150).
     *
     * <p>⚠ Only {@link com.laosun.aluminium.Queue#initialize()} (battle start) applies this multiplier;
     * after {@code setTopZero()} / {@code addCombatant()} everything is queued with the normal period.
     * When speed changes mid-battle it is preserved by accounting through
     * {@link com.laosun.aluminium.models.Signal#isFirstRound()}.
     */
    public static final double FIRST_ROUND_MULTIPLIER = 1.5;

    static {
        RELIC_MAIN_ATTRIBUTES = JSONReader.fromJSON("main_attribute.json", RelicMainAttribute.class);
        RELIC_SUB_ATTRIBUTES = JSONReader.fromJSON("sub_attribute.json", RelicSubAttribute.class);
        // Relic set bonuses come from a file whose top level is a map (so not JSONReader), and are
        // leniently loaded (a missing file is an empty table) because the relic affix tables above are
        // already enough to run a battle -- only the set bonuses would be missing.
        // Already immutable: RelicSets.index ends in Map.copyOf, and RelicSetTest pins the identity of
        // this table ("read once and cached"), so wrapping it here would both be redundant and break that.
        RELIC_SETS = RelicSets.table();
        // ⚠ Every table below is `frozen(...)` (H-1). `public static final` locks the reference, not the
        // contents, and Gson hands back mutable LinkedHashMaps whose nesting is mutable too
        // (`SKILLS.get(cid)` is another map, `SKILL_TRACES.get(cid)` a list). One `clear()` or `put()` from
        // any caller -- a test, a future UI, a plugin -- would have silently changed what every later
        // consumer in the same JVM sees. The tables NOT wrapped here were already immutable at load:
        // RELIC_SETS (RelicSets.index ends in Map.copyOf), MONSTER_CONFIGS (normalizeMonsterConfigs ends in
        // Map.copyOf) and ENEMY_SKILLS (Map.copyOf).
        WEAPONS = frozen(JSONReader.fromJSON("weapons.json", new TypeToken<Map<Integer, WeaponData>>() {
        }.getType()));
        CHARACTERS = frozen(JSONReader.fromJSON("character_data.json", new TypeToken<Map<Integer, CharacterData>>() {
        }.getType()));
        SKILL_TRACES = frozen(JSONReader.fromJSON("skill_traces.json", new TypeToken<Map<Integer, List<SkillTraceData>>>() {
        }.getType()));
        SKILLS = frozen(JSONReader.fromJSON("skills.json", new TypeToken<Map<Integer, Map<Integer, Skill>>>() {
        }.getType()));
        MONSTER_TEMPLATES = frozen(JSONReader.fromJSON("monster_template_config.json",
                new TypeToken<Map<Integer, MonsterTemplate>>() {
                }.getType()));
        HARD_LEVEL_GROUPS = frozen(JSONReader.fromJSON("hard_level_group.json",
                new TypeToken<Map<Integer, Map<Integer, HardLevelGroup>>>() {
                }.getType()));
        MONSTER_CONFIGS = normalizeMonsterConfigs(
                JSONReader.fromJSON("monster_config.json", new TypeToken<Map<Integer, MonsterConfig>>() {
                }.getType()),
                JSONReader.fromJSON("monster_attack_modify_ratio.json", new TypeToken<Map<Integer, Double>>() {
                }.getType()));
        BREAKING_RATE = frozen(JSONReader.fromJSON("breaking_rate.json", new TypeToken<Map<Integer, Double>>() {
        }.getType()));
        // The top level of enemy_skills.json is { "_comment": [...], "skills": {monster id: {...}} };
        // use an inline record to take only skills (Gson ignores the undeclared _comment).
        EnemySkillsFile enemySkills = JSONReader.fromJSON("enemy_skills.json", EnemySkillsFile.class);
        ENEMY_SKILLS = Map.copyOf(enemySkills.skills());
    }

    /**
     * The top-level structure of {@code enemy_skills.json} (only to skip {@code _comment}).
     */
    private record EnemySkillsFile(Map<Integer, EnemySkillData> skills) {
    }

    /**
     * Fill in the ratios missing from the instance data so that downstream code (EnemyScaler) always gets a
     * definite value:
     * <ul>
     *   <li><b>Attack modifier</b>: this data set does not export tbgd's {@code AttackModifyRatio}
     *   (444 of the 2649 monsters are ≠ 1), so it is merged from the patch file
     *   {@code monster_attack_modify_ratio.json}; anything not in the table is 1.0.</li>
     *   <li>Other missing ratios are 1.0 (game semantics = no modification).</li>
     *   <li>{@code stance_weak} missing (102 monster entries do not have this item) → empty list;
     *   {@code damage_resistance} → empty map.</li>
     * </ul>
     */
    /**
     * Freezes a loaded table so that no caller can mutate shared engine data (H-1).
     *
     * <p><b>Why this exists.</b> {@code public static final} locks the <i>reference</i>, not the contents:
     * every table in the static block used to be a mutable {@code LinkedHashMap} straight out of Gson, and
     * the nesting was mutable as well — {@code SKILLS.get(cid)} is another map, {@code SKILL_TRACES.get(cid)}
     * a list. One {@code Constant.SKILLS.clear()} from anywhere (a test, a future UI, a plugin) would have
     * silently changed what every later consumer <b>in the same JVM</b> sees, with no compile error and no
     * failing test.
     *
     * <p><b>Scope: containers, not beans.</b> Maps and lists are copied and wrapped; the beans inside are
     * returned unchanged, because they <i>are</i> the data and nothing mutates them. That is the honest
     * boundary of this fix — it stops "somebody emptied a table", not "somebody reassigned a field of a
     * bean". (The two exposed bean-internal maps, {@code RelicMainAttribute.getAttributeByStar} and its
     * {@code RelicSubAttribute} twin, therefore stay mutable and are still registered as N-11.)
     *
     * <p><b>Order is preserved</b>: {@link LinkedHashMap} and {@link ArrayList} rather than
     * {@code Map.copyOf}/{@code List.copyOf}, whose iteration order is unspecified. Several of these tables
     * are read in file order.
     *
     * <p>Costs one extra container copy per table at startup (the beans are shared, not duplicated), which
     * is why it is applied at load time rather than by returning a defensive copy on every read.
     *
     * @param table a map, a list, or anything else
     * @param <T>   the declared type of the table
     * @return an unmodifiable deep copy of the containers, or the value itself when it is not a container
     */
    @SuppressWarnings("unchecked")
    private static <T> T frozen(T table) {
        if (table instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(key, frozen(value)));
            return (T) Collections.unmodifiableMap(copy);
        }
        if (table instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(item -> copy.add(frozen(item)));
            return (T) Collections.unmodifiableList(copy);
        }
        return table;
    }

    private static Map<Integer, MonsterConfig> normalizeMonsterConfigs(Map<Integer, MonsterConfig> raw,
                                                                       Map<Integer, Double> attackRatios) {
        Map<Integer, Double> patches = attackRatios == null ? Map.of() : attackRatios;
        Map<Integer, MonsterConfig> normalized = new LinkedHashMap<>();
        raw.forEach((id, config) -> normalized.put(id, new MonsterConfig(
                config.name(),
                config.templateId(),
                config.eliteGroup(),
                config.hardLevelGroup(),
                config.stanceWeak() == null ? List.of() : List.copyOf(config.stanceWeak()),
                orOne(config.hpRatio()),
                patches.getOrDefault(id, orOne(config.attackRatio())),
                orOne(config.defenceRatio()),
                orOne(config.speedRatio()),
                orOne(config.stanceRatio()),
                config.damageResistance() == null ? Map.of() : Map.copyOf(config.damageResistance()),
                config.debuffResistance() == null ? Map.of() : Map.copyOf(config.debuffResistance()),
                // P9-4: the summon roster. `summon_id` is spelled as a list even when a monster has none
                // (`[]` for 1957 of them) and occasionally as `[0]` for "none" (one monster, 405301004), so
                // non-positive entries are dropped here -- once, at load time -- rather than at every read.
                // Deliberately NOT tolerant of unknown ids as well: a roster entry that names no monster is
                // a data error, and dropping it would turn a broken summon into an absent one (see
                // MonsterConfig#summonIds).
                filterPositive(config.summonIds()))));
        return Map.copyOf(normalized);
    }

    /**
     * The positive entries of a summon roster, in order; missing field = empty roster.
     */
    private static List<Integer> filterPositive(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Integer> kept = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            if (id != null && id > 0) {
                kept.add(id);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * A missing modifier ratio is 1.0 (no modification).
     */
    private static double orOne(Double value) {
        return value == null ? 1.0 : value;
    }
}
