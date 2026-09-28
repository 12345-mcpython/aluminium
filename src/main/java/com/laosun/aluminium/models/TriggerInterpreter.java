package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.TriggerTable.CompiledRule;
import com.laosun.aluminium.models.TriggerTable.TriggerContext;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.buff.BuffManager;
import com.laosun.aluminium.models.buff.ClassResistBuff;
import com.laosun.aluminium.models.buff.ControlBuff;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.buff.StatModifierBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.models.skill.Skill;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the effects of a {@link TriggerTable} (P8-7). The only place in the trigger system that
 * touches engine state.
 *
 * <p><b>The op vocabulary is restricted on purpose</b> to capabilities the engine already has --
 * the trigger table is an interpreter over existing operations, not a second engine. An op whose
 * prerequisite phase has not landed yet is <b>rejected at load time</b> with the phase named, so a
 * content author learns immediately instead of shipping a rule that silently never works.
 *
 * <table border="1">
 *   <tr><th>op</th><th>arguments</th><th>status</th></tr>
 *   <tr><td>{@code GAIN_ENERGY}</td><td>{@code amount}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code GAIN_SKILL_POINT}</td><td>{@code amount}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code HEAL}</td><td>{@code amount}, optional {@code target} — <b>or</b> {@code scale} +
 *       {@code percent} (a share of a Max HP)</td><td>✅ wired</td></tr>
 *   <tr><td>{@code SHIELD}</td><td>{@code amount}, optional {@code target} — <b>or</b> {@code scale} +
 *       {@code percent}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code EXTRA_TURN}</td><td>optional {@code target}</td><td>✅ wired</td></tr>
 *   <tr><td>{@code ADVANCE}</td><td>{@code percent}, optional {@code target}</td><td>✅ wired (0.0–1.0 = the fraction of the target's <b>remaining</b> time to act that gets skipped)</td></tr>
 *   <tr><td>{@code MODIFY_ATTR}</td><td>{@code attribute}, {@code percent}, <b>exactly one of</b>
 *       {@code turns} / {@code permanent} / {@code until}, optional {@code target}, {@code max_stacks} (alias
 *       {@code stacks})</td>
 *       <td>✅ wired (P10-3) — a negative {@code percent} becomes a {@code DEBUFF}, so a buff and a
 *           debuff on the same attribute coexist; for a ratio attribute ({@code CRIT_ATTACK} and
 *           friends) {@code percent} is the value itself (0.25 = +25 percentage points), because an
 *           additive percentage would multiply their zero base and change nothing.
 *           <b>{@code permanent: true}</b> means "until the battle ends": the modifier is never
 *           ticked, so its duration is unbounded rather than merely long. <b>{@code until}</b> is the
 *           third duration and the one the texts keep asking for — 「持续到施放首次攻击后结束」 is not a
 *           number of turns; it ends the buff when its <b>owner</b> attacks / casts a Skill / casts an
 *           Ultimate ({@code AbstractBuff.Lifetime}), and such a buff is likewise never ticked.
 *           <b>{@code max_stacks}</b>
 *           (&gt; 1) makes re-applications <b>accumulate</b> up to that cap instead of replacing the
 *           previous one; each stack is an ordinary buff instance with its own id, so it can be
 *           removed on its own. Absent {@code max_stacks} keeps the historical replace behaviour.</td></tr>
 *   <tr><td>{@code BOOST_DAMAGE}</td><td>{@code percent}</td>
 *       <td>✅ wired — changes <b>the instance being settled</b> when the rule fires on
 *           {@code DEALING_DAMAGE} (「对处于 X 状态的目标造成的伤害提高 Y%」). No duration, no target and no
 *           buff: the instance is the state, so nothing persists and nothing can leak into the next hit</td></tr>
 *   <tr><td>{@code DISPEL}</td><td>{@code amount}, optional {@code target}</td>
 *       <td>✅ wired — removes up to {@code amount} <b>negative effects</b> (「解除 N 个负面效果」), newest
 *           first. What counts as negative is {@code AbstractBuff.isDebuff()}, decided per buff class</td></tr>
 *   <tr><td>{@code MODIFY_DAMAGE_TAKEN}</td><td>{@code percent}, <b>exactly one of</b> {@code turns} /
 *       {@code permanent} / {@code until}, optional {@code target}</td>
 *       <td>✅ wired — the sign decides the zone: {@code percent > 0} is 「受到的伤害提高」 (vulnerability,
 *           a debuff on the defender), {@code percent < 0} is 「受到的伤害降低」 (reduction, a buff on the
 *           defender). Neither is an attribute, which is why {@code MODIFY_ATTR} cannot express them</td></tr>
 *   <tr><td>{@code REMOVE_STACK}</td><td>{@code attribute}, {@code amount}, optional {@code target}</td>
 *       <td>✅ wired — takes up to {@code amount} stacks of that attribute's modifier off the target
 *           (「每回合移除 1 层」); removing nothing is not an error, because the rule fires every turn
 *           anyway</td></tr>
 *   <tr><td>{@code APPLY_BUFF}</td><td>{@code buff}, <b>exactly one of</b> {@code turns} /
 *       {@code permanent} / {@code until}, optional {@code target}</td>
 *       <td>✅ wired — puts the target into a <b>named state</b> ({@code StateBuff}, e.g. 【协奏】/【转魄】/
 *           【触电】). What the state <i>does</i> is separate effects conditioned on {@code has_state}, which
 *           keeps "what the state is" apart from "what it changes"; plain stat buffs stay {@code MODIFY_ATTR}</td></tr>
 *   <tr><td>{@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}</td><td>{@code resource}, {@code amount}</td>
 *       <td>✅ wired (P8-8)</td></tr>
 *   <tr><td>{@code REDUCE_TOUGHNESS}</td><td>{@code amount}</td><td>☐ needs an element + enemy target</td></tr>
 *   <tr><td>{@code SUMMON}</td><td><b>no arguments</b></td>
 *       <td>✅ wired — brings the <b>rule owner's own memosprite</b> (忆灵) onto the field
 *           ({@code Battle.summonMemosprite}). No {@code target}: a memosprite belongs to its summoner and
 *           fights for that camp, so there is nothing to point at. Idempotent — 「若已在场，则使其生命值
 *           回复至上限」 is what the texts say and the refresh is <b>not</b> modelled, so a second firing
 *           keeps the one already out rather than making a second copy</td></tr>
 *   <tr><td>{@code DAMAGE}</td><td>{@code skill}, {@code damage_param}, optional {@code target}</td>
 *       <td>✅ wired (P8-3)</td></tr>
 * </table>
 *
 * <p>Effects run in the order written. Every effect credits the <b>owner</b> (the character whose
 * table fired) unless it names a {@code target}; see {@link #resolveTarget}.
 */
public final class TriggerInterpreter {

    /**
     * Ops that are implemented today.
     */
    private static final Set<String> WIRED = Set.of(
            "GAIN_ENERGY", "GAIN_SKILL_POINT", "HEAL", "SHIELD", "EXTRA_TURN", "ADVANCE",
            "GAIN_RESOURCE", "SPEND_RESOURCE", "DAMAGE", "MODIFY_ATTR", "APPLY_BUFF", "REMOVE_STACK",
            "MODIFY_DAMAGE_TAKEN", "BOOST_DAMAGE", "DISPEL", "SUMMON", "COMMAND_SUMMON", "DELEGATE_DAMAGE",
            "REMOVE_STATE", "TAUNT", "APPLY_CONTROL", "APPLY_DOT", "EXTEND_BUFF", "RESIST_DEBUFF",
            "MODIFY_RULE", "ADD_DAMAGE", "RAISE_SKILL_LEVEL", "START_COUNTDOWN", "ADD_STACK", "APPLY_REGEN");

    /**
     * Ops that are declared in the roadmap but whose prerequisite phase has not landed. Listing
     * them here (rather than treating them as typos) lets the error message say <i>why</i>.
     */
    private static final Set<String> PLANNED = Set.of(
            "REDUCE_TOUGHNESS");

    /**
     * The ops that grant something with a duration, and therefore accept {@code permanent: true}
     * ("for the rest of the battle"). See {@link #requireNoStackArguments}.
     */
    private static final Set<String> OPS_WITH_DURATION =
            Set.of("MODIFY_ATTR", "APPLY_BUFF", "MODIFY_DAMAGE_TAKEN", "RESIST_DEBUFF");

    /**
     * The selectors an effect's {@code target} may name.
     *
     * <p>This set exists to close a silent-typo hole: the resolver used to fall back to "the owner"
     * for anything it did not recognise, so a misspelled {@code target} behaved exactly like
     * {@code "self"} — a wrong answer that reports nothing. Same reasoning as the condition
     * variables being a closed set.
     */
    private static final Set<String> TARGET_SELECTORS =
            Set.of("self", "target", "attacker", "all_allies", "party", "other_allies", "summon",
                    "target_and_summon", "all_enemies", "lowest_hp_ally");

    /**
     * The two spellings of "every one of our characters".
     */
    private static final Set<String> TARGET_ALL_ALLIES = Set.of("all_allies", "party");

    /**
     * "Our side except the rule's owner" — 「除自身以外」.
     *
     * <p>Its first user is 知更鸟's ultimate: 「使<b>除自身以外的队友</b>立即行动」. {@code all_allies} cannot say it
     * (302 不老者的仙舟's 「我方全体攻击力提高」 includes the wearer, and that is pinned), and a condition cannot
     * say it either — conditions filter <b>rules</b>, not the units an effect reaches.
     */
    private static final String TARGET_OTHER_ALLIES = "other_allies";

    /**
     * "The unit this cast <b>aimed at</b>, and <b>its</b> summon" — 「指定我方单体<b>及其召唤物</b>」.
     *
     * <p>Its first user is 星期日's Skill (131302): 「使指定我方单体角色<b>及其召唤物</b>立即行动」. It is a
     * <b>pair</b> and it is not the same pair as any existing selector:
     * <ul>
     *   <li>{@code target} advances the chosen ally but not its summon;</li>
     *   <li>{@code summon} is the <b>rule owner's</b> summon — the wrong unit entirely (星期日's own 忆灵 is not
     *       what his Skill advances);</li>
     *   <li>two effects cannot express it either, because the second one would have to name "the summon of the
     *       unit the first one resolved", and a selector cannot refer to another effect's result.</li>
     * </ul>
     *
     * <p>⚠ A chosen unit <b>without</b> a summon is just that unit, not an error: 「及其召唤物」 only has something
     * to add when there is one, and 星期日's Skill is cast on ordinary allies all the time. ⚠ Only the summon
     * <b>that is out</b> counts ({@code Battle.summonsOf} skips the dead ones), which is why this selector needs a
     * battle like the other group selectors.
     */
    private static final String TARGET_AND_SUMMON = "target_and_summon";

    /**
     * "The whole of the <b>other</b> side" — 「对敌方全体」.
     *
     * <p>Its first user is 姬子's Talent (「对敌方全体目标造成等同于姬子140%攻击力的火属性伤害」). Every other
     * group selector reads {@link Battle#allies}, so "all enemies" had no spelling at all: the only way to reach
     * the other camp was {@code target}, one unit at a time, which is not what 「全体」 says.
     *
     * <p>Read through {@link Battle#getOpponents}, so 「敌方」 means "the camp opposing the rule's owner" rather
     * than "the {@code enemies} list" — the same answer for every character (they are all on our side), and the
     * honest one if a rule ever belongs to an enemy. ⚠ The dead are <b>not</b> filtered here: like
     * {@code all_allies}, the selector answers "who is on that side", and the op decides what it can do with
     * them ({@code DAMAGE} skips a dead victim on its own). It needs a battle, which is why it is resolved in
     * the list resolver and not the single-target one.
     */
    private static final String TARGET_ALL_ENEMIES = "all_enemies";

    /**
     * "The ally with the lowest HP <b>percentage</b>" — 「当前<b>生命值百分比</b>最低的我方目标」.
     *
     * <p>Its first user is 三月七's 星魂 2 (「进入战斗时，为当前生命值百分比最低的我方目标提供等同于三月七24%防御力+
     * 320的护盾」), and 藿藿's 【禳命】 / 灵砂's 【浮元】 heal the same unit. Before it, "the most hurt one" could
     * only be picked by a <b>condition</b> — which filters <em>rules</em>, not the units an effect reaches — so the
     * sentence had no spelling at all.
     *
     * <p>⚠ <b>Percentage, not absolute HP</b>, and the difference is real: with allies at 100/1000 and 900/10000,
     * the second has fewer HP <i>points</i> (900) but the first has the lower <i>share</i> (10% vs 9%) — the two
     * questions have different answers, and the documents spell them differently (「生命值百分比最低」 vs 灵砂's
     * 「生命值最低」). Only the percentage spelling is built, because that is the one three of the four readers use;
     * the absolute variant is registered with its reader (灵砂 1222) rather than guessed at now.
     *
     * <p>⚠ <b>Ties go to the earliest unit in the party order</b>, which is what makes the answer deterministic —
     * and at {@code BATTLE_START} everybody is at 100%, so every 星魂-2-style rule hits a tie and the choice has to
     * be stated rather than left to the iteration order of a map. The dead are skipped (a corpse has no share to
     * speak of), and an empty camp resolves to <b>no target</b> rather than an error, like the other group
     * selectors.
     */
    private static final String TARGET_LOWEST_HP_ALLY = "lowest_hp_ally";

    private TriggerInterpreter() {
    }

    /**
     * Validates one effect at table-load time.
     *
     * @param effect the effect to check
     * @param spec   the owning rule, used to report the source
     * @throws IllegalArgumentException when the op is unknown, planned-but-unwired, or missing a
     *                                  required argument
     */
    public static void validate(EffectSpec effect, TriggerSpec spec) {
        String op = normalizeOp(effect, spec);
        requireTargetSelector(effect, op, spec);
        if (PLANNED.contains(op)) {
            throw new IllegalArgumentException(
                    "Trigger op '" + op + "' is declared in the roadmap but not wired yet "
                            + "(see ROADMAP P8-7 / TriggerInterpreter); rule source: " + spec.getSource());
        }
        if (!WIRED.contains(op)) {
            throw new IllegalArgumentException(
                    "Unknown trigger op '" + op + "' (source: " + spec.getSource() + ")");
        }
        switch (op) {
            case "GAIN_ENERGY" -> {
                requireAmountOrScale(effect, op, spec, ENERGY_SCALES, "max energy");
                requireNoStackArguments(effect, op, spec);
            }
            case "GAIN_SKILL_POINT" -> {
                requireAmount(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "HEAL", "SHIELD" -> {
                requireAmountOrScale(effect, op, spec, SCALES, "Max HP");
                requireNoStackArguments(effect, op, spec);
                if ("HEAL".equals(op)) {
                    // Healing has no duration -- HP comes back and stays back. Before this, `turns` on a HEAL
                    // was accepted and silently dropped, which is the class of mistake this project refuses
                    // (the same reason the six "owner-only" ops refuse a `target`): say it is a mistake rather
                    // than let the author believe the healing lasts.
                    requireNoDuration(effect, op, spec);
                } else if (effect.getTurns() != null && effect.getTurns() <= 0) {
                    // A timed shield with a non-positive duration would be a shield that expires before it can
                    // be used -- "no shield" written as a mechanic. Omitting `turns` is how "no limit" is spelled.
                    throw new IllegalArgumentException(
                            "Op " + op + " needs a positive \"turns\" when it states one (how many of the "
                                    + "shielded unit's turns the shield lasts), but has " + effect.getTurns()
                                    + "; omit the field for a shield that is only removed by being used up "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            case "ADVANCE" -> {
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {
                requireAmount(effect, op, spec);
                requireResource(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "DAMAGE" -> {
                requireSkill(effect, op, spec);
                requireDamageParam(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireFixedCrit(effect, op, spec);
            }
            case "MODIFY_ATTR" -> {
                requireAttribute(effect, op, spec);
                requirePercent(effect, op, spec);
                requireDerivedScale(effect, op, spec);
                requireDuration(effect, op, spec);
                requireStackCap(effect, op, spec);
                requireTickOwner(effect, op, spec);
            }
            case "APPLY_BUFF" -> {
                requireBuff(effect, op, spec);
                requireDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireTickOwner(effect, op, spec);
            }
            case "REMOVE_STACK" -> {
                requireAttribute(effect, op, spec);
                requirePositiveAmount(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "MODIFY_DAMAGE_TAKEN" -> {
                requirePercent(effect, op, spec);
                requireNonZeroPercent(effect, op, spec);
                requireDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "BOOST_DAMAGE" -> {
                requirePercent(effect, op, spec);
                requireNonZeroPercent(effect, op, spec);
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireEvent(spec, op, TriggerEvent.DEALING_DAMAGE);
            }
            case "DISPEL" -> {
                requirePositiveAmount(effect, op, spec);
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "TAUNT" -> {
                // 「使目标陷入嘲讽状态，持续N回合」: the engine's TauntBuff is a pure marker with a hard
                // target-selection constraint, so all this op needs is the duration. No `permanent` / `until`: a
                // marker with no turn count would never come off, and no document writes it that way.
                Integer turns = effect.getTurns();
                if (turns == null || turns <= 0) {
                    throw new IllegalArgumentException(
                            "Op " + op + " requires a positive \"turns\" (how long the taunt lasts); the documents "
                                    + "write 「使目标陷入嘲讽状态，持续1回合」 (source: " + spec.getSource() + ")");
                }
                if (Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " takes a turn count and nothing else; \"permanent\" / \"until\" would be "
                                    + "a taunt that never ends (source: " + spec.getSource() + ")");
                }
                requireNoStackArguments(effect, op, spec);
            }
            case "REMOVE_STATE" -> {
                // `buff` is the state's name — the same spelling `has_state` reads and `APPLY_BUFF` writes, so the
                // three are one vocabulary. ⚠ No count: 「解除…状态」 takes the state off, it does not take N of
                // them, and an `amount` here would be silently ignored (which is what this op exists to avoid).
                requireBuff(effect, op, spec);
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                if (effect.getAmount() != null) {
                    throw new IllegalArgumentException(
                            "Op " + op + " takes the named state off entirely and has no \"amount\" (it states "
                                    + effect.getAmount() + "); write one effect per state to remove "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            case "APPLY_CONTROL" -> {
                // 「有 50% 基础概率使敌方目标陷入冻结状态，持续 1 回合」: a named control state, a duration, and
                // (optionally) a base chance that goes through 效果命中 / 效果抵抗. The effect may also carry the
                // state's OWN per-turn damage (`element` + a magnitude), which lands with the state.
                requireControl(effect, op, spec);
                requirePositiveTurns(effect, op, spec);
                requireBaseChance(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                if (Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " states a number of turns and nothing else; \"permanent\" / \"until\" "
                                    + "would be a control that never ends (source: " + spec.getSource() + ")");
                }
                requireStateDamage(effect, op, spec);
            }
            case "APPLY_DOT" -> {
                // 「使目标陷入灼烧状态，每回合造成等同于…的伤害」: a damage-over-time attached by a rule. The
                // element is the engine's own spelling (Fire → 灼烧), the magnitude is either flat or derived
                // from the RULE OWNER's attribute, and it is frozen into the buff when it lands.
                requireElement(effect, op, spec);
                requireDotMagnitude(effect, op, spec);
                requirePositiveTurns(effect, op, spec);
                requireBaseChance(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                if (Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " states a number of turns and nothing else; \"permanent\" / \"until\" "
                                    + "would be a damage-over-time that never ends (source: " + spec.getSource()
                                    + ")");
                }
                requireNoMalformedArguments(effect, op, spec);
            }
            case "EXTEND_BUFF" -> {
                // 「…的持续时间增加1回合」: lengthen the buffs THIS owner already applied to the target, named by
                // their state (「战技提供的护盾」/「天赋使敌方目标陷入的风化状态」) or by the attribute a modifier
                // sits on (「伤害提高效果」). Nothing else is stated.
                requirePositiveTurns(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                if (Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " adds turns to a buff that already exists; \"permanent\" / \"until\" "
                                    + "describe how a NEW buff ends and are not read here "
                                    + "(source: " + spec.getSource() + ")");
                }
                requireExtendFilter(effect, op, spec);
            }
            case "RESIST_DEBUFF" -> {
                // 「抵抗控制类负面状态的概率提高35%」 / 「免疫控制类负面状态」: a class resistance the CARRIER holds, so
                // it is a buff on the target (timed, or permanent for a 行迹).
                requireDebuffClass(effect, op, spec);
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireDuration(effect, op, spec);
                requireTickOwner(effect, op, spec);
                // `buff` is READ (2026-09-28): the name the resistance carries, so 「处于【协奏】状态时免疫控制类」
                // can be taken off together with the state it belongs to (see `named`). It used to be refused here.
                if (effect.getAmount() != null || effect.getScale() != null || effect.getAttribute() != null
                        || effect.getSkill() != null
                        || effect.getDamageParam() != null || effect.getElement() != null
                        || effect.getControl() != null || effect.getBaseChance() != null) {
                    throw new IllegalArgumentException(
                            "Op " + op + " takes a class (\"kind\") and a \"percent\", plus how long it lasts "
                                    + "(and an optional \"buff\" name so a state's own effect can be removed with "
                                    + "the state); it has no amount / scale / attribute / skill / element / control / "
                                    + "base_chance (source: " + spec.getSource() + ")");
                }
                if (effect.getPercent() <= 0 || effect.getPercent() > 1) {
                    throw new IllegalArgumentException(
                            "Op " + op + " has \"percent\": " + effect.getPercent() + ", but a class resistance is "
                                    + "a fraction of 1 in (0, 1] (0.35 = 「抵抗…的概率提高35%」, 1.0 = 「免疫」); a "
                                    + "resistance of 0 is a rule that provably does nothing "
                                    + "(source: " + spec.getSource() + ")");
                }
            }
            case "START_COUNTDOWN" -> {
                // 「行动序列上出现【协奏】倒计时…倒计时固定拥有 90 点速度」 (M-49): a unit whose only job is to have a turn.
                if (effect.getSpeed() == null || effect.getSpeed() <= 0) {
                    throw new IllegalArgumentException(
                            "Op " + op + " needs a positive \"speed\": the countdown's turn is decided by it, and a "
                                    + "countdown that never acts is a duration that never ends "
                                    + "(source: " + spec.getSource() + ")");
                }
                rejectCountdownExtras(effect, op, spec);
            }
            case "APPLY_REGEN" -> {
                // 「每回合开始时为其回复等同于…生命上限的 X% + N，持续 M 回合」: the healing twin of APPLY_DOT.
                // Its magnitude is a HEAL amount, so it takes the heal/shield scale vocabulary (SCALES), NOT the
                // self_attr: one the MODIFY_ATTR family uses -- 「等同于她生命上限的 7.2%」 is owner_max_hp.
                requireAmountOrScale(effect, op, spec, SCALES, "Max HP");
                requireDuration(effect, op, spec);
                requireBuff(effect, op, spec);
            }
            case "ADD_STACK" -> {
                // 「每当我方目标…施放2次…后」 / 「触发2次后自动解除」: a counter, which is a named stack buff (see StackBuff).
                requireBuff(effect, op, spec);
                requireDuration(effect, op, spec);
            }
            case "RAISE_SKILL_LEVEL" -> {
                // 「战技等级+1」「终结技等级+1」 (1001 星魂 3/5 and the same sentence in most kits): the level a skill is
                // READ at is character data plus this battle's raises (M-32), and one op is what raises it -- never a
                // second copy of the rule at another level, which would double a cast like `per_turn` doubling does.
                requireSkill(effect, op, spec);
                requireEvent(spec, op, TriggerEvent.BATTLE_START);
                double amount = effect.getAmount() == null ? 0 : effect.getAmount();
                if (amount < 1 || amount != Math.floor(amount)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " raises a skill's level, so \"amount\" is a count of levels: a whole number "
                                    + ">= 1, got " + (effect.getAmount() == null ? "nothing" : amount)
                                    + " (source: " + spec.getSource() + ")");
                }
                rejectSkillLevelExtras(effect, op, spec);
            }
            case "MODIFY_RULE" -> {
                // 「天赋的反击效果每回合可触发的次数增加1次」 / 「施放终结技时，冻结敌方目标的基础概率提高15%」: a passive
                // that RAISES a number on another rule in the same file. Which number is decided by which field is
                // stated -- an `amount` is a count of firings, a `percent` is a probability -- and the shape of the
                // named rule is checked by the table (TriggerTable.validateAmendments), which is the only place that
                // can see the whole file.
                requireRuleReference(effect, op, spec);
                requireOneAmendment(effect, op, spec);
                if (effect.getAmount() != null) {
                    double amount = effect.getAmount();
                    if (amount < 1 || amount != Math.floor(amount)) {
                        throw new IllegalArgumentException(
                                "Op " + op + " raises a rule's per-turn limit, so \"amount\" is a count of firings: "
                                        + "a whole number >= 1, got " + amount + " (a probability is a \"percent\", "
                                        + "which is a different number on a different rule) "
                                        + "(source: " + spec.getSource() + ")");
                    }
                    rejectAmendmentExtras(effect, op, spec, "amount");
                } else if (effect.getEffectTurns() != null) {
                    if (effect.getEffectTurns() == 0) {
                        throw new IllegalArgumentException(
                                "Op " + op + " raises a rule's effect duration, so \"effect_turns\" must be a whole "
                                        + "number of turns other than 0, got " + effect.getEffectTurns()
                                        + " (source: " + spec.getSource() + ")");
                    }
                    rejectAmendmentExtras(effect, op, spec, "effect_turns");
                } else if (effect.getEffectPercent() != null) {
                    if (effect.getEffectPercent() == 0) {
                        throw new IllegalArgumentException(
                                "Op " + op + " raises a rule's effect value, so \"effect_percent\" must not be 0 "
                                        + "(source: " + spec.getSource() + ")");
                    }
                    rejectAmendmentExtras(effect, op, spec, "effect_percent");
                } else {
                    requirePercent(effect, op, spec);
                    if (effect.getPercent() <= 0 || effect.getPercent() > 1) {
                        throw new IllegalArgumentException(
                                "Op " + op + " raises a rule's base chance, so \"percent\" is a fraction of 1 in "
                                        + "(0, 1]: 0.15 = 「提高15%」, got " + effect.getPercent()
                                        + " (source: " + spec.getSource() + ")");
                    }
                    rejectAmendmentExtras(effect, op, spec, "percent");
                }
                if (TriggerEvent.fromString(spec.getOn()) != TriggerEvent.BATTLE_START) {
                    throw new IllegalArgumentException(
                            "Op " + op + " amends a rule for the whole battle, so it only makes sense on "
                                    + "BATTLE_START (a bonus granted mid-battle would have to be taken back when "
                                    + "whatever granted it ended, and nothing does that); this rule fires on "
                                    + spec.getOn() + " (source: " + spec.getSource() + ")");
                }
            }
            case "ADD_DAMAGE" -> {
                requireEvent(spec, op, TriggerEvent.DEALING_DAMAGE);
                scaleAttribute(effect, op, spec);
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }
            case "SUMMON" -> {
                // No arguments at all: the memosprite belongs to the rule's owner, and everything about it
                // (name, panel derivation) lives in resources/memosprites/<cid>.json. A `target` here would
                // be silently ignored, which is the class of mistake the closed selector set exists to
                // prevent -- so it is refused rather than dropped.
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireNoTarget(effect, op, spec);
            }
            case "COMMAND_SUMMON" -> {
                // The numbers are the NAMED SKILL's, not this file's: 长夜月's ultimate is skill 141303, whose
                // parameter row says 2.0 at Lv10 and whose effect says AoEAttack / Ice. Stating them again here
                // would be a second copy that drifts (and would lose the per-level table -- see M-28).
                requireSkill(effect, op, spec);
                requireDamageParam(effect, op, spec);
                requireAttribute(effect, op, spec);
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                // The victims come from the SKILL's shape (the summon's opposing camp), not from a selector:
                // pointing this op at `self` or at a teammate would deal the summon's damage to our own side.
                requireNoTarget(effect, op, spec);
            }
            case "DELEGATE_DAMAGE" -> {
                // Only the slot being cast is named. Everything else about the swing -- who delivers it and with
                // what numbers -- belongs to the rule that DOES deliver it (for 长夜月, the COMMAND_SUMMON on
                // ULT_CAST below); this op only says "not by me".
                requireSkill(effect, op, spec);
                requireNoDuration(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                requireNoTarget(effect, op, spec);
                requireEvent(spec, op, TriggerEvent.CAST_SETUP);
            }
            default -> requireNoStackArguments(effect, op, spec);
        }
    }

    /**
     * Runs every effect of a rule.
     *
     * @param battle the running battle
     * @param rule   the matched rule
     * @param ctx    the context the rule was matched with
     */
    public static void apply(Battle battle, CompiledRule rule, TriggerContext ctx) {
        for (EffectSpec effect : rule.effects()) {
            // 「终结技的持续时间额外增加 1 回合」/「天赋的伤害提高效果额外提高 10%」 (2026-09-28): an amendment to the
            // named rule's own effect values. ⚠ A COPY, not a mutation: the compiled EffectSpec is shared by every
            // battle, so adjusting it in place would leak the amendment (and, in the test suite, into other tests).
            // The fast path returns the same instance, so the 57 places that read `percent`/`turns` stay untouched.
            effect = amendedEffect(effect, ctx);
            // The rule's own id travels with the effect: `MODIFY_RULE` can raise a rule's base chance, and the only
            // op that consumes that amendment (APPLY_CONTROL) has to know which rule it is running inside. Passing it
            // down beats a field on the context -- a nested firing would clobber shared state, and this is per-rule.
            applyOne(battle, effect, ctx);
        }
    }

    /**
     * Fires an event against one table: matches the rules and runs them.
     *
     * @param battle the running battle
     * @param table  the table to fire (may be {@code null} or empty, which is a no-op)
     * @param event  the event that happened
     * @param ctx    the context
     * @return how many rules fired (useful for tests and diagnostics)
     */
    public static int fire(Battle battle, TriggerTable table, TriggerEvent event, TriggerContext ctx) {
        if (table == null || table.isEmpty()) {
            return 0;
        }
        // ⚠ Per rule, not up front (2026-09-28): see TriggerTable.rulesFor. A condition that reads what an earlier
        // rule of the SAME event just changed (a counter marked on this attack, say) has to be evaluated when its own
        // rule is reached, or it can never be true.
        List<CompiledRule> rules = table.rulesFor(event);
        CanHit owner = ctx.owner();
        int fired = 0;
        for (CompiledRule rule : rules) {
            // ⚠ The conditions are checked HERE, per rule, because rulesFor no longer filters (2026-09-28). Harmless
            // to re-check: matches is the same pure predicate matching used.
            if (!rule.matches(ctx)) {
                continue;
            }
            // Firing limits (cooldown / once per battle / per-turn count). Checked *after* matching and before
            // applying, because the limit is about how often the rule may run, not about whether it fits the
            // event: `matching` stays a pure predicate, which is what `TriggerTable.ruleCount` and the
            // data-binding tests read.
            if (owner != null && !owner.isTriggerReady(rule.key(),
                    rule.perTurn() + owner.rulePerTurnBonus(rule.id()))) {
                continue;
            }
            // An Eidolon gate (「星魂 N 解锁」): the rank is a construction-time property of the rule's owner, so
            // the rule itself is the only place that has to know it is an Eidolon.
            if (rule.minEidolon() > 0 && eidolonRankOf(owner) < rule.minEidolon()) {
                continue;
            }
            // A rule-level probability (「有 35% 的固定概率…」). A failed roll costs nothing: no cooldown is
            // started and no once-per-battle flag is set, because nothing happened.
            if (rule.chance() < 1 && !battle.rollChance(rule.chance())) {
                continue;
            }
            apply(battle, rule, ctx.withRule(rule.id()));
            if (owner != null) {
                // ⚠ The count recorded is the SAME number the check above used: recording the stated per_turn while
                // checking the amended one would make an amended rule fire forever (its counter would never reach the
                // raised cap). One expression, read twice -- see `amendedPerTurn`.
                owner.startTriggerCooldown(rule.key(), rule.cooldownTurns(), rule.oncePerBattle(),
                        rule.perTurn() + owner.rulePerTurnBonus(rule.id()));
            }
            fired++;
        }
        return fired;
    }

    /**
     * Runs one effect of a rule.
     *
     * @param ruleId the id of the rule this effect belongs to ({@code ""} when it states none) — the handle a
     *               {@code MODIFY_RULE} amendment is filed under, read by {@code APPLY_CONTROL}
     */
    /**
     * The effect as this firing must see it: the compiled one, or a copy with the named rule's value/duration
     * amendments applied ({@code MODIFY_RULE} with {@code effect_percent} / {@code effect_turns}).
     *
     * <p>Returning the <b>same instance</b> when there is no amendment is what keeps this out of the hot path, and it
     * is also why the amendments are not applied at load time: a rule's amendments are facts about <i>this</i>
     * combatant in <i>this</i> battle (an Eidolon is one), while the compiled effect belongs to all of them.
     */
    private static EffectSpec amendedEffect(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null || ctx.ruleId() == null) {
            return effect;
        }
        Double percentDelta = owner.ruleEffectPercentBonus(ctx.ruleId());
        Integer turnsDelta = owner.ruleEffectTurnsBonus(ctx.ruleId());
        if (percentDelta == null && turnsDelta == null) {
            return effect;
        }
        EffectSpec amended = effect;
        if (percentDelta != null) {
            amended = amended.withPercent(percentDelta);
        }
        if (turnsDelta != null) {
            amended = amended.withTurns(turnsDelta);
        }
        return amended;
    }

    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String op = normalizeOp(effect, null);
        switch (op) {
            case "GAIN_ENERGY" -> gainEnergy(battle, effect, ctx);
            case "GAIN_SKILL_POINT" -> battle.gainSkillPoint((int) Math.round(scaledAmount(effect, ctx)));
            case "HEAL" -> {
                for (CanHit target : resolveTargets(battle, effect, ctx)) {
                    // ⚠ The healer is the RULE OWNER, not `null` (changed 2026-09-28 with M-43). A `HEAL` effect is
                    // the owner healing somebody, and `HEALED`'s `actor` is what 「受到<b>队友提供的</b>治疗」 reads; with
                    // `null` there was no way to ask who healed, so that half of 大丽花's trace could not be written.
                    // ⚠ Measured before the change: no shipped content subscribes to `HEALED` at all, and no shipped
                    // content grants `OUTGOING_HEALING_BOOST`, so this changes no number today -- it only makes the
                    // attribution true (and a rule-driven heal now reads the owner's healing boost, as a skill's does).
                    battle.heal(ctx.owner(), target, grantAmount(effect, target, ctx));
                }
            }
            case "SHIELD" -> {
                for (CanHit target : resolveTargets(battle, effect, ctx)) {
                    double amount = grantAmount(effect, target, ctx);
                    // 「持续N回合」 is a *timed* shield: the ShieldBuff installs the value and takes it off
                    // again, so the number and its lifetime stay one fact. Without `turns` the shield is the
                    // raw grant it has always been (it comes off only when it is used up).
                    // ⚠ Both arms name the provider (`ctx.owner()`): 「装备者提供的护盾量提高 X%」 is about the
                    // *giver's* shields, so a grant with no provider would silently ignore the set bonus.
                    if (effect.getTurns() == null) {
                        battle.grantShield(ctx.owner(), target, amount, ctx.ruleId());
                    } else if (target != null && !target.isDeath()) {
                        target.getBuffManager().addBuff(
                                withSource(new ShieldBuff(ctx.owner(), amount, effect.getTurns()), ctx));
                        // P12 (M-43): the timed path installs its shield through a buff, which has no Battle handle,
                        // so this is the only place that can announce it. Fired AFTER the buff is on, so a rule that
                        // answers 「获得护盾时」 sees the shield it is reacting to.
                        if (amount > 0) {
                            battle.fireTriggersForAlly(TriggerEvent.SHIELD_GRANTED, ctx.owner(), target, amount);
                        }
                    }
                }
            }
            case "EXTRA_TURN" -> battle.grantExtraTurn(resolveTarget(effect, ctx));
            case "ADVANCE" -> {
                // One target or a group: 「使该目标立即行动」 and 「使除自身以外的队友立即行动」 are the same op, and
                // the list resolver is what tells them apart (it is the one HEAL/SHIELD already use for "our
                // side"). Advancing several units is what the group selectors were resolved for.
                for (CanHit target : resolveTargets(battle, effect, ctx)) {
                    battle.queue.advanceActionByPercent(target, effect.getPercent());
                }
            }
            case "GAIN_RESOURCE" -> gainResource(effect, ctx);
            case "SPEND_RESOURCE" -> spendResource(effect, ctx);
            case "DAMAGE" -> {
                // A list, like HEAL/SHIELD: 「对敌方全体」 is one effect that reaches several units, and the
                // engine settles one instance per victim (that is what a group attack is here).
                for (CanHit victim : resolveTargets(battle, effect, ctx)) {
                    damage(battle, effect, ctx, victim);
                }
            }
            case "MODIFY_ATTR" -> modifyAttr(battle, effect, ctx);
            case "APPLY_BUFF" -> applyState(battle, effect, ctx);
            case "REMOVE_STACK" -> removeStacks(battle, effect, ctx);
            case "MODIFY_DAMAGE_TAKEN" -> modifyDamageTaken(battle, effect, ctx);
            case "BOOST_DAMAGE" -> boostDamage(effect, ctx);
            case "ADD_DAMAGE" -> addDamageFlat(effect, ctx);
            case "DISPEL" -> dispel(battle, effect, ctx);
            case "REMOVE_STATE" -> removeState(effect, ctx);
            case "TAUNT" -> taunt(battle, effect, ctx);
            case "APPLY_CONTROL" -> applyControl(battle, effect, ctx);
            case "APPLY_DOT" -> applyDot(battle, effect, ctx);
            case "EXTEND_BUFF" -> extendBuff(battle, effect, ctx);
            case "RESIST_DEBUFF" -> resistDebuff(battle, effect, ctx);
            case "MODIFY_RULE" -> modifyRule(effect, ctx);
            case "RAISE_SKILL_LEVEL" -> raiseSkillLevel(effect, ctx);
            case "START_COUNTDOWN" -> startCountdown(battle, effect, ctx);
            case "ADD_STACK" -> addStack(battle, effect, ctx);
            case "APPLY_REGEN" -> applyRegen(battle, effect, ctx);
            case "SUMMON" -> battle.summonMemosprite(requireCharacterOwner(effect, ctx));
            case "COMMAND_SUMMON" -> commandSummon(battle, effect, ctx);
            case "DELEGATE_DAMAGE" -> delegateDamage(effect, ctx);
            default -> throw new IllegalStateException(
                    "Op '" + op + "' passed validation but has no implementation");
        }
    }

    /**
     * {@code DELEGATE_DAMAGE} (P11-1, M-40): the cast in progress must not deal its own damage.
     *
     * <p>This is the one op that fires <b>before</b> a swing exists, and it can only touch the cast that is in
     * progress — so all three of its checks are about "is this rule talking about the swing it thinks it is":
     * <ul>
     *   <li>the cast must be the <b>rule owner's own</b>. {@code CAST_SETUP} is delivered to every ally's table,
     *       so a rule that forgot {@code actor == self} would otherwise hand away <i>somebody else's</i> damage —
     *       an over-trigger that no later observation could distinguish from the intended one;</li>
     *   <li>the named {@code skill} must be the slot being cast. Without this the rule would delegate every cast
     *       the owner makes (the condition DSL has no variable for "which slot", so this comparison <i>is</i> the
     *       gate); naming a different slot is a rule that loads, fires, and silently does nothing;</li>
     *   <li>a cast must exist at all. Load-time validation already pins the op to
     *       {@link TriggerEvent#CAST_SETUP}, so a missing token here would be an <b>engine</b> fault, not a
     *       content one — and it is reported as such rather than as a quiet no-op.</li>
     * </ul>
     */
    private static void delegateDamage(EffectSpec effect, TriggerContext ctx) {
        Battle battle = ctx.battle();
        Battle.PendingCast cast = battle == null ? null : battle.currentCast();
        if (cast == null) {
            throw new IllegalStateException(
                    "DELEGATE_DAMAGE ran with no cast in progress; CAST_SETUP is fired by SkillExecutor.execute "
                            + "with a cast token, so this is an engine fault rather than a content error");
        }
        Character owner = requireCharacterOwner(effect, ctx);
        if (cast.caster() != owner) {
            throw new IllegalStateException(
                    "DELEGATE_DAMAGE names the rule owner's own cast, but the cast in progress belongs to "
                            + cast.caster().getName() + ": CAST_SETUP reaches every character's table, so the rule "
                            + "needs `actor == self` (rule owner: " + owner.getName() + ")");
        }
        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Integer slotNumber = Constant.SKILL_SLOT.get(slot);
        if (slotNumber == null) {
            throw new IllegalStateException(
                    "DELEGATE_DAMAGE names " + slot + ", which is not one of the character's castable slots ("
                            + Constant.SKILL_SLOT.keySet() + ")");
        }
        if (cast.slot() != slotNumber) {
            throw new IllegalStateException(
                    "DELEGATE_DAMAGE names " + slot + " (slot " + slotNumber + ") but the cast in progress is slot "
                            + cast.slot() + "; a rule can only hand over the swing that is actually happening "
                            + "(rule owner: " + owner.getName() + ")");
        }
        cast.delegateDamage();
    }

    /**
     * Adds to the target's resource (P8-8).
     *
     * @param effect the effect ({@code resource} + {@code amount})
     * @param ctx    the context
     */
    /**
     * {@code GAIN_ENERGY}: a flat amount, or a share of the receiving unit's <b>maximum energy</b>.
     *
     * <p>⚠ A unit with <b>no energy bar</b> (the six that run on a stack resource instead) has a maximum of 0, and
     * a share of nothing is not a number — that is a loud failure rather than a silent grant of 0, the same call the
     * scale family makes everywhere else.
     */
    private static void gainEnergy(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit target = resolveTarget(effect, ctx);
        if (effect.getScale() == null || effect.getScale().isBlank()) {
            battle.grantEnergy(target, scaledAmount(effect, ctx));
            return;
        }
        String scale = effect.getScale().trim();
        if (scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {
            // 「每冻结1个目标，恢复6点能量」: percent is the amount PER landed application, so the count multiplies it.
            // The count is the cast's own outcome (Battle.recordCastApplied), never the number of targets aimed at.
            String state = scale.substring(TriggerTable.CAST_APPLIED_PREFIX.length()).trim();
            battle.grantEnergy(target, effect.getPercent() * battle.castAppliedCount(state));
            return;
        }
        double maxEnergy = target.getMaxEnergy();
        if (maxEnergy <= 0) {
            throw new IllegalStateException(
                    "GAIN_ENERGY scales off " + target.getName() + "'s maximum energy, but that unit has no energy "
                            + "bar (its maximum is 0); a share of it is not a number");
        }
        battle.grantEnergy(target, effect.getPercent() * maxEnergy);
    }

    private static void gainResource(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = resolveTarget(effect, ctx);
        holder.getResources().gain(effect.getResource(), (int) Math.round(scaledAmount(effect, ctx)));
    }

    /**
     * Spends from the target's resource (P8-8).
     *
     * <p>A spend that does not fit is an <b>error</b>, not a silent no-op: unlike skill points (where
     * "not enough" legitimately means "the player cannot press the button"), a trigger spending a
     * resource is a rule the author wrote believing the resource would be there. Reporting it beats
     * letting a character's stack quietly fail to be consumed.
     *
     * @throws IllegalStateException when the resource is missing or does not hold enough
     */
    private static void spendResource(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = resolveTarget(effect, ctx);
        int amount = (int) Math.round(scaledAmount(effect, ctx));
        String id = effect.getResource();
        if (!holder.getResources().has(id)) {
            throw new IllegalStateException(
                    "SPEND_RESOURCE '" + id + "' but " + holder.getName() + " has no such resource");
        }
        if (!holder.getResources().spendExactly(id, amount)) {
            throw new IllegalStateException(
                    "SPEND_RESOURCE '" + id + "' needs " + amount + " but " + holder.getName()
                            + " has only " + holder.getResources().value(id));
        }
    }

    /**
     * The effect's amount, scaled by the event's hit count when the rule asked for it.
     *
     * <p>See {@link EffectSpec#getPerTarget()}: the game states both "2 per attack" (Robin) and
     * "1.5 per target hit" (Tribbie), and treating them the same would be wrong by a factor equal to
     * the number of targets.
     *
     * @param effect the effect
     * @param ctx    the context supplying the hit count
     * @return the amount to apply
     */
    private static double scaledAmount(EffectSpec effect, TriggerContext ctx) {
        double amount = effect.getAmount() == null ? 0 : effect.getAmount();
        return Boolean.TRUE.equals(effect.getPerTarget()) ? amount * ctx.hitCount() : amount;
    }

    /**
     * Who an effect applies to.
     *
     * <p>The default is the <b>owner</b> (the character whose table fired) -- what nearly every
     * "my own resource" mechanic wants, and what lets the JSON stay terse. Other selectors pick a
     * party of the event instead:
     * <ul>
     *   <li>{@code "target"} — the event's subject (the one who lost HP, was healed, ...), which is
     *       what an ally-affecting rule needs;</li>
     *   <li>{@code "attacker"} — who caused the event, which is what a counter needs
     *       ("I was hit, so I hit the one who hit me").</li>
     *   <li>{@code "summon"} — <b>the owner's own summon</b> ({@link Battle#summonOf}). This one reads the
     *       field rather than the event, because 「装备者及其忆灵」 ("the wearer and their memosprite") names a
     *       unit that no event carries: the ability affects both, and only one of them acted. It is why a
     *       partner condition {@code self_summon_count >= 1} usually sits next to it — without one, the rule
     *       would fail (loudly) whenever nothing is out.</li>
     * </ul>
     *
     * @param effect the effect
     * @param ctx    the context
     * @return the resolved entity
     * @throws IllegalStateException when the requested party is missing from this event
     */
    private static CanHit resolveTarget(EffectSpec effect, TriggerContext ctx) {
        String selector = normalizeTarget(effect);
        return switch (selector) {
            case "self" -> ctx.owner();
            case "target" -> require(ctx.target(), "target", ctx);
            case "attacker" -> require(ctx.actor(), "attacker", ctx);
            case "summon" -> requireSummon(ctx);
            default -> throw new IllegalStateException(
                    "Effect names the target selector '" + selector + "', which can reach several units: it needs "
                            + "an op that takes a list, not one that resolves a single target");
        };
    }

    /**
     * Who an effect applies to, as a list, for the ops that can reach more than one party member.
     *
     * <p>{@code "all_allies"} (alias {@code "party"}) is what makes "all allies' ATK +X%" expressible.
     * It matters more than it looks: most buff talents in this game's data are party-wide, so without
     * this selector the trigger table would only cover self-buffs. It is resolved against
     * {@link Battle#allies} — the camp, <b>not</b> {@code characters} — so a player-side summon counts as
     * 「我方」 and receives the party buff too; and it therefore needs a battle, which the single-target
     * selectors do not.
     *
     * @param battle the running battle (may be {@code null} only when the selector is single-target)
     * @param effect the effect
     * @param ctx    the context
     * @return the entities the effect applies to (never empty for the single-target selectors)
     * @throws IllegalStateException when {@code all_allies} is used without a battle
     */
    private static List<CanHit> resolveTargets(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String selector = normalizeTarget(effect);
        if (TARGET_ALL_ALLIES.contains(selector) || TARGET_OTHER_ALLIES.equals(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + selector
                                + "\" but no battle was supplied to take the party from");
            }
            List<CanHit> party = new ArrayList<>();
            for (CanHit ally : battle.allies) {
                // `other_allies` is the same camp minus the rule's owner (「除自身以外」); the order is the
                // roster's either way, so a group effect reaches units in a stable order.
                if (TARGET_OTHER_ALLIES.equals(selector) && ally == ctx.owner()) {
                    continue;
                }
                party.add(ally);
            }
            return List.copyOf(party);
        }
        if (TARGET_LOWEST_HP_ALLY.equals(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + TARGET_LOWEST_HP_ALLY
                                + "\" but no battle was supplied to read the party's health from");
            }
            CanHit lowest = null;
            double lowestShare = Double.MAX_VALUE;
            for (CanHit ally : battle.allies) {
                if (ally == null || ally.isDeath() || !(ally.getMaxHp() > 0)) {
                    continue;                       // a corpse, or a unit with no health bar, has no share
                }
                double share = ally.getCurrentHp() / ally.getMaxHp();
                // Strictly less than: a tie keeps the EARLIER unit, so the answer is the party order's, not luck.
                if (share < lowestShare) {
                    lowestShare = share;
                    lowest = ally;
                }
            }
            return lowest == null ? List.of() : List.of(lowest);
        }
        if (TARGET_ALL_ENEMIES.equals(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + TARGET_ALL_ENEMIES
                                + "\" but no battle was supplied to take the opposing camp from");
            }
            // `getOpponents` is the one predicate for "the other side" (it also answers correctly for an enemy
            // owner), and it does not filter the dead -- the op does, because only the op knows whether a dead
            // unit is something it can act on.
            return List.copyOf(battle.getOpponents(ctx.owner()));
        }
        if (TARGET_AND_SUMMON.equals(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + TARGET_AND_SUMMON
                                + "\" but no battle was supplied to look for that unit's summon");
            }
            CanHit chosen = require(ctx.target(), TARGET_AND_SUMMON, ctx);
            List<CanHit> pair = new ArrayList<>();
            pair.add(chosen);
            // `summonsOf` is the one predicate for "the summons this unit owns" (it already drops the dead ones),
            // so "及其召唤物" means the same thing here as it does for the `summon` selector.
            pair.addAll(battle.summonsOf(chosen));
            return List.copyOf(pair);
        }
        return List.of(resolveTarget(effect, ctx));
    }

    /**
     * {@code COMMAND_SUMMON}: the owner's summon performs <b>one attack, right now</b>, with the numbers of the
     * skill the rule names — 「召唤忆灵「长夜」，随后使忆灵「长夜」对敌方全体造成等同于「长夜」#1[i]%生命上限的冰属性伤害」.
     *
     * <p><b>Where every number comes from.</b> The rule names a {@code skill} + {@code damage_param} of the
     * <b>owner's</b> skills — her ultimate, whose row says 2.0 at Lv10 and 2.5 at Lv15 — and the element (Ice) and
     * the shape (AoEAttack) come from that same skill data. Only the <b>base attribute</b> ({@code HEALTH}) is
     * stated here, because that is the one thing the row does not say: 「等同于<b>忆灵</b>的生命上限」, not 长夜月's
     * attack. A first draft of this op carried its own {@code element} / {@code shape} / {@code percent} fields
     * and would have been a second copy of data that already exists, one that also loses the per-level table.
     *
     * <p>⚠ The skill is the owner's and the <b>attacker is the summon</b> — a first draft looked the skill up on
     * the summon and failed with "长夜 has no ULTRA skill", which is exactly right: the numbers are hers, the
     * swing is its.
     *
     * <p><b>Whose action it is.</b> The owner's: the summon acts without spending its turn, exactly like a
     * follow-up, and its own turn (with its own skill) is untouched. The damage is settled with the
     * <b>summon</b> as the attacker, so everything downstream is the summon's — its attribute is what the share
     * reads, kill credit is the summon's, and the attack announces itself to our side like any other attack by
     * it ({@code Battle.fireAfterAttack} + {@code TriggerEvent.SUMMON_ATTACK}, both reached through
     * {@link EnemySkill#execute}).
     *
     * <p>⚠ Reusing {@link EnemySkill} is the point: it already owns the shape dispatch (single / AOE / blast),
     * per-segment settlement and those two announcements. What it does not do is choose targets, which is the one
     * thing this method adds — and it must filter to the <b>living</b>, because {@code EnemySkill} takes the
     * first entry of the list it is handed as the main target.
     */
    private static void commandSummon(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit summon = requireSummon(ctx);
        Character owner = requireCharacterOwner(effect, ctx);
        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        // The skill is the OWNER's: it is her ultimate whose parameter row carries the numbers. The summon is
        // only the one that swings.
        Skill skill = owner.getSkills().get(slot);
        if (skill == null || skill.getData() == null) {
            throw new IllegalStateException(
                    owner.getName() + " has no " + slot + " skill, so a COMMAND_SUMMON effect has nothing to "
                            + "read: the rule names the skill whose numbers the commanded attack uses");
        }
        if (!skill.getData().getEffect().isDamaging()) {
            throw new IllegalStateException(
                    "COMMAND_SUMMON effect points at " + slot + ", whose effect is "
                            + skill.getData().getEffect() + " rather than a damaging one");
        }
        List<CanHit> victims = new ArrayList<>();
        for (CanHit unit : battle.getOpponents(summon)) {
            if (unit != null && !unit.isDeath()) {
                victims.add(unit);
            }
        }
        if (victims.isEmpty()) {
            return;                                  // nothing left to hit: an empty battlefield, not a bad rule
        }
        EnemySkill attack = new EnemySkill(
                skill.getData().getElement(),
                // ⚠ `owner`, not the summon: the skill and its parameter row are the OWNER's (see above), so its
                // level — and any 「终结技等级+1」 this battle has raised — is the owner's too.
                multiplierOf(skill, effect, owner),
                1,                                   // one segment: 长夜月's ultimate is 「单目标段数: 1」 in its own split
                DamageType.NORMAL,                   // a real attack by the summon, not 附加伤害
                skill.getData().getEffect(),         // the shape the skill itself declares (AoEAttack)
                AttributeType.fromString(effect.getAttribute()),
                // ⚠ The toughness too, and for the same reason as the element and the shape: it is written down
                // once, in this skill's own `stance_list` (141303: `single 0 / all 90`), and the commanded swing
                // is that skill's damage. ⚠ It has to be here rather than "the caster's cast already removed it":
                // when a cast is DELEGATED (`DELEGATE_DAMAGE`, M-40) the executor expands no damage of its own, so
                // the stance would otherwise be dropped on the floor — exactly one of the two must carry it, and
                // this is the one that actually swings. `stanceFor(true)` is the skill's main-target column: for
                // an AOE that is `all` (per victim, matching the caster-side path), for BLAST `single`.
                skill.getData().stanceFor(true));
        attack.execute(battle, summon, victims);
    }

    /**
     * The owner's summon, for the {@code "summon"} selector.
     *
     * <p>A missing summon is an {@link IllegalStateException} rather than a quiet no-op, and the message says
     * what to do about it: an author who writes 「装备者及其忆灵」 without a {@code self_summon_count >= 1}
     * condition has a rule that fires exactly when the unit it names is not there. Silence would be a wrong
     * state; the loud version is a one-line fix in the rule file.
     */
    private static CanHit requireSummon(TriggerContext ctx) {
        if (ctx.battle() == null || ctx.owner() == null) {
            throw new IllegalStateException(
                    "Effect targets \"summon\" but this rule was evaluated without a battlefield, so the "
                            + "owner's summon cannot be identified");
        }
        CanHit summon = ctx.battle().summonOf(ctx.owner());
        if (summon == null) {
            throw new IllegalStateException(
                    "Effect targets \"summon\" but " + ctx.owner().getName() + " has no summon on the field; "
                            + "gate the rule with `self_summon_count >= 1` so it only fires when there is one "
                            + "(the summon perishes with its master, so a stale rule would otherwise fire "
                            + "every time)");
        }
        return summon;
    }

    /**
     * The rule's owner, as the {@code Character} a memosprite spec is keyed by.
     *
     * <p>Why this can be a cast rather than a check worth worrying about: trigger tables only ever live on
     * characters ({@code Battle.fireTriggers} walks {@code battle.characters}), so anything else here means
     * the engine grew a second kind of table owner and this op needs a story for it. That is worth an
     * exception rather than a silent skip — {@code SUMMON} would otherwise simply do nothing.
     */
    private static Character requireCharacterOwner(EffectSpec effect, TriggerContext ctx) {
        if (ctx.owner() instanceof Character character) {
            return character;
        }
        throw new IllegalStateException(
                "Op SUMMON needs its rule owner to be a character (the memosprite spec is keyed by cid), but "
                        + "this rule's owner is " + (ctx.owner() == null ? "nobody" : ctx.owner().getClass().getSimpleName()));
    }

    private static CanHit require(CanHit entity, String what, TriggerContext ctx) {
        if (entity == null) {
            throw new IllegalStateException(
                    "Effect targets \"" + what + "\" but this event has no such party");
        }
        return entity;
    }

    private static String normalizeTarget(EffectSpec effect) {
        // `target` lives on the effect as an optional selector; absent means "self".
        return effect.getTarget() == null ? "self" : effect.getTarget().trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOp(EffectSpec effect, TriggerSpec spec) {
        if (effect == null || effect.getOp() == null || effect.getOp().isBlank()) {
            throw new IllegalArgumentException(
                    "Trigger effect without an op" + (spec == null ? "" : " (source: " + spec.getSource() + ")"));
        }
        return effect.getOp().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Settles a {@code MODIFY_ATTR} effect (P10-3): "the target's {@code attribute} changes by
     * {@code percent} for {@code turns} turns" -- the generic stat buff / debuff.
     *
     * <p><b>Why this op matters more than its size suggests.</b> 72 of the 93 characters have a
     * buff / enhance talent, and before this op the only way to express one was a new Java buff
     * class per character. With it, "ATK +33% for 2 turns" is data, and the engine gained no
     * character-specific knowledge at all: it forwards (attribute, value, turns) to
     * {@link StatModifierBuff}.
     *
     * <p><b>The sign decides buff or debuff.</b> {@code percent >= 0} is applied as a
     * {@code BUFF}, a negative one as a {@code DEBUFF}. The distinction is not cosmetic: it decides
     * which half of the attribute the modifier lands in, so "ATK +50%" and "ATK -30%" can be on the
     * same character at once and be removed independently instead of overwriting each other.
     *
     * <p>{@code percent} is a decimal ({@code 0.5} = +50%).
     *
     * <p><b>What the decimal means depends on the attribute kind</b>, and the difference is not
     * cosmetic either:
     * <ul>
     *   <li>For a <b>base</b> attribute ({@code HEALTH / ATTACK / DEFENCE / SPEED}) it is an
     *       {@code ADD_PERCENT} modifier, so it sums with the character's other add-percent bonuses
     *       (traces, relics, light cones) rather than multiplying on top of them -- see
     *       {@link DoubleValue} for the formula.</li>
     *   <li>For a <b>ratio</b> attribute ({@code CRIT_CHANCE}, {@code CRIT_ATTACK}, the damage
     *       boosts, {@code BREAKING_EFFECT}, {@code ENERGY_REGENERATION_RATE}, ...) it is the value
     *       <b>itself</b>: {@code 0.25} on {@code CRIT_ATTACK} means +25 percentage points. This is
     *       the engine's existing convention for those attributes (see {@code RelicSuit.appendTo}:
     *       "any other {@code isPercent} attribute is a percentage-point value") and the reason is
     *       that their whole value is a flat modifier -- the builder writes them with
     *       {@code AttributeBuilder.addPercentPoint}, so their base is literally 0 and an
     *       {@code ADD_PERCENT} modifier would multiply zero: the rule would fire, add a modifier and
     *       change nothing. A silent no-op is the one outcome this op must not have.</li>
     * </ul>
     *
     * @param effect the effect ({@code attribute} / {@code percent} / {@code turns} or
     *               {@code permanent}, optional {@code max_stacks}, {@code target})
     * @param ctx    the context
     */
    private static void modifyAttr(Battle battle, EffectSpec effect, TriggerContext ctx) {
        AttributeType attribute = AttributeType.fromString(effect.getAttribute());
        // P11-2 (M-42): a **derived** magnitude — 「提高数值等同于<某人的属性>的 X% + Y%」. It is computed once,
        // when the rule fires, and it is an ABSOLUTE number in the target attribute's units: the documents that
        // need this state a value ("equal to 12% of Sunday's CRIT DMG plus 8%"), not a share of the target's base.
        // ⚠ Which is why it does not go through `statModifier`'s usual base-attribute convention (`add_percent`).
        boolean derived = effect.getScale() != null && !effect.getScale().isBlank();
        double magnitude = derived ? derivedMagnitude(effect, ctx) : effect.getPercent();
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        int maxStacks = effect.stackCap() == null ? 1 : effect.stackCap();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            AbstractBuff buff = withSource(withTickOwner(
                    withLifetime(statModifier(attribute, magnitude, turns, permanent, maxStacks, derived), effect),
                    effect, ctx), ctx);
            // 「处于【协奏】状态时，我方全体攻击力提高…」 (2026-09-28): the boost lasts as long as the STATE, and the state ends
            // when a countdown's turn arrives -- a lifetime no `turns` can state. Naming the modifier is what lets
            // `REMOVE_STATE <the same name>` take it off; ⚠ without a name the modifier is skipped by that loop, i.e.
            // unnamed boosts keep exactly the lifetime they always had.
            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());
            }
            target.getBuffManager().addBuff(buff);
        }
    }

    /**
     * The magnitude of a derived {@code MODIFY_ATTR}: {@code percent × <the rule owner's attribute> + amount}.
     *
     * <p>The first user is 大丽花's trace 「又一场葬礼」: 「进入战斗时，使其他角色的击破特攻提高，提高数值等同于 #1% 大丽花的
     * 击破特攻 + #3%」 — a percentage of her own Break Effect plus a flat part, granted to the rest of the party.
     *
     * <p>⚠ <b>Read at fire time, then frozen.</b> The value is taken from the owner's <b>resolved</b> attribute (so it
     * includes whatever buffs are on them at that moment) and stored in an ordinary modifier, so a later change to
     * the owner's attribute does not retroactively rewrite it. That is the engine's existing snapshot convention
     * (§24.5: a memosprite's panel is derived once, at summon time) and it is also what "提高数值等同于" means in
     * the documents — a number that was computed, then held for the duration.
     *
     * <p>⚠ A missing attribute slot is a loud failure rather than {@code 0}: the alternative is a buff that grants
     * nothing, which is a wrong number with no symptom.
     */
    private static double derivedMagnitude(EffectSpec effect, TriggerContext ctx) {
        Character owner = requireCharacterOwner(effect, ctx);
        if (SELF_MAX_ENERGY.equals(effect.getScale().trim())) {
            // 「每超过 1 点」 where the points are MAX ENERGY: the same derived shape, off a value the attribute
            // table has no slot for (see the `self_max_energy` condition variable).
            return effect.getPercent() * owner.getMaxEnergy() + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
        AttributeType source = scaleAttribute(effect, effect.getOp(), null);
        DoubleValue value = owner.getAttribute(source);
        if (value == null) {
            throw new IllegalStateException(
                    "MODIFY_ATTR derives its value from " + source + ", which " + owner.getName()
                            + " has no resolved value for; the rule's \"scale\" cannot be read");
        }
        return effect.getPercent() * value.get() + (effect.getAmount() == null ? 0 : effect.getAmount());
    }

    /**
     * The attribute a {@code "scale": "self_attr:<ATTRIBUTE>"} names.
     *
     * <p>Deliberately the same spelling as the condition DSL's variable ({@code self_attr:SPEED} —
     * {@code TriggerTable.SELF_ATTR_PREFIX}, shared so the two cannot drift): one says "my Speed matters", the
     * other says "the number is a share of my Speed", and both read the <b>rule owner</b>.
     *
     * @param effect the effect
     * @param op     the op, for the error message
     * @param spec   the rule, for the source ({@code null} when called at fire time, where the loader's message
     *               already did its job)
     * @return the attribute named by the scale
     */
    private static AttributeType scaleAttribute(EffectSpec effect, String op, TriggerSpec spec) {
        String raw = effect.getScale() == null ? "" : effect.getScale().trim();
        String origin = spec == null ? "" : " (source: " + spec.getSource() + ")";
        if (SELF_MAX_ENERGY.equals(raw)) {
            return null;                      // handled by derivedMagnitude; not an AttributeType
        }
        if (!raw.startsWith(TriggerTable.SELF_ATTR_PREFIX)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"scale\": \"" + effect.getScale() + "\", which is not a spelling this op "
                            + "knows; a derived modifier names one of the RULE OWNER's attributes, e.g. "
                            + "\"scale\": \"" + TriggerTable.SELF_ATTR_PREFIX + "BREAKING_EFFECT\"" + origin);
        }
        String name = raw.substring(TriggerTable.SELF_ATTR_PREFIX.length()).trim();
        AttributeType attribute;
        try {
            attribute = AttributeType.fromString(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " derives its value from \"" + name + "\", which is not an attribute" + origin);
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Op " + op + " derives its value from '" + name + "', which is a builder-only input key "
                            + "rather than a runtime attribute (name the base attribute)" + origin);
        }
        return attribute;
    }

    /**
     * Whether the buff this effect creates must <b>not be ticked</b>: either it has no turn limit at all
     * ({@code permanent: true}) or its end is an event ({@code until}), which no turn boundary can bring
     * forward.
     *
     * <p><b>Why an event-bound buff counts as "permanent".</b> That flag means "never ticked"
     * ({@link AbstractBuff#isPermanent()}), which is exactly right here: a buff that ends 持续到施放首次攻击
     * must survive any number of turn boundaries, and giving it a placeholder turn count instead would make it
     * expire on the first {@code afterMove} — a wrong answer that looks like a working rule. The flag's name
     * reads as "for the rest of the battle" in the JSON (where it is what authors write), but mechanically it
     * is "no turn limit", and an event ends this one.
     *
     * <p>⚠ Also the reason this helper exists: {@code until} leaves {@code turns} null, and reading
     * {@code getTurns()} into an {@code int} would throw at fire time — inside a battle, where the project
     * puts nothing.
     */
    private static boolean unticked(EffectSpec effect) {
        return Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect);
    }

    private static boolean eventBound(EffectSpec effect) {
        return effect.getUntil() != null && !effect.getUntil().isEmpty();
    }

    /**
     * Attaches the {@code "until": …} a rule names to a buff it just created (see {@link EffectSpec#getUntil()}).
     *
     * <p>A no-op when the effect states no lifetime, so every buff that does not ask for one behaves exactly
     * as before — the field's default is an <b>empty</b> set of ending events.
     */
    private static AbstractBuff withLifetime(AbstractBuff buff, EffectSpec effect) {
        buff.setLifetimes(lifetimesOf(effect));
        return buff;
    }

    /**
     * Anchors a fresh buff's duration to the rule owner's turns when the rule asks for it ({@code "ticks_on":
     * "self"}, M-42 ④), and leaves it on its carrier otherwise.
     *
     * <p>Separate from {@link #withLifetime} because the two say different things about time: {@code until} names the
     * <b>event</b> that ends a buff, this names <b>whose turn boundary</b> spends it. A rule may state either, and
     * the state names are the same ones the rest of the op vocabulary uses ({@code "self"} = the rule owner).
     */
    private static AbstractBuff withTickOwner(AbstractBuff buff, EffectSpec effect, TriggerContext ctx) {
        if (effect.getTicksOn() != null) {
            buff.setTickOwner(ctx.owner());
        }
        return buff;
    }

    /**
     * Validates {@code "ticks_on"} at load time: one value, spelled exactly, and only on ops that create a timed
     * buff — the same "fail while the file is read" discipline as every other argument.
     */
    private static void requireTickOwner(EffectSpec effect, String op, TriggerSpec spec) {
        String ticksOn = effect.getTicksOn();
        if (ticksOn == null || ticksOn.isBlank()) {
            return;
        }
        if (!"self".equals(ticksOn.trim())) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"ticks_on\": \"" + ticksOn + "\", which is not a spelling it knows; the "
                            + "only other clock a document has asked for is the RULE OWNER's, written "
                            + "\"ticks_on\": \"self\" (say nothing for the ordinary case: the unit that carries "
                            + "the buff) (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent())) {
            throw new IllegalArgumentException(
                    "Op " + op + " is \"permanent\": true, so nothing counts it down and \"ticks_on\": \"self\" "
                            + "would be ignored (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The lifetimes a validated effect names; <b>empty</b> when it names none.
     *
     * <p>Every name has already been checked against {@link #LIFETIMES} at load time, so anything else here is an
     * engine-side inconsistency rather than a content error — hence {@link IllegalStateException}, exactly as
     * before. A rule may name several (「普攻<b>或</b>战技」): they are collected into one set, and the buff ends at
     * the first of them.
     */
    private static Set<AbstractBuff.Lifetime> lifetimesOf(EffectSpec effect) {
        List<String> until = effect.getUntil();
        if (until == null || until.isEmpty()) {
            return Set.of();
        }
        Set<AbstractBuff.Lifetime> lifetimes = EnumSet.noneOf(AbstractBuff.Lifetime.class);
        for (String name : until) {
            lifetimes.add(switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "next_attack" -> AbstractBuff.Lifetime.NEXT_ATTACK;
                case "next_skill" -> AbstractBuff.Lifetime.NEXT_SKILL;
                case "next_ultimate" -> AbstractBuff.Lifetime.NEXT_ULTIMATE;
                default -> throw new IllegalStateException(
                        "Lifetime '" + name + "' passed validation but has no implementation");
            });
        }
        return lifetimes;
    }

    /**
     * The lifetimes a rule states, spelled the way the file writes them, for error messages.
     */
    private static String spellUntil(EffectSpec effect) {
        return effect.getUntil().stream().map(name -> "\"" + name + "\"").toList().toString();
    }

    /**
     * Settles an {@code APPLY_BUFF} effect: the target enters a <b>named state</b> for a duration.
     *
     * <p><b>Why a state and not a stat buff.</b> The rule texts say 「处于【协奏】状态时」/「【转魄】状态下」/
     * 「触电状态下的敌方目标」 — a <i>fact about the unit</i> that other rules read. {@code MODIFY_ATTR}
     * already covers "the target's ATTACK changes"; this op covers "the target is now in a state", and the
     * two compose: the state is applied here, and whatever it changes is a separate effect conditioned on
     * {@code self has_state …} / {@code target has_state …}. Keeping them apart means one state can drive
     * several effects (and several rules can read the same state) without the state itself knowing anything.
     *
     * <p>The state is an ordinary {@link StateBuff}, so it gets the whole buff lifecycle for free:
     * duration counted down on the owner's turns, refresh on re-application, removal by
     * {@code BuffManager.clearAll()}, and visibility to {@code hasBuff}-style queries. Applying a
     * <b>different</b> state does not evict this one — {@code StateBuff.isSameKind} compares names, not
     * classes.
     *
     * @param effect the effect ({@code buff}, exactly one of {@code turns} / {@code permanent}, optional
     *               {@code target})
     * @param ctx    the context
     */
    private static void applyState(Battle battle, EffectSpec effect, TriggerContext ctx) {
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        String state = effect.getBuff().trim();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            AbstractBuff buff = withSource(withTickOwner(
                    withLifetime(new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);
            // 「不会进入自己的回合」 rides on the state itself: a turn is not something a state could give back
            // later, so the flag and the state share one lifetime by construction.
            if (Boolean.TRUE.equals(effect.getSuspendsTurns())) {
                buff.setSuspendsTurns(true);
            }
            target.getBuffManager().addBuff(buff);
        }
    }

    /**
     * Settles a {@code REMOVE_STACK} effect: takes up to {@code amount} stacks of one attribute's modifier
     * off the target — the "removes 1 stack(s) of this effect" half of effects like relic set 131's.
     *
     * <p>The stack is the engine's existing stackable modifier ({@code MODIFY_ATTR} with {@code max_stacks}),
     * so nothing new is being modelled: what was missing was only that <b>data</b> could take one back. Until
     * this op existed a stack could grow and never shrink, which meant such a text could only be modelled by
     * dropping half of it.
     *
     * <p><b>Removing nothing is not an error</b>, which is where this differs from {@code SPEND_RESOURCE}:
     * there the author wrote a rule expecting the resource to be there, while here the rule is of the shape
     * "at the start of the wearer's turn, removes 1 stack" and fires on every turn, including the ones where
     * the counter is already at zero. Making that an exception would break the common case.
     *
     * @param effect the effect ({@code attribute}, {@code amount}, optional {@code target})
     * @param ctx    the context
     */
    private static void removeStacks(Battle battle, EffectSpec effect, TriggerContext ctx) {
        AttributeType attribute = AttributeType.fromString(effect.getAttribute());
        int amount = (int) Math.round(effect.getAmount());
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            target.getBuffManager().removeStacks(attribute, amount);
        }
    }

    /**
     * Settles a {@code MODIFY_DAMAGE_TAKEN} effect: 「受到的伤害提高 X%」（易伤）/「受到的伤害降低 X%」（减伤）.
     *
     * <p><b>Why it is not {@code MODIFY_ATTR}.</b> Both are zones, not attributes: neither 「incoming damage
     * ×1.12」 nor 「×0.92」 exists as an {@code AttributeType}, and the engine's two zone buffs
     * ({@link VulnerabilityBuff} / {@link ReductionBuff}) inject into the settlement of every hit instead of
     * holding state — which is also why relic set 106's 2-piece ("Reduces DMG taken by 8%", a passive with no
     * turn count) sat in {@code _unmodelled.json} until this op existed.
     *
     * <p><b>The sign picks the zone</b>, the same convention {@code MODIFY_ATTR} uses for buff/debuff: a
     * positive percent is vulnerability on the defender (a debuff), a negative one is reduction (a buff).
     * One op rather than two, because the data spells them as one family and the two are mutually exclusive
     * readings of one number — a content author writing {@code -0.08} means 减伤, and both buffs enforce
     * their own sign so a mistake cannot end up as the other zone.
     *
     * @param effect the effect ({@code percent}, exactly one of {@code turns} / {@code permanent}, optional
     *               {@code target})
     * @param ctx    the context
     */
    private static void modifyDamageTaken(Battle battle, EffectSpec effect, TriggerContext ctx) {
        double percent = effect.getPercent();
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            target.getBuffManager().addBuff(withSource(withLifetime(percent > 0
                    ? new VulnerabilityBuff(turns, percent, permanent)
                    : new ReductionBuff(turns, -percent, permanent), effect), ctx));
        }
    }

    /**
     * The owner's Eidolon rank, or {@code 0} when the owner is not a character.
     *
     * <p>Trigger tables only ever belong to characters ({@code Battle.fireTriggers} walks
     * {@code battle.characters} and enemies have none), so the branch is about the type system rather than about
     * a real case — but a hand-built context can carry anything, and "0 ranks" is the answer that never unlocks
     * an Eidolon by accident.
     */
    private static int eidolonRankOf(CanHit owner) {
        return owner instanceof Character character ? character.getEidolonRank() : 0;
    }

    /**
     * Settles a {@code BOOST_DAMAGE} effect: raises (or lowers) <b>the damage instance being settled</b>.
     *
     * <p>This is the op behind 「对处于 X 状态的目标造成的伤害提高 Y%」, and it exists because that sentence is
     * not a buff: it applies to the hits that happen to satisfy a condition, so the natural home for the state
     * is the hit itself. The rule fires on {@link TriggerEvent#DEALING_DAMAGE}, the engine hands the pending
     * instance over in the context, and this adds one modifier to its DMG-boost zone — after which the instance
     * is settled as usual. Nothing is attached to anybody, so nothing has to be removed, and a second hit is
     * unaffected unless its own firing says otherwise.
     *
     * <p>A negative {@code percent} is legal and means "this instance deals less" (the zone is {@code 1 + Σ},
     * and {@code assemble} floors the result at 1 anyway).
     *
     * @param effect the effect ({@code percent})
     * @param ctx    the context, which must carry the instance (guaranteed by the load-time event check)
     */
    private static void addDamageFlat(EffectSpec effect, TriggerContext ctx) {
        Damage damage = ctx.damage();
        if (damage == null) {
            throw new IllegalStateException(
                    "Op ADD_DAMAGE needs the damage instance being settled, but this context carries none");
        }
        damage.addFlat(derivedMagnitude(effect, ctx));
    }

    private static void boostDamage(EffectSpec effect, TriggerContext ctx) {
        Damage damage = ctx.damage();
        if (damage == null) {
            // Unreachable through a validated table (requireEvent pins the event), but `apply` is public and a
            // hand-built context can still lack an instance: better a loud error than a silent no-op.
            throw new IllegalStateException(
                    "Op BOOST_DAMAGE needs the damage instance being settled, but this context carries none");
        }
        damage.addBoost(effect.getPercent());
    }

    /**
     * The things a scaled {@code HEAL} / {@code SHIELD} amount may be a percentage of. Closed on purpose, like
     * every other vocabulary here: a typo has to be rejected at load time, and the two spellings are the ones
     * the content actually uses (see {@link EffectSpec#getScale()}).
     */
    private static final Set<String> SCALES = Set.of("target_max_hp", "owner_max_hp", "owner_def");

    /**
     * The one scale {@code GAIN_ENERGY} accepts: a share of the <b>receiving</b> unit's maximum energy.
     *
     * <p>「为指定我方单体角色恢复等同于 #1[f1]% 能量上限的能量」 (星期日's ultimate). It cannot be a flat number,
     * because the maximum is per character (姬子 120 / 星期日 130 / 翡翠 140) — writing 20 would be wrong for all of
     * them, and it would look right for whichever one the author happened to check.
     */
    private static final Set<String> ENERGY_SCALES = Set.of("target_max_energy");

    /**
     * The one derived scale that is not an attribute: {@code MODIFY_ATTR}'s magnitude may be a share of the rule
     * owner's <b>maximum energy</b> (relic set 328's 「每超过 1 点」). Same spelling family as the condition variable
     * above, and deliberately not an {@code AttributeType}.
     */
    private static final String SELF_MAX_ENERGY = "self_max_energy";

    /**
     * Validates the magnitude of a {@code HEAL} / {@code SHIELD} effect: either a flat {@code amount}, or
     * {@code scale} + {@code percent}.
     *
     * <p>Both spellings of each mistake are refused, because both would otherwise be silently ignored by Gson:
     * an {@code amount} together with a {@code scale} (which wins?), a {@code scale} without a
     * {@code percent} ("some share of a Max HP"), a {@code percent} without a {@code scale} (a percentage of
     * what?), and a {@code scale} together with {@code per_target} (a share of a Max HP is already per unit).
     */
    private static void requireAmountOrScale(EffectSpec effect, String op, TriggerSpec spec,
                                             Set<String> scales, String what) {
        String scale = effect.getScale() == null ? null : effect.getScale().trim();
        if (scale == null || scale.isEmpty()) {
            // A percentage without a scale is refused *before* the missing amount, because the author clearly
            // meant the scaled spelling: "requires amount" alone would send them looking in the wrong place.
            if (effect.getPercent() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " has \"percent\" but no \"scale\": the flat spelling states "
                                + "\"amount\" instead, and the scaled one states \"scale\" + \"percent\" "
                                + "(source: " + spec.getSource() + ")");
            }
            requireAmount(effect, op, spec);
            return;
        }
        // ⚠ `amount` together with a `scale` is the shape 「等同于 X% 防御力 + 760」: the scale is the share OF
        // something, and the amount is the flat addend. It used to be refused here ("which one wins?"), which is why
        // no shipped rule states both -- so allowing it now changes no existing content.
        if (effect.getAmount() != null && effect.getPercent() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"scale\" together with \"amount\" but no \"percent\": the scale says what "
                            + "the share is OF, so the share itself is missing "
                            + "(source: " + spec.getSource() + ")");
        }
        if (!scales.contains(scale) && !scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has unknown \"scale\": '" + effect.getScale() + "'; known scales for this op "
                            + "are " + String.join(", ", scales.stream().sorted().toList())
                            + " (source: " + spec.getSource() + ")");
        }
        if (scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {
            requireCastAppliedScale(effect, op, spec, scale);
        }
        requirePercent(effect, op, spec);
        if (effect.getPerTarget() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot combine \"scale\" with \"per_target\": the scale is already a share "
                            + "of one unit's " + what + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code "scale": "cast_applied:&lt;状态名&gt;"} — a per-<b>landed</b>-application multiplier.
     *
     * <p>Two things are checked, and both are mistakes that would otherwise be silent:
     * <ul>
     *   <li><b>the state name is one the engine rolls for</b> ({@link BuffManager#isRolledStateName}): 冻结 / 纠缠 /
     *       禁锢 and the four DOT states. A typo would make the counter answer 0 forever — a rule that pays nothing,
     *       with nothing to report;</li>
     *   <li><b>the rule fires on a cast</b>. The record only exists while a cast's own events are being delivered
     *       ({@code Battle.beginCast} … {@code endCastOutcome}); on {@code TAKING_HIT}, {@code TURN_START} or
     *       {@code KILL} it would read the <i>previous</i> cast's numbers or 0 — the kind of stale value this project
     *       refuses to leave implicit. The four cast events are the ones {@code SkillExecutor} fires inside that
     *       window.</li>
     * </ul>
     */
    private static void requireCastAppliedScale(EffectSpec effect, String op, TriggerSpec spec, String scale) {
        String state = scale.substring(TriggerTable.CAST_APPLIED_PREFIX.length()).trim();
        if (!BuffManager.isRolledStateName(state)) {
            throw new IllegalArgumentException(
                    "Op " + op + " counts applications of '" + state + "', which is not a state this engine rolls "
                            + "for; known names are the control states (" + String.join(" / ",
                            Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + ") and the DOT states (灼烧 / 触电 / 裂伤 / 风化), spelled as the documents spell them "
                            + "(source: " + spec.getSource() + ")");
        }
        TriggerEvent event = TriggerEvent.fromString(spec.getOn());
        if (event == null || !CAST_EVENTS.contains(event)) {
            throw new IllegalArgumentException(
                    "Op " + op + " reads \"scale\": \"" + scale + "\" — the number of targets THIS CAST applied the "
                            + "state to — but the rule fires on " + spec.getOn() + ", outside the window in which that "
                            + "count exists (it is cleared once the cast's own events have been delivered). Cast "
                            + "events: " + CAST_EVENTS.stream().map(Enum::name).sorted()
                            .collect(java.util.stream.Collectors.joining(", "))
                            + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * The events during which a cast's landed-application counts are readable.
     *
     * <p>Not "every event a cast can cause": {@code CAST_SETUP} fires <b>before</b> anything has landed (so it reads
     * 0 by construction) and the other three are the delivered cast events. Keeping the list here, next to the
     * validation that uses it, is what makes the window one fact instead of a comment.
     */
    private static final Set<TriggerEvent> CAST_EVENTS = Set.of(
            TriggerEvent.CAST_SETUP, TriggerEvent.BASIC_ATTACK, TriggerEvent.SKILL_CAST, TriggerEvent.ULT_CAST);

    /**
     * The magnitude of a {@code HEAL} / {@code SHIELD} for one target: the flat {@code amount}, or a share of a
     * Max HP.
     *
     * <p>Computed <b>per target</b> because {@code target_max_hp} differs from one recipient to the next: a
     * party-wide heal that restores 8% of each ally's own Max HP cannot be one number.
     *
     * @param effect the effect (already validated)
     * @param target the unit about to receive the heal or shield
     * @param ctx    the context ({@code owner_max_hp} reads the rule owner, i.e. the healer)
     */
    private static double grantAmount(EffectSpec effect, CanHit target, TriggerContext ctx) {
        String scale = effect.getScale();
        if (scale == null || scale.isBlank()) {
            return scaledAmount(effect, ctx);
        }
        double share = effect.getPercent();
        double flat = effect.getAmount() == null ? 0 : effect.getAmount();
        return switch (scale.trim()) {
            case "target_max_hp" -> target.getMaxHp() * share + flat;
            case "owner_max_hp" -> ownerAttributeOf(ctx, "owner_max_hp", AttributeType.HEALTH) * share + flat;
            // 三月七 100102: a shield of 「57% 防御力 + 760」 -- a share of the maker's DEFENCE plus a constant.
            case "owner_def" -> ownerAttributeOf(ctx, "owner_def", AttributeType.DEFENCE) * share + flat;
            default -> throw new IllegalStateException(
                    "Scale '" + scale + "' passed validation but has no implementation");
        };
    }

    /**
     * One attribute of the rule owner, for the {@code owner_*} scales of a heal or shield.
     *
     * <p>⚠ A missing owner, or an owner without that attribute, is a loud failure rather than 0: a shield of
     * 「some share of nothing」 would be a wrong number with no symptom.
     */
    private static double ownerAttributeOf(TriggerContext ctx, String scale, AttributeType attribute) {
        if (ctx.owner() == null) {
            throw new IllegalStateException(
                    "Op uses scale '" + scale + "' but the context has no owner to read it from");
        }
        DoubleValue value = ctx.owner().getAttribute(attribute);
        if (value == null) {
            throw new IllegalStateException(
                    "Op uses scale '" + scale + "' but " + ctx.owner().getName() + " has no " + attribute);
        }
        return value.get();
    }

    /**
     * Settles a {@code DISPEL} effect: removes up to {@code amount} <b>negative effects</b> from the target —
     * 「解除 N 个负面效果」.
     *
     * <p>What counts as negative is {@code AbstractBuff.isDebuff()}, i.e. a decision taken per buff class rather
     * than a guess made here: a DOT, a control, 易伤, 嘲讽 and a stat <i>debuff</i> are negative; 减伤, a speed
     * boost and a named state are not. That is why this op needs no filter of its own — and why it cannot be
     * tricked into removing a shield.
     *
     * <p>Nothing to dispel is <b>not</b> an error, for the same reason as {@code REMOVE_STACK}: the shape
     * 「受到攻击时，解除自身 1 个负面效果」 fires on every hit, including the ones where nothing is on you.
     *
     * @param effect the effect ({@code amount}, optional {@code target})
     * @param ctx    the context
     */
    /**
     * {@code REMOVE_STATE}: takes the named state off every resolved target — the "only the newest one holds it"
     * half of 星期日's 【蒙福者】.
     *
     * <p>⚠ Removing a state that is not there is <b>not</b> an error: an "only the latest target" rule runs on every
     * cast, and most of those casts find the state on somebody (or nobody) whose removal is simply nothing to do.
     * The op that <i>applies</i> a state to a unit that does not exist is loud ({@code "target": "summon"}); this one
     * has no such unit to miss.
     */
    /**
     * {@code TAUNT}: puts the engine's {@link TauntBuff} on every resolved target — 「使目标陷入嘲讽状态，持续1回合」.
     *
     * <p>The buff is a pure marker, and the constraint it carries is a <b>hard one</b> implemented at target
     * selection (`TargetSelector`): as long as it is attached to a living unit, single-target attacks and the centre
     * of a blast attack can only pick that unit. ⚠ That is the 「嘲讽」 the corpus asks for (云璃's ultimate puts it
     * on every enemy, 千冶·刃's skill on one) and it is deliberately NOT the same thing as 「被敌方攻击的概率提高」
     * (三月七/杰帕德/玲可), which is a soft weight — that sentence carries <b>no number</b> in any document, so it is
     * registered as a data gap rather than guessed at here.
     */
    private static void taunt(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            // ⚠ Through the resist pipeline like every other negative state: 「使目标陷入嘲讽状态」 states no
            // probability, which means a 100% BASE chance — the game still runs it through 效果命中 / 效果抵抗. Writing
            // the marker directly made the engine's taunt unconditionally certain, which no document says.
            battle.tryApplyDebuff(ctx.owner(), target, withSource(new TauntBuff(effect.getTurns()), ctx), 1.0, null);
        }
    }

    /**
     * {@code APPLY_CONTROL}: 「有 50% 基础概率使敌方目标陷入冻结状态，持续1回合」 — a named control state with a
     * duration, and (optionally) a <b>base chance</b> that runs through the real 效果命中 / 效果抵抗 pipeline.
     *
     * <p><b>What the state does is the engine's business, not the rule's.</b> 冻结 blocks acting; 纠缠 and 禁锢
     * slow instead — those three facts live in {@code Constant.CONTROL_STATES} (one entry per state, with the
     * resistance key the roll needs), and the rule states only <i>which</i> state, for how long, and how likely.
     * A rule cannot say 「冻结但随便」, and a misspelled state is refused at load time rather than attaching
     * nothing.
     *
     * <p><b>Why the roll is per target.</b> A base chance is rolled for each victim — three enemies can see three
     * different outcomes, which is what 「每个目标各有 50% 概率」 means and what the rule-level {@code chance}
     * (one roll per firing) could not express. It goes through {@link Battle#tryApplyDebuff}, the same entry point
     * the enemy-side debuff path uses, so the applier's 效果命中, the victim's 效果抵抗 and its per-state
     * resistance ({@code STAT_CTRL_Frozen} — the reason {@code ControlEffect.resistKey} exists) all apply.
     *
     * <p>⚠ The control's <b>per-turn damage</b> (「冻结状态下…每回合开始时受到等同于三月七60%攻击力的冰属性附加
     * 伤害」) is <b>not</b> written by this op yet: it needs an "attach a damage-over-time" op of its own, and that
     * is registered rather than folded in here as a field nobody else can use. What this op attaches is the state
     * itself, so 「不能行动」 is complete.
     */
    private static void applyControl(Battle battle, EffectSpec effect, TriggerContext ctx) {
        Constant.ControlEffect control = Constant.CONTROL_STATES.get(effect.getControl().trim());
        if (control == null) {
            // Load-time validation already refused this; reaching here means the table changed under a compiled
            // rule, which is an engine fault rather than a content one.
            throw new IllegalStateException(
                    "APPLY_CONTROL ran with the unknown state '" + effect.getControl() + "'; the loader validates "
                            + "against Constant.CONTROL_STATES, so this is an engine fault");
        }
        // ⚠ ALWAYS through the resist pipeline, even with no `base_chance` stated: 「使目标陷入冻结状态」 with no
        // probability in the text is a 100% BASE chance, which the game still runs through 效果命中 / 效果抵抗 / the
        // state's own resistance. Attaching it directly would have made every 「免疫控制类负面状态」 clause silently
        // ineffective against exactly the controls the documents write without a number.
        double baseChance = effect.getBaseChance() == null ? 1.0 : effect.getBaseChance();
        // …plus whatever a 行迹 / 星魂 raised it by (「冻结敌方目标的基础概率提高15%」), filed on this combatant under
        // THIS rule's id. Read here rather than written into the effect: rules are compiled once and cached per cid,
        // so the stated 0.5 stays the file's number and the amendment is a fact about this battle.
        if (ctx.owner() != null) {
            baseChance += ctx.owner().ruleBaseChanceBonus(ctx.ruleId());
        }
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // The state's own per-turn damage (「冻结状态下…每回合开始时受到…冰属性附加伤害」) rides WITH the
            // state: attached in ControlBuff.applyEffect, taken off in its removeBuff, and only ever created on
            // the units the roll let through. ⚠ The magnitude is read here, once, and frozen into the DOT.
            ControlBuff applied = effect.getElement() == null || effect.getElement().isBlank()
                    ? new ControlBuff(control, effect.getTurns())
                    : new ControlBuff(control, effect.getTurns(), DamageElement.fromString(effect.getElement()),
                            dotMagnitude(effect, ctx));
            applied.setSource(ctx.owner());
            // ⚠ The roll's RESULT is recorded as well as used: 「每冻结1个目标」 counts the targets this cast really
            // froze, so a resisted application must not be counted (see Battle.recordCastApplied).
            if (battle.tryApplyDebuff(ctx.owner(), target, applied, baseChance, control.resistKey())) {
                battle.recordCastApplied(control.name());
            }
        }
    }

    /**
     * {@code APPLY_DOT}: 「使目标陷入灼烧状态，每回合造成等同于…#1[i]%攻击力的火属性伤害」 — a damage-over-time
     * attached by a <b>rule</b>.
     *
     * <p>Before this op only a weakness break could attach one ({@code Battle.attachBreakDot}), so the whole
     * 「使目标陷入灼烧/触电/裂伤/风化状态」 family (11 of the 97 documents) had no spelling — and 三月七's frozen
     * enemies took no 「每回合冰属性附加伤害」.
     *
     * <p><b>What the rule states.</b> The element (which picks the RES zone and doubles as the state's identity:
     * Fire is 灼烧), the magnitude (flat, or a share of one of the <b>rule owner's</b> attributes) and the turns.
     * The damage is computed <b>once, when it lands</b>, and frozen into the buff — 「等同于三月七60%攻击力」
     * means her attack at that moment, not a live link to her panel (the same snapshot rule every derived value
     * follows).
     *
     * <p>A {@code base_chance} rolls per target through the same pipeline as a control ({@link Battle#tryApplyDebuff}),
     * with no specific-resistance key: the data's {@code STAT_*} resistances are per <i>state</i>, and a DOT's
     * state is its element — which the four-element family has no key for. (That is a fact about the data, not a
     * shortcut: adding one later is passing a key here.)
     */
    private static void applyDot(Battle battle, EffectSpec effect, TriggerContext ctx) {
        DamageElement element = DamageElement.fromString(effect.getElement());
        if (element == null) {
            throw new IllegalStateException(
                    "APPLY_DOT ran with element '" + effect.getElement() + "'; the loader validates it against "
                            + "DamageElement, so this is an engine fault");
        }
        double damage = dotMagnitude(effect, ctx);
        // ⚠ The same "always roll" rule as a control: an unstated chance is 100% BASE chance, not "bypasses 效果抵抗".
        double baseChance = effect.getBaseChance() == null ? 1.0 : effect.getBaseChance();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            DotBuff dot = new DotBuff(ctx.owner(), element, damage, effect.getTurns());
            if (battle.tryApplyDebuff(ctx.owner(), target, dot, baseChance, null)) {
                // The DOT's state name is the document's name for the element (灼烧), from the one table that maps
                // them — the same spelling 「每使1个目标陷入灼烧」 would count with.
                battle.recordCastApplied(BuffManager.dotStateName(element));
            }
        }
    }

    /**
     * How much damage one tick of a rule's DOT deals: the flat {@code amount}, or {@code percent} × one of the
     * rule owner's own attributes (+ the optional constant {@code amount}).
     *
     * <p>Shared by {@code APPLY_DOT} and by {@code APPLY_CONTROL}'s per-turn payload, so the two cannot drift —
     * and both read the <b>rule owner</b>, never the victim.
     */
    private static double dotMagnitude(EffectSpec effect, TriggerContext ctx) {
        if (effect.getScale() == null) {
            return effect.getAmount();
        }
        return derivedMagnitude(effect, ctx);
    }

    /**
     * {@code EXTEND_BUFF}: 「…的持续时间增加1回合」 — lengthen the buffs the rule's <b>owner</b> has already put on
     * each resolved target.
     *
     * <p><b>Why the op names no buff.</b> Every sentence in this family identifies it by its origin instead:
     * 「<b>战技提供的</b>护盾持续时间增加1回合」 (三月七 加护), 「<b>战技对指定我方目标造成的</b>伤害提高效果的持续时间增加1回合」
     * (布洛妮娅), 「<b>天赋使敌方目标陷入的</b>风化状态的持续时间延长1回合」 (桑博), 「对于<b>已拥有</b>【生息】的我方目标…延长1回合」
     * (白露). The engine already records that fact — {@code AbstractBuff.source}, the applier — and the filter is
     * exact, so the author states the duration and nothing else. 10 of the 97 documents use one of the two
     * phrasings.
     *
     * <p>⚠ <b>Folding the +1 into the ability it lengthens is the wrong fix</b>, and this op exists so that it is
     * not tempting: the trace's own line would vanish from the data, and the base ability would state a duration
     * that is not its own. The two numbers belong to two rules because they are two sentences (and a trace could
     * one day be gated on its own).
     *
     * <p>Nothing to lengthen is <b>not</b> an error: the rule fires whenever its event happens (usually the same
     * cast that applied the buff), and "the target carries nothing of mine" is an ordinary empty case.
     */
    private static void extendBuff(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            target.getBuffManager().extendBuffsFrom(ctx.owner(), effect.getBuff(),
                    effect.getAttribute() == null ? null : AttributeType.fromString(effect.getAttribute()),
                    effect.getTurns());
        }
    }

    /**
     * {@code MODIFY_RULE}: 「天赋的反击效果每回合可触发的次数增加1次」 / 「冻结敌方目标的基础概率提高15%」 — a passive that
     * <b>raises a number on another rule of the same file</b>.
     *
     * <p><b>Why it is not a second rule.</b> Both sentences modify something that already exists, and the honest
     * spellings are impossible without saying so: a second {@code per_turn: 3} rule <i>adds</i> firings (the engine
     * would fire 2 + 3 = 5 times a turn rather than raising the cap to 3), and a second {@code base_chance: 0.65}
     * rule would roll <b>twice</b> (1 − 0.5 × 0.35 = 82.5% instead of 65%). Both are numbers that look right from the
     * outside and are wrong in play — the failure mode this project refuses.
     *
     * <p><b>Where the raised number lives.</b> On the <b>combatant</b>, not on the rule: a table is compiled once per
     * cid and a relic's rules are shared by every wearer, so an amendment written onto the rule would leak into every
     * battle in the JVM (the same reasoning as the firing limits in {@code CanHit}). The two consumers are
     * {@code TriggerInterpreter.fire} (per-turn cap) and {@link #applyControl} (base chance) — both read the amendment
     * only when the rule they are running carries the id it was filed under.
     *
     * <p>⚠ Which number moves is decided by <b>which field is stated</b>: {@code amount} raises a count, {@code
     * percent} raises a probability. The load-time validator refuses both-at-once, neither, an out-of-range value, a
     * non-integer count, any other field, a non-{@code BATTLE_START} event, an unknown rule id, and a named rule that
     * does not state the number being raised.
     */
    /**
     * {@code RAISE_SKILL_LEVEL}: 「战技等级+1」「终结技等级+1」 (M-32) — this battle reads one skill slot one level higher.
     *
     * <p><b>Why an op and not a rule field.</b> The level is not a property of this rule: it changes what <b>another</b>
     * ability reads out of its parameter table (the skill's own execution in {@code SkillExecutor}, a rule-driven
     * {@code DAMAGE}, and a {@code COMMAND_SUMMON} swing). All three go through the one resolver
     * {@link CanHit#skillLevel}, which is the only place the base level and the raises are added together.
     *
     * <p>⚠ <b>Per battle, not per character</b> (see {@code CanHit.skillLevelBonus}): the same {@code Character} can be
     * put into a second battle by a stage, so writing the raised level onto the skill would stack once per battle and
     * never come off — the same leak the {@code MODIFY_RULE} amendments were moved off the rule for.
     *
     * <p>⚠ <b>{@code BATTLE_START} only</b>, like {@code MODIFY_RULE}: a level raise is a passive fact of the loadout,
     * and a raise applied mid-battle would have to be taken back at a point nobody states.
     */
    /**
     * {@code START_COUNTDOWN}: 「行动序列上出现【协奏】倒计时…倒计时固定拥有 90 点速度」 (M-49).
     *
     * <p>Its turn is announced as {@link TriggerEvent#COUNTDOWN_TURN}, and the content answers with vocabulary it
     * already has (`REMOVE_STATE` + `EXTRA_TURN self` for 「退出【协奏】状态并立即行动」) — so this op does one thing only,
     * which is what keeps the countdown reusable for every other 倒计时 sentence in the corpus.
     *
     * <p>⚠ The name comes from the rule's own {@code buff} when it states one (it is what the log shows, and nothing
     * reads it back, so it needs no id).
     */
    /**
     * {@code APPLY_REGEN}: attaches a {@link com.laosun.aluminium.models.buff.RegenBuff}, whose amount is derived once
     * (the applier's panel) and settled on each of the carrier's turns by {@code Battle.tickRegens}.
     *
     * <p>⚠ Its name is required, and not for bookkeeping: 「施放战技产生的持续回复效果延长1回合」 (1105 行迹 调理) and any
     * removal by name reach it through that string, exactly as a 遗器 set's temporary buff does.
     */
    private static void applyRegen(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String name = effect.getBuff() == null ? "" : effect.getBuff().trim();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // ⚠ grantAmount, not derivedMagnitude: the regeneration's magnitude is a HEAL amount, so it shares the
            // heal/shield scale vocabulary and is worked out per carrier (a 	arget_max_hp regeneration is a different
            // number for each one). The value is derived once, here, and frozen in the buff.
            double amount = grantAmount(effect, target, ctx);
            AbstractBuff regen = withSource(withLifetime(
                    new com.laosun.aluminium.models.buff.RegenBuff(ctx.owner(), name, amount, effect.getTurns()),
                    effect), ctx);
            target.getBuffManager().addBuff(regen);
        }
    }

    /**
     * {@code ADD_STACK}: 「每当我方目标对【承负】状态下的敌方目标施放 2 次普攻/战技/终结技后…」 (2026-09-28) — one more mark on a
     * named counter.
     *
     * <p><b>Why an op and not a modifier.</b> A counter must not touch the panel: a {@code MODIFY_ATTR} with a zero
     * magnitude would be a lie about what the buff is, and picking some attribute to hang it on would be worse. So the
     * carrier is {@link com.laosun.aluminium.models.buff.StackBuff}: a buff with a name, a lifetime and no effect.
     *
     * <p>⚠ <b>Capped by {@code max_stacks}</b> (default 1, i.e. a plain flag): 「2 次」 is a threshold the content reads
     * with {@code target_stacks:<name> >= 2}, and without a cap a stray extra event would push the count past it and
     * the rule that resets it would fire twice in a row.
     */
    private static void addStack(Battle battle, EffectSpec effect, TriggerContext ctx) {
        int cap = effect.stackCap() == null ? 1 : effect.stackCap();
        String name = effect.getBuff().trim();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            if (target.getBuffManager().stacksOf(name) >= cap) {
                continue;                       // 「2 次」 is a threshold, not an invitation to keep counting
            }
            boolean permanent = unticked(effect);
            AbstractBuff stack = new com.laosun.aluminium.models.buff.StackBuff(
                    name, permanent ? 1 : effect.getTurns(), permanent, cap);
            stack = withLifetime(stack, effect);
            stack = withTickOwner(stack, effect, ctx);
            stack = withSource(stack, ctx);
            target.getBuffManager().addBuff(stack);
        }
    }

    private static void startCountdown(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String name = effect.getBuff() == null || effect.getBuff().isBlank()
                ? (ctx.owner() == null ? "countdown" : ctx.owner().getName() + " 倒计时")
                : effect.getBuff().trim();
        battle.startCountdown(ctx.owner(), name, effect.getSpeed());
    }

    /**
     * Refuses every field a {@code START_COUNTDOWN} effect does not read.
     */
    private static void rejectCountdownExtras(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() != null || effect.getScale() != null || effect.getTurns() != null
                || effect.getAttribute() != null || effect.getElement() != null
                || effect.getControl() != null || effect.getBaseChance() != null
                || effect.getKind() != null || effect.getStacks() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null
                || effect.getRule() != null || effect.getSkill() != null
                || effect.getResource() != null || effect.getAmount() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getTicksOn() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " places one countdown and states nothing else: \"speed\" (its fixed speed) and "
                            + "optionally \"buff\" (the name it shows in logs) "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    private static void raiseSkillLevel(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null) {
            throw new IllegalStateException(
                    "RAISE_SKILL_LEVEL ran without a rule owner, so there is no combatant whose skill to raise; this "
                            + "is an engine fault (the op is validated onto BATTLE_START, which always has one)");
        }
        owner.raiseSkillLevel(SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT)),
                (int) Math.round(effect.getAmount()));
    }

    /**
     * Exactly <b>one</b> amendment per {@code MODIFY_RULE}: a firing count, a probability, a value or a duration.
     *
     * <p>⚠ Stated rather than inferred: two at once would make "which number did the author mean" a guess, and the
     * guess would be a silent one (the extra field would simply be ignored).
     */
    private static void requireOneAmendment(EffectSpec effect, String op, TriggerSpec spec) {
        int stated = 0;
        for (Object candidate : new Object[] {effect.getAmount(), effect.getBaseChance(), effect.getPercent(),
                effect.getEffectPercent(), effect.getEffectTurns()}) {
            if (candidate != null) {
                stated++;
            }
        }
        if (stated != 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " must state exactly ONE amendment -- \"amount\" (firings per turn), \"base_chance\" "
                            + "(probability), \"effect_percent\" (raise a value) or \"effect_turns\" (raise a "
                            + "duration) -- but " + stated + " were stated (source: " + spec.getSource() + ")");
        }
    }

    private static void modifyRule(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null) {
            throw new IllegalStateException(
                    "MODIFY_RULE ran without a rule owner, so there is no combatant to file the amendment under; this "
                            + "is an engine fault (the op is validated onto BATTLE_START, which always has one)");
        }
        String target = effect.getRule().trim();
        if (effect.getAmount() != null) {
            owner.addRulePerTurnBonus(target, (int) Math.round(effect.getAmount()));
        } else if (effect.getEffectTurns() != null) {
            // 「终结技的持续时间额外增加 1 回合」: the rule's own effect durations, raised where they are read
            // (see amendedEffect). ⚠ Not an extra rule with a longer `turns`: the two modifiers of one skill are the
            // same kind, so a second one would REPLACE the first rather than stack.
            owner.amendRuleEffectTurns(target, (int) Math.round(effect.getEffectTurns()));
        } else if (effect.getEffectPercent() != null) {
            // 「天赋的伤害提高效果额外提高 10%」: 30% -> 40%, the same rule, raised in place.
            owner.amendRuleEffectPercent(target, effect.getEffectPercent());
        } else {
            owner.addRuleBaseChanceBonus(target, effect.getPercent());
        }
    }

    /**
     * {@code RESIST_DEBUFF}: 「抵抗<b>控制类</b>负面状态的概率提高35%」 / 「免疫<b>控制类</b>负面状态」 — the resolved targets
     * become harder to control (or burn), for the stated duration or for the whole battle.
     *
     * <p><b>Why it is a buff on the carrier.</b> 克拉拉's 守护 is a permanent trace, while 银狼LV.999's 【防火墻】 lasts
     * one turn; a buff covers both, expires by itself, and can be removed by name like any other state.
     *
     * <p>⚠ <b>What it does NOT do</b> is touch a specific state: the check lives in
     * {@link Battle#tryApplyDebuff}, which asks the victim for its resistance to the <b>class</b> the incoming state
     * declares ({@code AbstractBuff.debuffClass()}). So a control written tomorrow is covered by a resistance written
     * today — the property that makes 「免疫控制类负面状态」 mean the whole family rather than a list of keys.
     *
     * <p>⚠ {@code percent: 1} is immunity in this vocabulary, and that is deliberate: 「免疫控制类负面状态」 and
     * 「抵抗控制类负面状态的概率提高35%」 are the same mechanic at different strengths, and two spellings for one mechanic
     * is what the closed vocabularies exist to avoid.
     */
    private static void resistDebuff(Battle battle, EffectSpec effect, TriggerContext ctx) {
        DebuffClass kind = DebuffClass.fromString(effect.getKind());
        if (kind == null) {
            throw new IllegalStateException(
                    "RESIST_DEBUFF ran with kind '" + effect.getKind() + "'; the loader validates it against "
                            + "DebuffClass (" + String.join(" / ", DebuffClass.names())
                            + "), so this is an engine fault");
        }
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            AbstractBuff resist = withSource(withTickOwner(withLifetime(
                    new ClassResistBuff(kind, effect.getPercent(), turns, permanent), effect), effect, ctx), ctx);
            // the name is optional and only content that asks for it gets it (see `named`)
            target.getBuffManager().addBuff(named(resist, effect));
        }
    }

    /**
     * Gives a freshly built buff the <b>name the data stated</b> ({@code "buff": "协奏"}), when it states one.
     *
     * <p>⚠ Unnamed is the normal case and stays exactly as it was: {@code BuffManager.removeState} skips modifiers and
     * resistances with no name, so only the content that asks for a name gets the "removable by that name" lifetime.
     */
    private static <T extends AbstractBuff> T named(T buff, EffectSpec effect) {
        if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
            buff.setBuffName(effect.getBuff().trim());
        }
        return buff;
    }

    /**
     * Stamps a freshly built buff with <b>who applied it</b> ({@code AbstractBuff.source}).
     *
     * <p><b>Why every path does this now.</b> The source used to be set only where a constructor demanded it
     * ({@code DotBuff} needs it for kill credit, {@code ShieldBuff}/{@code ControlBuff} take it), so a
     * {@code StateBuff} or a stat modifier could be anonymous. {@code EXTEND_BUFF} filters by origin — 「战技提供的护盾」
     * means "the one <i>I</i> gave" — and an anonymous buff would make that filter silently extend nothing at all.
     * Recorded here once, for every op that creates a buff, rather than in each of them.
     */
    private static AbstractBuff withSource(AbstractBuff buff, TriggerContext ctx) {
        buff.setSource(ctx.owner());
        // …and WHICH RULE created it, so a condition can ask 「战技提供的护盾」 rather than 「三月七给的盾」 (see
        // AbstractBuff.ruleId). Stamped here, at the one place every buff-creating op already passes through.
        buff.setRuleId(ctx.ruleId());
        return buff;
    }

    private static void removeState(EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : resolveTargets(ctx.battle(), effect, ctx)) {
            target.getBuffManager().removeState(effect.getBuff());
        }
    }

    private static void dispel(Battle battle, EffectSpec effect, TriggerContext ctx) {
        int amount = (int) Math.round(effect.getAmount());
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            target.getBuffManager().removeDebuffs(amount);
        }
    }

    /**
     * The duration handed to a permanent modifier.
     *
     * <p>Deliberately {@code 1} and not a large number: {@link StatModifierBuff} marks the buff
     * permanent, and {@code BuffManager.processBuffTick} never counts a permanent buff down, so this
     * value is never read. It exists only because {@code AbstractBuff}'s constructor takes a turn count.
     * Spelling it as {@code Integer.MAX_VALUE} would suggest the engine relies on a big number, which
     * is exactly the misconception the flag removes.
     */
    private static final int UNBOUNDED_DURATION_PLACEHOLDER = 1;

    /**
     * The modifier a {@code MODIFY_ATTR} effect produces, as a buff or a debuff according to the sign.
     *
     * <p>See {@link #modifyAttr} for why a ratio attribute gets a flat modifier and a base attribute
     * gets an additive percentage. Splitting it out keeps the "which modifier kind" decision in one
     * readable place instead of a nested conditional at the call site.
     *
     * @param attribute the attribute to touch
     * @param percent   the magnitude; {@code < 0} produces a debuff
     * @param turns     how long it lasts (validated at load time; ignored when {@code permanent})
     * @param permanent {@code true} = "for the rest of the battle", i.e. never ticked
     * @param maxStacks how many copies may accumulate; {@code 1} = replace on re-application
     */
    private static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                                 boolean permanent, int maxStacks) {
        return statModifier(attribute, percent, turns, permanent, maxStacks, false);
    }

    /**
     * The same, for a magnitude that is already an <b>absolute</b> number in the attribute's own units.
     *
     * <p>See {@link #modifyAttr} for why a literal on a ratio attribute gets a flat modifier and a literal on a
     * base attribute gets an additive percentage — and why a <b>derived</b> magnitude ({@code scale}) is flat even
     * on a base attribute: 「提高数值等同于 X」 states a value, so treating it as "X% of the target's base" would
     * multiply the derived number by the target's own stat.
     */
    private static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                                 boolean permanent, int maxStacks, boolean absolute) {
        boolean debuff = percent < 0;
        // The sign is carried by `percent` itself (a negative value), which is what the original
        // percentBuff/flatBuff/… factories produced; only the modifier kind depends on the attribute.
        return StatModifierBuff.of(attribute, absolute || attribute.isPercent ? "pure" : "add_percent", percent,
                debuff ? "debuff" : "buff", turns, false, permanent, maxStacks);
    }

    /**
     * Validates the optional {@code scale} of a {@code MODIFY_ATTR}, and the flat {@code amount} that comes with it.
     *
     * <p>Two silent holes are closed here, both of the "the rule loads and does something other than what the file
     * says" kind:
     * <ul>
     *   <li>a {@code scale} this op cannot read (a {@code HEAL}/{@code SHIELD} scale such as {@code target_max_hp},
     *       or a typo) would otherwise be ignored, and the modifier would use {@code percent} as a literal — a
     *       number that has nothing to do with the sentence;</li>
     *   <li>an {@code amount} <b>without</b> a scale. It used to be ignored outright (nothing on this op read it),
     *       and it is exactly what an author writes for "plus a flat part"; now that it means that, the case it
     *       cannot mean is refused instead of being dropped.</li>
     * </ul>
     */
    private static void requireDerivedScale(EffectSpec effect, String op, TriggerSpec spec) {
        boolean derived = effect.getScale() != null && !effect.getScale().isBlank();
        if (!derived) {
            if (effect.getAmount() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"amount\" without a \"scale\": a plain modifier's magnitude is "
                                + "\"percent\", so the amount would be ignored. Either say what it is a share of "
                                + "(\"scale\": \"" + TriggerTable.SELF_ATTR_PREFIX + "BREAKING_EFFECT\" plus "
                                + "\"percent\") or write the number into \"percent\" "
                                + "(source: " + spec.getSource() + ")");
            }
            return;
        }
        // The spelling and (for the attribute family) the name; also re-checks `percent`, because a scale with no
        // magnitude is not a number ("some share of my Speed" says nothing about how much).
        scaleAttribute(effect, op, spec);
        requirePercent(effect, op, spec);
    }

    /**
     * Validates an effect's optional {@code target} selector <b>at load time</b>.
     *
     * <p>This closes a silent-typo hole: the resolver used to fall back to "the owner" for anything
     * it did not recognise, so {@code "target": "atacker"} behaved exactly like {@code "self"} —
     * the rule fired, nothing was reported, and the character just quietly did the wrong thing. The
     * condition variables are already a closed set for the same reason; the selector is now too.
     *
     * <p>Absent means {@code "self"}, which is the common case and stays implicit so the JSON can
     * stay terse.
     */
    private static void requireTargetSelector(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTarget() == null || effect.getTarget().isBlank()) {
            return;
        }
        String selector = normalizeTarget(effect);
        if (!TARGET_SELECTORS.contains(selector)) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown \"target\" selector '" + effect.getTarget()
                            + "' (known: " + String.join(" / ", TARGET_SELECTORS.stream().sorted().toList())
                            + "); it used to fall back to the owner, which made a typo behave like self "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    private static void requireAmount(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"amount\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an amount is present <b>and positive</b>.
     *
     * <p>Used by the ops where zero is not a legal no-op but a silent bug: {@code REMOVE_STACK amount: 0}
     * (or a negative) would load happily and remove nothing, which is the "the rule fires and nothing
     * happens" symptom this project keeps closing. Ops where a zero amount is meaningful on purpose (a
     * {@code HEAL 0} a content author might use as a placeholder) keep using {@link #requireAmount}.
     */
    private static void requirePositiveAmount(EffectSpec effect, String op, TriggerSpec spec) {
        requireAmount(effect, op, spec);
        if (effect.getAmount() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a positive \"amount\" but has " + effect.getAmount()
                            + "; a removal of 0 would load and silently do nothing "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    private static void requirePercent(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"percent\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an effect does <b>not</b> claim a duration.
     *
     * <p>Used by the ops whose effect is over the moment it is applied ({@code BOOST_DAMAGE} changes one damage
     * instance). A {@code turns} written there would be silently ignored by Gson and by the interpreter — the
     * author would see a rule that loads and behaves as if the field were not there.
     */
    private static void requireNoDuration(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTurns() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " has no duration (it acts on a single moment), but states \"turns\": "
                            + effect.getTurns() + " (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent())) {
            // The same hole as `turns`, one field over: `permanent` would be read, validated, and then ignored,
            // so the author would believe a permanent effect was installed. Only ops that create a buff may say
            // it, and those do not come through here.
            throw new IllegalArgumentException(
                    "Op " + op + " creates no buff, so \"permanent\" has nothing to keep alive; it acts on a "
                            + "single moment (source: " + spec.getSource() + ")");
        }
        if (eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " creates no buff, so it has nothing an \"until\" could end; it states "
                            + "\"until\": " + spellUntil(effect) + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an effect is <b>not</b> given a {@code target}.
     *
     * <p>Used by ops that act on a unit no selector could name — {@code SUMMON} brings out the rule owner's
     * own memosprite, so a {@code target} there would be read by Gson, validated by
     * {@link #requireTargetSelector} (it is a real selector) and then <b>ignored</b>: the author would get a
     * rule that loads, fires and does something other than what the file says.
     *
     * <p>⚠ The same hole exists for the ops that credit the owner by definition ({@code GAIN_ENERGY},
     * {@code GAIN_SKILL_POINT}, {@code BOOST_DAMAGE}, …): they ignore a stray {@code target} today. No shipped
     * rule does that (checked 2026-09-27), so it is latent rather than live, and it is registered rather than
     * fixed here because widening the guard would change the accepted vocabulary of seven ops at once.
     */
    private static void requireNoTarget(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getTarget() != null && !effect.getTarget().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not take a \"target\" (it acts on the rule's own owner), but states "
                            + "\"target\": \"" + effect.getTarget() + "\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an effect is only used on the event that can carry what it needs.
     *
     * <p>The "fail at load, not mid-battle" rule again: {@code BOOST_DAMAGE} changes the damage instance being
     * settled, and only {@link TriggerEvent#DEALING_DAMAGE} hands one over. A rule on any other event would
     * load, fire, and have nothing to change — so it is refused while the file is read.
     */
    /**
     * Validates that an op is on the one event that hands it what it acts on.
     *
     * <p>The "fail at load, not mid-battle" rule again: {@code BOOST_DAMAGE} changes the damage instance being
     * settled and only {@link TriggerEvent#DEALING_DAMAGE} hands one over; {@code DELEGATE_DAMAGE} changes the cast
     * being set up and only {@link TriggerEvent#CAST_SETUP} hands one over. A rule on any other event would load,
     * fire, and have nothing to change — so it is refused while the file is read.
     */
    private static void requireEvent(TriggerSpec spec, String op, TriggerEvent expected) {
        TriggerEvent actual = TriggerEvent.fromString(spec.getOn());
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Op " + op + " only means something on " + expected.value() + ": that is the event that hands "
                            + "over what it changes. This rule is on " + spec.getOn()
                            + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that a percentage is present and <b>does something</b>.
     *
     * <p>For {@code MODIFY_DAMAGE_TAKEN} zero is the trap: it would attach a buff that multiplies nothing,
     * so the rule would load, fire and be invisible. The sign is meaningful (positive = vulnerability,
     * negative = reduction), so only zero is refused.
     */
    private static void requireNonZeroPercent(EffectSpec effect, String op, TriggerSpec spec) {
        requirePercent(effect, op, spec);
        if (effect.getPercent() == 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"percent\": 0, which would attach a buff that changes nothing; the "
                            + "magnitude is the point (positive = 受到的伤害提高, negative = 受到的伤害降低) "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the {@code attribute} name of a {@code MODIFY_ATTR} / {@code COMMAND_SUMMON} effect
     * <b>at load time</b>.
     *
     * <p>The "fail at load, not mid-battle" rule (the same one behind the op vocabulary) applies to
     * arguments too: a misspelled attribute must be reported when the table is read, not when the
     * character finally takes the action that fires the rule.
     *
     * <p>The four builder-only {@code *_PERCENT} variants are rejected here as well, because
     * {@link com.laosun.aluminium.models.CanHit#getAttribute(AttributeType)} returns {@code null}
     * for them: targeting one would raise a {@code NullPointerException} in the middle of a battle
     * instead of "this attribute cannot hold a buff".
     */
    private static void requireAttribute(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() == null || effect.getAttribute().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"attribute\" (e.g. ATTACK / DEFENCE / SPEED / CRIT_ATTACK) "
                            + "(source: " + spec.getSource() + ")");
        }
        AttributeType attribute;
        try {
            attribute = AttributeType.fromString(effect.getAttribute());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown attribute '" + effect.getAttribute()
                            + "' (source: " + spec.getSource() + ")");
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "Op " + op + " cannot use '" + effect.getAttribute()
                            + "': it is a builder-only input key, not a runtime attribute, so reading it "
                            + "mid-battle would fail (or silently do nothing). Name the base attribute "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates that an {@code APPLY_BUFF} effect names the state it applies <b>at load time</b>.
     *
     * <p>Same discipline as {@link #requireAttribute}: a missing {@code buff} would otherwise reach
     * {@code StateBuff}, whose constructor rejects it — but that would be a mid-battle exception in the
     * middle of a turn, instead of "this rule file is wrong" while it is being read.
     */
    private static void requireBuff(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getBuff() == null || effect.getBuff().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"buff\" (the state name as the rule text spells it, e.g. "
                            + "\"协奏\") (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates how long the buff lasts — <b>exactly one</b> of {@code turns}, {@code permanent: true} and
     * {@code until}.
     *
     * <p>There is deliberately no default: an omitted duration would be either "forever" (wrong: most
     * buffs in this game expire) or a number this class invented. Making the author write it is the
     * same call as {@code damage_param} having no default.
     *
     * <p>Stating <b>two</b> is rejected for the same reason a typo is: the answers disagree, and
     * silently letting one win would produce a rule that does not do what its text says. That includes
     * {@code until} + {@code turns} — the one expires on an event, the other on a turn boundary.
     */
    private static void requireDuration(EffectSpec effect, String op, TriggerSpec spec) {
        boolean permanent = Boolean.TRUE.equals(effect.getPermanent());
        boolean hasUntil = eventBound(effect);
        if (permanent && effect.getTurns() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " has both \"turns\" (" + effect.getTurns()
                            + ") and \"permanent\": true; they are two different durations and only "
                            + "one may be stated (source: " + spec.getSource() + ")");
        }
        if (hasUntil && (effect.getTurns() != null || permanent)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"until\": " + spellUntil(effect) + " together with "
                            + (permanent ? "\"permanent\": true" : "\"turns\": " + effect.getTurns())
                            + "; \"until\" IS the duration (the buff ends when its owner does that), so the "
                            + "other one has to go (source: " + spec.getSource() + ")");
        }
        if (hasUntil) {
            // Every name is checked, not just the first: 「普攻或战技」 written as a list must not let a typo in
            // the second entry load (the buff would then end on one event while the file claims two).
            for (String name : effect.getUntil()) {
                String normalized = name.trim().toLowerCase(Locale.ROOT);
                if (!LIFETIMES.contains(normalized)) {
                    throw new IllegalArgumentException(
                            "Op " + op + " has unknown \"until\": '" + name + "' in " + spellUntil(effect)
                                    + "; known lifetimes are "
                                    + String.join(", ", LIFETIMES.stream().sorted().toList())
                                    + " (source: " + spec.getSource() + ")");
                }
            }
            return;
        }
        if (permanent) {
            return;
        }
        if (effect.getTurns() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"turns\" (how long the buff lasts), \"permanent\": true (until "
                            + "the battle ends) or \"until\" (until its owner attacks, e.g. "
                            + "\"next_attack\"); there is no default (source: " + spec.getSource() + ")");
        }
        if (effect.getTurns() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive \"turns\" (" + effect.getTurns()
                            + "); such a buff would expire before it could do anything. Use "
                            + "\"permanent\": true for an effect with no turn limit "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * The lifetimes {@code "until"} accepts. Closed on purpose, like every other vocabulary here: an author
     * writing {@code "next_atack"} must hear about it while the file is read, not by watching a buff that
     * quietly never expires.
     */
    private static final Set<String> LIFETIMES = Set.of("next_attack", "next_skill", "next_ultimate");

    /**
     * Validates the optional stack cap of a {@code MODIFY_ATTR} effect <b>at load time</b>.
     *
     * <p>Same discipline as the rest of the vocabulary ("reject loudly at load, naming the phase"): a
     * cap of {@code 0} would create a modifier that never applies, a negative one is meaningless, and a
     * misspelled argument name would otherwise be ignored by Gson and quietly leave the rule
     * non-stacking — the exact "rule fires, nothing accumulates" symptom the author would never notice.
     *
     * <p>Also rejects the two spellings being stated at once, and any use of the argument on an op that
     * has no stacking concept.
     */
    private static void requireStackCap(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getMaxStacks() != null && effect.getStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states both \"max_stacks\" and its alias \"stacks\"; use one "
                            + "(source: " + spec.getSource() + ")");
        }
        Integer cap = effect.stackCap();
        if (cap == null) {
            return;
        }
        if (cap <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a non-positive stack cap (" + cap
                            + "); such a modifier would never apply (source: " + spec.getSource() + ")");
        }
        if (cap > Constant.MAX_STACKS_LIMIT) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a stack cap of " + cap + ", above the engine's limit of "
                            + Constant.MAX_STACKS_LIMIT + " (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Rejects the stacking / "until the battle ends" arguments on ops that do not have those concepts.
     *
     * <p>A Gson field is simply {@code null} when the JSON omits it, which means an argument written on
     * the <b>wrong op</b> is silently ignored — the author sees the rule load and the effect never
     * stack. That is the same class of silent failure the closed op vocabulary exists to prevent, so the
     * arguments are checked here rather than being read only by {@code MODIFY_ATTR}.
     *
     * <p>⚠ The two arguments do <b>not</b> belong to the same set of ops, which is why they are checked
     * separately. {@code permanent} is a <i>duration</i>, and both buff-granting ops have one:
     * {@code MODIFY_ATTR} grants a modifier and {@code APPLY_BUFF} grants a state, and 「直到战斗结束」 is a
     * real duration for a state (镜流's 【转魄】 lasts the whole battle). {@code max_stacks} is a
     * <i>modifier</i> concept and stays exclusive to {@code MODIFY_ATTR}: two states of the same name refresh
     * each other rather than accumulating.
     */
    private static void requireNoStackArguments(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getMaxStacks() != null || effect.getStacks() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"max_stacks\"/\"stacks\"; only MODIFY_ATTR "
                            + "accumulates (source: " + spec.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent()) && !OPS_WITH_DURATION.contains(op)) {
            throw new IllegalArgumentException(
                    "Op " + op + " does not support \"permanent\"; only "
                            + String.join(" / ", OPS_WITH_DURATION.stream().sorted().toList())
                            + " has a duration (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Settles a {@code DAMAGE} effect: a hit whose shape, element and multiplier all come from the
     * skill data of a named slot (P8-3).
     *
     * <p>Why read the skill data instead of letting the data file carry a number: the attack is
     * <b>already defined</b> in {@code skills.json}. Clara's talent, for instance, says
     * "单体攻击 / Physical / 削韧 30 / params [1, 0.8, 0.1]" — everything except <i>when</i> to fire it.
     * The trigger table supplies the "when", so a follow-up attack stays a pure data rule rather than
     * a Java class with a duplicated multiplier.
     *
     * <p>Per the official definition, such a hit is settled as {@code ADDITIONAL} damage: it counts
     * toward the attacker's kill credit but "does not count as dealing 1 attack", so the target gains
     * no on-hit energy and no toughness is reduced. It is a {@code FOLLOW_UP} for the trigger tables
     * (that is the event the texts about 追加攻击 subscribe to) while the attack-level notification
     * {@code AttackEvent} is deliberately not raised for it, so it does not consume an
     * {@code "until": "next_attack"} buff — a derived hit is part of somebody else's attack, and letting it
     * announce one would recurse (see {@code AttackEvent} and M-27). {@code as_attack} is accepted for
     * future use but does not change that yet — the engine has one settlement path for supplementary hits.
     *
     * <p>⚠ Consequence worth knowing: the hit causes HP loss, which fires {@code HP_LOST} again. Two
     * characters who both counter each other therefore ping-pong until
     * {@code Battle.MAX_TRIGGER_DEPTH} stops it with an exception, rather than hanging.
     *
     * @param battle the running battle
     * @param effect the effect ({@code skill}, {@code damage_param}, optional {@code target})
     * @param ctx    the context
     */
    /**
     * {@code DAMAGE}: one extra damage instance, settled with the numbers of one of the owner's own skills.
     *
     * <p>The victim is handed in by the caller ({@link #applyOne} walks the resolved list), because the same
     * effect may reach a whole side: 「对敌方全体」 settles one instance per victim, and every number is read
     * from the skill's parameter row exactly as before.
     */
    private static void damage(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit victim) {
        CanHit attacker = ctx.owner();
        if (victim == null || victim.isDeath()) {
            return;
        }
        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = attacker.getSkills().get(slot);
        if (skill == null || skill.getData() == null) {
            throw new IllegalStateException(
                    attacker.getName() + " has no " + slot + " skill to fire a DAMAGE effect from");
        }
        // ⚠ A *rider* may legitimately read a row out of a skill that deals no damage of its own: 知更鸟's 【协奏】
        // adds 「1 次等同于其自身 120% 攻击力的物理属性附加伤害」 and that 120% lives in her ultimate's own parameter row,
        // while the ultimate itself is a Support skill. So a non-damaging slot is accepted **when the rule states the
        // element** -- that is the part the skill could not have supplied, and without it the instance would be
        // element-less, which is the silent hole this check was written for.
        boolean statesElement = effect.getElement() != null && !effect.getElement().isBlank();
        if (!skill.getData().getEffect().isDamaging() && !statesElement) {
            throw new IllegalStateException(
                    "DAMAGE effect points at " + slot + ", whose effect is "
                            + skill.getData().getEffect() + " rather than a damaging one; a rule that reads a number "
                            + "out of such a skill must state the \"element\" itself (the skill does not have one "
                            + "to lend)");
        }
        double multiplier = multiplierOf(skill, effect, attacker);
        double base = attacker.getAttribute(AttributeType.ATTACK).get() * multiplier;
        battle.applyAdditionalDamage(attacker, victim, elementOf(effect, skill), base,
                effect.getCritRate(), effect.getCritDamage());
    }

    /**
     * Reads the damage multiplier out of the skill's per-level parameter row.
     *
     * <p>The <b>row</b> is {@link EffectSpec#getDamageLevel()} when the rule states one, and the skill's own
     * level otherwise; the <b>column</b> is {@link EffectSpec#getDamageParam()}. Both are stated by the author
     * where the document's figure depends on them — see {@code damage_level}'s javadoc for why the engine's
     * default (level 1) is not the same thing as "the number in the text".
     *
     * @param skill  the skill
     * @param effect the effect naming the column (and optionally the row)
     * @return the multiplier for that row
     * @throws IllegalStateException when the row or the column falls outside the data
     */
    /**
     * The element of a rule-driven damage instance: the rule's own {@code element} when it states one, otherwise the
     * skill's (2026-09-28).
     *
     * <p>⚠ The rule's own spelling had to exist for 知更鸟's 【协奏】 rider: the number comes from her ultimate's row,
     * but her ultimate is a Support skill with <b>no element</b>, while the text says the damage is Physical. Reading
     * the skill's element there would have produced an element-less instance — and one that silently takes no element
     * bonus or resistance.
     */
    private static DamageElement elementOf(EffectSpec effect, Skill skill) {
        if (effect.getElement() != null && !effect.getElement().isBlank()) {
            return DamageElement.fromString(effect.getElement().trim());
        }
        return skill.getData().getElement();
    }

    private static double multiplierOf(Skill skill, EffectSpec effect, CanHit attacker) {
        var levels = skill.getData().getSkills();
        int level = effect.getDamageLevel() == null ? attacker.skillLevel(skill) : effect.getDamageLevel();
        int row = level - 1;
        if (row < 0 || row >= levels.size()) {
            throw new IllegalStateException(
                    "damage_level " + level + " is outside " + skill.getData().getSkillType()
                            + "'s parameter table (rows=" + levels.size()
                            + "); the rule states a row the skill's data does not have");
        }
        var params = levels.get(row);
        int index = effect.getDamageParam();
        if (index >= params.size()) {
            throw new IllegalStateException(
                    "damage_param " + index + " is outside this skill's parameter row (size="
                            + params.size() + ")");
        }
        return params.get(index);
    }

    /**
     * Validates the state an {@code APPLY_CONTROL} names, at load time, against the engine's closed table.
     *
     * <p>A control is a <b>known state</b>, not a free-form name: 冻结 is one thing (it blocks acting), and a rule
     * that misspelled it would attach nothing at all — the same silent-failure shape the target-selector and
     * condition-variable closed sets exist to close. The message lists the names so the author has the fix in hand.
     */
    private static void requireControl(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getControl() == null || effect.getControl().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"control\" (which state to apply; known: "
                            + String.join(" / ", Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + ") (source: " + spec.getSource() + ")");
        }
        if (!Constant.CONTROL_STATES.containsKey(effect.getControl().trim())) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the control state '" + effect.getControl() + "', which the engine does "
                            + "not know (known: "
                            + String.join(" / ", Constant.CONTROL_STATES.keySet().stream().sorted().toList())
                            + "); a misspelled state would attach nothing at all "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the duration of an {@code APPLY_CONTROL} (required and positive).
     *
     * <p>Required rather than defaulted: 「陷入冻结状态」 with no turn count would be a <b>permanent</b> lock —
     * a wrong mechanic that reads like a working rule, which is the one thing this vocabulary refuses. The
     * documents always state it (「持续1回合」).
     */
    private static void requirePositiveTurns(EffectSpec effect, String op, TriggerSpec spec) {
        Integer turns = effect.getTurns();
        if (turns == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"turns\" (how many of the victim's turns the state lasts, e.g. "
                            + "\"持续1回合\"); without it the control would never end "
                            + "(source: " + spec.getSource() + ")");
        }
        if (turns <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"turns\": " + turns + ", but a control has to last at least one of "
                            + "the victim's turns (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code base_chance} (基础概率) when stated: a fraction of 1, and above 0.
     *
     * <p>Absent means "always lands", which is how a rule with no roll is written — so {@code 0} is a mistake
     * rather than a way to spell "never" (a state that can never land is not a mechanic), and {@code 1} is legal
     * and simply means the pipeline still runs (effect hit rate can never make it <i>more</i> than certain).
     */
    private static void requireBaseChance(EffectSpec effect, String op, TriggerSpec spec) {
        Double chance = effect.getBaseChance();
        if (chance == null) {
            return;
        }
        if (!(chance > 0) || chance > 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"base_chance\": " + chance + ", but a base chance is a fraction of 1 "
                            + "(0.5 = 「50% 基础概率」); omit the field entirely for a state that always lands "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Refuses the magnitude family ({@code amount} / {@code scale} / {@code percent} / {@code attribute} /
     * {@code buff}) on an op that has no use for any of them.
     *
     * <p>Same reasoning as {@code requireNoTarget} and {@code requireNoStackArguments}: a field the interpreter
     * never reads is a rule that says one thing and does another (M-26). The message names the field, because
     * "which of my six fields was ignored" is otherwise a guessing game.
     */
    private static void requireNoMagnitudeArguments(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAmount() != null || effect.getScale() != null || effect.getPercent() != null
                || effect.getAttribute() != null || effect.getBuff() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a control state, its turns and an optional base chance; it has no "
                            + "\"amount\" / \"scale\" / \"percent\" / \"attribute\" / \"buff\" "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates a state's own per-turn damage ({@code element} + a magnitude) or its absence, on
     * {@code APPLY_CONTROL}.
     *
     * <p>Two shapes are refused rather than resolved:
     * <ul>
     *   <li>a magnitude with <b>no element</b> — a number nothing can attach as damage (the author probably meant
     *       a bare {@code APPLY_DOT}, which is a different op);</li>
     *   <li>an element with <b>no magnitude</b> — a DOT that would settle 0 damage every turn, which reads as a
     *       working rule and does nothing.</li>
     * </ul>
     * Everything else ({@code attribute} / {@code buff}) is refused too: this op reads neither (M-26's rule).
     */
    private static void requireStateDamage(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() != null || effect.getBuff() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a control state, its turns, an optional base chance and an optional "
                            + "per-turn damage ({element} + amount/scale/percent); it has no \"attribute\" / "
                            + "\"buff\" (source: " + spec.getSource() + ")");
        }
        boolean anyMagnitude = effect.getAmount() != null || effect.getScale() != null
                || effect.getPercent() != null;
        if (effect.getElement() == null || effect.getElement().isBlank()) {
            if (anyMagnitude) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a magnitude (amount/scale/percent) but no \"element\": there is "
                                + "nothing to attach that damage to. A state that deals no damage states neither; "
                                + "a damage-over-time with no control is a separate op, APPLY_DOT "
                                + "(source: " + spec.getSource() + ")");
            }
            return;
        }
        requireElement(effect, op, spec);
        requireDotMagnitude(effect, op, spec);
    }

    /**
     * Validates the element of a per-turn damage: one of {@code DamageElement}'s spellings.
     *
     * <p>An unknown name would otherwise become a DOT that applies to nothing — {@code DamageElement.fromString}
     * answers {@code null} for "Unknown" and for typos alike, so the check has to be here.
     */
    private static void requireElement(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getElement() == null || effect.getElement().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"element\" (which damage element the per-turn damage deals, e.g. "
                            + "\"Ice\" / \"Fire\" / \"Quantum\") (source: " + spec.getSource() + ")");
        }
        if (DamageElement.fromString(effect.getElement()) == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names element '" + effect.getElement() + "', which is not a DamageElement "
                            + "spelling (e.g. Ice / Fire / Wind / Thunder / Physical / Quantum / Imaginary); an "
                            + "unknown element would attach nothing (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates how much damage each of the victim's turns takes: a flat {@code amount}, or a derived value
     * ({@code scale} + {@code percent}, optionally plus {@code amount} as a constant term), or neither-and-both
     * is refused.
     *
     * <p>⚠ Derived from the <b>rule owner</b> ({@code self_attr:<ATTRIBUTE>} / {@code self_max_energy}) and
     * computed when the effect lands, then frozen into the DOT — the same 「触发时算一次」 semantics the other
     * derived values follow (a later buff on the applier must not change damage that is already burning).
     */
    private static void requireDotMagnitude(EffectSpec effect, String op, TriggerSpec spec) {
        boolean derived = effect.getScale() != null;
        if (!derived) {
            if (effect.getAmount() == null) {
                throw new IllegalArgumentException(
                        "Op " + op + " requires a magnitude for its per-turn damage: either \"amount\" (a flat "
                                + "number) or \"scale\" + \"percent\" (a share of one of the rule owner's own "
                                + "attributes, e.g. \"self_attr:ATTACK\" with 0.6) "
                                + "(source: " + spec.getSource() + ")");
            }
            if (effect.getPercent() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"percent\" without \"scale\": a percent is a share of *something*, "
                                + "and the something is the scale (source: " + spec.getSource() + ")");
            }
            return;
        }
        // The spelling, and (for the attribute family) the name: `scaleAttribute` is the one reader of that
        // vocabulary, shared with MODIFY_ATTR's derived value so the two cannot drift.
        scaleAttribute(effect, op, spec);
        requirePercent(effect, op, spec);
    }

    /**
     * Refuses the arguments {@code APPLY_DOT} has no use for ({@code attribute} / {@code buff} / {@code skill} /
     * {@code damage_param}).
     *
     * <p>Same reasoning as {@code requireNoTarget} / {@code requireNoStackArguments}: a field the interpreter never
     * reads is a rule that says one thing and does another (M-26).
     */
    private static void requireNoMalformedArguments(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getAttribute() != null || effect.getBuff() != null || effect.getSkill() != null
                || effect.getDamageParam() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes an element, a magnitude and a duration; it has no \"attribute\" / "
                            + "\"buff\" / \"skill\" / \"damage_param\" (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates the <b>one</b> filter {@code EXTEND_BUFF} needs: {@code "buff"} (a state's name, or 护盾) or
     * {@code "attribute"} (the attribute a modifier sits on).
     *
     * <p>⚠ <b>Exactly one, and it is required.</b> Without a filter the op would mean "lengthen everything I have on
     * that unit", and that is a wrong number waiting to happen: 布洛妮娅's DEFENCE trace buff from {@code BATTLE_START}
     * is still ticking when she casts her Skill, so her 星魂 6 would silently lengthen that one too. Stating both is
     * refused for the same reason — there is no reading in which a buff is named by a state <i>and</i> an attribute.
     *
     * <p>The <b>name</b> is checked against the closed spellings the engine knows (the four DOT names, the three
     * control names, 护盾) and otherwise accepted as a {@code StateBuff} name — which is free-form by design, exactly
     * like {@code has_state}'s argument. The <b>attribute</b> is a name from {@code AttributeType}.
     */
    private static void requireExtendFilter(EffectSpec effect, String op, TriggerSpec spec) {
        boolean byName = effect.getBuff() != null && !effect.getBuff().isBlank();
        boolean byAttribute = effect.getAttribute() != null && !effect.getAttribute().isBlank();
        if (byName && byAttribute) {
            throw new IllegalArgumentException(
                    "Op " + op + " states both \"buff\" and \"attribute\"; a buff is named one way or the other "
                            + "(a state's name, or the attribute a modifier sits on) -- keep one "
                            + "(source: " + spec.getSource() + ")");
        }
        if (!byName && !byAttribute) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs the buff it lengthens: \"buff\" (a state's name such as 灼烧 / 冻结 / "
                            + "\"" + BuffManager.SHIELD_STATE + "\", or any named state) or \"attribute\" (e.g. "
                            + "ALL_DAMAGE_TYPE_BOOST for a 「伤害提高效果」). Without one it would mean \"everything "
                            + "I have on that unit\", which would lengthen buffs the sentence never mentions "
                            + "(source: " + spec.getSource() + ")");
        }
        if (byAttribute) {
            AttributeType attribute;
            try {
                attribute = AttributeType.fromString(effect.getAttribute());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Op " + op + " names unknown attribute '" + effect.getAttribute()
                                + "'; use a name from AttributeType, e.g. ALL_DAMAGE_TYPE_BOOST / ATTACK "
                                + "(source: " + spec.getSource() + ")");
            }
            if (attribute.isPercentVariant()) {
                throw new IllegalArgumentException(
                        "Op " + op + " names '" + effect.getAttribute() + "', one of the four *_PERCENT "
                                + "AttributeBuilder input keys; a modifier is never stored on one of those, so "
                                + "nothing could ever match " + "(source: " + spec.getSource() + ")");
            }
        }
        if (effect.getAmount() != null || effect.getScale() != null || effect.getPercent() != null
                || effect.getSkill() != null || effect.getDamageParam() != null
                || effect.getElement() != null || effect.getControl() != null || effect.getBaseChance() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " adds turns to a buff that already exists and states nothing else: the buff it "
                            + "lengthens keeps its own numbers (no amount / scale / percent / element / control / "
                            + "base_chance / skill) (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code MODIFY_RULE}'s target: the rule being raised, named by its {@code id} — resolved against the
     * <b>whole file</b> by {@code TriggerTable.validateAmendments}, which is the only place that can see it.
     */
    /** Applies one value/duration amendment to the rule it names. */
    private static void amendRuleEffect(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null) {
            return;
        }
        String ruleId = effect.getRule() == null ? null : effect.getRule().trim();
        if (ruleId == null || ruleId.isEmpty()) {
            return;
        }
        if (effect.getEffectTurns() != null) {
            owner.amendRuleEffectTurns(ruleId, (int) Math.round(effect.getEffectTurns()));
        }
        if (effect.getEffectPercent() != null) {
            owner.amendRuleEffectPercent(ruleId, effect.getEffectPercent());
        }
    }

    private static void requireRuleReference(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getRule() == null || effect.getRule().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs \"rule\": the id of the rule whose number it raises (that rule states "
                            + "\"id\": \"…\"), because otherwise there is nothing to point at "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Refuses every field a {@code MODIFY_RULE} effect does not read, naming the one it does.
     *
     * <p>The two spellings are closed on purpose: an {@code amount} raises a per-turn <b>count</b> and a
     * {@code percent} raises a <b>probability</b>, so stating both (or neither) would leave the reader to guess which
     * number on the named rule moves — the exact ambiguity this op exists to remove.
     *
     * @param kept the field that selected the amendment ({@code "amount"} or {@code "percent"})
     */
    /**
     * Refuses every field a {@code RAISE_SKILL_LEVEL} effect does not read.
     *
     * <p>⚠ It cannot reuse {@link #rejectAmendmentExtras}: that one forbids {@code skill} outright (a
     * {@code MODIFY_RULE} names a <b>rule</b>, never a slot), and this op's whole meaning is <b>which</b> slot is
     * raised. Written out rather than parameterised because the two lists really are different statements — the
     * shared-looking version made the op refuse the field it needs (measured: five cases went red with
     * "raises exactly one number on the rule it names").
     */
    private static void rejectSkillLevelExtras(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getPercent() != null || effect.getScale() != null || effect.getTurns() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getAttribute() != null || effect.getBuff() != null
                || effect.getTarget() != null || effect.getResource() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getElement() != null || effect.getControl() != null
                || effect.getBaseChance() != null || effect.getKind() != null
                || effect.getStacks() != null || effect.getTicksOn() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null
                || effect.getRule() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises one skill slot's level and states nothing else: \"skill\" (which slot) "
                            + "and \"amount\" (how many levels), and it reads no other field "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    private static void rejectAmendmentExtras(EffectSpec effect, String op, TriggerSpec spec, String kept) {
        boolean bothOrNeither = "amount".equals(kept) ? effect.getPercent() != null : effect.getAmount() != null;
        if (bothOrNeither || effect.getScale() != null || effect.getTurns() != null
                || effect.getPermanent() != null || effect.getUntil() != null
                || effect.getAttribute() != null || effect.getBuff() != null || effect.getSkill() != null
                || effect.getTarget() != null || effect.getResource() != null
                || effect.getDamageParam() != null || effect.getDamageLevel() != null
                || effect.getElement() != null || effect.getControl() != null
                || effect.getBaseChance() != null || effect.getKind() != null
                || effect.getStacks() != null || effect.getTicksOn() != null
                || effect.getAsAttack() != null || effect.getPerTarget() != null
                || effect.getCritRate() != null || effect.getCritDamage() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises exactly one number on the rule it names: either \"amount\" (how many more "
                            + "times per turn) or \"percent\" (how much more likely), and it reads no other field "
                            + "(source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates {@code RESIST_DEBUFF}'s {@code "kind"}: one of the classes the documents name
     * ({@code DebuffClass}), refused at load time with the list in the message.
     *
     * <p>Same reasoning as every other closed vocabulary here: a misspelled class would attach a resistance to a family
     * that no state belongs to — a rule that loads, fires, and does nothing, with nothing to report.
     */
    private static void requireDebuffClass(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getKind() == null || effect.getKind().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"kind\" (which class of negative state it resists; known: "
                            + String.join(" / ", DebuffClass.names()) + ") (source: " + spec.getSource() + ")");
        }
        if (DebuffClass.fromString(effect.getKind()) == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the class '" + effect.getKind() + "', which the documents do not: known "
                            + "classes are " + String.join(" / ", DebuffClass.names())
                            + " (「控制类」 and 「持续伤害类」); a resistance to a family nobody belongs to would do "
                            + "nothing (source: " + spec.getSource() + ")");
        }
    }

    private static void requireResource(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getResource() == null || effect.getResource().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"resource\" (source: " + spec.getSource() + ")");
        }
    }

    private static void requireSkill(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getSkill() == null || effect.getSkill().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"skill\" (which slot the attack comes from, e.g. TALENT) "
                            + "(source: " + spec.getSource() + ")");
        }
        try {
            SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Op " + op + " names an unknown skill slot '" + effect.getSkill()
                            + "' (source: " + spec.getSource() + ")");
        }
    }

    /**
     * Validates a <b>stated crit</b> on a damage instance: {@code crit_rate: 1} plus {@code crit_damage: X}.
     *
     * <p>⚠ {@code 1.0} is the only legal rate (see {@link EffectSpec#getCritRate()}): "always crits" is a different fact
     * from the {@code CRIT_CHANCE} attribute, and any other number would be a third mechanic with no reader. The pair
     * is also both-or-neither: a crit damage without a rate says which number to use for a roll nobody described.
     */
    private static void requireFixedCrit(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getCritRate() == null && effect.getCritDamage() == null) {
            return;
        }
        if (effect.getCritRate() == null || effect.getCritDamage() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states half a fixed crit: \"crit_rate\" (100%) and \"crit_damage\" (e.g. 1.5 for "
                            + "150%) go together, because neither number is usable without the other "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getCritRate() != 1.0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"crit_rate\": " + effect.getCritRate() + ", but only 1.0 is a spelling this "
                            + "engine has: a probabilistic crit rate is the CRIT_CHANCE attribute, and \"fixed\" "
                            + "means the roll does not happen at all (source: " + spec.getSource() + ")");
        }
        if (effect.getCritDamage() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"crit_damage\": " + effect.getCritDamage() + ", which is not a crit damage "
                            + "(1.5 = 150%) (source: " + spec.getSource() + ")");
        }
    }

    private static void requireDamageParam(EffectSpec effect, String op, TriggerSpec spec) {
        if (effect.getDamageParam() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"damage_param\" (which parameter of the skill row is the "
                            + "multiplier); there is no default because the index differs per ability "
                            + "(source: " + spec.getSource() + ")");
        }
        if (effect.getDamageParam() < 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " has a negative \"damage_param\" (source: " + spec.getSource() + ")");
        }
        // The row, when stated, must be a real 1-based level. Whether the skill's data actually HAS that many
        // rows is checked when the effect fires: a rule does not know its owner's cid while it is being read.
        if (effect.getDamageLevel() != null && effect.getDamageLevel() < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"damage_level\": " + effect.getDamageLevel()
                            + "; levels are 1-based, and 0 or below would read a row that cannot exist "
                            + "(source: " + spec.getSource() + ")");
        }
    }
}
