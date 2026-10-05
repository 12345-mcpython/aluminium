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
 * One effect inside a trigger (P8-): "do this".
 *
 * <p>Every effect is a {@code (op, ...args)} pair. The {@code op} vocabulary is deliberately
 * restricted to <b>capabilities the engine already has</b> -- the trigger table is an interpreter
 * over existing engine operations, not a second engine. See
 * {@link com.laosun.aluminium.models.TriggerInterpreter} for the implemented ops and
 * {@code ROADMAP.md} P8-for the planned ones.
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
     * <p>Note: <b>Every field must be listed here.</b> A field added to this bean and forgotten in this method would be
     * silently dropped for exactly the firings that amend something -- a wrong number with no symptom. The guard is
     * {@code RuleEffectAmendmentTest.theCopyCarriesEveryField}, which compares all getters by reflection.
     */
    public EffectSpec copy() {
        EffectSpec copy = new EffectSpec();
        copy.op = this.op;
        copy.amount = this.amount;
        copy.amountFromAttr = amountFromAttr;
        copy.amountPercent = amountPercent;
        copy.amountFromResource = amountFromResource;
        copy.overflowOnly = overflowOnly;
        copy.amountPercentFromResource = amountPercentFromResource;
        copy.percentFromResource = percentFromResource;
        copy.skillParamCid = skillParamCid;
        copy.amountFromEvent = amountFromEvent;
        copy.amountFromPrevious = amountFromPrevious;
        copy.minEidolon = minEidolon;
        copy.amountCap = amountCap;
        copy.ordinary = ordinary;
        copy.perStack = this.perStack;
        copy.scale = this.scale;
        copy.attribute = this.attribute;
        copy.percent = this.percent;
        copy.turns = this.turns;
        copy.permanent = this.permanent;
        // The deferral must survive a copy, or a rule that is amended/copied silently loses it (the guard test says so).
        copy.defersDeath = this.defersDeath;
        copy.stackable = this.stackable;
        copy.coexist = this.coexist;
        copy.perStackLive = this.perStackLive;
        copy.maxStacks = this.maxStacks;
        copy.instance = this.instance;
        copy.stacks = this.stacks;
        copy.resource = this.resource;
        copy.until = this.until;
        copy.buff = this.buff;
        copy.ticksOn = this.ticksOn;
        copy.target = this.target;
        copy.castTarget = this.castTarget;
        copy.skill = this.skill;
        copy.damageParam = this.damageParam;
        copy.percentFromCastParam = this.percentFromCastParam;
        copy.percentFromSkillParam = this.percentFromSkillParam;
        copy.castCategory = this.castCategory;
        copy.damageLevel = this.damageLevel;
        copy.asAttack = this.asAttack;
        copy.rule = this.rule;
        copy.perTarget = this.perTarget;
        copy.times = this.times;
        copy.timesFrom = this.timesFrom;
        copy.spendAll = this.spendAll;
        copy.control = this.control;
        copy.baseChance = this.baseChance;
        copy.speed = this.speed;
        copy.suspendsTurns = this.suspendsTurns;
        copy.critRate = this.critRate;
        copy.critDamage = this.critDamage;
        copy.element = this.element;
        copy.kind = this.kind;
        copy.effectPercent = this.effectPercent;
        copy.effectPercentFromResource = effectPercentFromResource;
        copy.effectTurns = this.effectTurns;
        copy.targetWhen = this.targetWhen == null ? null : new java.util.ArrayList<>(this.targetWhen);
        copy.effectMaxStacks = this.effectMaxStacks;
copy.damageType = this.damageType;
                copy.capScale = this.capScale;
        copy.capPercent = this.capPercent;
        copy.capAmount = this.capAmount;
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
     * An amount read from an ATTRIBUTE instead of a literal (2026-09-30; reader: light cone/character 1505
     * 绯英's talent "绯英获得等同于暴击伤容 <b>50%</b> 的欢愉度"). The clause names a SHARE of a panel
     * value, which no literal can carry -- until this field a `GAIN_RESOURCE` rule could only add a fixed number, so
     * that sentence had no spelling at all.
     */
    private String amountFromAttr;
    /**
     * The share of {@link #amountFromAttr} to take (0.5 for "50%"). Null means the whole value.
     */
    private Double amountPercent;

    /**
     * An amount that IS a resource's value (2026-10-02).
     *
     * <p>Reader: 1415's time ode -- "长夜月施放战技/终结技后，额外获得 #2 点[亿质]". #2 lives in the memosprite's own row (unreachable later), so it is captured
     * into a resource while the ode is cast and handed over by name here.
     */
    @com.google.gson.annotations.SerializedName("amount_from_resource")
    private String amountFromResource;

    /**
     * Spend only the OVERFLOW tier (2026-10-02; reader: 114151"召唤死龙时会消耗所有溢出[新蕊]").
     *
     * <p>A resource has a normal cap and an overflow above it; "消耗所有溢出" is the part above the cap, which `spendAll` (everything) and a stated `amount` (a
     * flat number) both fail to say.
     */
    @com.google.gson.annotations.SerializedName("overflow_only")
    private Boolean overflowOnly;

    /**
     * The share of an EVENT's magnitude carried in a resource, in basis points (2026-10-02).
     *
     * <p>Reader: 1415's sky ode -- "提高数值等同于本次治疗数值的 #1%". `amount_percent` is a literal, and #1 runs with level AND lives in a
     * memosprite row that cannot be read later, so it is captured (in basis points) and read back here.
     */
    @com.google.gson.annotations.SerializedName("amount_percent_from_resource")
    private String amountPercentFromResource;

    /**
     * A share CARRIED IN A RESOURCE, in basis points (2026-10-02).
     *
     * <p>Reader: 1415's odes -- a number that lives in an ode's own row cannot be read later (a memosprite's skill row is unreachable), so it is captured while the ode is cast and
     * read back through this spelling. {@code percent_from_resource: "充能"} means the resource's value over 10000, which is why the capture stores {@code percent: 10000} times the
     * share.
     */
    @com.google.gson.annotations.SerializedName("percent_from_resource")
    private String percentFromResource;

    /**
     * Whose skill row a {@code skill_param:} / {@code percent_from_skill_param} share is read from, when it is NOT the rule owner's own (2026-10-02).
     *
     * <p>Readers: 1415's odes -- "提高数值等同于本次治疗数值的 #1%" (the value is in slot 19's row; the healing is 风堇's),
     * the time ode's memosprite boost (value in slot 24; the damage is the memosprite's skill ), and the ocean ode's overflow sentence (value in slot 22; the attack is 海瑟音's).
     *
     * <p>Note: It is a FIELD and not part of the spelling on purpose: measured, a cid inside the slot string is refused --
     * {@code Op MODIFY_ATTR scales off skill slot "1415|SKILL", which is not a SkillType}.
     */
    @com.google.gson.annotations.SerializedName("skill_param_cid")
    private Integer skillParamCid;
    /**
     * Take the amount from the EVENT itself (2026-09-30; reader: 1505 绯英's talent "绯英获得能量时，
     * 将同步获得等值的[好活当赏]"). The magnitude a rule reacts to -- energy credited, damage dealt -- is already on the
     * context ({@code TriggerContext.amount}); until this flag no op could spend it, so "as much as it just gained" had no spelling.
     */
    private Boolean amountFromEvent;

    /**
     * 2026-10-02：效果级的星魂分层。读者：1505 星魂的 50%／100%，
     * 以及 1415 第二半那条"随层级"的穿透。规则级的 `min_eidolon`
     * 只能关掉整条规则，而这里要关的只是其中一条效果。
     */
    @com.google.gson.annotations.SerializedName("min_eidolon")
    private Integer minEidolon;

    /**
     * Note: 2026-10-02（读者：1505 星魂"额外获得等同于本次获得的[好活当赏]50%/100%"）：
     * 读的是<b>本条规则里前一条效果实际入账的量</b>（已经过上限截断），而不是原始事件量。
     * 原始事件量请用 {@link #amountFromEvent}。
     */
    private Boolean amountFromPrevious;
    /**
     * A ceiling on a single conversion (2026-09-30; reader: 1505 绯英's "单次通过此方式计算的
     * [好活当赏]不超过 100 点"). The clause bounds one conversion, not the resource: "获得一点能量\n     * 就得一点礼包，但一次最多给 100" is two different statements, and only the first had a spelling before this.
     */
    private Double amountCap;
    /**
     * Settle this {@code DAMAGE} effect as an ORDINARY instance rather than additional damage (2026-09-30;
     * reader: 1505 绯英's technique, "进入战斗后，对敌方全体造成等同于绯英 <b>100%</b> 攻击力的
     * <b>物理属性伤容</b>"). The two are not the same thing and the difference is measurable: additional damage is
     * boostable-only-as-additional, does not count as an attack and credits the victim energy only on a kill, while the
     * sentence above describes an ordinary hit.\n     */
    private Boolean ordinary;

    /**
     * What a {@code HEAL} / {@code SHIELD} amount is a <b>percentage of</b>, instead of a flat number.
     *
     * <p>The game states most heals and shields as a share of somebody's Max HP ("回复等同于 X% 生命上限的生命值"),
     * and the skill-side loader already carries that idea in {@code skill_effects.json}'s {@code scale} field.
     * Two spellings, and the set is closed:
     * <ul>
     *   <li>{@code "target_max_hp"} - a share of the <b>receiving</b> unit's Max HP (relic set 106's 4-piece:
     *       "restores HP equal to 8% of their Max HP");</li>
     *   <li>{@code "owner_max_hp"} - a share of the <b>rule owner's</b> Max HP, i.e. the healer's
     *       ({@code skill_effects.json} spells this one {@code healer_max_hp}).</li>
     * </ul>
     * Stated <b>instead of</b> {@link #amount}, and it requires {@link #percent}: a scale without a magnitude
     * would say "some share of a Max HP", which is not a number.
     *
     * <p>Note: <b>{@code GAIN_ENERGY} names one more</b> (M-44): {@code "target_max_energy"} - "恢复等同于 #1% <b>能量
     * 上限</b>的能量" (星期日's ultimate). It is the same idea as the Max-HP ones (a share of a per-character maximum
     * that the rule cannot know), and it exists because the maximum is per character: 姬子 120 / 星期日 130 / 翡翠 140,
     * so any flat number would be wrong for every one of them.
     *
     * <p>Note: <b>{@code MODIFY_ATTR} has a third spelling</b> (P11-2, M-42): {@code "self_attr:<ATTRIBUTE>"} - the
     * modifier's value is then <b>derived</b> from one of the <b>rule owner's</b> attributes
     * ({@code "scale": "self_attr:BREAKING_EFFECT"} + {@code percent} + optional {@code amount}), i.e.
     * "提高数值等同于大丽花 <b>#1% 的击破特攻 + #3%</b>". It is the same prefix the condition DSL uses for "my
     * own attribute" ({@code TriggerTable.SELF_ATTR_PREFIX}), deliberately: one reads it as a threshold, the other
     * as a magnitude, and both mean the rule owner. Two things follow from "the number was computed":
     * <ul>
     *   <li>the result is an <b>absolute</b> value in the target attribute's own units - even on a base
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
     * summon</b> ({@code "HEALTH"} = "等同于忆灵 X% 生命上限"), which is the same question
     * {@code MODIFY_ATTR} asks of the unit it modifies: "which attribute is in play here". The multiplier
     * itself comes from the named {@code skill}'s parameter row, not from this file.
     */
    @SerializedName("attribute")
    private String attribute;

    /**
     * A percentage argument: the modifier for {@code MODIFY_ATTR} (0.5 = +50%), or the fraction for
     * {@code ADVANCE} (0.5 = skip half of the target's remaining time to act, matching
     * {@code Queue.advanceActionByPercent}, which requires 0.0-1.0).
     */
    @SerializedName("percent")
    private Double percent;

    /** A counter this modifier's magnitude is multiplied by (2026-09-29): "每层[当品]额外使翡翠的攻击力
     * 提高0.50%" is a SHARE of the base per layer. Read from the effect's TARGET. */
    @SerializedName("per_stack")
    private String perStack;

    /**
     * Which <b>kind</b> of incoming damage this modifier is about, or {@code null} for "all damage" (2026-09-28).
     *
     * <p>"[酩酊]使目标受到的<b>击破伤害</b>提高 12.00%" (1301 Gallagher's talent) names one of the kinds the engine settles,
     * and the trap it has to avoid is on the other side of the same word: {@code BREAKING_EFFECT} is "how hard <b>I</b> break",
     * this is "how hard break damage hurts <b>me</b>". Spell it with {@code DamageType}'s own names; a typo is refused loudly.
     */
    /**
     * A derived <b>ceiling</b> on this effect's magnitude: {@code cap_scale} + {@code cap_percent} (2026-09-28).
     *
     * <p>"受到等同于自身 24.00% 生命上限的…持续伤害，<b>最多不超过卢卡攻击力的 338%</b>" (1111 卢卡 战技) is a
     * {@code min_of_two}: the magnitude is the smaller of two derived values. Spell it as the primary value plus a ceiling --
     * {@code scale}/{@code percent} for the first, {@code cap_scale}/{@code cap_percent} for the second -- and the engine takes
     * the minimum.
     *
     * <p>Note: Only {@code APPLY_DOT} reads these today, and every other op <b>refuses</b> them rather than ignoring them: a field
     * that is silently dropped is the class of mistake this project keeps closing.
     */
    @SerializedName("cap_scale")
    private String capScale;

    /** The ceiling's share, used with {@link #capScale} (both are required together). */
    @SerializedName("cap_percent")
    private Double capPercent;

    /**
     * A <b>constant</b> ceiling on this effect's magnitude (2026-09-29): {@code cap_amount}.
     *
     * <p>{@code cap_scale}+{@code cap_percent} state a ceiling DERIVED from another value; "最多使造成的伤害提高 #4%" states a
     * plain number, and no scale spelling is a constant. Three cones need it (21039, 21034, 23018).
     */
    @SerializedName("cap_amount")
    private Double capAmount;

    /**
     * A skill <b>loader key</b> for a skill, used by {@code REPLACE_SKILL} (2026-09-28).
     *
     * <p>Note: <b>The key is a SLOT, not a data row</b> (measured 2026-09-28): {@code data/skills.json} nests
     * {@code Map<cid, Map<key, Skill>>} with keys 1,2,3,4,6,,8 - slot 8 is the enhanced basic attack
     * (1301[酒花奔涌], 1111[直冲碎天拳]). A DATA ROW id loads nothing, which installs a no-op skill
     * that still passes every identity assertion; {@code EnhancedSkillDataProbeTest} guards both sides.
     * Note: The data row's last digit happens to equal the slot (130108 to 8) - a coincidence of the data, not a rule.
     */
    @SerializedName("skill_id")
    private Integer skillId;

    /**
     * The cast category of the instance a DAMAGE effect produces, spelled as {@code SkillCategory} (2026-10-02).
     *
     * <p>Reader: 1415's ode of passage, "缇宝施放追加攻击触发…时" -- the follow-up half has to be stated somewhere, and
     * `applyAdditionalDamage` fires `FOLLOW_UP` without one.
     */
    @SerializedName("cast_category")
    private String castCategory;

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
     * <p>Spelled as a flag on purpose. The alternative - a large {@code turns} value - would be a magic
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
     * <p>Absent or {@code 1} keeps the engine's historical replace-on-same-kind rule - re-applying the
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
     * Apply this modifier to the damage instance being settled instead of attaching a buff (2026-09-29).
     *
     * <p>Only meaningful on {@code DEALING_DAMAGE}, the one event carrying a {@code Damage}. Opt-in on purpose: seven
     * shipped rules already use {@code MODIFY_ATTR} on that event and must keep attaching buffs.
     */
    @SerializedName("instance")
    private Boolean instance;

    /**
     * Alias of {@link #maxStacks}, from the wording of the effect text ("stacking up to N time(s)").
     * Stating both is rejected at load time rather than silently picking one.
     */
    @SerializedName("stacks")
    private Integer stacks;

    /**
     * "将笑点计入该状态": apply this state as a <b>stackable</b> one (2026-10-02; reader: 1505's [好活当赏]).
     *
     * <p>A plain state REFRESHES when re-applied (its identity is its name, deliberately), so a count cannot ride on it;
     * this flag selects {@link com.laosun.aluminium.models.buff.StackableStateBuff}, whose instances accumulate and are
     * counted by name, while still being the `StateBuff` that announces its own end.
     */
    @SerializedName("stackable")
    private Boolean stackable;

    /**
     * {@code "coexist": true} -- this effect must not evict another effect of the same kind (2026-10-02; reader: 1408's
     * trace "进入战斗或变身结束时攻击力提高 50%", which is in effect together with her transformation's +80%).
     */
    @SerializedName("coexist")
    private Boolean coexist;

    /**
     * `per_stack` resolved at READ time instead of when the modifier is attached (2026-10-02).
     *
     * <p>"艾丝妲每拥有 1 层蓄能，会使我方全体攻击力提高 14.00%，最多 5 层": a sustained aura whose number has to follow the count.
     * A snapshot is right only at the instant it is taken, and re-attaching on every change would stack the buff
     * itself. Only the ctx-free forms of `per_stack` may be live, and the loader refuses the others rather than
     * silently keeping a snapshot.
     */
    @SerializedName("per_stack_live")
    private Boolean perStackLive;

    /**
     * Resource id for {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} (e.g. {@code "tribbie_charge"}).
     */
    @SerializedName("resource")
    private String resource;

    /**
     * For {@code MODIFY_ATTR} / {@code APPLY_BUFF} / {@code MODIFY_DAMAGE_TAKEN}: the buff ends when its
     * <b>owner does something</b>, instead of after a number of turns ("持续到施放首次攻击后结束").
     *
     * <p><b>Why a lifetime and not a turn count.</b> The texts that need this say things like "for the
     * next attack" / "the next Skill" / "until after the wearer's first attack" - none of which is a
     * number of turns. Writing them as {@code turns: 1} would expire the buff on the wrong turn boundary
     * (and keep it through a turn in which nothing was attacked), and writing them as
     * {@code permanent: true} would leave it up for the rest of the battle: a wrong number with nothing
     * to see. So the lifetime names the <b>event</b> that ends it, and the set is closed:
     * <ul>
     *   <li>{@code "next_attack"} - after the owner finishes an attack that landed (basic attack, Skill or
     *       Ultimate; relic set 305's "持续到施放首次攻击后结束" and set 10's "for the next attack"). A
     *       <b>summon's</b> attack ends a buff on the <b>summon</b> the same way (P9-4 忆灵), and it does not
     *       end its summoner's: the notification asks {@code attacker == owner};</li>
     *   <li>{@code "next_skill"} - after the owner casts a Skill (set 122's "the next Skill");</li>
     *   <li>{@code "next_ultimate"} - after the owner casts an Ultimate.</li>
     * </ul>
     *
     * <p><b>Several events may be named at once</b> - a list, and the buff ends at the <b>first</b> of them. The
     * game states durations as disjunctions ("持续至装备者下次施放<b>普攻或战技</b>后", relic set 12), and naming
     * only one of the two would be a wrong duration with nothing to see; two separate buffs would instead
     * <b>replace each other</b> (same kind, same target) and leave only the last one up. {@code "until"} is
     * therefore either one name or a list of them, and the set of names stays closed:
     * {@code ["until": "next_attack"]} and {@code ["until": ["next_attack", "next_skill"]]} are both a single
     * duration, not a fallback chain.
     *
     * <p>Note: Exactly one of {@code turns} / {@code permanent} / {@code until} may be stated; the interpreter
     * refuses two rather than picking one. Note: An attack that <b>hits nothing</b> does not consume it either: the
     * engine announces an attack only once a target has been hit. Note: A <b>follow-up attack does not consume</b>
     * it: derived hits (additional damage, true damage, DOT, break) are deliberately kept out of the
     * attack-level notification, which is what stops "additional damage kills to additional damage" from
     * recursing - registered as M-2rather than worked around.
     *
     * <p>Note: The event that <b>created</b> the buff does not consume it: the engine settles the landed attack
     * ({@code Battle.fireAfterAttack}) and the buff-level cast notification before it delivers the cast trigger
     * events that can create the buff, so "施放普攻后…持续至下次施放普攻后" lasts for a whole turn rather than
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
     * <p>A {@link JsonDeserializer} rather than a {@code TypeAdapter}, because only reading has to bend - nothing
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
     * "暂时<b>延后</b>陷入无法战斗状态": the state this effect applies does not let its carrier die (2026-10-02; reader: 140's 月茧之庇).
     *
     * <p>Note: Only {@code APPLY_BUFF} reads it. The engine holds the death instead of committing it, and commits it at the
     * carrier's next turn if the state is still there -- so the state's own removal is what saves the carrier. See
     * {@link com.laosun.aluminium.models.buff.DeferredDeathBuff}.
     */
    @SerializedName("defers_death")
    private Boolean defersDeath;

    /**
     * Whose <b>turns</b> spend this buff's duration: {@code "self"} = the <b>rule owner's</b> (M-42 ④). Absent =
     * the unit that receives the buff.
     *
     * <p><b>Why it has to be statable.</b> 星期日's [蒙福者] is granted to an ally and says "星期日自身每回合开始时
     * [蒙福者]状态持续回合减1" - the state sits on the ally while its clock is <b>his</b>. Left to itself the engine
     * counts a buff down on the turns of whoever carries it, so the same sentence would last a different number of
     * turns in every fight, with nothing to report.
     *
     * <p>Note: One value only, and it is the one a document asked for: {@code "self"}. The default (the carrier) is
     * spelled by saying nothing - an axis with one used value is an axis nobody has tested (the same call as
     * {@code self_attr:} having no {@code target_attr:} twin). Note: The anchor's <b>death</b> removes these buffs
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
     * <b>Who a commanded cast is aimed at</b> - {@code CAST_SKILL} only (2026-10-02).
     *
     * <p><b>Why a cast needs two names.</b> {@link #target} says <i>who performs the cast</i>, and this one says
     * <i>which unit that cast is aimed at</i>. The sentence that needed it states both, and they are different units:
     * 1414 丹恒-腾荒's technique says "下一次战斗开始时自动对<b>持有[同袍]的角色</b>施放 1 次战技" - <b>he</b> casts it
     * ({@code "target": "self"}), and it is aimed at <b>whoever holds [同袍]</b>
     * ({@code "cast_target": "holder_of:同袍"}). Folding the two into one field was not an option: with the aim
     * expressed as "the caster", the skill's own "使指定我方单体角色成为[同袍]" would re-designate the wrong ally.
     *
     * <p>It takes the same selector vocabulary as {@link #target} (a closed set plus the {@code holder_of:} prefix),
     * is validated at load time, and is <b>refused on every other op</b> - a field the engine ignores is the failure
     * mode this project ranks worst.
     *
     * <p>For a damaging cast the aimed unit becomes the main target (the one a single-target skill lands on, and the
     * centre of a blast); for a non-damaging one it is the unit the cast's own events name as its target, while the
     * effect still reaches the whole side.
     */
    @SerializedName("cast_target")
    private String castTarget;

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
     * <p>Note: This exists because <b>no single index works</b>. Basic attacks and skills happen to carry
     * their multiplier first, but for talents it moves around:
     * <ul>
     *   <li>Clara 110- {@code [1, 0.8, 0.1]} to index 1;</li>
     *   <li>Moze 1223 - {@code [0.15, 3, 0.8]} to index 2;</li>
     *   <li>Dahlia 1321 - {@code [0.15, 5, 1, 35, 0.3]} to index 2.</li>
     * </ul>
     * Guessing a fixed index would quietly compute the wrong number for a whole class of abilities, so
     * the parameter's meaning is stated per skill in the data instead.
     */
    @SerializedName("damage_param")
    private Integer damageParam;

    /**
     * The share itself, read from the parameter table of the skill that produced the event (zero-based index into its row), at the
     * caster's CURRENT level -- "等同于德谬歌生命上限的 <b>#1%</b>", where #1 runs with the level. Exactly one of this and
     * {@code percent} may be stated. Note: It exists because some sentences multiply a skill parameter BY an attribute
     * ({@code "scale": "summon_attr:HEALTH"}), and a single {@code scale} can only name one factor.
     */
    /**
     * The share itself, read from one of the RULE OWNER's OWN skills as {@code "<SKILLTYPE>:<index>"} (2026-10-02).
     *
     * <p>The sibling of {@link #percentFromCastParam}: that one reads the skill that produced the event, this one reads a slot the rule names -- which is
     * what "造成 1 次等同于缇宝 #3% 生命上限的…附加伤害" needs, since #3 belongs to his ULTIMATE while the rider hangs on somebody else's attack.
     */
    @SerializedName("percent_from_skill_param")
    private String percentFromSkillParam;

    @SerializedName("percent_from_cast_param")
    private Integer percentFromCastParam;

    /**
     * Which <b>level row</b> of the skill's parameter table the multiplier is read from; absent means the
     * skill's own level ({@code Skill.getLevel()}).
     *
     * <p>Note: This exists because a character's skills are all at <b>level 1</b> in this engine
     * ({@code Character.Builder} initialises every slot to 1 and nothing raises it), while the documents quote
     * their figures at <em>whatever level that skill's prose happens to be written at</em> - 长夜月's ultimate
     * quotes 200% (the Lv10 row) and its 忆灵技1 quotes 50% (the Lv6 row). Reading "the skill's level" therefore
     * silently produces a different number from the text: her ultimate would deal 100% of the memosprite's Max HP
     * instead of 200%, with nothing to report. A rule that means the number the document states says which row
     * it read, exactly as it says which column ({@link #damageParam}).
     *
     * <p>{@code damage_param} picks the <b>column</b>, this picks the <b>row</b> - both are needed, and a level
     * outside the table is refused loudly when the effect fires (the rule does not know its owner's cid at load
     * time, so that is the earliest point at which the table is in hand).
     */
    @SerializedName("damage_level")
    private Integer damageLevel;

    /**
     * Whether a {@code DAMAGE} effect <b>counts as an attack</b>.
     *
     * <p>{@code true} - a follow-up attack: a real hit, so it participates in attack-level events and
     * the target's "on being hit" energy.
     * {@code false} - supplementary damage (the officially defined 附加伤害/真伤, which explicitly
     * "does not count as dealing 1 attack").
     *
     * <p>Absent means {@code false}: the conservative reading, and what the engine's existing
     * additional-damage path already implements.
     */
    @SerializedName("as_attack")
    private Boolean asAttack;

    /**
     * The <b>id of another rule</b> this effect amends - read only by {@code MODIFY_RULE}
     * ("天赋的反击效果每回合可触发的次数增加1次" / "施放终结技时，冻结敌方目标的基础概率提高15%").
     *
     * <p><b>Why a rule needs a name at all.</b> These sentences do not create anything: they raise a number that
     * <b>already exists</b> on another rule in the same file ("that counter", "that freeze"). Writing a second rule
     * with the raised number instead is a wrong answer that looks right - a second {@code per_turn: 3} rule would
     * <i>add</i> firings (2 + 3 = 5 per turn) rather than raise the cap to 3, and a second {@code base_chance: 0.65}
     * rule would roll twice (1 − 0.5  x  0.35 = 82.5% instead of 65%). So the target is named, and the reference is
     * checked at load time (it must exist in the same file, and must actually state the number being raised).
     *
     * <p>Note: The name is scoped to <b>one file</b>: ids are unique per table, and a reference that does not resolve
     * inside the same table is refused. That is deliberate - a relic rule shared by every wearer has no way to know
     * which character's rules it is being merged with, so "amend somebody else's rule" is not expressible by accident.
     */
    @SerializedName("rule")
    private String rule;

    /**
     * Whether {@link #amount} is <b>per target hit</b> rather than a flat total.
     *
     * <p>This distinction is not cosmetic - the game states both forms and they differ:
     * <ul>
     *   <li>Robin's talent: "after an ally attacks an enemy, restore <b>2</b> energy" - a flat 2 per
     *       attack, no matter how many targets it hit;</li>
     *   <li>Tribbie's trace: "for <b>each target hit</b>, restore <b>1.50</b> energy" - 1.5  x  the
     *       number of targets.</li>
     * </ul>
     * When {@code true}, the interpreter multiplies {@link #amount} by the event's hit count; when
     * absent or {@code false} the amount is used as-is. It is opt-in so that a rule which happens to
     * fire on an attack event does not silently start scaling.
     */
    @SerializedName("per_target")
    private Boolean perTarget;

    /**
     * How many times this effect settles, <b>each time re-resolving its targets</b> -- "额外造成 N 次伤害，
     * 每次对随机敌方单体", which nine shipped clauses are registered on (1009, 1214, 1302, 1312, 1513, 1505,
     * 1510, 8005, 1221). Only {@code DAMAGE} reads it.
     *
     * <p><b>Why it is not {@link #perTarget}.</b> That one multiplies <i>one</i> settlement by the event's
     * hit count ("hit three enemies, so  x 3"); this one makes N <i>independent</i> settlements, each drawing
     * its own target. Stating both would have two readings, so the pair is refused at load time -- the same
     * house rule that refuses {@code scale} next to {@code per_target}.
     */
    /**
     * How many times the effect repeats, read from the triggering EVENT instead of a constant (2026-10-02);
     * the only value today is {@code "event_amount"} ("每消耗 1 点…额外 1 次"). {@code null} = use {@code times}.
     */
    /**
     * "消耗所有[X]" (2026-10-02): {@code SPEND_RESOURCE} takes whatever the holder has.
     * Mutually exclusive with {@code amount}, because "all of it" and "5 of it" are different claims.
     */
    @SerializedName("spendAll")
    private Boolean spendAll;

    @SerializedName("times_from")
    private String timesFrom;

    @SerializedName("times")
    private Integer times;

    /**
     * The <b>control state</b> this effect applies - the name the documents and the {@code has_state} condition
     * use (冻结 / 纠缠 / 禁锢). Only {@code APPLY_CONTROL} reads it; the closed set is
     * {@code Constant.CONTROL_STATES}, and a name that is not in it is refused at load time.
     *
     * <p>What the state <i>does</i> (whether it blocks acting, how far it slows) is the engine's table, not the
     * rule's: a rule says "陷入冻结状态", and 冻结 is one thing. What the rule states is how long it lasts
     * ({@link #turns}) and how likely it is ({@link #baseChance}).
     */
    @SerializedName("control")
    private String control;

    /**
     * The <b>base chance</b> (基础概率) that this effect lands, per target - "有 50% 基础概率陷入冻结状态" is
     * {@code 0.5}. Absent = it always lands.
     *
     * <p>Note: <b>Not the same thing as the rule's {@code chance}</b>, which is why the two are spelled with
     * different words:
     * <ul>
     *   <li>a rule's {@code chance} is a <b>fixed</b> probability (固定概率) for the whole rule: one roll per
     *       firing, and nothing in the battle can change it;</li>
     *   <li>this is a <b>base</b> chance per target, which the engine runs through the real pipeline
     *       ({@code Battle.hitChance}: base  x  (1 + the applier's 效果命中)  x  (1 − the victim's 效果抵抗)  x 
     *       (1 − its specific resistance for this state)). "50% 基础概率" is 50% <i>before</i> those, exactly
     *       as the text means it, and the roll happens once per victim - three enemies can see three different
     *       outcomes, which a rule-level roll could never express.</li>
     * </ul>
     */
    @SerializedName("base_chance")
    private Double baseChance;

    /**
     * "固定拥有 90 点速度" -- the speed a {@code START_COUNTDOWN} countdown runs at (M-49).
     *
     * <p>Note: Its own field rather than a reused {@code percent}: a speed is an absolute number in the speed stat's units
     * (90, the same 90 that appears on a stat sheet), and folding it into a percentage field would make the reader
     * guess the base it is a percentage <i>of</i>.
     */
    @SerializedName("speed")
    private Double speed;

    /**
     * "该伤害暴击率固定为 100%" -- a damage instance that does not roll to crit (M-55 姊妹).
     *
     * <p>Note: Only {@code 1.0} is a legal value, and that is the vocabulary being closed rather than lazy: a
     * <b>probabilistic</b> crit rate is the {@code CRIT_CHANCE} attribute and always has been, while this field says
     * "固定为" -- the outcome is not rolled at all (`Damage.fixedCrit`). A "fixed 50%" would be a third thing
     * nobody can read, so the loader refuses it by name.
     */
    @SerializedName("suspends_turns")
    private Boolean suspendsTurns;

    @SerializedName("crit_rate")
    private Double critRate;

    /**
     * "暴击伤害固定为 150%" -- the crit damage a {@code fixed_crit} instance uses instead of the
     * attacker's own crit damage stat (1.5 = 150%). Stated together with {@link #critRate}, never alone.
     */
    @SerializedName("crit_damage")
    private Double critDamage;

    /**
     * The <b>damage element</b> of the per-turn damage this effect attaches - {@code "Ice"} / {@code "Fire"} / …
     * (the {@code DamageElement} spelling, the same one {@code memosprites/<cid>.json}'s {@code attack.element}
     * uses). Read by {@code APPLY_DOT}, and by {@code APPLY_CONTROL} for the state's own per-turn damage.
     *
     * <p>Note: Only the <b>element</b> is named in English; the <i>state</i> a DOT represents is spelled in Chinese
     * (灼烧 / 触电 / 裂伤 / 风化) because that is what the documents and the {@code has_state} condition use - the
     * engine's one translation between the two is {@code BuffManager.DOT_STATES}, deliberately the only place that
     * knows they are the same thing.
     */
    @SerializedName("element")
    private String element;

    /**
     * The <b>class</b> of negative state an effect is about - {@code "control"} (控制类) or {@code "dot"} (持续伤害类).
     * Read by {@code RESIST_DEBUFF}, whose sentence is about a whole family rather than one named state
     * ("抵抗<b>控制类</b>负面状态的概率提高35%").
     *
     * <p>The closed set is {@code DebuffClass}, and it is deliberately small: it holds the two classes the documents
     * name, and it grows when a third is quoted rather than when someone imagines one.
     */
    @SerializedName("kind")
    private String kind;
    /**
     * {@code MODIFY_RULE}: raise every {@code percent} read of the named rule's effects by this much
     * ("天赋的伤害提高效果额外提高 10%"，1215 星魂 6).
     */
    @SerializedName("effect_percent")
    private Double effectPercent;

    /**
     * The SIZE of an amendment, carried in a resource (2026-10-02; reader: 114151"每消耗 1% 溢出值，使…伤害倍率提高 #2%").
     *
     * <p>Like {@code effect_percent}, but the number is not a literal: it is the value of one of the OWNER's resources, in basis points (the convention {@code percent_from_resource} uses).
     */
    @com.google.gson.annotations.SerializedName("effect_percent_from_resource")
    private String effectPercentFromResource;
    /**
     * {@code MODIFY_RULE}: raise every {@code turns} read of the named rule's effects by this many turns
     * ("终结技的持续时间额外增加 1 回合"，1215 星魂 4).
     */
    @SerializedName("effect_turns")
    private Integer effectTurns;
    /**
     * <b>Per-target conditions</b> (2026-09-28, M-53): the effect reaches only the units that satisfy these, tested one
     * candidate at a time with {@code target} bound to that candidate.
     *
     * <p>"对所有<b>触电状态下的</b>敌方目标造成…附加伤害" (1103's talent) is the sentence that needed it: a rule's own
     * conditions filter the <b>rule</b>, so "all shocked enemies" could only be spelled "the enemy I hit was shocked"
     * (which then also hit the unshocked ones) or not at all. The selector says <i>which units</i>; this says <i>which
     * of them qualify</i>.
     */
    @SerializedName("target_when")
    private List<String> targetWhen;
    /**
     * {@code MODIFY_RULE}: raise the <b>stack cap</b> of the named rule's effects by this many layers
     * ("天赋的效果可叠加上限提高 2 层"，1302 星魂 4).
     *
     * <p>Note: A cap is not a value: the difference only shows when the content *would* have exceeded the old limit, which is
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
