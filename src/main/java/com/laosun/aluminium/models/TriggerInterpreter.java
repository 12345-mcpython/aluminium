package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.Path;
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
import com.laosun.aluminium.models.buff.DeferredDeathBuff;
import com.laosun.aluminium.models.buff.StackableStateBuff;
import com.laosun.aluminium.models.buff.StateBuff;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the effects of a {@link TriggerTable}. The only place in the trigger system that
 * touches engine state.
 *
 * <p><b>The op vocabulary is restricted on purpose</b> to capabilities the engine already has --
 * the trigger table is an interpreter over existing operations, not a second engine. An op whose
 * prerequisite phase has not landed yet is <b>rejected at load time</b> with the phase named, so a
 * content author learns immediately instead of shipping a rule that silently never works. Those
 * load-time rules live in {@link TriggerValidator}; this class is the half that runs a rule which
 * passed them. The table below is the contract the two halves share, and the names in it are
 * declared in {@link TriggerVocabulary}.
 *
 * <table border="1">
 *   <tr><th>op</th><th>arguments</th><th>status</th></tr>
 *   <tr><td>{@code GAIN_ENERGY}</td><td>{@code amount}</td><td>wired</td></tr>
 *   <tr><td>{@code RAISE_ENERGY_TO}</td><td>{@code percent} (a floor; no-op when already above)</td><td>wired</td></tr>
 *   <tr><td>{@code GAIN_SKILL_POINT}</td><td>{@code amount}</td><td>wired</td></tr>
 *   <tr><td>{@code RAISE_SKILL_POINT_CAP}</td><td>{@code amount} (negative takes the bonus away)</td><td>wired</td></tr>
 *   <tr><td>{@code HEAL}</td><td>{@code amount}, optional {@code target} - <b>or</b> {@code scale} +
 *       {@code percent} (a share of a Max HP)</td><td>wired</td></tr>
 *   <tr><td>{@code SHIELD}</td><td>{@code amount}, optional {@code target} - <b>or</b> {@code scale} +
 *       {@code percent}</td><td>wired</td></tr>
 *   <tr><td>{@code EXTRA_TURN}</td><td>optional {@code target}</td><td>wired</td></tr>
 *   <tr><td>{@code ADVANCE}</td><td>{@code percent}, optional {@code target}</td><td>wired (0.0-1.0 = the fraction of the target's <b>remaining</b> time to act that gets skipped)</td></tr>
 *   <tr><td>{@code MODIFY_ATTR}</td><td>{@code attribute}, {@code percent}, <b>exactly one of</b>
 *       {@code turns} / {@code permanent} / {@code until}, optional {@code target}, {@code max_stacks} (alias
 *       {@code stacks})</td>
 *       <td>wired - a negative {@code percent} becomes a {@code DEBUFF}, so a buff and a
 *           debuff on the same attribute coexist; for a ratio attribute ({@code CRIT_ATTACK} and
 *           friends) {@code percent} is the value itself (0.25 = +25 percentage points), because an
 *           additive percentage would multiply their zero base and change nothing.
 *           <b>{@code permanent: true}</b> means "until the battle ends": the modifier is never
 *           ticked, so its duration is unbounded rather than merely long. <b>{@code until}</b> is the
 *           third duration and the one the texts keep asking for - the "ends after the first attack is cast" one is not a
 *           number of turns; it ends the buff when its <b>owner</b> attacks / casts a Skill / casts an
 *           Ultimate ({@code AbstractBuff.Lifetime}), and such a buff is likewise never ticked.
 *           <b>{@code max_stacks}</b>
 *           (&gt; 1) makes re-applications <b>accumulate</b> up to that cap instead of replacing the
 *           previous one; each stack is an ordinary buff instance with its own id, so it can be
 *           removed on its own. Absent {@code max_stacks} keeps the historical replace behaviour.</td></tr>
 *   <tr><td>{@code BOOST_DAMAGE}</td><td>{@code percent}</td>
 *       <td>wired - changes <b>the instance being settled</b> when the rule fires on
 *           {@code DEALING_DAMAGE} ("the damage dealt to a target in state X is raised by Y%"). No duration, no target and no
 *           buff: the instance is the state, so nothing persists and nothing can leak into the next hit</td></tr>
 *   <tr><td>{@code DISPEL}</td><td>{@code amount}, optional {@code target}</td>
 *       <td>wired - removes up to {@code amount} <b>negative effects</b> ("remove N negative effects"), newest
 *           first. What counts as negative is {@code AbstractBuff.isDebuff()}, decided per buff class</td></tr>
 *   <tr><td>{@code MODIFY_DAMAGE_TAKEN}</td><td>{@code percent}, <b>exactly one of</b> {@code turns} /
 *       {@code permanent} / {@code until}, optional {@code target}</td>
 *       <td>wired - the sign decides the zone: {@code percent > 0} is "damage taken is raised" (vulnerability,
 *           a debuff on the defender), {@code percent < 0} is "damage taken is lowered" (reduction, a buff on the
 *           defender). Neither is an attribute, which is why {@code MODIFY_ATTR} cannot express them</td></tr>
 *   <tr><td>{@code REMOVE_STACK}</td><td>{@code attribute}, {@code amount}, optional {@code target}</td>
 *       <td>wired - takes up to {@code amount} stacks of that attribute's modifier off the target
 *           ("remove 1 stack per turn"); removing nothing is not an error, because the rule fires every turn
 *           anyway</td></tr>
 *   <tr><td>{@code SPEND_STEPS}</td><td>{@code steps} ({@code "<counter>:<step>"})</td>
 *       <td>wired - consumes {@code floor(value / step) * step} of that counter: the other half of
 *           {@code per_steps}, which counts the milestones an effect repeats without spending them. The two are
 *           separate because one rule may pay TWO effects per milestone while the counter is charged once</td></tr>
 *   <tr><td>{@code APPLY_BUFF}</td><td>{@code buff}, <b>exactly one of</b> {@code turns} /
 *       {@code permanent} / {@code until}, optional {@code target}</td>
 *       <td>wired - puts the target into a <b>named state</b> ({@code StateBuff}, e.g. [协奏]/[转魄]/
 *           [shock]). What the state <i>does</i> is separate effects conditioned on {@code has_state}, which
 *           keeps "what the state is" apart from "what it changes"; plain stat buffs stay {@code MODIFY_ATTR}</td></tr>
 *   <tr><td>{@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}</td><td>{@code resource}, {@code amount}</td>
 *       <td>wired</td></tr>
 *   <tr><td>{@code REDUCE_TOUGHNESS}</td><td>{@code amount}</td><td>needs an element + enemy target</td></tr>
 *   <tr><td>{@code SUMMON}</td><td><b>no arguments</b></td>
 *       <td>wired - brings the <b>rule owner's own memosprite</b> onto the field
 *           ({@code Battle.summonMemosprite}). No {@code target}: a memosprite belongs to its summoner and
 *           fights for that camp, so there is nothing to point at. Idempotent - "if already on the field, its HP is
 *           restored to its maximum" is what the texts say and the refresh is <b>not</b> modelled, so a second firing
 *           keeps the one already out rather than making a second copy</td></tr>
 *   <tr><td>{@code DAMAGE}</td><td>{@code skill}, {@code damage_param}, optional {@code target}</td>
 *       <td>wired</td></tr>
 * </table>
 *
 * <p>Effects run in the order written. Every effect credits the <b>owner</b> (the character whose
 * table fired) unless it names a {@code target}; see {@link TriggerTargets#resolveTarget}.
 */
public final class TriggerInterpreter {

    private TriggerInterpreter() {
    }

    /**
     * Rejects an effect that could never work, naming the rule that wrote it.
     *
     * <p>The rules themselves live in {@link TriggerValidator}, which is where the closed-set
     * vocabulary, the argument shapes and the duration constraints are checked. This stays as
     * the entry point {@link TriggerTable} calls while a character's file is compiled, so that
     * the load-time contract has one name regardless of which class enforces it.
     *
     * @param effect the effect to check
     * @param spec   the rule it belongs to (used for the error message's source line)
     * @throws IllegalArgumentException when the effect names an unknown op, an unwired op, an
     *                                  unknown condition or selector, or a combination the
     *                                  dispatcher could not act on
     */
    public static void validate(EffectSpec effect, TriggerSpec spec) {
        TriggerValidator.validateOne(effect, spec);
    }

    /**
     * How much of a {@code one_of} pick an effect gets: its {@code weight}, plus every {@code weight_steps} entry whose
     * counter is at or above its threshold. See {@link EffectSpec#getWeight()}.
     *
     * <p>Note: An effect that states neither counts as {@code 1}, which is what keeps a partly-weighted pool meaningful.
     */
    private static double weightOf(EffectSpec effect, TriggerContext ctx) {
        double weight = effect.getWeight() == null ? 1 : effect.getWeight();
        if (effect.getWeightSteps() == null) {
            return weight;
        }
        for (String raw : effect.getWeightSteps()) {
            String[] parts = raw == null ? new String[0] : raw.trim().split(":", 3);
            if (parts.length != 3) {
                throw new IllegalStateException("a weight step is written \"<resource>:<threshold>:<delta>\", e.g. "
                        + "\"战技点:4:-0.9\", and this one is \"" + raw + "\"");
            }
            String name = parts[0].trim();
            double threshold;
            double delta;
            try {
                threshold = Double.parseDouble(parts[1].trim());
                delta = Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalStateException("a weight step's threshold and delta must be numbers, and \"" + raw
                        + "\" is not");
            }
            if (counterValue(name, ctx) >= threshold) {
                weight += delta;
            }
        }
        return weight;
    }

    /**
     * The quantity a weight step watches: an attribute of the rule owner, one of its own counters, or the BATTLE's
     * skill points - the last one because 「战技点」 is a counter the battle owns, not a {@code Resource} (see
     * {@link #SKILL_POINT}).
     */
    private static double counterValue(String name, TriggerContext ctx) {
        CanHit owner = ctx == null ? null : ctx.owner();
        if (SKILL_POINT.equals(name) && ctx != null) {
            return ctx.battle() == null ? 0 : ctx.battle().getSkillPoints();
        }
        if (owner == null) {
            return 0;
        }
        try {
            return owner.getAttribute(AttributeType.fromString(name)).get();
        } catch (IllegalArgumentException notAnAttribute) {
            // falls through to the counter readout below, exactly like the `self_attr_above` scale does
        }
        if (owner.getResources() != null && owner.getResources().has(name)) {
            return owner.getResources().value(name);
        }
        throw new IllegalStateException("a weight step reads \"" + name + "\", which is neither an attribute of "
                + owner.getName() + ", one of its counters, nor the battle's skill points");
    }

    /**
     * Whether the owner has any opponent left standing - the question {@code defer_until_wave} asks of the field.     *
     * <p>Note: {@code getOpponents} does not filter the dead (the ops do, because only they know whether a dead unit is
     * something they can act on), so the filter lives here: "no target" means no LIVING one.
     */
    private static boolean hasLivingOpponent(Battle battle, CanHit owner) {
        if (battle == null || owner == null) {
            return true;                          // nothing to judge the field with: deliver, as every rule did before
        }
        for (CanHit opponent : battle.getOpponents(owner)) {
            if (opponent != null && !opponent.isDeath()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs every effect of a rule.
     *
     * @param battle the running battle
     * @param rule   the matched rule
     * @param ctx    the context the rule was matched with
     */
    public static void apply(Battle battle, CompiledRule rule, TriggerContext ctx) {
        int effectIndex = 0;
        double previousCredited = 0;
        // `one_of`: ONE of the rule's effects runs, picked at random. Rolled HERE, once per firing, and not inside the
        // loop: a roll per effect would as easily run none of them as two of them, and neither is "one of". Reader:
        // 1506's 【头号补给盲盒】 and light cone 21032's 「随机生效 1 个效果」.
        // Note: The roll is over the POOL - the effects NOT marked `always` - and it answers with the effect's own
        // index. Reader for the marker: the same box's own 90%, which 「造成…伤害，并随机触发 1 种效果」 - it runs every
        // time, and it is in this rule rather than a second one because the box's fixed chance is ONE roll (and, with
        // `chance_decay`, one decaying number) for the whole box.
        int chosen = -1;
        if (rule.oneOf() && battle != null) {
            java.util.List<Integer> pool = new java.util.ArrayList<>();
            for (int index = 0; index < rule.effects().size(); index++) {
                if (!Boolean.TRUE.equals(rule.effects().get(index).getAlways())) {
                    pool.add(index);
                }
            }
            // "替换上次的效果且本次不会与上次重复": the memory is named by the rule, so two rules of one sentence (a battle start
            // and a turn start) share it - see TriggerSpec.oneOfNoRepeat. ⚠ The remembered effect is excluded and the pick
            // stays UNIFORM among the rest, which is what the game's own script does (remove the current, roll 50/50 over
            // the other two). An unknown or already-excluded memory changes nothing, so the first pick is uniform.
            String memory = rule.oneOfNoRepeat() == null ? "" : rule.oneOfNoRepeat().trim();
            CanHit picker = ctx.owner();
            if (!memory.isEmpty() && picker != null && pool.size() > 1) {
                int last = picker.lastPick(memory);
                if (last >= 0) {
                    pool.removeIf(index -> index == last);
                }
            }
            if (!pool.isEmpty()) {
                // WEIGHTS: 「有概率触发 1 次…」 where the chance is graded per effect - 「战技点较少时，触发概率提高…较多时降低」.
                // Scale is 1000, because the game's own smallest grade is 0.001 (measured: its ability script multiplies
                // the graded chance by 10 / 0.1 / 0.001). A pool where nobody states a weight keeps the uniform roll.
                boolean weighted = false;
                int[] weights = new int[pool.size()];
                for (int index = 0; index < pool.size(); index++) {
                    EffectSpec candidate = rule.effects().get(pool.get(index));
                    if (candidate.getWeight() != null || candidate.getWeightSteps() != null) {
                        weighted = true;
                    }
                    weights[index] = (int) Math.round(weightOf(candidate, ctx) * 1000);
                    if (weights[index] < 0) {
                        throw new IllegalStateException("the pick weight of " + candidate.getOp() + " is negative ("
                                + weights[index] / 1000.0 + "); a weight is a share of the pick, so it cannot be less "
                                + "than zero (source: " + rule.source() + ")");
                    }
                }
                if (!weighted) {
                    chosen = pool.get(battle.getRng().nextInt(pool.size()));
                } else {
                    long total = 0;
                    for (int weight : weights) {
                        total += weight;
                    }
                    if (total <= 0) {
                        throw new IllegalStateException("this rule's `one_of` is WEIGHTED but every weight adds up to "
                                + "nothing, so there is no distribution to roll against (source: " + rule.source() + ")");
                    }
                    if (total > Integer.MAX_VALUE) {
                        throw new IllegalStateException("this rule's `one_of` weights add up to " + total
                                + ", which no single roll can cover (source: " + rule.source() + ")");
                    }
                    int roll = battle.getRng().nextInt((int) total);
                    for (int index = 0; index < pool.size(); index++) {
                        roll -= weights[index];
                        if (roll < 0) {
                            chosen = pool.get(index);
                            break;
                        }
                    }
                }
                if (!memory.isEmpty() && picker != null && chosen >= 0) {
                    picker.rememberPick(memory, chosen);
                }
            }
        }
        for (EffectSpec effect : rule.effects()) {
            if (chosen >= 0 && effectIndex != chosen && !Boolean.TRUE.equals(effect.getAlways())) {
                effectIndex++;
                continue;
            }
            final int thisEffect = effectIndex++;
            // "the Ultimate's duration is additionally increased by 1 turn"/"the talent's damage boost effect is additionally raised by 10%": an amendment to the
            // named rule's own effect values. Note: A COPY, not a mutation: the compiled EffectSpec is shared by every
            // battle, so adjusting it in place would leak the amendment (and, in the test suite, into other tests).
            // The fast path returns the same instance, so the 5places that read `percent`/`turns` stay untouched.
            if (effect.getMinEidolon() != null && eidolonRankOf(ctx.owner()) < effect.getMinEidolon()) {
                //: this EFFECT belongs to an Eidolon rank the unit does not have. The rule-level
                // `min_eidolon` cannot express this -- it would switch off the whole rule, base part included.
                continue;
            }
            effect = amendedEffect(effect, ctx);
            // This effect's per-target conditions. The ops below take `effectCtx`, whose filter `resolveTargets`
            // applies -- so the whole vocabulary cost four lines in the loop and one branch in the resolver.
            TriggerContext effectCtx = ctx.withTargetFilter(rule.targetFilterAt(thisEffect));
            // The rule's own id travels with the effect: `MODIFY_RULE` can raise a rule's base chance, and the only
            // op that consumes that amendment (APPLY_CONTROL) has to know which rule it is running inside. Passing it
            // down beats a field on the context -- a nested firing would clobber shared state, and this is per-rule.
            // BOTH resource-moving ops: the first version measured only a GAIN, so a SPEND went down the `else` and never set
            // `previousCredited` at all -- measured: "for each 1% of overflow spent..." read 0 instead of the amount spent.
            String movingOp = normalizeOp(effect, null);
            // Match the RAW op too: `normalizeOp` derives its name and a spend spelled `overflow_only` did not reach this branch, so the capture of a spend read 0.
            String rawOp = effect.getOp() == null ? "" : effect.getOp().trim().toUpperCase(java.util.Locale.ROOT);
            for (int repeat = perStepsRepeat(effect, effectCtx); repeat > 0; repeat--) {
                if ("GAIN_RESOURCE".equals(movingOp) || "SPEND_RESOURCE".equals(movingOp)
                        || "GAIN_RESOURCE".equals(rawOp) || "SPEND_RESOURCE".equals(rawOp)) {
                    //: the amount this effect ACTUALLY credits (after the cap) is what the next effect may
                    // take a share of. Measured from the holder itself, so the cap is included by construction.
                    CanHit holder = TriggerTargets.resolveTarget(effect, effectCtx);
                    String resourceId = effect.getResource();
                    // Through `resourceAmount`, which knows BOTH stores (measured: [新蕊] is `scope: PARTY`, so reading the holder's own store gave 0 on
                    // both sides of the move and the capture of a spend read 0 even though the spend itself worked).
                    // EITHER store, never both (measured: `resourceAmount` ADDS the holder's store to the party's, so a SELF resource was counted twice and a capture of 4 read 8).
                    int creditedBefore = measureForTheDelta(effectCtx, holder, resourceId);
                    applyOne(battle, effect, effectCtx, previousCredited);
                    int creditedAfter = measureForTheDelta(effectCtx, holder, resourceId);
                    // The MAGNITUDE of what the previous effect moved (reader: 114151"for each 1% of overflow spent..."). A gain arrives positive and a spend
                    // NEGATIVE, and `amount_from_previous` is asked for a size, not a direction -- measured: this is the only place `previousCredited` is set, and `gainResource` is its
                    // only reader, so the sign carried no information anyone used.
                    previousCredited = Math.abs(creditedAfter - creditedBefore);
                } else {
                    applyOne(battle, effect, effectCtx, previousCredited);
                }
            }
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
        // Note: Per rule, not up front: see TriggerTable.rulesFor. A condition that reads what an earlier
        // rule of the SAME event just changed (a counter marked on this attack, say) has to be evaluated when its own
        // rule is reached, or it can never be true.
        List<CompiledRule> rules = table.rulesFor(event);
        CanHit owner = ctx.owner();
        int fired = 0;
        for (CompiledRule rule : rules) {
            // ONE expression, read at all four sites below (the count-and-reset family).
            // The comment on startTriggerCooldown names the failure this avoids: recording one key while
            // checking another makes a rule fire forever, or never again.
            String limitKey = rule.key() + subjectSuffix(rule, ctx);
            // Note: The conditions are checked HERE, per rule, because rulesFor does not filter. Harmless
            // to re-check: matches is the same pure predicate matching used.
            if (!rule.matches(ctx)) {
                continue;
            }
            // Firing limits (cooldown / once per battle / per-turn count). Checked *after* matching and before
            // applying, because the limit is about how often the rule may run, not about whether it fits the
            // event: `matching` stays a pure predicate, which is what `TriggerTable.ruleCount` and the
            // data-binding tests read.
            if (owner != null && !owner.isTriggerReady(limitKey,
                    rule.perTurn() + owner.rulePerTurnBonus(rule.id()))) {
                continue;
            }
            // "each attack may trigger only 1 time": the attack in progress is one sequence value for every instance it
            // settles, so this is the one cap a per-turn count cannot express.
            if (owner != null && rule.perAttack() > 0
                    && !owner.isAttackLimitReady(limitKey, battle.attackSequence(), rule.perAttack())) {
                continue;
            }
            // An Eidolon gate ("unlocked at Eidolon (星魂) N"): the rank is a construction-time property of the rule's owner, so
            // the rule itself is the only place that has to know it is an Eidolon.
            // [WAVE CAP] the same shape as the attack cap above, compared against the wave number instead.
            if (owner != null && rule.oncePerWave()
                    && !owner.isWaveLimitReady(limitKey, battle.waveSequence())) {
                continue;
            }
            if (rule.minEidolon() > 0 && eidolonRankOf(owner) < rule.minEidolon()) {
                continue;
            }
            // The other end of the band: "this clause replaces that one from rank N" needs the shipped rule to STOP
            // applying, which a floor alone cannot say (see TriggerSpec.maxEidolon).
            if (rule.maxEidolon() > 0 && eidolonRankOf(owner) > rule.maxEidolon()) {
                continue;
            }
            // A rule-level probability ("there is a 35% fixed chance..."). A failed roll costs nothing: no cooldown is
            // started and no once-per-battle flag is set, because nothing happened.
            // Note: The number is read from the combatant, not from the rule, because a rule may DECAY: "触发该效果的初始固定
            // 概率为 100%，成功触发后…降低至当前概率的 20%" (reader: 1506's 【头号补给盲盒】). `chance` is then the initial value
            // and the owner holds where it has got to; a rule with no `chance_decay` has no entry and answers its own
            // `chance` forever, which is why this read changed no shipped rule's behaviour.
            double chance = owner == null ? rule.chance() : owner.currentRuleChance(rule.id(), rule.chance());
            if (chance < 1 && !battle.rollChance(chance)) {
                continue;
            }
            // "若触发前目标被消灭则对新入场的敌方目标触发": the trigger already HAPPENED (its conditions and its roll are
            // spent), but there is nobody to deliver it to - so it is held and delivered when a wave brings enemies in.
            // Note: The effects - including which one a `one_of` picks - are decided at delivery; only the gate is now.
            if (rule.deferUntilWave() && !hasLivingOpponent(battle, owner)) {
                battle.deferUntilWave(rule, ctx.withRule(rule.id()));
            } else {
                apply(battle, rule, ctx.withRule(rule.id()));
            }
            if (owner != null && rule.chanceDecay() > 0) {
                // "成功触发后": the rule really ran, so the NEXT time it is asked for, its probability is this much
                // smaller. A failed roll returned above, and a firing turned away by a condition or a limit never got
                // here at all - neither is a successful trigger.
                owner.decayRuleChance(rule.id(), rule.chance(), rule.chanceDecay());
            }
            if (owner != null) {
                // Note: The count recorded is the SAME number the check above used: recording the stated per_turn while
                // checking the amended one would make an amended rule fire forever (its counter would never reach the
                // raised cap). One expression, read twice -- see `amendedPerTurn`.
                owner.startTriggerCooldown(limitKey, rule.cooldownTurns(), rule.oncePerBattle(),
                        rule.perTurn() + owner.rulePerTurnBonus(rule.id()));
                if (rule.oncePerWave()) {
                    owner.recordWaveUse(limitKey, battle.waveSequence());
                }
                if (rule.perAttack() > 0) {
                    owner.recordAttackUse(limitKey, battle.attackSequence());
                }
            }
            fired++;
        }
        return fired;
    }

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
        Integer stacksDelta = owner.ruleEffectMaxStacksBonus(ctx.ruleId());
        if (percentDelta == null && turnsDelta == null && stacksDelta == null) {
            return effect;
        }
        EffectSpec amended = effect;
        if (percentDelta != null && effect.getPercent() != null) {
            // Only a LITERAL share is raised in place. When the share comes from a spelling (`percent_from_skill_param` and friends), writing a `percent`
            // here would REPLACE it -- `shareOf` reads `percent` first -- so the amendment would erase the rule's own number instead of adding to it. `shareOf` adds it instead.
            amended = amended.withPercent(percentDelta);
        }
        if (turnsDelta != null) {
            amended = amended.withTurns(turnsDelta);
        }
        if (stacksDelta != null) {
            amended = amended.withMaxStacks(stacksDelta);
        }
        return amended;
    }

    /**
     * The suffix that scopes a firing count to another unit, or {@code ""} for the owner.
     *
     * <p>"this effect triggers at most 1 time per character" counts per TRIGGERER, "the target has 1 negative effect"-style
     * limits count per TARGET. Note: The identity is System.identityHashCode: names are for messages and may
     * repeat, and the counters live on the combatant, so an identity within one battle is exactly the scope
     * the counters have.
     */
    private static String subjectSuffix(CompiledRule rule, TriggerContext ctx) {
        String subject = rule.perSubject();
        if (subject == null || subject.isBlank()) {
            return "";
        }
        CanHit unit = switch (subject) {
            case "target" -> ctx.target();
            case "actor" -> ctx.actor();
            default -> ctx.owner();
        };
        return unit == null ? "" : "@" + System.identityHashCode(unit);
    }

    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx,
                                 double previousCredited) {
        String op = normalizeOp(effect, null);
        switch (op) {
            case "ADD_ELEMENTAL_WEAKNESS" -> {
                // Note: Two named sources beside a real element name (the loader keeps this a closed set):
                //   `party_first` -- the first character of the party, per 1006's own skill text.
                //   `random_absent` -- a random element the target does NOT already have (1405).
                String named = effect.getElement().trim();
                DamageElement weakness;
                if ("party_first".equals(named)) {
                    weakness = battle == null || battle.characters.isEmpty()
                            ? null : battle.characters.getFirst().getElement();
                } else if ("random_absent".equals(named)) {
                    weakness = null;
                    for (CanHit victim : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                        if (victim instanceof com.laosun.aluminium.models.enemy.Enemy en) {
                            java.util.List<DamageElement> absent = new java.util.ArrayList<>();
                            for (DamageElement candidate : DamageElement.values()) {
                                if (!en.isWeakTo(candidate)) {
                                    absent.add(candidate);
                                }
                            }
                            weakness = absent.isEmpty() ? null
                                    : absent.get(battle.getRng().nextInt(absent.size()));
                        }
                    }
                } else {
                    weakness = DamageElement.fromString(named);
                }
                if (weakness == null) {
                    // Note: Not an error for the two named sources: 1006 may have no party attribute to offer and
                    // 1405 may find no absent element. The clause simply does nothing then.
                    if (TriggerVocabulary.SPECIAL_ELEMENTS.contains(named)) {
                        break;
                    }
                    throw new IllegalStateException("Op ADD_ELEMENTAL_WEAKNESS names an element that is not a DamageElement");
                }
                for (CanHit victim : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    // "when adding a weakness to an enemy target"(cone 23050). Note: Asked first, then changed, then fired:
                    //    a repeat insertion is NOT an insertion, and firing then would be a quiet OVER-fire.
                    if (victim instanceof com.laosun.aluminium.models.enemy.Enemy en && !en.isWeakTo(weakness)) {
                        if (effect.getTurns() != null && effect.getTurns() > 0) {
                            en.addWeakness(weakness, effect.getTurns());
                        } else {
                            en.addWeakness(weakness);
                        }
                        battle.fireTriggers(TriggerEvent.WEAKNESS_ADDED, ctx.owner(), en, 0, 0);
                    }
                }
            }
            case "RESET_TRIGGER_LIMIT" -> {
                String wanted = effect.getRule().trim();
                for (CanHit cleared : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    TriggerTable table = cleared instanceof com.laosun.aluminium.models.Character ch
                            ? ch.getTriggerTable() : null;
                    String limitKey = table == null ? null : table.keyOf(wanted);
                    if (limitKey == null) {
                        throw new IllegalStateException(
                                "Op RESET_TRIGGER_LIMIT names the rule " + wanted + ", which " + cleared.getName()
                                        + " does not carry -- an id that matches nothing would clear nothing, silently");
                    }
                    cleared.resetTriggerLimit(limitKey);
                }
            }
            case "GAIN_ENERGY" -> gainEnergy(battle, effect, ctx);
            case "RAISE_ENERGY_TO" -> {
                // A FLOOR, not a gain: energy is raised to the stated share of the maximum and left alone
                // when it is already at or above it. Adding a share instead would overshoot.
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    double floor = target.getMaxEnergy() * effect.getPercent();
                    if (target.getCurrentEnergy() < floor) {
                        target.setCurrentEnergy(floor);
                    }
                }
            }
            case "GAIN_SKILL_POINT" -> battle.gainSkillPoint((int) Math.round(scaledAmount(effect, ctx)));
            case "HEAL" -> {
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    // Note: The healer is the RULE OWNER, not `null`. A `HEAL` effect is
                    // the owner healing somebody, and `HEALED`'s `actor` is what "healing <b>provided by a teammate</b>" reads; with
                    // `null` there was no way to ask who healed, so that half of The Dahlia (大丽花)'s trace could not be written.
                    // Note: no shipped content subscribes to `HEALED` at all, and no shipped
                    // content grants `OUTGOING_HEALING_BOOST`, so this changes no number today -- it only makes the
                    // attribution true (and a rule-driven heal now reads the owner's healing boost, as a skill's does).
                    battle.heal(ctx.owner(), target, grantAmount(effect, target, ctx));
                }
            }
            case "SHIELD" -> {
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    double amount = grantAmount(effect, target, ctx);
                    // "lasting N turns" is a *timed* shield: the ShieldBuff installs the value and takes it off
                    // again, so the number and its lifetime stay one fact. Without `turns` the shield is the
                    // raw grant it has always been (it comes off only when it is used up).
                    // Note: Both arms name the provider (`ctx.owner()`): "the shield amount provided by the wearer is raised by X%" is about the
                    // *giver's* shields, so a grant with no provider would silently ignore the set bonus.
                    if (effect.getTurns() == null) {
                        battle.grantShield(ctx.owner(), target, amount, ctx.ruleId());
                    } else if (target != null && !target.isDeath()) {
                        target.getBuffManager().addBuff(
                                withSource(new ShieldBuff(ctx.owner(), amount, effect.getTurns()), ctx));
                        // The timed path installs its shield through a buff, which has no Battle handle,
                        // so this is the only place that can announce it. Fired AFTER the buff is on, so a rule that
                        // answers "when a shield is gained" sees the shield it is reacting to.
                        if (amount > 0) {
                            battle.fireTriggersForAlly(TriggerEvent.SHIELD_GRANTED, ctx.owner(), target, amount);
                        }
                    }
                }
            }
            case "ABSORB_DAMAGE" -> {
                // "每点【好活当赏】等同于 200 点抵御值，我方目标受到伤害时，真珠可消耗抵御值抵挡 60% 的伤害" (1503 Pearl (真珠)'s
                // talent). The grant rides on the PROTECTED unit - that is where the hit lands, so that is where the
                // absorber has to be - while the counter belongs to whoever PAYS.
                // Note: `resource`, `unit_amount` and `percent` are all validated at LOAD time (see `validate`), so by
                // the time this runs they are known good; the casts here are reads, not checks.
                String resource = effect.getResource().trim();
                double unitAmount = effect.getUnitAmount();
                double share = effect.getPercent();
                // Who pays. Defaults to the rule's owner (Pearl), which is the documented shape: the counter is hers
                // while the protected units are the party.
                CanHit payer = effect.getPayer() == null || effect.getPayer().isBlank()
                        ? ctx.owner()
                        : TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
                boolean permanent = unticked(effect);
                int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    if (target == null || target.isDeath()) {
                        continue;
                    }
                    target.getBuffManager().addBuff(withSource(withLifetime(
                            new com.laosun.aluminium.models.buff.AbsorbBuff(
                                    turns, permanent, resource, unitAmount, share, payer),
                            effect), ctx));
                }
            }
            case "INSERT_ACTION" -> {
                // "Demiurge (德谬歌) immediately gains 1 extra turn and automatically casts [花与箭的舞曲]"(1415 memosprite skill 10): a unit outside the action
                // order acts now -- the game's `TurnInsertAction`. Note: NOT `EXTRA_TURN`: that one cuts into a queue the unit is already in, and
                // a memosprite at Speed 0 is not in it at all (the game pins it there with `SpeedOverride`).
                CanHit acting = TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
                if (!battle.insertAction(acting)) {
                    throw new IllegalStateException("INSERT_ACTION makes " + acting.getName()
                            + " act now, but it is dead or already waiting to act");
                }
            }
            case "EXTRA_TURN" -> {
                // Note: A refused grant can vanish: `grantExtraTurn` answers false for a unit that is not in the action
                // order -- and a memosprite at Speed 0 is exactly that -- so the rule looked like it had worked. Measured on 1415's ode of
                // genesis, whose clause grants Demiurge (德谬歌) an extra turn: the judge read "extra turn actor = none" and nothing said why.
                CanHit holder = TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
                if (!battle.grantExtraTurn(holder)) {
                    throw new IllegalStateException("EXTRA_TURN gives " + holder.getName()
                            + " an extra turn, but it is not in the action order (a memosprite at Speed 0 is skipped "
                            + "when the battle starts), so no extra turn can be granted");
                }
            }
            case "ADVANCE" -> {
                // One target or a group: "make that target act immediately" and "make the teammates other than oneself act immediately" are the same op, and
                // the list resolver is what tells them apart (it is the one HEAL/SHIELD already use for "our
                // side"). Advancing several units is what the group selectors were resolved for.
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    battle.queue.advanceActionByPercent(target, effect.getPercent());
                }
            }
            case "RAISE_RESOURCE_CAP" -> raiseResourceCap(effect, ctx);
            // The pool is team-level and its owner is the policy, so this is the one op that goes
            // straight to the policy rather than through a unit's resources.
            case "RAISE_SKILL_POINT_CAP" -> battle.skillPointPolicy.raiseMax((int) Math.round(effect.getAmount()));
            case "GAIN_RESOURCE" -> {
                // ⚠ The store that MOVED, not the rule owner's: an effect may grant to somebody else ("give that ally 2
                // points"), and `resourceAmount(owner)` measured on a unit that does not carry the counter reads 0 on
                // both sides - measured: a teammate granted her 280 hidden points, her store went 20 to 300, and the
                // event announced nothing. One resolution, handed to the op, so a rolling selector rolls once.
                CanHit mover = TriggerTargets.resolveTarget(effect, ctx);
                String movedId = effect.getResource();
                int before = resourceAmount(battle, mover, movedId);
                gainResource(effect, ctx, previousCredited, mover);
                fireResourceChanged(battle, ctx, mover, movedId, resourceAmount(battle, mover, movedId) - before);
            }
            case "SPEND_RESOURCE" -> {
                CanHit mover = TriggerTargets.resolveTarget(effect, ctx);
                String movedId = effect.getResource();
                int before = resourceAmount(battle, mover, movedId);
                spendResource(effect, ctx, mover);
                fireResourceChanged(battle, ctx, mover, movedId, resourceAmount(battle, mover, movedId) - before);
            }
            // "该额外回合开始时，【美学底本】获得 30 点【好活当赏】…额外回合结束时移除" (1503 Pearl (真珠)'s ultimate): the
            // inverse of a temporary grant. Deliberately NOT `SPEND_RESOURCE`, which is a strict cost that errors when
            // the pool is short - a revocation has to clamp instead, because this pool is meant to be consumed.
            case "REMOVE_RESOURCE" -> {
                CanHit mover = TriggerTargets.resolveTarget(effect, ctx);
                String movedId = effect.getResource();
                int removed = removeResource(effect, ctx, mover);
                fireResourceChanged(battle, ctx, mover, movedId, -removed);
            }
            // "真珠回合开始时重置可获得【好活当赏】数量" (1503 Pearl (真珠)'s trace): starts a new round for the per-round
            // allowances that `max_per_round` charges against. Nothing about the COUNTER changes - only what this round
            // may still hand out.
            case "RESET_RESOURCE_CAP" -> {
                CanHit resetOwner = ctx.owner();
                if (resetOwner != null) {
                    resetOwner.getResources().resetRoundAllowances();
                }
            }
            // "将触发【头号补给盲盒】的固定概率重置为初始值" (1506's 崩坏级伤害演示): the decaying probability goes back to the
            // rule's own `chance`, so the next firing rolls the initial number again.
            case "RESET_RULE_CHANCE" -> {
                CanHit chanceOwner = ctx.owner();
                if (chanceOwner != null) {
                    chanceOwner.resetRuleChance(effect.getRule().trim());
                }
            }
            case "DAMAGE" -> {
                // A list, like HEAL/SHIELD: "to all enemies" is one effect that reaches several units, and the
                // engine settles one instance per victim (that is what a group attack is here).
                // Note: `times` repeats the WHOLE settlement, and because resolveTargets is called INSIDE the outer
                // loop, every repetition re-draws its target -- which is what "randomly a single enemy each time" means.
                // "for each 1 point spent ... 1 extra instance": the repeat count can follow the event.
                int times = effect.getTimes() == null ? 1 : effect.getTimes();
                if (effect.getTimesFrom() != null) {
                    String from = effect.getTimesFrom().trim();
                    if ("event_amount".equals(from)) {
                        times = (int) Math.abs(ctx.amount());
                    } else if ("hit_count".equals(from)) {
                        // "for each 1 target attacked, ... deal 1 instance" (reader: 1403 Tribbie (缇宝)'s zone rider): the repeat count is how many
                        // targets this attack connected with. It belongs HERE and not in `per_target`, which multiplies a magnitude.
                        times = Math.max(0, ctx.hitCount());
                    } else if (from.startsWith("resource:")) {
                        // "as many extra hits as the counter has points" (reader: 1141526's extra hit).
                        String id = from.substring("resource:".length()).trim();
                        if (id.isEmpty()) {
                            throw new IllegalStateException(
                                    "times_from 'resource:' names no resource; say which counter drives the repeat count");
                        }
                        times = resourceAmount(battle, ctx.actor(), id);
                    } else {
                        throw new IllegalStateException("times_from '" + effect.getTimesFrom()
                                + "' is not a spelling this engine has: \"event_amount\", \"hit_count\" or \"resource:<name>\"");
                    }
                    if (times <= 0) {
                        return;
                    }
                }
                for (int repeat = 0; repeat < times; repeat++) {
                    List<CanHit> victims = TriggerTargets.resolveTargets(battle, effect, ctx);
                    // "由敌方全体均分": the stated magnitude is the TOTAL, so each living victim takes an even share of it.
                    // ⚠ The divisor counts only the victims that can really take it: a corpse is skipped by `damage`
                    // below, and counting it would make part of the stated total vanish with nothing to see. An
                    // INVULNERABLE victim IS counted - its share is simply not settled (see EffectSpec.getSplit).
                    int divisor = 1;
                    if (Boolean.TRUE.equals(effect.getSplit())) {
                        divisor = 0;
                        for (CanHit victim : victims) {
                            if (victim != null && !victim.isDeath()) {
                                divisor++;
                            }
                        }
                        divisor = Math.max(1, divisor);
                    }
                    for (CanHit victim : victims) {
                        damage(battle, effect, ctx, victim, divisor);
                    }
                }
            }
            case "MODIFY_ATTR" -> modifyAttr(battle, effect, ctx);
            case "APPLY_BUFF" -> applyState(battle, effect, ctx);
            case "REMOVE_STACK" -> removeStacks(battle, effect, ctx);
            case "MODIFY_DAMAGE_TAKEN" -> modifyDamageTaken(battle, effect, ctx);
            case "BOOST_DAMAGE" -> boostDamage(effect, ctx);
            case "ADD_DAMAGE" -> addDamageFlat(effect, ctx);
            case "DISPEL" -> dispel(battle, effect, ctx);
            case "REMOVE_BUFF" -> removeBuffs(battle, effect, ctx);
            case "REPLACE_SKILL" -> replaceSkill(battle, effect, ctx);
            case "TICK_DOT" -> tickDot(battle, effect, ctx);
            case "REMOVE_STATE" -> removeState(effect, ctx);
            case "TAUNT" -> taunt(battle, effect, ctx);
            case "APPLY_CONTROL" -> applyControl(battle, effect, ctx);
            case "APPLY_DOT" -> applyDot(battle, effect, ctx);
            case "EXTEND_BUFF" -> extendBuff(battle, effect, ctx);
            case "RESIST_DEBUFF" -> resistDebuff(battle, effect, ctx);
            case "MODIFY_RULE" -> modifyRule(effect, ctx);
            case "RAISE_SKILL_LEVEL" -> raiseSkillLevel(effect, ctx);
            case "RAISE_SKILL_HITS" -> raiseSkillHits(effect, ctx);
            case "START_COUNTDOWN" -> startCountdown(battle, effect, ctx);
            case "ADD_STACK" -> addStack(battle, effect, ctx);
            case "SPEND_STEPS" -> spendSteps(effect, ctx);
            case "CONSUME_HP" -> {
                // Note: The share vocabulary is the HEAL/SHIELD one, not the modifier one: "consume HP equal to 30% of Blade (刃)'s Max HP"
                // is `owner_max_hp`, which derivedMagnitude refuses (it names an ATTRIBUTE).
                for (CanHit spender : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    if (spender != null) {
                        double spent = spender.consumeHp(grantAmount(effect, spender, ctx));
                        if (spent > 0) {
                            // "take damage <b>or</b> consume HP": the second half gets its own event.
                            battle.fireTriggers(TriggerEvent.HP_CONSUMED, spender, spender, (int) Math.round(spent), 0);
                        }
                    }
                }
            }
            case "BOOST_TOUGHNESS" -> boostToughness(battle, effect, ctx);
            case "SUPER_BREAK" -> superBreak(battle, effect, ctx);
            case "APPLY_REGEN" -> applyRegen(battle, effect, ctx);
            case "SUMMON" -> battle.summonMemosprite(TriggerMagnitudes.requireCharacterOwner(effect, ctx));
            case "COMMAND_SUMMON" -> commandSummon(battle, effect, ctx);
            case "CAST_SKILL" -> castSkill(battle, effect, ctx);
            case "SUMMON_SERVANT" -> battle.summonServant(TriggerMagnitudes.requireCharacterOwner(effect, ctx));
            case "DELEGATE_DAMAGE" -> delegateDamage(effect, ctx);
            default -> throw new IllegalStateException(
                    "Op '" + op + "' passed validation but has no implementation");
        }
    }

    /**
     * {@code DELEGATE_DAMAGE}: the cast in progress must not deal its own damage.
     *
     * <p>This is the one op that fires <b>before</b> a swing exists, and it can only touch the cast that is in
     * progress - so all three of its checks are about "is this rule talking about the swing it thinks it is":
     * <ul>
     *   <li>the cast must be the <b>rule owner's own</b>. {@code CAST_SETUP} is delivered to every ally's table,
     *       so a rule that forgot {@code actor == self} would otherwise hand away <i>somebody else's</i> damage -
     *       an over-trigger that no later observation could distinguish from the intended one;</li>
     *   <li>the named {@code skill} must be the slot being cast. Without this the rule would delegate every cast
     *       the owner makes (the condition DSL has no variable for "which slot", so this comparison <i>is</i> the
     *       gate); naming a different slot is a rule that loads, fires, and silently does nothing;</li>
     *   <li>a cast must exist at all. Load-time validation already pins the op to
     *       {@link TriggerEvent#CAST_SETUP}, so a missing token here would be an <b>engine</b> fault, not a
     *       content one - and it is reported as such rather than as a quiet no-op.</li>
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
        Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
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
     * Whether a {@code GAIN_ENERGY} effect takes its amount out of the skill that produced the event
     * (a {@code cast_skill_param:} scale) rather than stating it literally or as a share of Max Energy.
     *
     * @param effect the effect to inspect
     * @return {@code true} when the amount is read from the producing skill's parameter table
     */
    static boolean isCastParamScale(EffectSpec effect) {
        return effect.getScale() != null
                && effect.getScale().trim().startsWith(TriggerTable.CAST_SKILL_PARAM_PREFIX);
    }

    /**
     * {@code GAIN_ENERGY}: "restore N energy" / "restore energy equal to X% of each one's own Max Energy".
     *
     * <p>It resolves a <b>list</b>: the sentences that pay a group say "restore energy equal to
     * <b>each one's own</b> 20% Max Energy to the <b>teammates</b> other than oneself" (121 藿藿), i.e. every
     * recipient gets a share of <b>its own</b> maximum. Resolving one target made the party-wide reading
     * impossible, and the fix cannot be "pay the resolved unit N times": the amount differs per recipient, so the
     * resolution - not the amount - had to become a list.
     */
    private static void gainEnergy(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            gainEnergyFor(battle, effect, ctx, target);
        }
    }

    /**
     * The amount one recipient gets: a flat sum, a share of <b>its own</b> maximum energy, or a per-landing count.
     *
     * <p>A unit with <b>no energy bar</b> (the six that run on a stack resource instead) has a maximum of 0, and
     * a share of nothing is not a number - that is a loud failure rather than a silent grant of 0, the same call
     * the scale family makes everywhere else.
     */
    private static void gainEnergyFor(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit target) {
        // The event own magnitude (reader: 1312 per-spent-point energy), as gainResource does.
        if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
            double share = effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();
            battle.grantEnergy(target, Math.round(ctx.amount() * share));
            return;
        }
        if (effect.getScale() == null || effect.getScale().isBlank()) {
            battle.grantEnergy(target, scaledAmount(effect, ctx));
            return;
        }
        // "restore #2 energy to Hyacine (风堇)": an ABSOLUTE amount out of the skill that produced the event. The reader is the same one
        // `MODIFY_ATTR` uses for `cast_skill_param:`, so the two cannot drift.
        if (isCastParamScale(effect)) {
            battle.grantEnergy(target, Math.round(TriggerMagnitudes.castParamValue(effect, ctx,
                    effect.getScale().trim().substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()))));
            return;
        }
        String scale = effect.getScale().trim();
        if (scale.startsWith(TriggerTable.CAST_APPLIED_PREFIX)) {
            // "for each 1 target frozen, restore 6 energy": percent is the amount PER landed application, so the count multiplies it.
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

    /**
     * The share of the EVENT's magnitude: a literal `amount_percent`, or basis points carried in a resource.
     */
    private static double eventShare(EffectSpec effect, TriggerContext ctx) {
        if (effect.getAmountPercentFromResource() != null) {
            Character holder = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
            return holder.getResources().value(effect.getAmountPercentFromResource().trim()) / 10000.0;
        }
        return effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();
    }

    /**
     * Widens a resource's OVERFLOW for its owner (reader: 114151"[新蕊] can overflow to #3%").
     *
     * <p>A declaration states one overflow; a sentence can raise it for the battle. The NORMAL cap is untouched -- this is only the second tier, which is what the sentence is about.
     */
    private static void raiseResourceCap(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = TriggerTargets.resolveTarget(effect, ctx);
        // A PARTY-scoped resource has no home on the unit (the same fact `gainResource` records) -- [新蕊] is declared `scope: PARTY` -- so the fallback is the
        // battle-level counter, which is the SAME `Resource` object and therefore carries the settable overflow.
        com.laosun.aluminium.models.Resource resource = holder.getResources().get(effect.getResource());
        if (resource == null) {
            resource = ctx.battle() == null ? null : ctx.battle().partyResource(effect.getResource());
        }
        if (resource == null) {
            throw new IllegalStateException("RAISE_RESOURCE_CAP names the resource '" + effect.getResource()
                    + "', which this unit neither declares nor has at party level");
        }
        int raise = (int) Math.round(effect.getAmount());
        resource.setMaxOverflow(resource.getMaxOverflow() + raise);
    }

    private static void gainResource(EffectSpec effect, TriggerContext ctx,
                                     double previousCredited) {
        gainResource(effect, ctx, previousCredited, TriggerTargets.resolveTarget(effect, ctx));
    }

    /**
     * The same, told which unit receives: the caller has already resolved it, and it announces on the same one.
     */
    private static void gainResource(EffectSpec effect, TriggerContext ctx, double previousCredited, CanHit holder) {
        // A gain whose amount is a SHARE of an attribute (reader: 1505's talent "gain an Elation degree equal to
        // 50% of CRIT DMG"). `scaledAmount` stays literal-only on purpose: this share is read off the HOLDER, which only
        // this method has resolved. Unknown attribute names are refused loudly rather than silently adding zero.
        int amount;
        if (Boolean.TRUE.equals(effect.getAmountFromPrevious())) {
            // 1505's Eidolon "additionally gain 50%/100% of the [好活当赏] gained this time".
            // It takes the credited amount of the previous effect after the cap has truncated it.
            amount = (int) Math.round(previousCredited
                    * (effect.getAmountPercent() == null ? 1 : effect.getAmountPercent()));
        } else if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
            // The event's own magnitude: "when energy is gained, an equal amount of [好活当赏] is gained at the same time" is exactly this -- the
            // amount is not a literal and not an attribute, it is what the trigger just reported.
            amount = (int) Math.round(ctx.amount() * eventShare(effect, ctx));
        } else if (effect.getAmountFromResource() != null) {
            // The amount IS a resource's value: a number captured while an ode was cast, handed over when the character later casts.
            Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
            amount = owner.getResources().value(effect.getAmountFromResource().trim());
        } else if (isCastParamScale(effect)) {
            //  x percent: the share is usually BELOW 1 and a resource holds an integer, so content stores basis points (percent: 10000).
            amount = (int) Math.round(TriggerMagnitudes.castParamValue(effect, ctx,
                    effect.getScale().trim().substring(TriggerTable.CAST_SKILL_PARAM_PREFIX.length()))
                    * (effect.getPercent() == null ? 1 : effect.getPercent()));
        } else if (effect.getAmountFromAttr() == null) {
            amount = (int) Math.round(scaledAmount(effect, ctx));
        } else {
            AttributeType attribute = AttributeType.fromString(effect.getAmountFromAttr());
            double share = effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();
            amount = (int) Math.round(holder.getAttribute(attribute).get() * share);
        }
        // A single conversion may be capped (reader: 1505's "a single time ... no more than 100 points"). The clamp is the LAST
        // thing that happens, so it bounds whichever source answered -- a literal, an attribute share or the event's own magnitude.
        if (effect.getAmountCap() != null) {
            amount = (int) Math.min(amount, Math.round(effect.getAmountCap()));
        }
        com.laosun.aluminium.models.Resource party = partyCounterOrNull(ctx, holder, effect.getResource());
        // `to_max`: the counter is raised to its ceiling, so the amount becomes "however much it is short". Reader: 1503
        // Pearl (真珠)'s 充能 - the counter IS 【深度学习】's duration, so re-entering the state restores the FULL three
        // rather than adding three to the remainder. Note it is measured against the counter's MAXIMUM and not its
        // overflow ceiling: a plain gain on a counter that HAS an overflow allowance pours the spare on top, and the
        // state would then outlast the document by a use.
        if (Boolean.TRUE.equals(effect.getToMax())) {
            com.laosun.aluminium.models.Resource counter = party != null ? party
                    : holder.getResources().get(effect.getResource());
            if (counter == null) {
                throw new IllegalStateException("GAIN_RESOURCE to_max names the resource '"
                        + effect.getResource() + "', which neither " + holder.getName()
                        + " declares nor the battle holds as a party counter");
            }
            amount = Math.min(amount, counter.missingToMax());
            if (amount <= 0) {
                return;
            }
        }
        // "5 points per ally turn, at most 50 in a round, reset when Pearl's own turn begins": a per-ROUND allowance
        // rather than a cap on the counter, so the amount is bounded by what the round has left before anything is
        // credited. Note: It is read from the RULE OWNER (`ctx.owner()`), not from the holder: the allowance belongs to
        // whoever the rule is about, which is the same unit the counters are hers (measured: the trace's own wording
        // makes the reset Pearl's turn, and the gaining rule fires on her allies' turns).
        CanHit allowanceOwner = ctx.owner() == null ? holder : ctx.owner();
        if (effect.getMaxPerRound() != null && allowanceOwner != null) {
            int already = allowanceOwner.getResources().gainedThisRound(effect.getResource());
            int allowance = (int) Math.round(effect.getMaxPerRound());
            amount = Math.clamp(allowance - already, 0, amount);
            if (amount <= 0) {
                return;
            }
        }
        // The battle's SKILL POINTS: a counter the battle owns outright (`Battle.skillPointPolicy`), not a `Resource`, so
        // `partyCounterOrNull` cannot answer with it and this is where a rule reaches it. Reader: 1506 Silver Wolf
        // LV.999's 【头号补给盲盒】 - 「【爆爆爆炸蛋】：恢复 2 个战技点」 - which is one of the three effects that rule picks
        // between. Measured: nothing anywhere gained or spent 战技点 before this, so the name had no readout at all.
        if (SKILL_POINT.equals(effect.getResource()) && ctx.battle() != null) {
            ctx.battle().skillPointPolicy.gain(amount);
            return;
        }
        // A PARTY-scoped counter has no home on the unit: the battle owns it, so any ally may add to it.
        if (party != null) {
            int credited = party.gain(amount);
            if (effect.getMaxPerRound() != null) {
                allowanceOwner.getResources().countGainThisRound(effect.getResource(), credited);
            }
            // Note: The state is granted to the HOLDER either way - a party counter still means "this unit received
            // 【好活当赏】" - so the count is taken here too. Measured: a holder that does not declare the counter itself
            // took no snapshot at all, which read as "the second holder's count is 0".
            noteElationGiftIfThisIsOne(ctx, effect, holder, credited);
            return;
        }
        int credited = holder.getResources().gain(effect.getResource(), amount);
        if (effect.getMaxPerRound() != null && allowanceOwner != null) {
            // The CREDITED amount, not the amount asked for: a gain truncated by the counter's own cap must not spend
            // the round's allowance on points it never delivered.
            allowanceOwner.getResources().countGainThisRound(effect.getResource(), credited);
        }
        noteElationGiftIfThisIsOne(ctx, effect, holder, credited);
    }

    /**
     * The counter 【好活当赏】's name, and how many of its holder's TURN-ENDS the document's 2 rounds span.
     */
    private static final String ELATION_GIFT = "好活当赏";
    /**
     * 「持续 2 回合」, counted as the number of the holder's turn-ENDS it survives.
     *
     * <p>Three, not two, and the off-by-one is the boundary's: the snapshot is ticked when a turn ENDS, so a value of 2
     * would be gone after the holder's FIRST full turn and cover one round rather than the document's two. Measured on
     * the judge: with 2 the count read 0 after the second turn; with 3 it survives the first and second and is gone on
     * the third.
     */
    private static final int ELATION_GIFT_TURNS = 3;

    /**
     * A gain of 【好活当赏】 is the moment the document says the current Aha Moment's 笑点 is counted INTO the holder -
     * 「使参演阿哈时刻的角色获得【好活当赏】状态，并将本次阿哈时刻的笑点计入该状态」 - so the snapshot is taken here, once,
     * rather than by each of the twelve rules that grant it.
     *
     * <p>Note: Only on a gain that actually CREDITED something. A grant truncated to nothing (a counter already at its
     * cap) did not hand the unit the count, and freezing a snapshot for it would invent one.
     */
    private static void noteElationGiftIfThisIsOne(TriggerContext ctx, EffectSpec effect, CanHit holder,
                                                   int credited) {
        if (ctx.battle() == null || credited <= 0 || !ELATION_GIFT.equals(effect.getResource())) {
            return;
        }
        ctx.battle().noteElationGift(holder, ELATION_GIFT_TURNS);
    }

    /**
     * The counter a resource id names, wherever it actually lives: the unit's own, or the battle's PARTY-scoped one.
     *
     * <p>Note: Extracted because three ops need the same answer for the same reason - 笑点 is declared
     * {@code scope: PARTY}, so the holder's own {@code Resources} does not have it and the battle does. A version that
     * looked only at the holder would silently do nothing for every party counter, which is the failure
     * {@code spendResource} had to fix once already.
     *
     * @return the unit's own counter, or {@code null} when it is a PARTY one (see the second return value's role)
     */
    private static com.laosun.aluminium.models.Resource partyCounterOrNull(TriggerContext ctx, CanHit holder,
                                                                           String id) {
        if (id == null || holder == null || holder.getResources().has(id)) {
            return null;
        }
        return ctx.battle() == null ? null : ctx.battle().partyResource(id);
    }

    /**
     * Takes an amount back off a counter, keeping whatever is left when the counter cannot cover it.
     *
     * <p>This is the inverse of {@code GAIN_RESOURCE} for a grant that is explicitly temporary: 1503 Pearl (真珠)'s
     * ultimate pays the archetype 30 好活当赏 and 60 笑点 for its extra turn and says 「额外回合结束时移除」.
     *
     * <p>⚠ <b>Why not {@code SPEND_RESOURCE}.</b> That op is deliberately strict - a spend the author believed would
     * fit is an error when it does not - and it is the right reading for a COST. A revocation is not a cost: the pool
     * may legitimately have less by then, because the whole point of this counter is that it gets CONSUMED (Pearl's own
     * talent spends it as 抵御值). So this clamps: it removes what is there, up to the stated amount, and never fails.
     *
     * <p>⚠ <b>What this does NOT do, and why it does not matter here.</b> It removes from the CURRENT value, so a
     * grant that was spent and then re-earned within the extra turn takes the re-earned points too. Making it
     * exact would need the grant to be tracked as a separate lot, and the sentence's numbers are fixed and paired
     * (30 and 60 granted, 30 and 60 removed at the end of the same turn), so there is nothing to track.
     *
     * @return how much was actually taken back (0 when there was nothing left)
     */
    private static int removeResource(EffectSpec effect, TriggerContext ctx) {
        return removeResource(effect, ctx, TriggerTargets.resolveTarget(effect, ctx));
    }

    /**
     * The same, told which unit's store gives the points back: the caller has already resolved it.
     */
    private static int removeResource(EffectSpec effect, TriggerContext ctx, CanHit holder) {
        String id = effect.getResource();
        int asked = (int) Math.round(scaledAmount(effect, ctx));
        if (asked <= 0) {
            return 0;
        }
        com.laosun.aluminium.models.Resource party = partyCounterOrNull(ctx, holder, id);
        if (party != null) {
            return party.spend(asked);
        }
        if (!holder.getResources().has(id)) {
            // Note: A granted-then-removed counter that the unit does not have at all would be a rule whose two halves
            // disagree about WHERE the counter lives; that is worth saying rather than returning 0.
            throw new IllegalStateException("REMOVE_RESOURCE names '" + id + "', but " + holder.getName()
                    + " has no such resource (nor does the party)");
        }
        return holder.getResources().spend(id, asked);
    }

    /**
     * Spends from the target's resource.
     *
     * <p>A spend that does not fit is an <b>error</b>, not a silent no-op: unlike skill points (where
     * "not enough" legitimately means "the player cannot press the button"), a trigger spending a
     * resource is a rule the author wrote believing the resource would be there. Reporting it beats
     * letting a character's stack quietly fail to be consumed.
     *
     * @throws IllegalStateException when the resource is missing or does not hold enough
     */
    private static void spendResource(EffectSpec effect, TriggerContext ctx) {
        spendResource(effect, ctx, TriggerTargets.resolveTarget(effect, ctx));
    }

    /**
     * The same, told which unit's store pays: the caller has already resolved it (see the SPEND_RESOURCE arm).
     */
    private static void spendResource(EffectSpec effect, TriggerContext ctx, CanHit holder) {
        String id = effect.getResource();
        // A PARTY counter has no home on the unit (the same fact `gainResource` records), so a spend has to fall back to the
        // battle-level one -- measured: [笑点] is declared `scope: PARTY` ("Punchline (笑点) is shared by the whole team", glossary 1000002) and the holder's
        // own Resources does not have it, so "Aha consumes all 笑点 after acting" (10000026) could not be written at all.
        com.laosun.aluminium.models.Resource partyCounter = partyCounterOrNull(ctx, holder, id);
        if (partyCounter != null) {
            // "consume all overflow" is the tier ABOVE the cap (reader: 114151) -- neither `spendAll` (everything) nor a stated amount says it.
            int spend = Boolean.TRUE.equals(effect.getOverflowOnly())
                    ? Math.max(0, partyCounter.value() - partyCounter.getMax())
                    : Boolean.TRUE.equals(effect.getSpendAll())
                    ? partyCounter.value()
                    : (int) Math.round(scaledAmount(effect, ctx));
            if (!partyCounter.spendExactly(spend)) {
                throw new IllegalStateException(
                        "SPEND_RESOURCE '" + id + "' needs " + spend + " but the party has only "
                                + partyCounter.value());
            }
            return;
        }
        // "consume all [X]": the size is whatever is there; the "not enough"
        // failure below cannot happen for it, which is why the two spellings are kept apart.
        int amount = Boolean.TRUE.equals(effect.getOverflowOnly())
                ? (holder.getResources().has(id)
                ? Math.max(0, holder.getResources().value(id) - holder.getResources().get(id).getMax())
                : 0)
                : Boolean.TRUE.equals(effect.getSpendAll())
                ? (holder.getResources().has(id) ? holder.getResources().value(id) : 0)
                : (int) Math.round(scaledAmount(effect, ctx));
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
     * {@code SPEND_STEPS}: gives up {@code floor(value / step) * step} of a counter - the other half of {@code per_steps}.
     *
     * <p>Why it is its own op rather than something the repeat does: her sentence pays TWO effects per milestone
     * ("gain 1 extra turn AND restore 1 use of the enhanced basic attack") while the counter may be charged only once, so
     * the count has to be readable twice and spendable once. A repeat that consumed as it counted could not say that.
     *
     * <p>The counter must be a real STORE - one of the unit's own counters, or the party's. An attribute has nothing to
     * spend from, and saying so beats silently keeping the milestones and paying them again on the next change. Nothing
     * to spend (fewer than one whole step) is a no-op, because this rule fires on every change of the counter.
     */
    private static void spendSteps(EffectSpec effect, TriggerContext ctx) {
        String written = effect.getSteps() == null ? "" : effect.getSteps().trim();
        CounterSteps counter = parseCounterSteps(written, "SPEND_STEPS steps");
        int milestones = counter.wholeSteps(ctx);
        if (milestones <= 0) {
            return;
        }
        int amount = (int) Math.round(milestones * counter.step());
        CanHit holder = TriggerTargets.resolveTarget(effect, ctx);
        // The STORE, wherever it lives: the unit's own counter, or the battle's PARTY-scoped one. An attribute is
        // neither, and a spend that kept the milestones would pay them again on the next change.
        com.laosun.aluminium.models.Resource party = partyCounterOrNull(ctx, holder, counter.counter());
        com.laosun.aluminium.models.Resource store = party != null ? party
                : holder.getResources().has(counter.counter()) ? holder.getResources().get(counter.counter())
                : null;
        if (store == null) {
            throw new IllegalStateException("SPEND_STEPS names '" + counter.counter() + "', which is not a counter "
                    + holder.getName() + " holds (nor a party one); an attribute has no store to spend from");
        }
        if (!store.spendExactly(amount)) {
            throw new IllegalStateException("SPEND_STEPS '" + counter.counter() + "' needs " + amount + " but "
                    + holder.getName() + " has only " + store.value());
        }
        // Announced like every other resource move: the GAIN/SPEND_RESOURCE arms measure around their own call for
        // the same reason, and a rule forwarding off this counter has to see the consumption. The subject is the
        // holder whose store was charged, exactly as those arms now name theirs.
        fireResourceChanged(ctx.battle(), ctx, holder, counter.counter(), -amount);
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
     * {@code COMMAND_SUMMON}: the owner's summon performs <b>one attack, right now</b>, with the numbers of the
     * skill the rule names - "summon the memosprite '长夜', then make the memosprite '长夜' deal Ice damage equal to '长夜''s #1[i]% Max HP to all enemies".
     *
     * <p><b>Where every number comes from.</b> The rule names a {@code skill} + {@code damage_param} of the
     * <b>owner's</b> skills - her ultimate, whose row says 2.0 at Lv10 and 2.5 at Lv15 - and the element (Ice) and
     * the shape (AoEAttack) come from that same skill data. Only the <b>base attribute</b> ({@code HEALTH}) is
     * stated here, because that is the one thing the row does not say: "equal to the <b>memosprite</b>'s Max HP", not Evernight (长夜月)'s
     * attack. A first draft of this op carried its own {@code element} / {@code shape} / {@code percent} fields
     * and would have been a second copy of data that already exists, one that also loses the per-level table.
     *
     * <p>Note: The skill is the owner's and the <b>attacker is the summon</b> - a first draft looked the skill up on
     * the summon and failed with "长夜 has no ULTRA skill", which is exactly right: the numbers are hers, the
     * swing is its.
     *
     * <p><b>Whose action it is.</b> The owner's: the summon acts without spending its turn, exactly like a
     * follow-up, and its own turn (with its own skill) is untouched. The damage is settled with the
     * <b>summon</b> as the attacker, so everything downstream is the summon's - its attribute is what the share
     * reads, kill credit is the summon's, and the attack announces itself to our side like any other attack by
     * it ({@code Battle.fireAfterAttack} + {@code TriggerEvent.SUMMON_ATTACK}, both reached through
     * {@link EnemySkill#execute}).
     *
     * <p>Note: Reusing {@link EnemySkill} is the point: it already owns the shape dispatch (single / AOE / blast),
     * per-segment settlement and those two announcements. What it does not do is choose targets, which is the one
     * thing this method adds - and it must filter to the <b>living</b>, because {@code EnemySkill} takes the
     * first entry of the list it is handed as the main target.
     */
    private static void commandSummon(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit summon = TriggerTargets.requireSummon(ctx);
        Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
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
                // Note: `owner`, not the summon: the skill and its parameter row are the OWNER's (see above), so its
                // level - and any "Ultimate Lv. +1" this battle has raised - is the owner's too.
                TriggerMagnitudes.multiplierOf(skill, effect, owner),
                1,                                   // one segment: Evernight (长夜月)'s ultimate is "single-target segment count: 1" in its own split
                DamageType.NORMAL,                   // a real attack by the summon, not additional damage
                skill.getData().getEffect(),         // the shape the skill itself declares (AoEAttack)
                AttributeType.fromString(effect.getAttribute()),
                // Note: The toughness too, and for the same reason as the element and the shape: it is written down
                // once, in this skill's own `stance_list` (141303: `single 0 / all 90`), and the commanded swing
                // is that skill's damage. Note: It has to be here rather than "the caster's cast already removed it":
                // when a cast is DELEGATED (`DELEGATE_DAMAGE`) the executor expands no damage of its own, so
                // the stance would otherwise be dropped on the floor - exactly one of the two must carry it, and
                // this is the one that actually swings. `stanceFor(true)` is the skill's main-target column: for
                // an AOE that is `all` (per victim, matching the caster-side path), for BLAST `single`.
                skill.getData().stanceFor(true));
        attack.execute(battle, summon, victims);
    }

    /**
     * {@code CAST_SKILL}: the resolved target performs <b>one cast, right now</b>, with the numbers of the skill
     * the rule names -- "make it cast 1 time immediately...".
     *
     * <p>Note: It is {@code commandSummon} with three spots loosened, and nothing else (see `aggro RECOVERY-785`):
     *
     * <ol><li>the <b>actor</b> is the resolved target, not the owner's summon;</li>
     * <li>the <b>skill</b> is looked up on that actor, not on the rule owner;</li>
     * <li>no {@code SUMMON_ATTACK} is announced -- that event belongs to a memosprite (the swing itself still
     * announces itself through {@code EnemySkill.execute}).</li></ol>
     *
     * <p>Note: <b>toughness must be carried by itself</b> -  - the precedent's own words: *when a cast is DELEGATED the executor expands no damage of its
     * own, so the stance would otherwise be dropped on the floor*. Note: Do not drop {@code stanceFor(true)}.
     */
    private static void castSkill(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit actor = TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
        SkillType slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = actor.getSkills().get(slot);
        // A MEMOSPRITE keeps its skills by DATA SLOT, not by SkillType: its table is `Summon.skillsByDataSlot`, and
        // "automatically cast [花与箭的舞曲]" is its slot 1. Commanding that cast fails with "has no SKILL skill" because only
        // the character table was consulted. The slot is named with `skill_id`, the same field `REPLACE_SKILL` uses for a data row.
        if (skill == null && effect.getSkillId() != null && actor instanceof Summon from) {
            skill = from.skillAt(effect.getSkillId());
        }
        if (skill == null || skill.getData() == null || !skill.getData().isLoaded()) {
            throw new IllegalStateException(
                    actor.getName() + " has no " + slot + " skill"
                            + (effect.getSkillId() == null ? "" : " and no own skill at data slot " + effect.getSkillId())
                            + ", so a CAST_SKILL effect has nothing to read: the rule names the skill whose numbers the "
                            + "commanded cast uses");
        }
        // What the engine can DELIVER, not "is it a swing": a cast may be a shield or a heal as well,
        // and those go through `SkillExecutor.dispatchNonDamaging`, which reads `skill_effects.json`. A skill the
        // engine has no definition for must fail HERE, loudly -- commanding it would be a cast that does nothing at
        // all, with no symptom (1414's own skill is a shield; 1303/1412's are buffs the table has no entry for).
        if (!SkillExecutor.canDeliver(skill)) {
            throw new IllegalStateException(
                    "CAST_SKILL effect points at " + slot + ", whose effect is " + skill.getData().getEffect()
                            + " and which the engine cannot deliver: a non-damaging skill needs an entry in "
                            + "skill_effects.json saying what it restores or shields, and this one has none -- the "
                            + "commanded cast would do nothing at all, which is exactly the kind of silence this "
                            + "engine refuses");
        }
        boolean damaging = skill.getData().getEffect().isDamaging();
        List<CanHit> victims = new ArrayList<>();
        // Note: Which side the cast reaches is the SKILL's business, not the rule's: a swing lands on the caster's
        // opponents, a shield or a heal on the caster's own camp. (Until this, every commanded cast was treated as a
        // swing -- a shield would have been granted to the ENEMY.)
        for (CanHit unit : damaging ? battle.getOpponents(actor) : battle.getSideOf(actor)) {
            if (unit != null && !unit.isDeath()) {
                victims.add(unit);
            }
        }
        if (victims.isEmpty()) {
            // Note: An empty battlefield stays a no-op, but a caster with NO CAMP is a different thing: `getOpponents` answers an empty list
            // for it, so the commanded cast silently did nothing at all. Say which one it is.
            if (actor.getCamp() == null) {
                throw new IllegalStateException("a commanded cast by " + actor.getName()
                        + " reaches nobody: the unit has no camp, so \"its opponents\" is an empty set rather than an empty battlefield");
            }
            return;                                  // nothing left to reach: an empty battlefield, not a bad rule
        }
        // The AIM, when the rule states one: the commanded cast's main target goes first, because that
        // is the unit `SkillExecutor` reads (`targets.getFirst()`) -- it decides which unit a single-target or blast
        // skill lands on, and which ally a skill's own cast events name as the unit it was aimed at (1414's skill
        // designates THAT ally as [同袍]).
        if (effect.getCastTarget() != null && !effect.getCastTarget().isBlank()) {
            CanHit aimed = TriggerTargets.resolveSelector(effect.getCastTarget().trim().toLowerCase(Locale.ROOT), effect, ctx);
            victims.remove(aimed);
            victims.addFirst(aimed);
        }
        // THE ENGINE'S OWN CAST PATH, and the whole point of this op is that there is only one
        // reading of a skill's row. Hand-building an `EnemySkill` beside `SkillExecutor` gives a second
        // reader that is wrong in three ways at once -- measured, because no test lets the op fire without it:
        //   * it reads the multiplier through `multiplierOf`, which REQUIRES `damage_param`, so all nine shipped
        //     rules (none of which states one) die with a NullPointerException on the first firing;
        //   * it hard-codes `DamageType.NORMAL`, while the engine asks the data (`damageTypeOf`) -- an Elation
        //     skill is settled as ordinary damage;
        //   * it passes ONE multiplier to every victim, so a BLAST skill gives the neighbours the centre's number --
        //     the exact defect section 24.10 names on the other path (1008's row is `[1.92, 0.96]`).
        // `SkillExecutor.execute` answers all three by construction: it reads `params.getFirst()` at the skill's own
        // level, the neighbouring column for BLAST, the element, the base attribute (`damageBaseAttribute`) and the
        // damage type from the row, and it dispatches a non-damaging skill the same way a real cast would.
        // Note: What follows from that: the cast is announced (CAST_SETUP / SKILL_CAST / ULT_CAST / BASIC_ATTACK /
        // ALLY_ATTACK) and settles its own energy, because a commanded cast IS a cast. A skill the engine's model
        // itself mis-reads (an Elation skill's row starts with a HIT COUNT) is mis-read here too -- which is why the
        // Elation auto-casts stay registered rather than shipped (see §24.12).
        // Note: It does NOT spend a skill point: that is the caller's act, and it is why "this Skill does not consume a skill point" holds
        // for free (pinned in `CastSkillTest`).
        //
        // An inserted cast: a "commanded cast" cannot command another one. "A surprise attack does not trigger surprise attack again" is the document's own
        // sentence, and without this guard the copy's own CAST_SETUP would command a copy of the copy -- forever. The
        // engine already keeps the cast STACK: `beginCast` links a new cast to the one it interrupts and `endCast`
        // restores it, so "the open cast is itself a commanded one" is exactly `currentCast().outer() != null`.
        // Note: Skipped, not thrown: this is a rule requirement of the sentence (like `castUltra` refusing inside an extra
        // turn), not an authoring mistake.
        if (battle.currentCast() != null && battle.currentCast().outer() != null) {
            return;
        }
        if (effect.getCastCountedLaughs() != null) {
            // ⚠ Loud when the cast cannot fold it: a fixed laugh count only reaches ELATION damage, and on any other
            // cast it would be a number nobody reads - the silence this interpreter refuses everywhere else. The damage
            // type is a fact about the skill's own row, so this can only be asked HERE (and it is the same reader the
            // settlement uses, `SkillExecutor.damageTypeOf`).
            if (SkillExecutor.damageTypeOf(skill.getData()) != com.laosun.aluminium.enums.DamageType.ELATION) {
                throw new IllegalStateException("CAST_SKILL states cast_counted_laughs="
                        + effect.getCastCountedLaughs() + ", but the commanded " + slot + " settles as "
                        + SkillExecutor.damageTypeOf(skill.getData())
                        + " damage: a laugh count is folded into Elation damage and nowhere else, so this number would "
                        + "be read by nobody");
            }
            battle.markNextCastCountedLaughs(effect.getCastCountedLaughs());
        }
        skill.execute(battle, actor, victims);
        // The moment "after the surprise attack ends" names: the commanded cast has finished, the original continues afterwards. A
        // commanded cast announces the same CAST_SETUP / SKILL_CAST a real one does, so this is the only event that
        // tells it apart -- see `TriggerEvent.INSERTED_CAST_END`.
        // The event carries the unit the commanded cast was AIMED at, not the caster (reader: 1415's
        // "after the surprise attack ends, grant Cerydra (刻律德菈) 1 point of charge"). The actor is still the actor; the target slot now answers "who was it cast on",
        // which is what that sentence needs -- and 1412's own subscriber states no target, so nothing shipped moves.
        CanHit aimed = victims.isEmpty() ? actor : victims.getFirst();
        battle.fireTriggers(TriggerEvent.INSERTED_CAST_END, actor, aimed, 0, 0);
    }









    static String normalizeOp(EffectSpec effect, TriggerSpec spec) {
        if (effect == null || effect.getOp() == null || effect.getOp().isBlank()) {
            throw new IllegalArgumentException(
                    "Trigger effect without an op" + (spec == null ? "" : " (source: " + spec.getSource() + ")"));
        }
        return effect.getOp().trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Whether {@code per_stack} names something a supplier can read later, without an event context.
     *
     * <p>Some {@code per_stack} forms count something about the <b>current hit</b>
     * ({@code target_debuff_count} / {@code target_dot_count} / {@code target_weakness_count} /
     * {@code shielded_count}), so they can only be read while an event is being delivered. Everything
     * else a {@code per_stack} may name (a bare counter name, or {@code self_stacks:<NAME>}) can be
     * read from the carrier alone, and a live aura built on it needs no event.
     */
    private static boolean ctxFreeCounter(EffectSpec effect) {
        return effect.getPerStack() != null && !effect.getPerStack().isBlank()
                && !COUNT_FORMS.contains(effect.getPerStack().trim());
    }

    /**
     * The counter name behind `self_stacks:<NAME>` (or the bare name), for a live reader.
     */
    private static String counterName(EffectSpec effect) {
        String stated = effect.getPerStack().trim();
        return stated.startsWith("self_stacks:") ? stated.substring("self_stacks:".length()).trim() : stated;
    }

    private static double perStackFactor(EffectSpec effect, CanHit target, TriggerContext ctx) {
        // A SELF counter (reader: cone 23053's "for each skill point the wearer consumes ... at most 4 stacks").
        // The bare name below is resolved on the TARGET, which is right for "each [当品] stack" (a counter on the victim) but
        // cannot say "MY counter" when the effect lands on somebody else -- so the condition vocabulary's own prefix is
        // accepted here too: `per_stack: self_stacks:<NAME>`. Same spelling, same meaning, one vocabulary.
        if (effect.getPerStack() != null && effect.getPerStack().trim().startsWith("self_stacks:")) {
            String own = effect.getPerStack().trim().substring("self_stacks:".length()).trim();
            CanHit owner = ctx == null ? null : ctx.owner();
            return own.isEmpty() || owner == null ? 1 : owner.getBuffManager().stacksOf(own);
        }
        String name = effect.getPerStack();
        if (name == null || name.isBlank()) {
            return 1;
        }
        return switch (name.trim()) {
            case "target_debuff_count" -> target.getBuffManager().debuffCount();
            case "target_dot_count" -> target.getBuffManager()
                    .countBuffs(com.laosun.aluminium.models.buff.DotBuff.class);
            // "for each character on the field holding a shield" (cone 21043): OUR units currently holding a shield. A LIVE count,
            // read where the effect is evaluated -- it needs the battlefield because a unit does not know its own side.
            case "target_weakness_count" -> {
                int weak = target instanceof com.laosun.aluminium.models.enemy.Enemy enemy ? enemy.weaknessCount() : -1;
                yield Math.max(weak, 0);
            }
            case "shielded_count" -> ctx == null || ctx.battle() == null ? 0
                    : ctx.battle().allies.stream().filter(unit -> unit.getShield() > 0).count();
            default -> target.getBuffManager().stacksOf(name.trim());
        };
    }

    /**
     * Settles a {@code MODIFY_ATTR} effect: "the target's {@code attribute} changes by
     * {@code percent} for {@code turns} turns" -- the generic stat buff / debuff.
     *
     * <p><b>Why this op matters more than its size suggests.</b> 2 of the 93 characters have a
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
     * @param battle the running battle
     * @param effect the effect ({@code attribute} / {@code percent} / {@code turns} or
     *               {@code permanent}, optional {@code max_stacks}, {@code target})
     * @param ctx    the context
     */
    private static void modifyAttr(Battle battle, EffectSpec effect, TriggerContext ctx) {
        AttributeType attribute = AttributeType.fromString(effect.getAttribute());
        // A derived magnitude - "the raise is equal to X% + Y% of <somebody's attribute>". It is computed once,
        // when the rule fires, and it is an ABSOLUTE number in the target attribute's units: the documents that
        // need this state a value ("equal to 12% of Sunday's CRIT DMG plus 8%"), not a share of the target's base.
        // Note: Which is why it does not go through `statModifier`'s usual base-attribute convention (`add_percent`).
        // ? A flat `amount` is ABSOLUTE too: measured, `amount: 50` on SPEED produced +5500, i.e. 50 x the target's base speed, because a
        // plain modifier is fed to `statModifier` as an `add_percent` fraction. The same reasoning the derived case already follows applies here.
        // A rule on DEALING_DAMAGE may say its modifier belongs to THIS hit: "when dealing damage to <some kind of target>,
        // ignore X% of their DEF" (relic 108/4) is a property of the instance, not of the wearer -- and `Battle` fires
        // DEALING_DAMAGE (2539) before it settles the defence zone (2551), so a rule really can still change it.
        // Opt-in (`instance: true`) because seven shipped rules already use MODIFY_ATTR on that event.
        if (Boolean.TRUE.equals(effect.getInstance()) && ctx.damage() != null) {
            // An optional damage-type scope: "<b>break damage</b> dealt to the target ignores X% of their DEF" (relic 119/4)
            // and "<b>follow-up attacks</b> ignore X% of their DEF". The instance already knows its own type, so this is a comparison, not a new dimension.
            com.laosun.aluminium.enums.DamageType instanceScope = parseDamageType(effect, "MODIFY_ATTR", null);
            if (instanceScope != null && ctx.damage().getType() != instanceScope) {
                return;   // the instance belongs to another damage type: this rule does not speak about it
            }
            if (attribute == AttributeType.DEFENCE_IGNORE) {
                double instanceMagnitude = effect.getPercent() == null ? 0 : effect.getPercent();
                instanceMagnitude *= ctx.target() == null ? 1 : perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addDefenceIgnore(instanceMagnitude);
                return;
            }
            if (attribute == AttributeType.CRIT_CHANCE) {
                ctx.damage().addCritChance(effect.getPercent() == null ? 0 : effect.getPercent());
                return;
            }
            if (attribute == AttributeType.CRIT_ATTACK) {
                // per_stack belongs here too (reader: cone 23016's "each stack of [温驯] raises CRIT DMG"):
                // the two branches beside this one already scaled by the factor, and this one did not -- measured, a rule at
                // one layer and at two layers produced the SAME number. Same shape as DEFENCE_IGNORE / the boost channel.
                double critDamage = effect.getPercent() == null ? 0 : effect.getPercent();
                critDamage *= ctx.target() == null ? 1 : perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addCritDamage(critDamage);
                return;
            }
            // "damage dealt is raised by X%" as a property of THIS hit (reader: cone 21043's
            // "for each character on the field holding a shield"). It reuses the same boost channel BOOST_DAMAGE writes, so the
            // number this rule adds is read by the damage formula exactly like any other boost of that instance -- and
            // `per_stack` is honoured, which is what makes a LIVE count ("the field right now") expressible at all.
            // Note: Before this, such a rule had to be a written modifier, i.e. a snapshot taken at cast time.
            if (attribute == AttributeType.ALL_DAMAGE_TYPE_BOOST) {
                double instanceBoost = effect.getPercent() == null ? 0 : effect.getPercent();
                instanceBoost *= ctx.target() == null ? 1 : perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addBoost(instanceBoost);
                return;
            }
            throw new IllegalStateException("Op MODIFY_ATTR with instance=true supports DEFENCE_IGNORE, CRIT_CHANCE and CRIT_ATTACK on "
                    + "DEALING_DAMAGE; " + attribute + " has no instance-level slot yet");
        }
        boolean derived = (effect.getScale() != null && !effect.getScale().isBlank()) || effect.getPercent() == null;
        // ? A plain modifier is a share OR a flat value: `amount` alone means "raise the attribute by this many points", which the
        // validator enforces as exactly one of the two.
        // Note: The share may come from the skill parameter now, so "is a share stated" is NOT `percent != null`:
        // asking only that sent a `percent_from_cast_param` modifier down the flat `amount` arm and unboxed a null. A share is
        // `percent` OR `percent_from_cast_param`; the derived flag above already covers the latter.
        double magnitude;
        // Note: `percent_from_skill_param` is the THIRD way to state a share, and this is the very condition the comment above warns
        // about: a share spelling left out of it sends the effect down the flat `amount` arm and unboxes a null. Measured: that is exactly what happened.
        if (effect.getPercent() != null || effect.getPercentFromCastParam() != null
                || effect.getPercentFromSkillParam() != null || effect.getPercentFromResource() != null) {
            magnitude = derived ? TriggerMagnitudes.derivedMagnitude(effect, ctx) : effect.getPercent();
        } else {
            magnitude = effect.getAmount();
        }
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        int maxStacks = effect.stackCap() == null ? 1 : effect.stackCap();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            // ? A stated ceiling, in the target's own unit. The two forms differ:
            //   * a DERIVED magnitude is already absolute, so the ceiling is compared directly;
            //   * a SHARE scales off the target's BASE value, so the ceiling is divided by that base first
            //     -- comparing a share with an absolute number (the first attempt) compares nothing.
            double applied = magnitude;
            // "each [当品] stack additionally raises Jade (翡翠)'s ATK by 0.50%": a SHARE times a count. A plain modifier's magnitude is
            // a share of the target's BASE, while the derived path yields ABSOLUTE units -- so "each stack ... raises X%" on a flat
            // attribute had no spelling. Read PER TARGET, because the count differs from one victim to the next.
            if (effect.getPerStack() != null && !effect.getPerStack().isBlank()) {
                // `per_stack` may name the target's DEBUFF COUNT, not only a counter: "for each 1 negative effect the enemy target suffers,
                // the wearer's <attribute> dealt to it is additionally raised by X%, up to N stacks" is cone 21001 / 23020, and 116/4 counts a target's DoTs. A named
                // counter keeps its old meaning, so nothing existing moves.
                // ONE definition, one use: this block computes per_stack itself and knows
                // only `target_debuff_count` plus a generic counter, so `self_stacks:`, `shielded_count`,
                // `target_dot_count` and `target_weakness_count` all fell through to
                // `stacksOf("shielded_count")` = 0 -- a silent multiplier of zero on this path. Measured
                // beforehand: 86 shipped uses live on this path (81 bare counters, 5 x target_debuff_count),
                // and every one of them resolves identically through perStackFactor, so merging changes no
                // shipped behaviour -- it removes the trap.
                applied *= perStackFactor(effect, target, ctx);
            }
            if (derived) {
                applied = TriggerMagnitudes.applyDerivedCeiling(effect, ctx, applied);
            } else if (effect.getCapScale() != null || effect.getCapPercent() != null) {
                double ceiling = TriggerMagnitudes.resolveScale(effect.getCapScale(), effect.getCapPercent(), ctx);
                double base = target.getAttribute(attribute).baseValue();
                if (base > 0) {
                    applied = Math.min(applied, ceiling / base);
                }
            }
            AbstractBuff buff = withSource(withTickOwner(
                    withLifetime(statModifier(attribute, applied, turns, permanent, maxStacks, derived), effect),
                    effect, ctx), ctx);
            // "while in the [协奏] state, raise the ATK of all of us ...": the boost lasts as long as the STATE, and the state ends
            // when a countdown's turn arrives -- a lifetime no `turns` can state. Naming the modifier is what lets
            // `REMOVE_STATE <the same name>` take it off; Note: without a name the modifier is skipped by that loop, i.e.
            // unnamed boosts keep exactly the lifetime they always had.
            // The ask travels onto the buff, so `BuffManager` can honour it without knowing about effects.
            if (Boolean.TRUE.equals(effect.getCoexist())) {
                buff.setKeepsSiblings(true);
            }
            if (Boolean.TRUE.equals(effect.getPerStackLive())) {
                // "raise X% for each 1 stack held ..." as a SUSTAINED aura (reader family: fourteen documents). The share is
                // asked for on every read, so the aura follows the count -- a snapshot would only be right at the instant it
                // was taken, and re-attaching on every change would stack the buff itself.
                boolean derivedCounter = effect.getScale() != null
                        && effect.getScale().trim().startsWith("self_stacks:");
                if (!derivedCounter && !ctxFreeCounter(effect)) {
                    throw new IllegalStateException(
                            "per_stack_live only exists for the counter forms (self_stacks:<NAME> or a bare name), but this "
                                    + "rule states per_stack='" + effect.getPerStack() + "' and scale='" + effect.getScale()
                                    + "'; the per-hit count forms are read from an event context and (source: "
                                    + ctx.ruleId() + ")");
                }
                if (buff instanceof com.laosun.aluminium.models.buff.StatModifierBuff liveBuff) {
                    if (derivedCounter) {
                        // The derived form is ABSOLUTE: percent x layers (+ amount), read on every computation. Building it from
                        // `magnitude` would square the count, because magnitude already contains it.
                        String own = effect.getScale().trim().substring("self_stacks:".length()).trim();
                        double extra = effect.getAmount() == null ? 0 : effect.getAmount();
                        double pct = effect.getPercent() == null ? 0 : effect.getPercent();
                        liveBuff.makeLive(() -> pct * target.getBuffManager().stacksOf(own) + extra);
                    } else {
                        String counter = counterName(effect);
                        liveBuff.makeLive(() -> magnitude * target.getBuffManager().stacksOf(counter));
                    }
                }
            }
            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                buff.setBuffName(effect.getBuff().trim());
            }
            // "with a 100% base chance, additionally lower the target's all-type RES by 10.00%": an attribute modifier may be ROLLED,
            // exactly like APPLY_DOT / APPLY_BUFF / APPLY_STATE / MODIFY_DAMAGE_TAKEN already are -- 1corpus documents
            // roll a base chance before an attribute change. Note: Unstated = attached directly, so no existing file changes
            // (no shipped file uses base_chance on MODIFY_ATTR).
            attachRolled(battle, target, buff, effect, ctx);
        }
    }









    /**
     * Whether the buff this effect creates must <b>not be ticked</b>: either it has no turn limit at all
     * ({@code permanent: true}) or its end is an event ({@code until}), which no turn boundary can bring
     * forward.
     *
     * <p><b>Why an event-bound buff counts as "permanent".</b> That flag means "never ticked"
     * ({@link AbstractBuff#isPermanent()}), which is exactly right here: a buff that ends when the first attack is cast
     * must survive any number of turn boundaries, and giving it a placeholder turn count instead would make it
     * expire on the first {@code afterMove} - a wrong answer that looks like a working rule. The flag's name
     * reads as "for the rest of the battle" in the JSON (where it is what authors write), but mechanically it
     * is "no turn limit", and an event ends this one.
     *
     * <p>Note: Also the reason this helper exists: {@code until} leaves {@code turns} null, and reading
     * {@code getTurns()} into an {@code int} would throw at fire time - inside a battle, where the project
     * puts nothing.
     */
    private static boolean unticked(EffectSpec effect) {
        return Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect);
    }

    static boolean eventBound(EffectSpec effect) {
        return effect.getUntil() != null && !effect.getUntil().isEmpty();
    }

    /**
     * Attaches the {@code "until": ...} a rule names to a buff it just created (see {@link EffectSpec#getUntil()}).
     *
     * <p>A no-op when the effect states no lifetime, so every buff that does not ask for one behaves exactly
     * as before - the field's default is an <b>empty</b> set of ending events.
     */
    private static AbstractBuff withLifetime(AbstractBuff buff, EffectSpec effect) {
        buff.setLifetimes(lifetimesOf(effect));
        return buff;
    }

    /**
     * Anchors a fresh buff's duration to another unit's turns when the rule asks for it ({@code "ticks_on"},
     * and leaves it on its carrier otherwise.
     *
     * <p>Separate from {@link #withLifetime} because the two say different things about time: {@code until} names the
     * <b>event</b> that ends a buff, this names <b>whose turn boundary</b> spends it. A rule may state either, and
     * the state names are the same ones the rest of the op vocabulary uses.
     *
     * <p>Two spellings since, and the second one is what "... removed when it disappears" is built on:
     * <ul>
     *   <li>{@code "self"} - the rule owner's turns (星期日's [蒙福者], 1321's domain, 缇宝's zone);</li>
     *   <li>{@code "summon"} - <b>the owner's memosprite</b> (1402's [至高之姿] "<b>when the Garmentmaker (衣匠) disappears</b>, Aglaea (阿格莱雅) removes
     *       the [至高之姿] state", and 140/1415's "removed as the dead dragon disappears").</li>
     * </ul>
     * Note: <b>The anchor IS the clock</b> ({@code BuffManager.removeBuffsAnchoredTo} asks {@code buff.ticksOn(dead)}),
     * so this one field delivers both halves for free: the duration is spent on that unit's turn boundaries, and when
     * it dies the buff goes with it ({@code Battle.releaseBuffsAnchoredToTheDead}, which already sweeps both camps).
     * Note: A missing summon is a <b>loud</b> error ({@link TriggerTargets#requireSummon}) rather than a silent fall-back to the owner:
     * "my memosprite's turns" is meaningless when there is none, and a rule that quietly anchored to herself instead
     * would keep a buff alive for the rest of the battle.
     */
    private static AbstractBuff withTickOwner(AbstractBuff buff, EffectSpec effect, TriggerContext ctx) {
        if (effect.getTicksOn() != null) {
            buff.setTickOwner("summon".equals(effect.getTicksOn().trim()) ? TriggerTargets.requireSummon(ctx) : ctx.owner());
        }
        return buff;
    }


    /**
     * The lifetimes a validated effect names; <b>empty</b> when it names none.
     *
     * <p>Every name has already been checked against {@link TriggerValidator} at load time, so anything else here is an
     * engine-side inconsistency rather than a content error - hence {@link IllegalStateException}, exactly as
     * before. A rule may name several ("basic attack <b>or</b> Skill"): they are collected into one set, and the buff ends at
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
                case "next_elation_skill" -> AbstractBuff.Lifetime.NEXT_ELATION_SKILL;
                // The cast being delivered right now: raised inside the cast window, dropped when its events are done.
                case "cast_end" -> AbstractBuff.Lifetime.CAST_END;
                case "turn_end" -> AbstractBuff.Lifetime.TURN_END;
                case "next_turn_start" -> AbstractBuff.Lifetime.NEXT_TURN_START;
                default -> throw new IllegalStateException(
                        "Lifetime '" + name + "' passed validation but has no implementation");
            });
        }
        return lifetimes;
    }


    /**
     * Settles an {@code APPLY_BUFF} effect: the target enters a <b>named state</b> for a duration.
     *
     * <p><b>Why a state and not a stat buff.</b> The rule texts say "while in the [协奏] state"/"in the [转魄] state"/
     * "the enemy target in the shock state" - a <i>fact about the unit</i> that other rules read. {@code MODIFY_ATTR}
     * already covers "the target's ATTACK changes"; this op covers "the target is now in a state", and the
     * two compose: the state is applied here, and whatever it changes is a separate effect conditioned on
     * {@code self has_state ...} / {@code target has_state ...}. Keeping them apart means one state can drive
     * several effects (and several rules can read the same state) without the state itself knowing anything.
     *
     * <p>The state is an ordinary {@link StateBuff}, so it gets the whole buff lifecycle for free:
     * duration counted down on the owner's turns, refresh on re-application, removal by
     * {@code BuffManager.clearAll()}, and visibility to {@code hasBuff}-style queries. Applying a
     * <b>different</b> state does not evict this one - {@code StateBuff.isSameKind} compares names, not
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
        // How many instances this application carries (reader: 1513's reward, whose state's INSTANCE COUNT is
        // the [符点] it spent). A plain state refreshes on re-application, so only a STACKABLE one is repeated --
        // otherwise the loop would be a no-op dressed as a feature.
        int times = 1;
        if (Boolean.TRUE.equals(effect.getStackable())) {
            if (effect.getScale() != null && effect.getScale().trim().startsWith("party_resource:")) {
                times = Math.max(1, (int) Math.round(TriggerMagnitudes.resolveScale(effect.getScale(),
                        effect.getPercent() == null ? 1 : effect.getPercent(), ctx)));
            } else if (effect.getAmount() != null) {
                times = Math.max(1, (int) Math.round(effect.getAmount()));
            }
        }
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            for (int instance = 0; instance < times; instance++) {
                AbstractBuff buff = withSource(withTickOwner(
                        withLifetime(Boolean.TRUE.equals(effect.getStackable())
                                ? new StackableStateBuff(state, turns, permanent,
                                effect.getMaxStacks() == null ? 1 : effect.getMaxStacks())
                                : Boolean.TRUE.equals(effect.getDefersDeath())
                                ? new DeferredDeathBuff(state, turns, permanent)
                                : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);
                // "will not enter its own turn" rides on the state itself: a turn is not something a state could give back
                // later, so the flag and the state share one lifetime by construction.
                if (Boolean.TRUE.equals(effect.getSuspendsTurns())) {
                    buff.setSuspendsTurns(true);
                }
                // "with a 100% base chance, make the enemy ... enter the [通解] state": when the rule states one, the state is ROLLED (effect resistance
                // included); when it states none, this is the plain attach it has always been -- so no existing file changes.
                attachRolled(battle, target, buff, effect, ctx);
            }
        }
    }

    /**
     * Settles a {@code REMOVE_STACK} effect: takes up to {@code amount} stacks of one attribute's modifier
     * off the target - the "removes 1 stack(s) of this effect" half of effects like relic set 131's.
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
        int amount = (int) Math.round(effect.getAmount());
        boolean byName = effect.getBuff() != null && !effect.getBuff().isBlank();
        AttributeType attribute = byName ? null : AttributeType.fromString(effect.getAttribute());
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (byName) {
                target.getBuffManager().removeNamedStacks(effect.getBuff(), amount);
            } else {
                target.getBuffManager().removeStacks(attribute, amount);
            }
        }
    }

    /**
     * Settles a {@code MODIFY_DAMAGE_TAKEN} effect: "damage taken is raised by X%" (vulnerability)/"damage taken is lowered by X%" (reduction).
     *
     * <p><b>Why it is not {@code MODIFY_ATTR}.</b> Both are zones, not attributes: neither "incoming damage
     * x 1.12" nor " x 0.92" exists as an {@code AttributeType}, and the engine's two zone buffs
     * ({@link VulnerabilityBuff} / {@link ReductionBuff}) inject into the settlement of every hit instead of
     * holding state - which is also why relic set 106's 2-piece ("Reduces DMG taken by 8%", a passive with no
     * turn count) sat in {@code _unmodelled.json} until this op existed.
     *
     * <p><b>The sign picks the zone</b>, the same convention {@code MODIFY_ATTR} uses for buff/debuff: a
     * positive percent is vulnerability on the defender (a debuff), a negative one is reduction (a buff).
     * One op rather than two, because the data spells them as one family and the two are mutually exclusive
     * readings of one number - a content author writing {@code -0.08} means damage reduction, and both buffs enforce
     * their own sign so a mistake cannot end up as the other zone.
     *
     * @param effect the effect ({@code percent}, exactly one of {@code turns} / {@code permanent}, optional
     *               {@code target})
     * @param ctx    the context
     */
    private static void modifyDamageTaken(Battle battle, EffectSpec effect, TriggerContext ctx) {
        // Note: A DERIVED magnitude, not `percent`: "at 1 stack raise the damage the enemy takes by 15.00%, and thereafter each additional 1 stack raises it by
        // 5.00%" (1218 椒丘) is `amount + percent x layers`, so a zone reads its own value the same way a MODIFY_ATTR
        // does. Before this, a `scale` on MODIFY_DAMAGE_TAKEN loaded fine and was silently IGNORED -- worse than refusing
        // it. A plain zone (no scale) still takes `percent` exactly as before.
        boolean derived = effect.getScale() != null && !effect.getScale().isBlank();
        double percent = derived ? TriggerMagnitudes.derivedMagnitude(effect, ctx) : effect.getPercent();
        boolean permanent = unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        // "break damage taken is raised": an optional damage-type scope, spelled with the enum's own names.
        com.laosun.aluminium.enums.DamageType scope = parseDamageType(effect, "MODIFY_DAMAGE_TAKEN", null);
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            AbstractBuff zone = withSource(withTickOwner(withLifetime(percent > 0
                    ? new VulnerabilityBuff(turns, percent, permanent, scope)
                    : new ReductionBuff(turns, -percent, permanent, scope), effect), effect, ctx), ctx);
            // Note: `withTickOwner` joined this chain on. It was missing, so `ticks_on` was a SILENT no-op on
            // this op: 1218's zone carried `ticks_on: "self"` and its clock still belonged to the carrier, which meant
            // "the zone is removed when 椒丘 enters the unable-to-fight state" could never fire -- measured at exactly 1.40x before and after she fell
            // (AnchorDeathTest). The other four buff-creating ops had it; this one did not.
            // A NAMED modifier is what REMOVE_STATE can take off (the same field MODIFY_ATTR has used since;
            // BuffManager.removeState's last loop walks every buff that carries a name). Without it a zone-scoped
            // "damage taken is lowered" could only be spelled `permanent`, i.e. it would stay on for the rest of the battle --
            // which is why 150's Soul, Tempered ad Mortem (千锻魂) needs it: the reduction lasts as long as the zone's countdown, not forever.
            if (effect.getBuff() != null && !effect.getBuff().isBlank()) {
                zone.setBuffName(effect.getBuff().trim());
            }
            // "with a 100% base chance, raise the DoT damage taken by ... by 30%" (1108 桑波): a zone may be ROLLED, through the same
            // resist pipeline APPLY_BUFF/APPLY_DOT use. Note: Unstated = attached directly, so no existing file changes.
            attachRolled(battle, target, zone, effect, ctx);
        }
    }

    /**
     * The owner's Eidolon rank, or {@code 0} when the owner is not a character.
     *
     * <p>Trigger tables only ever belong to characters ({@code Battle.fireTriggers} walks
     * {@code battle.characters} and enemies have none), so the branch is about the type system rather than about
     * a real case - but a hand-built context can carry anything, and "0 ranks" is the answer that never unlocks
     * an Eidolon by accident.
     */
    private static int eidolonRankOf(CanHit owner) {
        return owner instanceof Character character ? character.getEidolonRank() : 0;
    }

    /**
     * Settles a {@code BOOST_DAMAGE} effect: raises (or lowers) <b>the damage instance being settled</b>.
     *
     * <p>This is the op behind "the damage dealt to a target in state X is raised by Y%", and it exists because that sentence is
     * not a buff: it applies to the hits that happen to satisfy a condition, so the natural home for the state
     * is the hit itself. The rule fires on {@link TriggerEvent#DEALING_DAMAGE}, the engine hands the pending
     * instance over in the context, and this adds one modifier to its DMG-boost zone - after which the instance
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
        // An addition labelled ELATION belongs to an Elation instance, so it carries that instance's three
        // factors (ROADMAP:1262: an Elation base is `... x (1+欢愉度) x (1+增笑) x (1+笑点x5/(笑点+240))`).
        // They are read off the ATTACKER -- the unit whose instance this is -- not the rule owner.
        // `elation_base` is a table lookup, not an attribute, so it is resolved here rather than by
        // `derivedMagnitude` -- measured: that reader returned null and the settlement NPE'd.
        double magnitude = "elation_base".equals(effect.getScale() == null ? "" : effect.getScale().trim())
                ? TriggerMagnitudes.elationBase(ctx) * (effect.getPercent() == null ? 1 : effect.getPercent())
                : TriggerMagnitudes.derivedMagnitude(effect, ctx);
        com.laosun.aluminium.enums.DamageType only = parseDamageType(effect, "ADD_DAMAGE", null);
        if (only == com.laosun.aluminium.enums.DamageType.ELATION) {
            CanHit attacker = damage.getAttacker();
            if (attacker == null) {
                throw new IllegalStateException(
                        "Op ADD_DAMAGE states damage_type ELATION, but the instance being settled has no attacker"
                                + " to read 欢愉度 and 增笑 from");
            }
            magnitude *= 1 + attacker.getAttribute(
                    com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get();
            magnitude *= 1 + attacker.getAttribute(
                    com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_AMP).get();
            if (ctx.battle() != null) {
                magnitude *= laughFactor(ctx, effect);
            }
        }
        damage.addFlat(magnitude);
    }

    private static void boostDamage(EffectSpec effect, TriggerContext ctx) {
        Damage damage = ctx.damage();
        if (damage == null) {
            // Unreachable through a validated table (requireEvent pins the event), but `apply` is public and a
            // hand-built context can still lack an instance: better a loud error than a silent no-op.
            throw new IllegalStateException(
                    "Op BOOST_DAMAGE needs the damage instance being settled, but this context carries none");
        }
        // Note: A `damage_type` here must actually SCOPE the boost: the loader accepted it while this method
        // ignored it, so "follow-up attacks only" silently raised every hit -- measured: an ordinary hit against a low-HP
        // target came out 1.24x instead of 1.0x). Same semantics as MODIFY_ATTR's instance route.
        com.laosun.aluminium.enums.DamageType only = parseDamageType(effect, "BOOST_DAMAGE", null);
        if (only != null && damage.getType() != only) {
            return;
        }
        // Both a stated `scale` and a stated ceiling must be read (reader: light cone 23062).
        // Note: This method ignores `damage_type` the same way -- see the comment above.
        // Through `shareOf`, like every other op that reads a share (; measured: `percent_from_resource` passed validation -- `TriggerValidator.requirePercent` has admitted it since round
        // 83 -- and then died here with "getPercent() is null", so the op validated against a vocabulary it did not apply).
        double magnitude = effect.getScale() == null || effect.getScale().isBlank()
                ? TriggerMagnitudes.shareOf(effect, ctx)
                : TriggerMagnitudes.derivedMagnitude(effect, ctx);
        damage.addBoost(TriggerMagnitudes.applyDerivedCeiling(effect, ctx, magnitude));
    }

    /**
     * The {@code per_stack} forms that count something about the <b>current hit</b>, and therefore have no
     * context-free reader: {@code ctxFreeCounter} refuses them because a live count needs the event. Everything
     * else a {@code per_stack} may name (a bare counter name, or {@code self_stacks:<NAME>}) can be read from
     * the carrier alone.
     */
    private static final Set<String> COUNT_FORMS = Set.of(
            "target_debuff_count", "target_dot_count", "target_weakness_count", "shielded_count");



    /**
     * The one derived scale that is not an attribute: {@code MODIFY_ATTR}'s magnitude may be a share of the rule
     * owner's <b>maximum energy</b> (relic set 328's "for each 1 point above"). Same spelling family as the condition variable
     * above, and deliberately not an {@code AttributeType}.
     */
    static final String SELF_MAX_ENERGY = "self_max_energy";

    /**
     * "for each 1 point of energy consumed" (light cone 23062): the points are the energy THE CAST ITSELF spent.
     *
     * <p>Note: Not an attribute, so it cannot go through the operator "scaleAttribute" - its branch below returns first.
     * The value rides on the damage instance because DEALING_DAMAGE is the only event that hands it over.
     * Note: A missing instance and a non-ultimate both read 0, which is the right answer for both: this
     * clause adds nothing when no energy was spent.
     */
    static final String CAST_ENERGY_SPENT = "cast_energy_spent";

    /**
     * "raise X% for each 1 point consumed/lost ...": a modifier's magnitude that follows the event.
     *
     * <p>Sits beside {@link #CAST_ENERGY_SPENT} on purpose - the same shape bound to the cast - and, like it,
     * is deliberately not an {@code AttributeType}.
     */
    static final String EVENT_AMOUNT = "event_amount";

    /**
     * {@code counted_laughs}: the laugh points Elation damage is computed from, as a SCALE.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's Eidolon 4 - 「【崩坏级伤害演示】造成的欢愉伤害额外计入 5 倍的【笑点】」. The same
     * quantity {@code TriggerTable}'s numeric variable of that name reads for conditions; here it is the base of an
     * additive Elation rider, which is what "counts five times as many laughs" buys.
     */
    static final String COUNTED_LAUGHS = "counted_laughs";


    /**
     * The battle's SKILL POINTS, under the name content spells: 「战技点」.
     *
     * <p>Not a {@code Resource}: the battle owns it ({@code Battle.skillPointPolicy}), and every other counter this DSL
     * touches lives in a {@code ResourceManager}. So {@code GAIN_RESOURCE} reaches it through its own branch, which is
     * also why the name appears nowhere in the declared-resource checks - a character does not declare it, the battle has
     * it. Reader: 1506's 【爆爆爆炸蛋】.
     */
    private static final String SKILL_POINT = "战技点";

    /**
     * "when SPD is greater than or equal to X ... for each 1 point of SPD above that ...": {@code self_attr_above:<ATTRIBUTE>:<threshold>}.
     *
     * <p>Like {@link #SELF_MAX_ENERGY} - the same shape, with the threshold stated instead of implied by the
     * energy cap - and, like it, not an {@code AttributeType}.
     */
    static final String ABOVE_PREFIX = "self_attr_above:";
    /**
     * {@code steps:<COUNTER>:<N>} - "every N points of a counter", as the number of WHOLE steps: {@code floor(value / N)}.
     *
     * <p>Reader evidence gathered before building it (the house rule is to count readers first): 6 places across 5 files -
     * both Trailblazer documents 「每累计恢复 10 点能量」, GLOSSARY_EXTRA 「每累计损失 30% 生命值」, RELICS 「每累计获得 5 点笑点，造成的
     * 欢愉伤害…」 and two WEAPONS entries.
     *
     * <p>⚠ Being a FLOOR is the whole point: 1506's Eidolon 2 pays 「每使【隐藏分】提高 120 点…获得 1 个额外回合」, and a single gain
     * of 400 is THREE milestones - which a per-event ">= 120, then spend 120" check silently reports as one.
     */
    static final String STEPS_PREFIX = "steps:";




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
            // "restore HP equal to X% of each one's own lost HP" (cone 21023): "each one's own" is what
            // `target` already means here -- the heal is granted per recipient -- so the reading is `target`'s max HP minus
            // its current HP, never the healer's and never a battle-wide total (that is 1205's separate cumulative idea).
            case "target_lost_hp" -> Math.max(0, target.getMaxHp() - target.getCurrentHp()) * share + flat;
            case "owner_max_hp" -> TriggerMagnitudes.ownerAttributeOf(ctx, "owner_max_hp", AttributeType.HEALTH) * share + flat;
            // "consume HP equal to 35% of Mydei (万敌)'s current HP" (reader: 1404's two enhanced skills). The
            // vocabulary had max-HP shares and a lost-HP share, and neither says "a share of what is left": 35% of MAX on a
            // wounded unit is a plausible-looking wrong number, which is exactly the kind this engine refuses to guess.
            // Note: `target` is the unit being resolved (for CONSUME_HP, the spender), matching the `target_*` family.
            case "target_current_hp" -> target.getCurrentHp() * share + flat;
            // March 7th (三月七) 100102: a shield of "5% of DEF + 60" -- a share of the maker's DEFENCE plus a constant.
            case "owner_def" -> TriggerMagnitudes.ownerAttributeOf(ctx, "owner_def", AttributeType.DEFENCE) * share + flat;
            // ? 1414's shield is "20.00% of ATK + 400": the same derived shape, off ATTACK. Seven documents state
            // an ATK-scaled shield or heal and the vocabulary had no name for it.
            case "owner_attack" -> TriggerMagnitudes.ownerAttributeOf(ctx, "owner_attack", AttributeType.ATTACK) * share + flat;
            // "for each 1 point consumed/lost ...": the triggering EVENT's own magnitude.
            // The absolute value, because a spend arrives negative and "per 1 point" counts points.
            default -> throw new IllegalStateException(
                    "Scale '" + scale + "' passed validation but has no implementation");
        };
    }


    /**
     * {@code REMOVE_STATE}: takes the named state off every resolved target - the "only the newest one holds it"
     * half of Sunday (星期日)'s [蒙福者].
     * <p>Note: Removing a state that is not there is <b>not</b> an error: an "only the latest target" rule runs on every
     * cast, and most of those casts find the state on somebody (or nobody) whose removal is simply nothing to do.
     * The op that <i>applies</i> a state to a unit that does not exist is loud ({@code "target": "summon"}); this one
     * has no such unit to miss.
     * {@code TAUNT}: puts the engine's {@link TauntBuff} on every resolved target - "make the target enter the taunt state, lasting 1 turn".
     * <p>The buff is a pure marker, and the constraint it carries is a <b>hard one</b> implemented at target
     * selection (`TargetSelector`): as long as it is attached to a living unit, single-target attacks and the centre
     * of a blast attack can only pick that unit. Note: That is the "taunt" the corpus asks for (Yunli (云璃)'s ultimate puts it
     * on every enemy, Mortenax Blade (千冶-刃)'s skill on one) and it is deliberately NOT the same thing as "raise the chance of being attacked by the enemy"
     * (March 7th (三月七) / Gepard (杰帕德) / Lynx (玲可)), which is a soft weight - that sentence carries <b>no number</b> in any document, so it is
     * registered as a data gap rather than guessed at here.
     * {@code APPLY_BUFF}'s roll, when the rule states one: "with a 100% base chance, make each single enemy target enter the [通解] state" (1106 佩拉).
     *
     * <p>It goes through the <b>same</b> entry point {@code APPLY_DOT} and {@code APPLY_CONTROL} use, so a rolled state meets
     * effect resistance exactly as they do - Note: which is the reason this exists at all: applying the state unconditionally
     * would drop "base chance", and that is a different mechanic rather than a smaller number (100% base chance is still
     * resistible).
     *
     * <p>Note: A plain {@link com.laosun.aluminium.models.buff.StateBuff} reports {@code isDebuff() == false} and carries no
     * {@code debuffClass}, so it meets <b>no class resistance</b> - correct for a state no document calls control or DOT,
     * and the honest limit of what this spelling can say.
     *
     * @return {@code true} when the state was attached (or when the rule stated no chance at all)
     */
    private static boolean attachRolled(Battle battle, CanHit target, AbstractBuff buff, EffectSpec effect,
                                        TriggerContext ctx) {
        if (effect.getBaseChance() == null) {
            target.getBuffManager().addBuff(buff);
            return true;
        }
        return battle.tryApplyDebuff(ctx.owner(), target, buff, effect.getBaseChance(), null);
    }

    /**
     * {@code REMOVE_BUFF}: "remove 1 buff effect from a designated single enemy" - takes up to {@code amount} positive, temporary buffs off
     * each resolved target.
     *
     * <p>Note: Its direction is what makes it a separate op rather than a flag on {@code DISPEL}: that one is defined as
     * "negative effects on the carrier", and the two sentences mean opposite things about the same word ("remove"). Keeping
     * them apart is also what lets each one be validated and tested on its own.
     */
    private static void removeBuffs(Battle battle, EffectSpec effect, TriggerContext ctx) {
        int amount = (int) Math.round(effect.getAmount());
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target != null) {
                target.getBuffManager().removeBuffs(amount);
            }
        }
    }

    /**
     * Reads an optional {@code damage_type} and turns it into the engine's own spelling.
     *
     * <p>Note: A misspelling that silently meant "all damage types" would be a wrong number with no symptom - the same hole the
     * closed-set checks elsewhere in this interpreter exist to close, so the error names every legal value.
     */
    private static com.laosun.aluminium.enums.DamageType parseDamageType(EffectSpec effect, String op, TriggerSpec spec) {
        String raw = effect.getDamageType();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return com.laosun.aluminium.enums.DamageType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the damage type '" + raw + "', which is not one this engine settles; known: "
                            + java.util.Arrays.toString(com.laosun.aluminium.enums.DamageType.values())
                            + (spec == null ? "" : " (source: " + spec.getSource() + ")"));
        }
    }

    /**
     * What the DELTA between two moments must be measured with: the holder's own store when it has the resource,
     * otherwise the party's. Deliberately not {@link #resourceAmount}, which <b>adds</b> the two -- right for a
     * total, wrong for a difference. A self resource read through it looks twice as large: a capture of 4 came
     * out 8.
     */
    private static int measureForTheDelta(TriggerContext ctx, CanHit holder, String id) {
        if (holder != null && holder.getResources().has(id)) {
            return holder.getResources().value(id);
        }
        return ctx.battle() == null ? 0 : ctx.battle().partyResourceValue(id);
    }

    /**
     * How much of a resource exists right now: the unit's own copy plus the party counter.
     *
     * @param battle the running battle ({@code null} answers the unit's own copy alone)
     * @param owner  the unit whose copy is read first
     * @param id     the resource id
     * @return the total, summing both stores when there is a battle to ask
     */
    private static int resourceAmount(Battle battle, CanHit owner, String id) {
        int total = (owner == null || !owner.getResources().has(id)) ? 0 : owner.getResources().value(id);
        return battle == null ? total : total + battle.partyResourceValue(id);
    }

    /**
     * Tells the holder that a resource moved, with the resource's name and the delta: raises
     * {@link TriggerEvent#RESOURCE_CHANGED}.
     *
     * <p>From the op, not from {@code ResourceManager}: the manager owns no battle, so it cannot raise a trigger.
     *
     * <p>The SUBJECT is the holder - the unit whose store moved - because that is what the event is about; a
     * cross-unit grant names the grantee, not the rule's owner (see the GAIN_RESOURCE arm).
     *
     * @param battle   the running battle
     * @param ctx      the context
     * @param resource the resource whose value moved
     * @param delta    how much it moved (positive for a gain, negative for a spend)
     */
    private static void fireResourceChanged(Battle battle, TriggerContext ctx, String resource, int delta) {
        fireResourceChanged(battle, ctx, ctx.owner(), resource, delta);
    }

    /**
     * The same, naming the unit whose store moved - for the ops that may move somebody ELSE's counter.
     *
     * @param holder the unit the moved store belongs to (its own counter, or its copy of a PARTY one)
     */
    private static void fireResourceChanged(Battle battle, TriggerContext ctx, CanHit holder, String resource,
                                            int delta) {
        if (battle == null || holder == null) {
            return;
        }
        // ⚠ The fact is scoped to THIS firing: save and restore, exactly as `Battle.fireStateEnded` does with
        // `lastStateEndedName`. Without it, a rule delivered for this event that moves ANOTHER resource (ordinary -
        // 1506's forwarding clause grants 隐藏分 for a 笑点 move) overwrites the name, and the rules that come AFTER it
        // in the same firing read the wrong one: their `resource_changed:<this one>` condition is false and they
        // silently do not fire. The outer event's rules are about the outer event's resource.
        String previous = battle.lastChangedResource();
        battle.noteChangedResource(resource);
        try {
            battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, holder, holder, 0, delta);
        } finally {
            battle.noteChangedResource(previous);
        }
    }

    /**
     * The authored DOT layer ceiling, or {@code 0} when the rule states none.
     */
    private static int dotStackCap(EffectSpec effect) {
        return effect.stackCap() == null ? 0 : effect.stackCap();
    }


    /**
     * {@code REPLACE_SKILL}: swaps one of the rule owner's skill slots for a data row, for the effect's lifetime.
     *
     * <p>The swap rides on {@link com.laosun.aluminium.models.buff.SkillSwapBuff}, which installs the replacement when it lands
     * and puts the original back when it leaves - so `turns`, `permanent` and `until` ("the next basic attack") are the lifetime
     * machinery this engine already has, not a second one.
     *
     * <p>Note: The replacement is built ONCE per rule firing and shared by every resolved target: it is a skill, not state, and
     * the buff restores whatever each target had in that slot.
     */
    private static void replaceSkill(Battle battle, EffectSpec effect, TriggerContext ctx) {
        Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
        SkillType slot;
        try {
            slot = SkillType.valueOf(effect.getSkill().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException(
                    "REPLACE_SKILL names the slot '" + effect.getSkill() + "', which is not one this engine knows; known: "
                            + java.util.Arrays.toString(SkillType.values()));
        }
        // The replacement keeps the level the slot is currently at (the flow raises slot levels, and an enhanced attack
        // shares its slot's level rather than inventing one).
        Skill current = owner.getSkills().get(slot);
        int level = current == null ? 1 : Math.max(1, current.getLevel());
        Skill replacement = new com.laosun.aluminium.models.skill.DefaultSkill(owner.getCid(), effect.getSkillId(), level);
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target instanceof Character character) {
                character.getBuffManager().addBuff(withSource(withLifetime(
                        new com.laosun.aluminium.models.buff.SkillSwapBuff(slot, replacement), effect), ctx));
            }
        }
    }

    /**
     * {@code TICK_DOT}: "immediately produce 1 instance of ... damage" - one extra instance of the named state, at the stated share.
     */
    private static void tickDot(Battle battle, EffectSpec effect, TriggerContext ctx) {
        DamageElement element = DamageElement.fromString(effect.getElement());
        // Note: The share comes from `shareOf`, not from `percent` directly: a rule may state it as `percent_from_resource`, and unboxing `percent` here
        // NPEs. `shareOf` is the engine's own resolver for every share spelling.
        double percent = TriggerMagnitudes.shareOf(effect, ctx);
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target != null && !target.isDeath()) {
                battle.tickDotStateNow(target, element, percent);
            }
        }
    }

    private static void taunt(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            // Note: Through the resist pipeline like every other negative state: "make the target enter the taunt state" states no
            // probability, which means a 100% BASE chance - the game still runs it through Effect Hit Rate / Effect RES. Writing
            // the marker directly made the engine's taunt unconditionally certain, which no document says.
            battle.tryApplyDebuff(ctx.owner(), target, withSource(new TauntBuff(effect.getTurns()), ctx), 1.0, null);
        }
    }

    /**
     * {@code APPLY_CONTROL}: "with a 50% base chance, make the enemy target enter the frozen state for 1 turn" - a named control state with a
     * duration, and (optionally) a <b>base chance</b> that runs through the real Effect Hit Rate / Effect RES pipeline.
     *
     * <p><b>What the state does is the engine's business, not the rule's.</b> frozen blocks acting; entangled and imprisoned
     * slow instead - those three facts live in {@code Constant.CONTROL_STATES} (one entry per state, with the
     * resistance key the roll needs), and the rule states only <i>which</i> state, for how long, and how likely.
     * A rule cannot say "frozen but whatever", and a misspelled state is refused at load time rather than attaching
     * nothing.
     *
     * <p><b>Why the roll is per target.</b> A base chance is rolled for each victim - three enemies can see three
     * different outcomes, which is what "each target has its own 50% chance" means and what the rule-level {@code chance}
     * (one roll per firing) could not express. It goes through {@link Battle#tryApplyDebuff}, the same entry point
     * the enemy-side debuff path uses, so the applier's Effect Hit Rate, the victim's Effect RES and its per-state
     * resistance ({@code STAT_CTRL_Frozen} - the reason {@code ControlEffect.resistKey} exists) all apply.
     *
     * <p>Note: The control's <b>per-turn damage</b> ("in the frozen state ... at the start of each turn take Ice additional
     * damage equal to 60% of March 7th (三月七)'s ATK") is <b>not</b> written by this op yet: it needs an "attach a damage-over-time" op of its own, and that
     * is registered rather than folded in here as a field nobody else can use. What this op attaches is the state
     * itself, so "cannot act" is complete.
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
        // Note: ALWAYS through the resist pipeline, even with no `base_chance` stated: "make the target enter the frozen state" with no
        // probability in the text is a 100% BASE chance, which the game still runs through Effect Hit Rate / Effect RES / the
        // state's own resistance. Attaching it directly would have made every "immune to control-class negative states" clause silently
        // ineffective against exactly the controls the documents write without a number.
        double baseChance = effect.getBaseChance() == null ? 1.0 : effect.getBaseChance();
        // ...plus whatever a trace / Eidolon raised it by ("raise the base chance to freeze the enemy target by 15%"), filed on this combatant under
        // THIS rule's id. Read here rather than written into the effect: rules are compiled once and cached per cid,
        // so the stated 0.5 stays the file's number and the amendment is a fact about this battle.
        if (ctx.owner() != null) {
            baseChance += ctx.owner().ruleBaseChanceBonus(ctx.ruleId());
        }
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // The state's own per-turn damage ("in the frozen state ... at the start of each turn take ... Ice additional damage") rides WITH the
            // state: attached in ControlBuff.applyEffect, taken off in its removeBuff, and only ever created on
            // the units the roll let through. Note: The magnitude is read here, once, and frozen into the DOT.
            ControlBuff applied = effect.getElement() == null || effect.getElement().isBlank()
                    ? new ControlBuff(control, effect.getTurns())
                    : new ControlBuff(control, effect.getTurns(), DamageElement.fromString(effect.getElement()),
                    TriggerMagnitudes.dotMagnitude(effect, ctx));
            applied.setSource(ctx.owner());
            // Note: The roll's RESULT is recorded as well as used: "for each 1 target frozen" counts the targets this cast really
            // froze, so a resisted application must not be counted (see Battle.recordCastApplied).
            if (battle.tryApplyDebuff(ctx.owner(), target, applied, baseChance, control.resistKey())) {
                battle.recordCastApplied(control.name());
            }
        }
    }

    /**
     * {@code APPLY_DOT}: "make the target enter the burn state, dealing Fire damage equal to ...#1[i]% of ATK each turn" - a damage-over-time
     * attached by a <b>rule</b>.
     *
     * <p>Before this op only a weakness break could attach one ({@code Battle.attachBreakDot}), so the whole
     * "make the target enter the burn/shock/bleed/wind shear state" family (11 of the 9documents) had no spelling - and March 7th (三月七)'s frozen
     * enemies took no "per-turn Ice additional damage".
     *
     * <p><b>What the rule states.</b> The element (which picks the RES zone and doubles as the state's identity:
     * Fire is burn), the magnitude (flat, or a share of one of the <b>rule owner's</b> attributes) and the turns.
     * The damage is computed <b>once, when it lands</b>, and frozen into the buff - "equal to 60% of March 7th (三月七)'s ATK"
     * means her attack at that moment, not a live link to her panel (the same snapshot rule every derived value
     * follows).
     *
     * <p>A {@code base_chance} rolls per target through the same pipeline as a control ({@link Battle#tryApplyDebuff}),
     * with no specific-resistance key: the data's {@code STAT_*} resistances are per <i>state</i>, and a DOT's
     * state is its element - which the four-element family has no key for. (That is a fact about the data, not a
     * shortcut: adding one later is passing a key here.)
     */
    private static void applyDot(Battle battle, EffectSpec effect, TriggerContext ctx) {
        DamageElement element = DamageElement.fromString(effect.getElement());
        if (element == null) {
            throw new IllegalStateException(
                    "APPLY_DOT ran with element '" + effect.getElement() + "'; the loader validates it against "
                            + "DamageElement, so this is an engine fault");
        }
        double damage = TriggerMagnitudes.dotMagnitude(effect, ctx);
        // Note: The same "always roll" rule as a control: an unstated chance is 100% BASE chance, not "bypasses Effect RES".
        double baseChance = effect.getBaseChance() == null ? 1.0 : effect.getBaseChance();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // A name, when the rule states one: it is what `has_state <name>` asks about (see DotBuff's named constructor).
            // Without it the DOT keeps answering only to its element's state name, which is all the corpus asks for.
            String dotName = effect.getBuff() == null ? null : effect.getBuff().trim();
            DotBuff dot = new DotBuff(ctx.owner(), element, damage, effect.getTurns(), dotStackCap(effect), dotName);
            if (battle.tryApplyDebuff(ctx.owner(), target, dot, baseChance, null)) {
                // The DOT's state name is the document's name for the element (burn), from the one table that maps
                // them - the same spelling "for each 1 target made to enter burn" would count with.
                battle.recordCastApplied(BuffManager.dotStateName(element));
            }
        }
    }




    /**
     * {@code EXTEND_BUFF}: "...'s duration is increased by 1 turn" - lengthen the buffs the rule's <b>owner</b> has already put on
     * each resolved target.
     *
     * <p><b>Why the op names no buff.</b> Every sentence in this family identifies it by its origin instead:
     * "<b>Skill-provided</b> shield duration is increased by 1 turn" (March 7th (三月七) Reinforce (加护)), "<b>the Skill's damage boost effect on the designated ally</b> has its duration increased by 1 turn"
     * (布洛妮娅), "<b>the wind shear state the talent makes the enemy target enter</b> has its duration extended by 1 turn" (桑博), "for our target that <b>already has</b> [生息] ... extend by 1 turn"
     * (白露). The engine already records that fact - {@code AbstractBuff.source}, the applier - and the filter is
     * exact, so the author states the duration and nothing else. 10 of the 9documents use one of the two
     * phrasings.
     *
     * <p>Note: <b>Folding the +1 into the ability it lengthens is the wrong fix</b>, and this op exists so that it is
     * not tempting: the trace's own line would vanish from the data, and the base ability would state a duration
     * that is not its own. The two numbers belong to two rules because they are two sentences (and a trace could
     * one day be gated on its own).
     *
     * <p>Nothing to lengthen is <b>not</b> an error: the rule fires whenever its event happens (usually the same
     * cast that applied the buff), and "the target carries nothing of mine" is an ordinary empty case.
     */
    private static void extendBuff(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // "extend all of one's own buff effects by 1 turn" (readers: 1506's talent and its E2): `EXTEND_BUFF` with an explicit
            // `"kind": "all"`. The origin filter `extendBuffsFrom` applies is deliberate (its note names "the shield provided by the Skill"), so
            // "all" -- whoever applied it -- had no spelling; the implicit filter-less form stays refused by `requireExtendFilter`,
            // because that one would lengthen buffs the sentence never mentions.
            if (effect.getKind() != null && "all".equalsIgnoreCase(effect.getKind().trim())) {
                target.getBuffManager().extendAllBuffs(effect.getTurns());
                continue;
            }
            target.getBuffManager().extendBuffsFrom(ctx.owner(), effect.getBuff(),
                    effect.getAttribute() == null ? null : AttributeType.fromString(effect.getAttribute()),
                    effect.getTurns());
        }
    }

    /**
     * {@code SUPER_BREAK}: "after dealing damage to an enemy target in the weakness broken state, converts this damage's toughness reduction value into 1 instance of X% super break damage"
     * (1321 大丽花 / 8006 开拓者).
     *
     * <p>Note: The magnitude is the <b>instance's intended toughness reduction</b> ({@code ctx.damage().getStance()}), not its
     * damage - the two differ by an order of magnitude, so reaching for the wrong one would still look plausible. It is
     * settled as one {@link DamageType#SUPER_BREAK} instance, taking the zones and resistance of the element the
     * triggering attack used.
     */
    private static void superBreak(Battle battle, EffectSpec effect, TriggerContext ctx) {
        Damage source = ctx.damage();
        if (source == null || source.getStance() <= 0) {
            return;                                   // nothing to convert: an instance that reduces no toughness
        }
        double converted = source.getStance() * effect.getPercent();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            battle.applyAdditionalDamage(ctx.owner(), target, source.getElement(), converted);
        }
    }

    /**
     * {@code BOOST_TOUGHNESS}: "raise this attack's toughness reduction value by 100%" - a timed multiplier on the reduction the target's
     * attacks will cause.
     *
     * <p>Note: The documents say "this attack", and the engine's nearest honest spelling is a one-turn buff on the attacker: the
     * reduction happens inside the settlement of the attack, there is no "this instance only" lifetime for a buff, and a
     * bearer who attacks twice in one turn would have both boosted. That nuance is stated rather than hidden - the
     * alternative (a field on the damage instance) would need the reduction to be part of the instance, which is exactly
     * what the engine's split (damage first, then a second number) avoids.
     */
    private static void boostToughness(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null) {
                continue;
            }
            AbstractBuff boost = withSource(withLifetime(
                    new com.laosun.aluminium.models.buff.ToughnessBoostBuff(effect.getPercent(), effect.getTurns()),
                    effect), ctx);
            target.getBuffManager().addBuff(boost);
        }
    }

    /**
     * {@code APPLY_REGEN}: attaches a {@link com.laosun.aluminium.models.buff.RegenBuff}, whose amount is derived once
     * (the applier's panel) and settled on each of the carrier's turns by {@code Battle.tickRegens}.
     *
     * <p>Note: Its name is required, and not for bookkeeping: "the regeneration effect produced by casting the Skill is extended by 1 turn" (1105 trace 调理) and any
     * removal by name reach it through that string, exactly as a relic set's temporary buff does.
     */
    private static void applyRegen(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String name = effect.getBuff() == null ? "" : effect.getBuff().trim();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // Note: grantAmount, not derivedMagnitude: the regeneration's magnitude is a HEAL amount, so it shares the
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
     * {@code ADD_STACK}: "after our target casts a basic attack/Skill/Ultimate 2 times on an enemy target in the [承负] state ..." - one more mark on a
     * named counter.
     *
     * <p><b>Why an op and not a modifier.</b> A counter must not touch the panel: a {@code MODIFY_ATTR} with a zero
     * magnitude would be a lie about what the buff is, and picking some attribute to hang it on would be worse. So the
     * carrier is {@link com.laosun.aluminium.models.buff.StackBuff}: a buff with a name, a lifetime and no effect.
     *
     * <p>Note: <b>Capped by {@code max_stacks}</b> (default 1, i.e. a plain flag): "2 times" is a threshold the content reads
     * with {@code target_stacks:<name> >= 2}, and without a cap a stray extra event would push the count past it and
     * the rule that resets it would fire twice in a row.
     */
    private static void addStack(Battle battle, EffectSpec effect, TriggerContext ctx) {
        int cap = effect.stackCap() == null ? 1 : effect.stackCap();
        String name = effect.getBuff().trim();
        // Note: How many marks this application carries: "immediately gain 15 stacks of [当品]" (1314 Jade (翡翠)'s technique) grants
        // FIFTEEN at once, and `amount` alone would grant one. The readers are the "gain N stacks" family --
        // 1314 alone states 5 (its talent's follow-up), 15 (its technique), 1 and 3 (its traces). One application still
        // means one mark unless it says otherwise, so no existing file changes.
        int wanted = effect.getAmount() == null ? 1 : Math.max(1, effect.getAmount().intValue());
        // "for each 1 skill point restored, gain 1 stack of [彩焰]" (cone 23021): the marks follow the EVENT's own amount, not the
        // number of times the event fired -- `Battle.gainSkillPoint(2)` raises one event carrying 2, and "each 1" means two
        // marks. `scale: event_amount` + `percent: 1` is the same "share of a live quantity" shape HEAL/SHIELD already use.
        if ("event_amount".equals(String.valueOf(effect.getScale()).trim())) {
            double share = effect.getPercent() == null ? 1 : effect.getPercent();
            wanted = Math.max(1, (int) Math.round(share * ctx.amount()));
        }
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            for (int i = 0; i < wanted; i++) {
                if (target.getBuffManager().stacksOf(name) >= cap) {
                    break;                      // "2 times" is a threshold, not an invitation to keep counting
                }
                boolean permanent = unticked(effect);
                AbstractBuff stack = withSource(
                        withTickOwner(
                                withLifetime(
                                        new com.laosun.aluminium.models.buff.StackBuff(
                                                name, permanent ? 1 : effect.getTurns(), permanent, cap),
                                        effect),
                                effect, ctx),
                        ctx);
                target.getBuffManager().addBuff(stack);
            }
        }
    }

    /**
     * {@code START_COUNTDOWN}: "a [协奏] countdown appears on the action order ... the countdown has a fixed 90 SPD".
     *
     * <p>Its turn is announced as {@link TriggerEvent#COUNTDOWN_TURN}, and the content answers with vocabulary it
     * already has (`REMOVE_STATE` + `EXTRA_TURN self` for "leave the [协奏] state and act immediately") - so this op does one thing only,
     * which is what keeps the countdown reusable for every other countdown sentence in the corpus.
     *
     * <p>Note: The name comes from the rule's own {@code buff} when it states one (it is what the log shows, and nothing
     * reads it back, so it needs no id).
     */
    private static void startCountdown(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String name = effect.getBuff() == null || effect.getBuff().isBlank()
                ? (ctx.owner() == null ? "countdown" : ctx.owner().getName() + " 倒计时")
                : effect.getBuff().trim();
        battle.startCountdown(ctx.owner(), name, effect.getSpeed());
    }


    /**
     * {@code RAISE_SKILL_HITS}: adds damage segments to a data slot of the target (reader: 1405's ode of reason).
     *
     * <p>A negative amount takes them back, which is how "lasting 1 turn" is stated without a new lifetime.
     */
    private static void raiseSkillHits(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null) {
            throw new IllegalStateException(
                    "RAISE_SKILL_HITS ran without a rule owner, so there is no combatant whose hits to raise; this is "
                            + "an engine fault");
        }
        int slot = effect.getSkillId();
        int amount = effect.getAmount() == null ? 0 : (int) Math.round(effect.getAmount());
        for (CanHit target : TriggerTargets.resolveTargets(ctx.battle(), effect, ctx)) {
            target.raiseSkillHits(slot, amount);
        }
    }

    /**
     * {@code RAISE_SKILL_LEVEL}: "Skill Lv. +1""Ultimate Lv. +1" - this battle reads one skill slot one level higher.
     *
     * <p><b>Why an op and not a rule field.</b> The level is not a property of this rule: it changes what <b>another</b>
     * ability reads out of its parameter table (the skill's own execution in {@code SkillExecutor}, a rule-driven
     * {@code DAMAGE}, and a {@code COMMAND_SUMMON} swing). All three go through the one resolver
     * {@link CanHit#skillLevel}, which is the only place the base level and the raises are added together.
     *
     * <p>Note: <b>Per battle, not per character</b> (see {@code CanHit.skillLevelBonus}): the same {@code Character} can be
     * put into a second battle by a stage, so writing the raised level onto the skill would stack once per battle and
     * never come off - the same leak the {@code MODIFY_RULE} amendments were moved off the rule for.
     *
     * <p>Note: <b>{@code BATTLE_START} only</b>, like {@code MODIFY_RULE}: a level raise is a passive fact of the loadout,
     * and a raise applied mid-battle would have to be taken back at a point nobody states.
     */
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
     * {@code MODIFY_RULE}: "raise the number of times the talent's counter effect can trigger per turn by 1" / "raise the base chance to freeze the enemy target by 15%" - a passive that
     * <b>raises a number on another rule of the same file</b>.
     *
     * <p><b>Why it is not a second rule.</b> Both sentences modify something that already exists, and the honest
     * spellings are impossible without saying so: a second {@code per_turn: 3} rule <i>adds</i> firings (the engine
     * would fire 2 + 3 = 5 times a turn rather than raising the cap to 3), and a second {@code base_chance: 0.65}
     * rule would roll <b>twice</b> (1 − 0.5  x  0.35 = 82.5% instead of 65%). Both are numbers that look right from the
     * outside and are wrong in play - the failure mode this project refuses.
     *
     * <p><b>Where the raised number lives.</b> On the <b>combatant</b>, not on the rule: a table is compiled once per
     * cid and a relic's rules are shared by every wearer, so an amendment written onto the rule would leak into every
     * battle in the JVM (the same reasoning as the firing limits in {@code CanHit}). The two consumers are
     * {@code TriggerInterpreter.fire} (per-turn cap) and {@link #applyControl} (base chance) - both read the amendment
     * only when the rule they are running carries the id it was filed under.
     *
     * <p>Note: Which number moves is decided by <b>which field is stated</b>: {@code amount} raises a count, {@code
     * percent} raises a probability. The load-time validator refuses both-at-once, neither, an out-of-range value, a
     * non-integer count, any other field, a non-{@code BATTLE_START} event, an unknown rule id, and a named rule that
     * does not state the number being raised.
     */
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
            // "the Ultimate's duration is additionally increased by 1 turn": the rule's own effect durations, raised where they are read
            // (see amendedEffect). Note: Not an extra rule with a longer `turns`: the two modifiers of one skill are the
            // same kind, so a second one would REPLACE the first rather than stack.
            owner.amendRuleEffectTurns(target, effect.getEffectTurns());
        } else if (effect.getEffectMaxStacks() != null) {
            // "raise the talent's effect max stack count by 2": a CAP, not a value -- and it must be in place before any stack is
            // attached, which it is: an amendment is filed at battle start and the stacks come from later firings.
            owner.amendRuleEffectMaxStacks(target, effect.getEffectMaxStacks());
        } else if (effect.getEffectPercent() != null) {
            // "additionally raise the talent's damage boost effect by 10%": 30% -> 40%, the same rule, raised in place.
            owner.amendRuleEffectPercent(target, effect.getEffectPercent());
        } else if (effect.getEffectPercentFromResource() != null) {
            // The SAME amendment, sized by a resource in basis points (reader: 114151"for each 1% of overflow spent...").
            int basisPoints = resourceAmount(ctx.battle(), owner, effect.getEffectPercentFromResource().trim());
            owner.amendRuleEffectPercent(target, basisPoints / 10000.0);
        } else {
            owner.addRuleBaseChanceBonus(target, effect.getPercent());
        }
    }

    /**
     * {@code RESIST_DEBUFF}: "raise the chance to resist <b>control-class</b> negative states by 35%" / "immune to <b>control-class</b> negative states" - the resolved targets
     * become harder to control (or burn), for the stated duration or for the whole battle.
     *
     * <p><b>Why it is a buff on the carrier.</b> Clara (克拉拉)'s Guardian (守护) is a permanent trace, while Silver Wolf (银狼) LV.999's [防火墻] lasts
     * one turn; a buff covers both, expires by itself, and can be removed by name like any other state.
     *
     * <p>Note: <b>What it does NOT do</b> is touch a specific state: the check lives in
     * {@link Battle#tryApplyDebuff}, which asks the victim for its resistance to the <b>class</b> the incoming state
     * declares ({@code AbstractBuff.debuffClass()}). So a control written tomorrow is covered by a resistance written
     * today - the property that makes "immune to control-class negative states" mean the whole family rather than a list of keys.
     *
     * <p>Note: {@code percent: 1} is immunity in this vocabulary, and that is deliberate: "immune to control-class negative states" and
     * "raise the chance to resist control-class negative states by 35%" are the same mechanic at different strengths, and two spellings for one mechanic
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
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            AbstractBuff resist = withSource(withTickOwner(withLifetime(
                    new ClassResistBuff(kind, effect.getPercent(), turns, permanent), effect), effect, ctx), ctx);
            // the name is optional and only content that asks for it gets it (see `named`)
            target.getBuffManager().addBuff(named(resist, effect));
        }
    }

    /**
     * Gives a freshly built buff the <b>name the data stated</b> ({@code "buff": "协奏"}), when it states one.
     *
     * <p>Note: Unnamed is the normal case and stays exactly as it was: {@code BuffManager.removeState} skips modifiers and
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
     * <p><b>Why every path does this now.</b> The source is set wherever a constructor demands it
     * ({@code DotBuff} needs it for kill credit, {@code ShieldBuff}/{@code ControlBuff} take it), so a
     * {@code StateBuff} or a stat modifier could be anonymous. {@code EXTEND_BUFF} filters by origin - "the shield provided by the Skill"
     * means "the one <i>I</i> gave" - and an anonymous buff would make that filter silently extend nothing at all.
     * Recorded here once, for every op that creates a buff, rather than in each of them.
     */
    private static AbstractBuff withSource(AbstractBuff buff, TriggerContext ctx) {
        buff.setSource(ctx.owner());
        // ...and WHICH RULE created it, so a condition can ask "the shield provided by the Skill" rather than "the shield March 7th (三月七) gave" (see
        // AbstractBuff.ruleId). Stamped here, at the one place every buff-creating op already passes through.
        buff.setRuleId(ctx.ruleId());
        return buff;
    }

    private static void removeState(EffectSpec effect, TriggerContext ctx) {
        AttributeType byAttribute = effect.getAttribute() == null || effect.getAttribute().isBlank()
                ? null
                : AttributeType.fromString(effect.getAttribute().trim());
        boolean own = effect.getKind() != null && "own".equalsIgnoreCase(effect.getKind().trim());
        for (CanHit target : TriggerTargets.resolveTargets(ctx.battle(), effect, ctx)) {
            if (own) {
                target.getBuffManager().removeStateFrom(ctx.owner(), effect.getBuff(), byAttribute);
            } else if (byAttribute == null) {
                target.getBuffManager().removeState(effect.getBuff());
            } else {
                target.getBuffManager().removeState(byAttribute);
            }
        }
    }

    /**
     * Settles a {@code DISPEL} effect: removes up to {@code amount} <b>negative effects</b> from the target -
     * "remove N negative effects".
     *
     * <p>What counts as negative is {@code AbstractBuff.isDebuff()}, i.e. a decision taken per buff class rather
     * than a guess made here: a DOT, a control, vulnerability, taunt and a stat <i>debuff</i> are negative; damage reduction, a speed
     * boost and a named state are not. That is why this op needs no filter of its own - and why it cannot be
     * tricked into removing a shield.
     *
     * <p>Nothing to dispel is <b>not</b> an error, for the same reason as {@code REMOVE_STACK}: the shape
     * "when attacked, remove 1 negative effect from oneself" fires on every hit, including the ones where nothing is on you.
     *
     * @param effect the effect ({@code amount}, optional {@code target})
     * @param ctx    the context
     */
    private static void dispel(Battle battle, EffectSpec effect, TriggerContext ctx) {
        // A CLASS may be named: "remove ... all control-class negative states" (1415's memosprite skill 8). With a class and
        // no amount it means ALL of that class -- "all" spells no count -- which is why the validation below accepts either.
        com.laosun.aluminium.enums.DebuffClass kind = effect.getKind() == null || effect.getKind().isBlank()
                ? null
                : com.laosun.aluminium.enums.DebuffClass.fromString(effect.getKind().trim());
        int amount = effect.getAmount() == null ? 0 : (int) Math.round(effect.getAmount());
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (kind == null) {
                target.getBuffManager().removeDebuffs(amount);
            } else {
                target.getBuffManager().removeDebuffs(amount, kind);
            }
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
     * base attribute gets an additive percentage - and why a <b>derived</b> magnitude ({@code scale}) is flat even
     * on a base attribute: "the raise is equal to X" states a value, so treating it as "X% of the target's base" would
     * multiply the derived number by the target's own stat.
     */
    private static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                                 boolean permanent, int maxStacks, boolean absolute) {
        boolean debuff = percent < 0;
        // The sign is carried by `percent` itself (a negative value), which is what the original
        // percentBuff/flatBuff/... factories produced; only the modifier kind depends on the attribute.
        return StatModifierBuff.of(attribute, absolute || attribute.isPercent ? "pure" : "add_percent", percent,
                debuff ? "debuff" : "buff", turns, false, permanent, maxStacks);
    }

















    /**
     * The laugh factor one instance is folded with: the declared {@code counted_laughs} when the effect states one, and
     * otherwise the battle's snapshot. One reader of the table, called from the two places that fold Elation damage, so the
     * two cannot drift apart.
     */
    private static double laughFactor(TriggerContext ctx, EffectSpec effect) {
        if (effect != null && effect.getCountedLaughs() != null) {
            double counted = effect.getCountedLaughs();
            if (counted < 0) {
                throw new IllegalStateException("counted_laughs is " + counted
                        + "; a laugh count cannot be negative (source: " + ctx.ruleId() + ")");
            }
            return 1 + counted * 5.0 / (counted + 240);
        }
        return ctx == null || ctx.battle() == null ? 1 : ctx.battle().elationLaughMultiplier();
    }

    /**
     * One parsed {@code "<counter>:<step>"} specification: which counter is read, and how wide one step is.
     *
     * <p>Three spellings share this shape and therefore this parser - the {@code steps:} scale ("every N points of a
     * counter", as a magnitude), {@code per_steps} (the same count, as a repeat count) and {@code SPEND_STEPS}'s
     * {@code steps} (the same count, consumed). One string, one meaning.
     */
    private record CounterSteps(String counter, double step) {

        /**
         * How many WHOLE steps the counter has reached: {@code floor(value / step)}, read through the resolution
         * {@code weight_steps} uses - the rule owner's attribute, one of its own counters, or the battle's skill points.
         */
        int wholeSteps(TriggerContext ctx) {
            return (int) Math.floor(counterValue(counter, ctx) / step);
        }
    }

    /**
     * Parses {@code "<counter>:<step>"}, refusing a malformed specification or a step that is not positive.
     *
     * <p>Loud on purpose, at whichever point it is called: a step of zero would be divided by, and a misspelled counter
     * would read 0 forever - the "wrong number with no symptom" this interpreter refuses everywhere else.
     */
    static CounterSteps parseCounterSteps(String written, String spelling) {
        int colon = written.indexOf(':');
        if (colon <= 0 || colon == written.length() - 1) {
            throw new IllegalArgumentException(spelling + " must be \"<counter>:<step>\", e.g. \"隐藏分:120\", and this "
                    + "one is \"" + written + "\"");
        }
        double step;
        try {
            step = Double.parseDouble(written.substring(colon + 1).trim());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(spelling + " has a step that is not a number: \"" + written + "\"");
        }
        if (!(step > 0)) {
            throw new IllegalArgumentException(spelling + " divides by " + step
                    + "; a step must be greater than zero (\"" + written + "\")");
        }
        return new CounterSteps(written.substring(0, colon).trim(), step);
    }


    /**
     * How many times one effect repeats: {@code per_steps: "<counter>:<step>"} is {@code floor(value / step)}, and an
     * effect that states nothing runs exactly once.
     *
     * <p>Deliberately a COUNT and not a spend: her sentence pays two effects per milestone (an extra turn and a charge)
     * while the counter may be charged only once, so the spending is {@link #spendSteps} - and because reading is not
     * spending, the second effect of the same rule reads the same number. Read HERE, at dispatch time, so a counter moved
     * by an earlier effect of the same rule is already visible.
     */
    private static int perStepsRepeat(EffectSpec effect, TriggerContext ctx) {
        String written = effect == null ? null : effect.getPerSteps();
        if (written == null || written.isBlank()) {
            return 1;
        }
        return parseCounterSteps(written.trim(), "per_steps").wholeSteps(ctx);
    }




    /**
     * {@code DAMAGE}: one extra damage instance, settled with the numbers of one of the owner's own skills.
     *
     * <p>The victim is handed in by the caller ({@link #applyOne} walks the resolved list), because the same
     * effect may reach a whole side: "to all enemies" settles one instance per victim, and every number is read
     * from the skill's parameter row.
     *
     * <p><b>Why the numbers come from skill data.</b> The attack is already defined in {@code skills.json}:
     * Clara's talent says "single-target attack / Physical / toughness 30 / params [1, 0.8, 0.1]" - everything
     * except <i>when</i> to fire it. The trigger table supplies the "when", so a follow-up attack stays a pure
     * data rule rather than a Java class with a duplicated multiplier.
     *
     * <p><b>How such a hit settles.</b> Per the official definition it is {@code ADDITIONAL} damage: it counts
     * toward the attacker's kill credit but "does not count as dealing 1 attack", so the target gains no on-hit
     * energy and no toughness is reduced. It is a {@code FOLLOW_UP} for the trigger tables (the event the texts
     * about follow-up attacks subscribe to) while the attack-level notification {@code AttackEvent} is
     * deliberately not raised, so it does not consume an {@code "until": "next_attack"} buff - a derived hit is
     * part of somebody else's attack, and letting it announce one would recurse. {@code as_attack} is accepted
     * for future use but does not change that yet: the engine has one settlement path for supplementary hits.
     *
     * <p>The hit causes HP loss, which fires {@code HP_LOST} again. Two characters who both counter each other
     * therefore ping-pong until {@code Battle.MAX_TRIGGER_DEPTH} stops it with an exception, rather than hanging.
     *
     * @param battle the running battle
     * @param effect the effect ({@code skill}, {@code damage_param}, optional {@code target})
     * @param ctx    the context
     * @param victim the unit this instance is settled against
     */
    private static void damage(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit victim) {
        damage(battle, effect, ctx, victim, 1);
    }

    /**
     * The same, with the victim's share of the effect's TOTAL already worked out.
     *
     * @param divisor how many victims are sharing the stated magnitude ({@code 1} = the ordinary per-victim reading;
     *                see {@link EffectSpec#getSplit()})
     */
    private static void damage(Battle battle, EffectSpec effect, TriggerContext ctx, CanHit victim, int divisor) {
        CanHit attacker = ctx.owner();
        if (victim == null || victim.isDeath()) {
            return;
        }
        // ? A literal ratio states no skill at all: "deal damage equal to 80% of Sushang (素裳)'s ATK". The slot lookup is skipped, the multiplier comes
        // from `derivedMagnitude`, and `element` is required by the validator because there is no skill row to lend one.
        boolean literalRatio = effect.getSkill() == null || effect.getSkill().isBlank();
        SkillType slot = literalRatio ? null
                : SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = literalRatio ? null : attacker.getSkills().get(slot);
        if (!literalRatio && (skill == null || skill.getData() == null)) {
            throw new IllegalStateException(
                    attacker.getName() + " has no " + slot + " skill to fire a DAMAGE effect from");
        }
        // Note: A *rider* may legitimately read a row out of a skill that deals no damage of its own: Robin (知更鸟)'s [协奏]
        // adds "1 instance of Physical additional damage equal to 120% of her own ATK" and that 120% lives in her ultimate's own parameter row,
        // while the ultimate itself is a Support skill. So a non-damaging slot is accepted when the rule states the
        // element -- that is the part the skill could not have supplied, and without it the instance would be
        // element-less, which is the silent hole this check was written for.
        boolean statesElement = effect.getElement() != null && !effect.getElement().isBlank();
        // ? Skipped for a literal ratio: there is no slot to inspect, and the validator REQUIRES `element` for that form,
        // which is exactly the element-less instance this check was written to catch.
        if (!literalRatio && !skill.getData().getEffect().isDamaging() && !statesElement) {
            throw new IllegalStateException(
                    "DAMAGE effect points at " + slot + ", whose effect is "
                            + skill.getData().getEffect() + " rather than a damaging one; a rule that reads a number "
                            + "out of such a skill must state the \"element\" itself (the skill does not have one "
                            + "to lend)");
        }
        double base = skill == null
                ? TriggerMagnitudes.literalBase(attacker, victim, effect, ctx)   // ? a literal ratio: off the SETTLED attribute, a Max HP share, or the triggering instance's settled value
                : attacker.getAttribute(AttributeType.ATTACK).get() * TriggerMagnitudes.multiplierOf(skill, effect, attacker);
        // 「该次欢愉伤害使用【美学底本】的属性值计算」: the formula's UN-UNDERLINED terms are borrowed from the holder of the
        // named state - the base (this table's row, at the HOLDER's level), 欢愉度 and 增笑 - while the underlined ones
        // (the laugh factor, the six zones and the multiplier) stay the instance's own. Note what is NOT borrowed:
        // attack power, which the formula does not contain at all.
        CanHit elationSource = attacker;
        if (effect.getElationFrom() != null && !effect.getElationFrom().isBlank()) {
            String state = effect.getElationFrom().trim();
            elationSource = TriggerTargets.holderOfOrNull(battle, state, ctx.owner());
            if (elationSource == null) {
                throw new IllegalStateException("this Elation rider computes itself from the holder of 【" + state
                        + "】, and nobody on the field holds it");
            }
            base = TriggerMagnitudes.elationBaseOf(elationSource, "the holder of 【" + state + "】") * TriggerMagnitudes.shareOf(effect, ctx);
        }
        // "由敌方全体均分": this victim's share of the effect's stated TOTAL. Divided HERE, on the raw magnitude, so
        // everything downstream (the Elation fold, the zones, critical hits) applies to the share the sentence gives
        // each victim - and so the sum over the victims is the stated total, whatever N is.
        if (divisor > 1) {
            base /= divisor;
        }
        // The instance's type is the rule's own when it states one (reader: 1505's Elation (欢愉) riders). And because
        // `DamageType.ELATION` is deliberately not boostable, its own boost is folded into the BASE here, exactly as the cast
        // path does it in `SkillExecutor.hit` -- a type that settles but ignores its own boost zone is the silent hole this
        // project refuses.
        DamageType damageType = effect.getDamageType() == null || effect.getDamageType().isBlank()
                ? null
                : DamageType.fromString(effect.getDamageType().trim());
        double settledBase = damageType == DamageType.ELATION
                ? base * (1 + elationSource.getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get()) * (1 + elationSource.getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_AMP).get()) * laughFactor(ctx, effect)
                : base;
        // `damage_type: "TRUE"` is true damage: it SKIPS every zone. Without this branch the op stamps the
        // TYPE and still went through `applyAdditionalDamage`, so the instance was labelled TRUE while defence and
        // resistance multiplied it -- a right label on a wrong number, with no symptom. `Battle.applyTrueDamage` is the
        // engine's one true-damage entry: it sets `.trueDamage()`, which `toValue()` honours by skipping the zones.
        // Readers (2): 800 Trailblazer (开拓者)'s [迷迷的声援] (28%) and 1415 Cyrene (昔涟)'s zone (24%), both "true damage equal to X% of the original damage".
        if (damageType == DamageType.TRUE) {
            battle.applyTrueDamage(attacker, victim, TriggerMagnitudes.elementOf(effect, skill, attacker),
                    settledBase);
            return;
        }
        if (Boolean.TRUE.equals(effect.getOrdinary())) {
            // An ORDINARY instance: the public `applyDamage` settles it as the main instance of a hit, so the
            // victim is credited energy and the instance is an attack -- exactly what "deal Physical damage equal to ..." means, and what the
            // additional-damage path (KILL_ONLY, not-an-attack) could not express.
            battle.applyDamage(victim, new Damage(attacker, victim, TriggerMagnitudes.elementOf(effect, skill, attacker),
                    damageType == null ? DamageType.NORMAL : damageType, settledBase));
            return;
        }
        battle.applyAdditionalDamage(attacker, victim, TriggerMagnitudes.elementOf(effect, skill, attacker), settledBase,
                effect.getCritRate(), effect.getCritDamage(), damageType,
                // The rule may state that this instance is a "follow-up attack": `applyAdditionalDamage` fires FOLLOW_UP without a
                // category, so the instance has to carry it for a listener to be able to ask.
                effect.getCastCategory() == null || effect.getCastCategory().isBlank()
                        ? null
                        : com.laosun.aluminium.enums.SkillCategory.fromString(effect.getCastCategory().trim()));
    }























    /**
     * {@code settled_damage_this_event}: the damage the firing in progress has settled, summed over every instance
     * (see {@code Battle.settledDamageThisEvent}).
     *
     * <p>The sibling of {@code original_damage}, which reads the ONE instance {@code DAMAGE_SETTLED} hands over. This
     * one answers a different sentence - 「等同于本次总伤害值 20%」 - where "本次" is the whole action: a group action
     * settles one instance per victim, so the sum exists only on the battle, and it is why the two spellings are not
     * one spelling. Reader: 1506 Silver Wolf LV.999's 【超超超大剑】.
     *
     * <p>Note: It is refused on anything but true damage, because the value is already settled - see the DAMAGE
     * validation.
     */
    static final String SETTLED_DAMAGE_THIS_EVENT = "settled_damage_this_event";

    static final String ATTACKER_ELEMENT = "attacker";
}
