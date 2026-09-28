package com.laosun.aluminium.beans;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * One effect inside a trigger (P8-7): "do this".
 *
 * <p>Every effect is a {@code (op, ...args)} pair. The {@code op} vocabulary is deliberately
 * restricted to <b>capabilities the engine already has</b> -- the trigger table is an interpreter
 * over existing engine operations, not a second engine. See
 * {@link com.laosun.aluminium.models.TriggerInterpreter} for the implemented ops and
 * {@code ROADMAP.md} P8-7 for the planned ones.
 *
 * <p>Which fields an op reads depends on the op (for example {@code GAIN_ENERGY} uses
 * {@link #amount} while {@code APPLY_BUFF} uses {@link #buff}); unused fields stay
 * {@code null} in the JSON. The interpreter validates the required ones and reports the trigger's
 * origin when something is missing.
 */
@Getter
@ToString
@NoArgsConstructor
public class EffectSpec {

    /**
     * A <b>deep-enough copy</b>: every field is carried over, so a caller can adjust one of them without touching the
     * compiled rule that the whole battle (and every other battle) shares (2026-09-28).
     *
     * <p>⚠ <b>Every field must be listed here.</b> A field added to this bean and forgotten in this method would be
     * silently dropped for exactly the firings that amend something -- a wrong number with no symptom. The guard is
     * {@code RuleEffectAmendmentTest.theCopyCarriesEveryField}, which compares all getters by reflection.
     */
    public EffectSpec copy() {
        EffectSpec copy = new EffectSpec();
        copy.op = this.op;
        copy.amount = this.amount;
        copy.scale = this.scale;
        copy.attribute = this.attribute;
        copy.percent = this.percent;
        copy.turns = this.turns;
        copy.permanent = this.permanent;
        copy.maxStacks = this.maxStacks;
        copy.stacks = this.stacks;
        copy.resource = this.resource;
        copy.until = this.until;
        copy.buff = this.buff;
        copy.ticksOn = this.ticksOn;
        copy.target = this.target;
        copy.skill = this.skill;
        copy.damageParam = this.damageParam;
        copy.damageLevel = this.damageLevel;
        copy.asAttack = this.asAttack;
        copy.rule = this.rule;
        copy.perTarget = this.perTarget;
        copy.control = this.control;
        copy.baseChance = this.baseChance;
        copy.speed = this.speed;
        copy.suspendsTurns = this.suspendsTurns;
        copy.critRate = this.critRate;
        copy.critDamage = this.critDamage;
        copy.element = this.element;
        copy.kind = this.kind;
        copy.effectPercent = this.effectPercent;
        copy.effectTurns = this.effectTurns;
        copy.targetWhen = this.targetWhen == null ? null : new java.util.ArrayList<>(this.targetWhen);
        copy.effectMaxStacks = this.effectMaxStacks;
copy.damageType = this.damageType;
                copy.capScale = this.capScale;
        copy.capPercent = this.capPercent;
        copy.skillId = this.skillId;
return copy;
    }


    /** The same, with the value every {@code percent} read is raised by (see {@code MODIFY_RULE}). */
    public EffectSpec withPercent(Double delta) {
        EffectSpec copy = copy();
        if (copy.percent != null && delta != null) {
            copy.percent = copy.percent + delta;
        }
        return copy;
    }

    /** The same, with every {@code max_stacks} read raised by {@code delta} (see {@code MODIFY_RULE}). */
    public EffectSpec withMaxStacks(int delta) {
        EffectSpec copy = copy();
        if (copy.maxStacks != null) {
            copy.maxStacks = copy.maxStacks + delta;
        }
        return copy;
    }

    /** The same, with the duration every {@code turns} read is raised by (see {@code MODIFY_RULE}). */
    public EffectSpec withTurns(Integer delta) {
        EffectSpec copy = copy();
        if (copy.turns != null && delta != null) {
            copy.turns = copy.turns + delta;
        }
        return copy;
    }


    /**
     * The operation name, e.g. {@code "GAIN_ENERGY"}.
     */
    @SerializedName("op")
    private String op;

    /**
     * Magnitude for numeric ops (energy, resource stacks, damage multiplier, ...).
     */
    @SerializedName("amount")
    private Double amount;

    /**
     * What a {@code HEAL} / {@code SHIELD} amount is a <b>percentage of</b>, instead of a flat number.
     *
     * <p>The game states most heals and shields as a share of somebody's Max HP (「回复等同于 X% 生命上限的生命值」),
     * and the skill-side loader already carries that idea in {@code skill_effects.json}'s {@code scale} field.
     * Two spellings, and the set is closed:
     * <ul>
     *   <li>{@code "target_max_hp"} — a share of the <b>receiving</b> unit's Max HP (relic set 106's 4-piece:
     *       "restores HP equal to 8% of their Max HP");</li>
     *   <li>{@code "owner_max_hp"} — a share of the <b>rule owner's</b> Max HP, i.e. the healer's
     *       ({@code skill_effects.json} spells this one {@code healer_max_hp}).</li>
     * </ul>
     * Stated <b>instead of</b> {@link #amount}, and it requires {@link #percent}: a scale without a magnitude
     * would say "some share of a Max HP", which is not a number.
     *
     * <p>⚠ <b>{@code GAIN_ENERGY} names one more</b> (M-44): {@code "target_max_energy"} — 「恢复等同于 #1% <b>能量
     * 上限</b>的能量」 (星期日's ultimate). It is the same idea as the Max-HP ones (a share of a per-character maximum
     * that the rule cannot know), and it exists because the maximum is per character: 姬子 120 / 星期日 130 / 翡翠 140,
     * so any flat number would be wrong for every one of them.
     *
     * <p>⚠ <b>{@code MODIFY_ATTR} has a third spelling</b> (P11-2, M-42): {@code "self_attr:<ATTRIBUTE>"} — the
     * modifier's value is then <b>derived</b> from one of the <b>rule owner's</b> attributes
     * ({@code "scale": "self_attr:BREAKING_EFFECT"} + {@code percent} + optional {@code amount}), i.e.
     * 「提高数值等同于大丽花 <b>#1% 的击破特攻 + #3%</b>」. It is the same prefix the condition DSL uses for "my
     * own attribute" ({@code TriggerTable.SELF_ATTR_PREFIX}), deliberately: one reads it as a threshold, the other
     * as a magnitude, and both mean the rule owner. Two things follow from "the number was computed":
     * <ul>
     *   <li>the result is an <b>absolute</b> value in the target attribute's own units — even on a base
     *       attribute, where a literal {@code percent} would otherwise mean "a share of the target's base";</li>
     *   <li>it is computed once, when the rule fires, and then frozen inside an ordinary modifier (the engine's
     *       existing snapshot convention, §24.5), which is why {@link #amount} is the flat <b>addend</b> here and
     *       not a second magnitude.</li>
     * </ul>
     * An {@code amount} on {@code MODIFY_ATTR} <b>without</b> a scale is refused rather than ignored.
     */
    @SerializedName("scale")
    private String scale;

    /**
     * Attribute name for {@code MODIFY_ATTR}, matching {@code AttributeType} (e.g. {@code "ATTACK"}).
     *
     * <p>For {@code COMMAND_SUMMON} it names the attribute the commanded attack scales off <b>on the
     * summon</b> ({@code "HEALTH"} = 「等同于忆灵 X% 生命上限」), which is the same question
     * {@code MODIFY_ATTR} asks of the unit it modifies: "which attribute is in play here". The multiplier
     * itself comes from the named {@code skill}'s parameter row, not from this file.
     */
    @SerializedName("attribute")
    private String attribute;

    /**
     * A percentage argument: the modifier for {@code MODIFY_ATTR} (0.5 = +50%), or the fraction for
     * {@code ADVANCE} (0.5 = skip half of the target's **remaining** time to act, matching
     * {@code Queue.advanceActionByPercent}, which requires 0.0–1.0).
     */
    @SerializedName("percent")
    private Double percent;

    /**
     * Which <b>kind</b> of incoming damage this modifier is about, or {@code null} for "all damage" (2026-09-28).
     *
     * <p>「【酩酊】使目标受到的<b>击破伤害</b>提高 12.00%」 (1301 Gallagher's talent) names one of the kinds the engine settles,
     * and the trap it has to avoid is on the other side of the same word: {@code BREAKING_EFFECT} is "how hard <b>I</b> break",
     * this is "how hard break damage hurts <b>me</b>". Spell it with {@code DamageType}'s own names; a typo is refused loudly.
     */
    /**
     * A derived <b>ceiling</b> on this effect's magnitude: {@code cap_scale} + {@code cap_percent} (2026-09-28).
     *
     * <p>「受到等同于自身 24.00% 生命上限的…持续伤害，<b>最多不超过卢卡攻击力的 338%</b>」 (1111 卢卡 战技) is a
     * {@code min_of_two}: the magnitude is the smaller of two derived values. Spell it as the primary value plus a ceiling --
     * {@code scale}/{@code percent} for the first, {@code cap_scale}/{@code cap_percent} for the second -- and the engine takes
     * the minimum.
     *
     * <p>⚠ Only {@code APPLY_DOT} reads these today, and every other op <b>refuses</b> them rather than ignoring them: a field
     * that is silently dropped is the class of mistake this project keeps closing.
     */
    @SerializedName("cap_scale")
    private String capScale;

    /** The ceiling's share, used with {@link #capScale} (both are required together). */
    @SerializedName("cap_percent")
    private Double capPercent;

    /**
     * A <b>data row id</b> for a skill, used by {@code REPLACE_SKILL} (2026-09-28).
     *
     * <p>「将下一次普攻强化为【酒花奔涌】」 (1301 加拉赫): the enhanced attack is its own row (130108 for him) and belongs to no
     * {@code SkillType} slot, so it has to be named by row id. ⚠ `SkillData.init(cid, skillID)` resolves ids against
     * `Constant.SKILLS` -- the "slot" wording in {@code DefaultSkill}'s javadoc is about that class's own callers, not about
     * what the loader accepts.
     */
    @SerializedName("skill_id")
    private Integer skillId;

    @SerializedName("damage_type")
    private String damageType;

    /**
     * Duration in turns, where the op needs one.
     *
     * <p>For {@code MODIFY_ATTR} this is <b>exactly one of</b> this field and {@link #permanent}: a
     * modifier either lasts N of the owner's turns or lasts until the battle ends ("for the rest of the
     * battle"), and the interpreter rejects a rule that states neither or both.
     */
    @SerializedName("turns")
    private Integer turns;

    /**
     * For {@code MODIFY_ATTR}: whether the modifier lasts <b>until the battle ends</b> rather than for a
     * number of turns.
     *
     * <p>Spelled as a flag on purpose. The alternative — a large {@code turns} value — would be a magic
     * number that the engine still counts down once per turn, so "unbounded" would only mean "longer
     * than the battle probably lasts". The buff is instead never ticked at all
     * ({@code AbstractBuff.isPermanent()}), which is what makes the duration exact.
     *
     * <p>Absent/{@code false} means "use {@link #turns}"; there is no default duration.
     */
    @SerializedName("permanent")
    private Boolean permanent;

    /**
     * For {@code MODIFY_ATTR}: how many copies of the modifier may <b>accumulate</b> ("this effect can
     * stack up to #N time(s)").
     *
     * <p>Absent or {@code 1} keeps the engine's historical replace-on-same-kind rule — re-applying the
     * same attribute modifier refreshes it instead of adding a second one
     * ({@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking}). Above 1 the modifier becomes a
     * stack: every application adds another instance until the cap is reached, further applications
     * change nothing, and each stack is removable on its own
     * ({@code BuffManager.addStackable}).
     *
     * <p>{@code "stacks"} is accepted as an alias, because the game text says "stacking up to #N
     * time(s)" and an author writing the rule reads that word first.
     */
    @SerializedName("max_stacks")
    private Integer maxStacks;

    /**
     * Alias of {@link #maxStacks}, from the wording of the effect text ("stacking up to N time(s)").
     * Stating both is rejected at load time rather than silently picking one.
     */
    @SerializedName("stacks")
    private Integer stacks;

    /**
     * Resource id for {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} (e.g. {@code "tribbie_charge"}).
     */
    @SerializedName("resource")
    private String resource;

    /**
     * For {@code MODIFY_ATTR} / {@code APPLY_BUFF} / {@code MODIFY_DAMAGE_TAKEN}: the buff ends when its
     * <b>owner does something</b>, instead of after a number of turns (「持续到施放首次攻击后结束」).
     *
     * <p><b>Why a lifetime and not a turn count.</b> The texts that need this say things like "for the
     * next attack" / "the next Skill" / "until after the wearer's first attack" — none of which is a
     * number of turns. Writing them as {@code turns: 1} would expire the buff on the wrong turn boundary
     * (and keep it through a turn in which nothing was attacked), and writing them as
     * {@code permanent: true} would leave it up for the rest of the battle: a wrong number with nothing
     * to see. So the lifetime names the <b>event</b> that ends it, and the set is closed:
     * <ul>
     *   <li>{@code "next_attack"} — after the owner finishes an attack that landed (basic attack, Skill or
     *       Ultimate; relic set 305's 「持续到施放首次攻击后结束」 and set 107's 「for the next attack」). A
     *       <b>summon's</b> attack ends a buff on the <b>summon</b> the same way (P9-4 忆灵), and it does not
     *       end its summoner's: the notification asks {@code attacker == owner};</li>
     *   <li>{@code "next_skill"} — after the owner casts a Skill (set 122's 「the next Skill」);</li>
     *   <li>{@code "next_ultimate"} — after the owner casts an Ultimate.</li>
     * </ul>
     *
     * <p><b>Several events may be named at once</b> — a list, and the buff ends at the <b>first</b> of them. The
     * game states durations as disjunctions ("持续至装备者下次施放<b>普攻或战技</b>后", relic set 127), and naming
     * only one of the two would be a wrong duration with nothing to see; two separate buffs would instead
     * <b>replace each other</b> (same kind, same target) and leave only the last one up. {@code "until"} is
     * therefore either one name or a list of them, and the set of names stays closed:
     * {@code ["until": "next_attack"]} and {@code ["until": ["next_attack", "next_skill"]]} are both a single
     * duration, not a fallback chain.
     *
     * <p>⚠ Exactly one of {@code turns} / {@code permanent} / {@code until} may be stated; the interpreter
     * refuses two rather than picking one. ⚠ An attack that <b>hits nothing</b> does not consume it either: the
     * engine announces an attack only once a target has been hit. ⚠ A <b>follow-up attack does not consume</b>
     * it: derived hits (additional damage, true damage, DOT, break) are deliberately kept out of the
     * attack-level notification, which is what stops "additional damage kills → additional damage" from
     * recursing — registered as M-27 rather than worked around.
     *
     * <p>⚠ The event that <b>created</b> the buff does not consume it: the engine settles the landed attack
     * ({@code Battle.fireAfterAttack}) and the buff-level cast notification before it delivers the cast trigger
     * events that can create the buff, so 「施放普攻后…持续至下次施放普攻后」 lasts for a whole turn rather than
     * being granted and immediately taken away.
     */
    @SerializedName("until")
    @JsonAdapter(UntilNames.class)
    private List<String> until;

    /**
     * Reads {@code "until"} written either as one lifetime name or as a list of them.
     *
     * <p>Two shapes for one field, because both are natural to write and neither is wrong: a rule with a single
     * ending event says {@code "until": "next_attack"}, while a rule whose text is a disjunction says
     * {@code "until": ["next_attack", "next_skill"]}. Leaving the field a {@code String} would make the second
     * shape a parse failure of the whole file; a {@code String} that the interpreter split on commas would make
     * a typo ({@code "next_attak"}) into two lifetimes or none.
     *
     * <p>A {@link JsonDeserializer} rather than a {@code TypeAdapter}, because only reading has to bend — nothing
     * writes these files back.
     */
    static class UntilNames implements JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            if (json.isJsonPrimitive()) {
                return List.of(json.getAsString());
            }
            if (json.isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray()) {
                    if (!element.isJsonPrimitive()) {
                        throw new JsonParseException(
                                "\"until\" must name lifetimes, but one entry is " + element);
                    }
                    names.add(element.getAsString());
                }
                return names;
            }
            throw new JsonParseException("\"until\" must be a lifetime name or a list of them, e.g. "
                    + "\"next_attack\" or [\"next_attack\", \"next_skill\"], but is " + json);
        }
    }

    /**
     * Buff kind for {@code APPLY_BUFF}.
     */
    @SerializedName("buff")
    private String buff;

    /**
     * Whose <b>turns</b> spend this buff's duration: {@code "self"} = the <b>rule owner's</b> (M-42 ④). Absent =
     * the unit that receives the buff.
     *
     * <p><b>Why it has to be statable.</b> 星期日's 【蒙福者】 is granted to an ally and says 「星期日自身每回合开始时
     * 【蒙福者】状态持续回合减1」 — the state sits on the ally while its clock is <b>his</b>. Left to itself the engine
     * counts a buff down on the turns of whoever carries it, so the same sentence would last a different number of
     * turns in every fight, with nothing to report.
     *
     * <p>⚠ One value only, and it is the one a document asked for: {@code "self"}. The default (the carrier) is
     * spelled by saying nothing — an axis with one used value is an axis nobody has tested (the same call as
     * {@code self_attr:} having no {@code target_attr:} twin). ⚠ The anchor's <b>death</b> removes these buffs
     * ({@code Battle.releaseBuffsAnchoredToTheDead}): a clock that will never come again is a leak, not a duration.
     */
    @SerializedName("ticks_on")
    private String ticksOn;

    /**
     * Who the effect applies to: {@code "self"} (the default -- the character whose table fired),
     * {@code "target"} (the subject of the event: whoever lost HP, was healed, was hit, ...), or
     * {@code "attacker"} (whoever <b>caused</b> the event).
     *
     * <p>{@code "attacker"} is what a counter needs: "I was hit, so I hit the one who hit me".
     *
     * <p>Absent means {@code "self"}, which keeps "my own resource" rules terse -- they are the
     * overwhelming majority.
     */
    @SerializedName("target")
    private String target;

    /**
     * Which skill slot a {@code DAMAGE} effect takes its attack from, by
     * {@link com.laosun.aluminium.enums.SkillType} name (e.g. {@code "TALENT"}).
     *
     * <p>The talent slot is the usual source for a follow-up attack: in the game the talent is the
     * passive that <b>releases</b> it, which is also why the talent's own {@code attack_type} is empty
     * (a passive is not a swing). Its data still carries the full attack payload -- effect shape,
     * element, toughness values and the per-level multiplier -- so the engine reads the attack from
     * there instead of duplicating numbers into the character file.
     */
    @SerializedName("skill")
    private String skill;

    /**
     * Which entry of the skill's per-level parameter row is the damage multiplier.
     *
     * <p>⚠ This exists because <b>no single index works</b>. Basic attacks and skills happen to carry
     * their multiplier first, but for talents it moves around:
     * <ul>
     *   <li>Clara 1107 — {@code [1, 0.8, 0.1]} → index 1;</li>
     *   <li>Moze 1223 — {@code [0.15, 3, 0.8]} → index 2;</li>
     *   <li>Dahlia 1321 — {@code [0.15, 5, 1, 35, 0.3]} → index 2.</li>
     * </ul>
     * Guessing a fixed index would quietly compute the wrong number for a whole class of abilities, so
     * the parameter's meaning is stated per skill in the data instead.
     */
    @SerializedName("damage_param")
    private Integer damageParam;

    /**
     * Which <b>level row</b> of the skill's parameter table the multiplier is read from; absent means the
     * skill's own level ({@code Skill.getLevel()}).
     *
     * <p>⚠ This exists because a character's skills are all at <b>level 1</b> in this engine
     * ({@code Character.Builder} initialises every slot to 1 and nothing raises it), while the documents quote
     * their figures at <em>whatever level that skill's prose happens to be written at</em> — 长夜月's ultimate
     * quotes 200% (the Lv10 row) and its 忆灵技1 quotes 50% (the Lv6 row). Reading "the skill's level" therefore
     * silently produces a different number from the text: her ultimate would deal 100% of the memosprite's Max HP
     * instead of 200%, with nothing to report. A rule that means the number the document states says which row
     * it read, exactly as it says which column ({@link #damageParam}).
     *
     * <p>{@code damage_param} picks the <b>column</b>, this picks the <b>row</b> — both are needed, and a level
     * outside the table is refused loudly when the effect fires (the rule does not know its owner's cid at load
     * time, so that is the earliest point at which the table is in hand).
     */
    @SerializedName("damage_level")
    private Integer damageLevel;

    /**
     * Whether a {@code DAMAGE} effect <b>counts as an attack</b>.
     *
     * <p>{@code true} — a follow-up attack: a real hit, so it participates in attack-level events and
     * the target's "on being hit" energy.
     * {@code false} — supplementary damage (the officially defined 附加伤害/真伤, which explicitly
     * "does not count as dealing 1 attack").
     *
     * <p>Absent means {@code false}: the conservative reading, and what the engine's existing
     * additional-damage path already implements.
     */
    @SerializedName("as_attack")
    private Boolean asAttack;

    /**
     * The <b>id of another rule</b> this effect amends — read only by {@code MODIFY_RULE}
     * (「天赋的反击效果每回合可触发的次数增加1次」 / 「施放终结技时，冻结敌方目标的基础概率提高15%」).
     *
     * <p><b>Why a rule needs a name at all.</b> These sentences do not create anything: they raise a number that
     * <b>already exists</b> on another rule in the same file ("that counter", "that freeze"). Writing a second rule
     * with the raised number instead is a wrong answer that looks right — a second {@code per_turn: 3} rule would
     * <i>add</i> firings (2 + 3 = 5 per turn) rather than raise the cap to 3, and a second {@code base_chance: 0.65}
     * rule would roll twice (1 − 0.5 × 0.35 = 82.5% instead of 65%). So the target is named, and the reference is
     * checked at load time (it must exist in the same file, and must actually state the number being raised).
     *
     * <p>⚠ The name is scoped to <b>one file</b>: ids are unique per table, and a reference that does not resolve
     * inside the same table is refused. That is deliberate — a relic rule shared by every wearer has no way to know
     * which character's rules it is being merged with, so "amend somebody else's rule" is not expressible by accident.
     */
    @SerializedName("rule")
    private String rule;

    /**
     * Whether {@link #amount} is <b>per target hit</b> rather than a flat total.
     *
     * <p>This distinction is not cosmetic — the game states both forms and they differ:
     * <ul>
     *   <li>Robin's talent: "after an ally attacks an enemy, restore <b>2</b> energy" — a flat 2 per
     *       attack, no matter how many targets it hit;</li>
     *   <li>Tribbie's trace: "for <b>each target hit</b>, restore <b>1.50</b> energy" — 1.5 × the
     *       number of targets.</li>
     * </ul>
     * When {@code true}, the interpreter multiplies {@link #amount} by the event's hit count; when
     * absent or {@code false} the amount is used as-is. It is opt-in so that a rule which happens to
     * fire on an attack event does not silently start scaling.
     */
    @SerializedName("per_target")
    private Boolean perTarget;

    /**
     * The <b>control state</b> this effect applies — the name the documents and the {@code has_state} condition
     * use (冻结 / 纠缠 / 禁锢). Only {@code APPLY_CONTROL} reads it; the closed set is
     * {@code Constant.CONTROL_STATES}, and a name that is not in it is refused at load time.
     *
     * <p>What the state <i>does</i> (whether it blocks acting, how far it slows) is the engine's table, not the
     * rule's: a rule says 「陷入冻结状态」, and 冻结 is one thing. What the rule states is how long it lasts
     * ({@link #turns}) and how likely it is ({@link #baseChance}).
     */
    @SerializedName("control")
    private String control;

    /**
     * The <b>base chance</b> (基础概率) that this effect lands, per target — 「有 50% 基础概率陷入冻结状态」 is
     * {@code 0.5}. Absent = it always lands.
     *
     * <p>⚠ <b>Not the same thing as the rule's {@code chance}</b>, which is why the two are spelled with
     * different words:
     * <ul>
     *   <li>a rule's {@code chance} is a <b>fixed</b> probability (固定概率) for the whole rule: one roll per
     *       firing, and nothing in the battle can change it;</li>
     *   <li>this is a <b>base</b> chance per target, which the engine runs through the real pipeline
     *       ({@code Battle.hitChance}: base × (1 + the applier's 效果命中) × (1 − the victim's 效果抵抗) ×
     *       (1 − its specific resistance for this state)). 「50% 基础概率」 is 50% <i>before</i> those, exactly
     *       as the text means it, and the roll happens once per victim — three enemies can see three different
     *       outcomes, which a rule-level roll could never express.</li>
     * </ul>
     */
    @SerializedName("base_chance")
    private Double baseChance;

    /**
     * 「固定拥有 90 点速度」 -- the speed a {@code START_COUNTDOWN} countdown runs at (M-49).
     *
     * <p>⚠ Its own field rather than a reused {@code percent}: a speed is an absolute number in the speed stat's units
     * (90, the same 90 that appears on a stat sheet), and folding it into a percentage field would make the reader
     * guess the base it is a percentage <i>of</i>.
     */
    @SerializedName("speed")
    private Double speed;

    /**
     * \u300c\u8be5\u4f24\u5bb3\u66b4\u51fb\u7387\u56fa\u5b9a\u4e3a 100%\u300d -- a damage instance that does not roll to crit (M-55 姊妹).
     *
     * <p>⚠ Only {@code 1.0} is a legal value, and that is the vocabulary being closed rather than lazy: a
     * <b>probabilistic</b> crit rate is the {@code CRIT_CHANCE} attribute and always has been, while this field says
     * \u300c\u56fa\u5b9a\u4e3a\u300d -- the outcome is not rolled at all (`Damage.fixedCrit`). A "fixed 50%" would be a third thing
     * nobody can read, so the loader refuses it by name.
     */
    @SerializedName("suspends_turns")
    private Boolean suspendsTurns;

    @SerializedName("crit_rate")
    private Double critRate;

    /**
     * \u300c\u66b4\u51fb\u4f24\u5bb3\u56fa\u5b9a\u4e3a 150%\u300d -- the crit damage a {@code fixed_crit} instance uses instead of the
     * attacker's own crit damage stat (1.5 = 150%). Stated together with {@link #critRate}, never alone.
     */
    @SerializedName("crit_damage")
    private Double critDamage;

    /**
     * The <b>damage element</b> of the per-turn damage this effect attaches — {@code "Ice"} / {@code "Fire"} / …
     * (the {@code DamageElement} spelling, the same one {@code memosprites/<cid>.json}'s {@code attack.element}
     * uses). Read by {@code APPLY_DOT}, and by {@code APPLY_CONTROL} for the state's own per-turn damage.
     *
     * <p>⚠ Only the <b>element</b> is named in English; the <i>state</i> a DOT represents is spelled in Chinese
     * (灼烧 / 触电 / 裂伤 / 风化) because that is what the documents and the {@code has_state} condition use — the
     * engine's one translation between the two is {@code BuffManager.DOT_STATES}, deliberately the only place that
     * knows they are the same thing.
     */
    @SerializedName("element")
    private String element;

    /**
     * The <b>class</b> of negative state an effect is about — {@code "control"} (控制类) or {@code "dot"} (持续伤害类).
     * Read by {@code RESIST_DEBUFF}, whose sentence is about a whole family rather than one named state
     * (「抵抗<b>控制类</b>负面状态的概率提高35%」).
     *
     * <p>The closed set is {@code DebuffClass}, and it is deliberately small: it holds the two classes the documents
     * name, and it grows when a third is quoted rather than when someone imagines one.
     */
    @SerializedName("kind")
    private String kind;
    /**
     * {@code MODIFY_RULE}: raise every {@code percent} read of the named rule's effects by this much
     * (「天赋的伤害提高效果额外提高 10%」，1215 星魂 6).
     */
    @SerializedName("effect_percent")
    private Double effectPercent;
    /**
     * {@code MODIFY_RULE}: raise every {@code turns} read of the named rule's effects by this many turns
     * (「终结技的持续时间额外增加 1 回合」，1215 星魂 4).
     */
    @SerializedName("effect_turns")
    private Integer effectTurns;
    /**
     * <b>Per-target conditions</b> (2026-09-28, M-53): the effect reaches only the units that satisfy these, tested one
     * candidate at a time with {@code target} bound to that candidate.
     *
     * <p>「对所有<b>触电状态下的</b>敌方目标造成…附加伤害」 (1103's talent) is the sentence that needed it: a rule's own
     * conditions filter the <b>rule</b>, so 「all shocked enemies」 could only be spelled "the enemy I hit was shocked"
     * (which then also hit the unshocked ones) or not at all. The selector says <i>which units</i>; this says <i>which
     * of them qualify</i>.
     */
    @SerializedName("target_when")
    private List<String> targetWhen;
    /**
     * {@code MODIFY_RULE}: raise the <b>stack cap</b> of the named rule's effects by this many layers
     * (「天赋的效果可叠加上限提高 2 层」，1302 星魂 4).
     *
     * <p>⚠ A cap is not a value: the difference only shows when the content *would* have exceeded the old limit, which is
     * why filing two extra stacks instead would be wrong (they cannot exceed 10).
     */
    @SerializedName("effect_max_stacks")
    private Integer effectMaxStacks;

    /**
     * The stack cap, whichever spelling the rule used.
     *
     * <p>Read by the interpreter <b>after</b> it has rejected "both spellings stated at once", so the
     * two can never disagree here. Returns {@code null} when the rule says nothing about stacking,
     * which is the "replace, do not stack" default.
     *
     * @return the declared stack cap, or {@code null}
     */
    public Integer stackCap() {
        return maxStacks != null ? maxStacks : stacks;
    }
}
