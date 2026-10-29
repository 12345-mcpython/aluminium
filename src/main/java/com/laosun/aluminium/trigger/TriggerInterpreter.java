package com.laosun.aluminium.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.*;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.trigger.TriggerTable.CompiledRule;
import com.laosun.aluminium.trigger.TriggerTable.TriggerContext;

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
 *   <caption>Every op: its arguments and whether the engine performs it</caption>
 *   <tr><th>op</th><th>arguments</th><th>status</th></tr>
 *   <tr><td>{@code GAIN_ENERGY}</td><td>{@code amount}</td><td>wired</td></tr>
 *   <tr><td>{@code RAISE_ENERGY_TO}</td><td>{@code percent} (a floor; no-op when already above)</td><td>wired</td></tr>
 *   <tr><td>{@code GAIN_SKILL_POINT}</td><td>{@code amount}</td><td>wired</td></tr>
 *   <tr><td>{@code RAISE_SKILL_POINT_CAP}</td><td>{@code amount} (negative takes the bonus away)</td><td>wired</td></tr>
 *   <tr><td>{@code HEAL}</td><td>{@code amount}, optional {@code target} - <b>or</b> {@code scale} +
 *       {@code percent} (a share of a Max HP); the share may also be read out of the owner's own skill row at its
 *       current level ({@code percent_from_skill_param}), and the flat part either literally or the same way
 *       ({@code amount_from_skill_param})</td><td>wired</td></tr>
 *   <tr><td>{@code SHIELD}</td><td>{@code amount}, optional {@code target} - <b>or</b> {@code scale} +
 *       {@code percent}; same two level-aware spellings as {@code HEAL}</td><td>wired</td></tr>
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
            //
            // `chance_rolls` makes those rolls INDEPENDENT within one firing ("施放战技时额外增加 2 次【剑势】的发动判定",
            // reader 1206). It is not an effect's `times`: that repeats the SETTLEMENT of a decision already taken, so
            // `chance: 0.33, times: 2` is "one roll, two hits" while this is "two rolls, each hit its own". The two have
            // the same mean and different distributions, which is exactly why one cannot stand in for the other.
            // Note: The roll is re-read per attempt, so a decaying rule decays BETWEEN attempts - the second attempt of
            // one firing sees the reduced number, the same way a second firing would.
            int attempts = rule.chanceRolls();
            for (int attempt = 0; attempt < attempts; attempt++) {
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
                    // The same one-expression rule applies to the cooldown: `MODIFY_RULE{cooldown_delta}` amends the number
                    // the rule states, so the record must be the amended one (never below zero).
                    owner.startTriggerCooldown(limitKey,
                            Math.max(0, rule.cooldownTurns() + owner.ruleCooldownDelta(rule.id())), rule.oncePerBattle(),
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
        // ⚠ The only place that sees EVERY rule effect: the engine announces effects elsewhere, but a rule whose
        // effect is a skill-level raise or a resource gain reaches no other hook at all.
        // ⚠ Guarded: `apply(null, ...)` is a supported call for single-target selectors -- the engine's own unit tests use it,
        // because MODIFY_ATTR only touches the resolved unit. An announcement must not make that illegal.
        if (battle != null) {
            battle.announceRuleFired(ctx.owner(), effect.getOp());
        }
        String op = normalizeOp(effect, null);
        TriggerOperator operator = TriggerOperator.parse(op);
        if (operator == null) {
            throw new IllegalStateException("Op " + op + " is not in TriggerOperator; the enum is the vocabulary, so an unknown"
                    + " name here is an engine fault rather than a content one");
        }
        switch (operator) {
            case ADD_ELEMENTAL_WEAKNESS -> {
                // Note: Two named sources beside a real element name (the loader keeps this a closed set):
                //   `party_first` -- the first character of the party, per 1006's own skill text.
                //   `random_absent` -- a random element the target does NOT already have (1405).
                String named = effect.getElement().trim();
                DamageElement weakness;
                if ("party_first".equals(named)) {
                    weakness = battle == null || battle.characters().isEmpty()
                            ? null : battle.characters().getFirst().getElement();
                } else if ("random_absent".equals(named)) {
                    weakness = null;
                    for (CanHit victim : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                        if (victim instanceof com.laosun.aluminium.enemy.Enemy en) {
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
                    if (victim instanceof com.laosun.aluminium.enemy.Enemy en && !en.isWeakTo(weakness)) {
                        if (effect.getTurns() != null && effect.getTurns() > 0) {
                            en.addWeakness(weakness, effect.getTurns());
                        } else {
                            en.addWeakness(weakness);
                        }
                        battle.fire(TriggerPayload.Simple.of(TriggerEvent.WEAKNESS_ADDED, ctx.owner(), en, 0, 0));
                    }
                }
            }
            case RESET_TRIGGER_LIMIT -> {
                String wanted = effect.getRule().trim();
                for (CanHit cleared : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    TriggerTable table = cleared instanceof com.laosun.aluminium.entity.Character ch
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
            case GAIN_ENERGY -> gainEnergy(battle, effect, ctx);
            case RAISE_ENERGY_TO -> {
                // A FLOOR, not a gain: energy is raised to the stated share of the maximum and left alone
                // when it is already at or above it. Adding a share instead would overshoot.
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    double floor = target.getMaxEnergy() * effect.getPercent();
                    if (target.getCurrentEnergy() < floor) {
                        target.setCurrentEnergy(floor);
                    }
                }
            }
            case GAIN_SKILL_POINT -> battle.gainSkillPoint((int) Math.round(scaledAmount(effect, ctx)));
            case HEAL -> {
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
            case SHIELD -> {
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
                            battle.fireForAlly(TriggerPayload.AllySubject.of(
                                    TriggerEvent.SHIELD_GRANTED, ctx.owner(), target, amount));
                        }
                    }
                }
            }
            case ABSORB_DAMAGE -> {
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
                boolean permanent = TriggerLifetimes.unticked(effect);
                int turns = permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    if (target == null || target.isDeath()) {
                        continue;
                    }
                    target.getBuffManager().addBuff(withSource(TriggerLifetimes.withLifetime(
                            new com.laosun.aluminium.buff.AbsorbBuff(
                                    turns, permanent, resource, unitAmount, share, payer),
                            effect), ctx));
                }
            }
            case INSERT_ACTION -> {
                // "Demiurge (德谬歌) immediately gains 1 extra turn and automatically casts [花与箭的舞曲]"(1415 memosprite skill 10): a unit outside the action
                // order acts now -- the game's `TurnInsertAction`. Note: NOT `EXTRA_TURN`: that one cuts into a queue the unit is already in, and
                // a memosprite at Speed 0 is not in it at all (the game pins it there with `SpeedOverride`).
                CanHit acting = TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
                if (!battle.insertAction(acting)) {
                    throw new IllegalStateException("INSERT_ACTION makes " + acting.getName()
                            + " act now, but it is dead or already waiting to act");
                }
            }
            case EXTRA_TURN -> {
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
            case ADVANCE -> {
                // One target or a group: "make that target act immediately" and "make the teammates other than oneself act immediately" are the same op, and
                // the list resolver is what tells them apart (it is the one HEAL/SHIELD already use for "our
                // side"). Advancing several units is what the group selectors were resolved for.
                for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    battle.queue().advanceActionByPercent(target, effect.getPercent());
                }
            }
            case RAISE_RESOURCE_CAP -> raiseResourceCap(effect, ctx);
            // The pool is team-level and its owner is the policy, so this is the one op that goes
            // straight to the policy rather than through a unit's resources.
            case RAISE_SKILL_POINT_CAP -> battle.getSkillPointPolicy().raiseMax((int) Math.round(effect.getAmount()));
            case GAIN_RESOURCE -> {
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
            case SPEND_RESOURCE -> {
                CanHit mover = TriggerTargets.resolveTarget(effect, ctx);
                String movedId = effect.getResource();
                int before = resourceAmount(battle, mover, movedId);
                spendResource(effect, ctx, mover);
                fireResourceChanged(battle, ctx, mover, movedId, resourceAmount(battle, mover, movedId) - before);
            }
            // "该额外回合开始时，【美学底本】获得 30 点【好活当赏】…额外回合结束时移除" (1503 Pearl (真珠)'s ultimate): the
            // inverse of a temporary grant. Deliberately NOT `SPEND_RESOURCE`, which is a strict cost that errors when
            // the pool is short - a revocation has to clamp instead, because this pool is meant to be consumed.
            case REMOVE_RESOURCE -> {
                CanHit mover = TriggerTargets.resolveTarget(effect, ctx);
                String movedId = effect.getResource();
                int removed = removeResource(effect, ctx, mover);
                fireResourceChanged(battle, ctx, mover, movedId, -removed);
            }
            // "真珠回合开始时重置可获得【好活当赏】数量" (1503 Pearl (真珠)'s trace): starts a new round for the per-round
            // allowances that `max_per_round` charges against. Nothing about the COUNTER changes - only what this round
            // may still hand out.
            case RESET_RESOURCE_CAP -> {
                CanHit resetOwner = ctx.owner();
                if (resetOwner != null) {
                    resetOwner.getResources().resetRoundAllowances();
                }
            }
            // "将触发【头号补给盲盒】的固定概率重置为初始值" (1506's 崩坏级伤害演示): the decaying probability goes back to the
            // rule's own `chance`, so the next firing rolls the initial number again.
            case RESET_RULE_CHANCE -> {
                CanHit chanceOwner = ctx.owner();
                if (chanceOwner != null) {
                    chanceOwner.resetRuleChance(effect.getRule().trim());
                }
            }
            case DAMAGE -> {
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
            case MODIFY_ATTR -> TriggerEffects.modifyAttr(battle, effect, ctx);
            case APPLY_BUFF -> TriggerEffects.applyState(battle, effect, ctx);
            case REMOVE_STACK -> TriggerEffects.removeStacks(battle, effect, ctx);
            case MODIFY_DAMAGE_TAKEN -> modifyDamageTaken(battle, effect, ctx);
            case BOOST_DAMAGE -> boostDamage(effect, ctx);
            case ADD_DAMAGE -> TriggerEffects.addDamageFlat(effect, ctx);
            case DISPEL -> TriggerEffects.dispel(battle, effect, ctx);
            case DISMISS_SUMMON -> dismissSummon(battle, effect, ctx);
            case REMOVE_BUFF -> TriggerEffects.removeBuffs(battle, effect, ctx);
            case REPLACE_SKILL -> TriggerEffects.replaceSkill(battle, effect, ctx);
            case TICK_DOT -> TriggerEffects.tickDot(battle, effect, ctx);
            case REMOVE_STATE -> TriggerEffects.removeState(effect, ctx);
            case TAUNT -> TriggerEffects.taunt(battle, effect, ctx);
            case APPLY_CONTROL -> TriggerEffects.applyControl(battle, effect, ctx);
            case APPLY_DOT -> TriggerEffects.applyDot(battle, effect, ctx);
            case EXTEND_BUFF -> TriggerEffects.extendBuff(battle, effect, ctx);
            case RESIST_DEBUFF -> resistDebuff(battle, effect, ctx);
            case MODIFY_RULE -> modifyRule(effect, ctx);
            case RAISE_SKILL_LEVEL -> raiseSkillLevel(effect, ctx);
            case RAISE_SKILL_HITS -> raiseSkillHits(effect, ctx);
            case START_COUNTDOWN -> startCountdown(battle, effect, ctx);
            case ADD_STACK -> TriggerEffects.addStack(battle, effect, ctx);
            case SPEND_STEPS -> spendSteps(effect, ctx);
            case CONSUME_HP -> {
                // Note: The share vocabulary is the HEAL/SHIELD one, not the modifier one: "consume HP equal to 30% of Blade (刃)'s Max HP"
                // is `owner_max_hp`, which derivedMagnitude refuses (it names an ATTRIBUTE).
                for (CanHit spender : TriggerTargets.resolveTargets(battle, effect, ctx)) {
                    if (spender != null) {
                        double spent = spender.consumeHp(grantAmount(effect, spender, ctx));
                        if (spent > 0) {
                            // "take damage <b>or</b> consume HP": the second half gets its own event.
                            battle.fire(TriggerPayload.Simple.of(TriggerEvent.HP_CONSUMED, spender, spender,
                                    (int) Math.round(spent), 0));
                        }
                    }
                }
            }
            case BOOST_TOUGHNESS -> TriggerEffects.boostToughness(battle, effect, ctx);
            case SUPER_BREAK -> TriggerEffects.superBreak(battle, effect, ctx);
            case APPLY_REGEN -> TriggerEffects.applyRegen(battle, effect, ctx);
            case REGISTER_HEAL_BONUS -> registerHealBonus(effect, ctx);
            case SUMMON -> battle.summonMemosprite(TriggerMagnitudes.requireCharacterOwner(effect, ctx));
            case COMMAND_SUMMON -> TriggerEffects.commandSummon(battle, effect, ctx);
            case CAST_SKILL -> TriggerEffects.castSkill(battle, effect, ctx);
            case SUMMON_SERVANT -> battle.summonServant(TriggerMagnitudes.requireCharacterOwner(effect, ctx));
            case DELEGATE_DAMAGE -> delegateDamage(effect, ctx);
            default -> throw new IllegalStateException(
                    "Op '" + op + "' reached the dispatcher without an arm");
        }
    }

    /*
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
    /**
     * Files a heal bonus whose applicability is decided <b>per heal</b>, on the rule's owner.
     *
     * <p><b>What this expresses.</b> 「为当前生命值百分比小于等于 30% 的我方目标提供治疗时，娜塔莎的治疗量提高
     * 50%，该效果对持续治疗效果也会生效」 (1105's talent). The bonus cannot be an attribute -- one stat on the
     * healer would raise every heal, including one on a full-HP ally -- and it cannot be a fire-time rule, because
     * <b>no pre-heal event exists</b>: {@code HEALED} is emitted by {@code Battle.heal} after the amount is computed.
     *
     * <p><b>How the condition is carried.</b> The effect's {@code target_when} list, which is parsed at load time and
     * already delivered to the interpreter as {@code ctx.targetFilter()}. It is the effect-level condition slot that
     * exists today (an effect has no {@code when} of its own), and its meaning here is the one it has everywhere else
     * -- "which units is this about" -- except that the units are the future recipients of the heal rather than the
     * aim of a damage instance. {@code Battle.calculateHeal} asks it with the healer as {@code owner}/{@code actor}
     * and the recipient as {@code target}, so {@code target_hp_percent} reads the person being healed.
     *
     * <p><b>Why the list and not one condition.</b> An effect may state several, and they are conjunctive in the same
     * way a rule's own {@code when} is: the bonus applies only when all of them hold.
     *
     * @param effect the effect carrying the op (its {@code percent} is the bonus)
     * @param ctx    the firing context; {@code owner} is the healer, {@code targetFilter} its condition
     */
    private static void registerHealBonus(EffectSpec effect, TriggerContext ctx) {
        CanHit owner = ctx.owner();
        if (owner == null) {
            return;
        }
        List<TriggerTable.Condition> conditions = ctx.targetFilter();
        if (conditions == null || conditions.isEmpty()) {
            // A bonus with no condition would apply to every heal, which is exactly the wrong number this op exists
            // to avoid. Refused loudly rather than registered unconditionally.
            throw new IllegalStateException(
                    "REGISTER_HEAL_BONUS needs a target_when condition saying WHICH heals it applies to "
                            + "(an unconditional version would raise every heal, including on a full-HP ally)");
        }
        double percent = effect.getPercent() == null ? 0 : effect.getPercent();
        for (TriggerTable.Condition condition : conditions) {
            owner.addConditionalHealBonus(condition, percent);
        }
    }

    private static void delegateDamage(EffectSpec effect, TriggerContext ctx) {
        Battle battle = ctx.battle();
        Battle.PendingCast cast = battle == null ? null : battle.currentCast();
        if (cast == null) {
            throw new IllegalStateException(
                    "DELEGATE_DAMAGE ran with no cast in progress; CAST_SETUP is fired by SkillExecutor.execute "
                            + "with a cast token, so this is an engine fault rather than a content error");
        }
        com.laosun.aluminium.entity.Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
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
        // 「固定恢复 N 点」: the number as stated, NOT a base for the energy-regeneration rate (reader: light cone 23055).
        // Note: the model already had the two kinds side by side (`EnergyGain.fixed` / `.normal`); what was missing was a
        // way for a RULE to pick the fixed one. Measured before this: a fixed +10 became +15 at a 50% rate.
        if (Boolean.TRUE.equals(effect.getFixed())) {
            battle.applyEnergyGain(target, com.laosun.aluminium.energy.EnergyGain.fixed(effect.getAmount()));
            return;
        }
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
            com.laosun.aluminium.entity.Character holder = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
            return holder.getResources().value(effect.getAmountPercentFromResource().trim()) / 10000.0;
        }
        return effect.getAmountPercent() == null ? 1 : effect.getAmountPercent();
    }

    /**
     * Widens a resource's OVERFLOW for its owner (reader: 114151"[新蕊] can overflow to #3%").
     *
     * <p>A declaration states one overflow; a sentence can raise it for the battle. The NORMAL cap is untouched -- this is only the second tier, which is what the sentence is about.
     *
     * <p>{@code unbounded: true} sets the allowance to {@link Integer#MAX_VALUE} instead of adding a count, which is
     * <b>the largest cap the engine can hold</b> - reader: 1408 Phainon (白厄)'s Eidolon 6, 「【火种】的可溢出点数<u>不再
     * 拥有上限</u>」. {@code Resource.absoluteMax()} saturates on the sum, so the invariant still holds. The two spellings
     * are exclusive: "by this many more" and "by no limit" are different claims (the validator refuses the pair).
     */
    private static void raiseResourceCap(EffectSpec effect, TriggerContext ctx) {
        CanHit holder = TriggerTargets.resolveTarget(effect, ctx);
        // A PARTY-scoped resource has no home on the unit (the same fact `gainResource` records) -- [新蕊] is declared `scope: PARTY` -- so the fallback is the
        // battle-level counter, which is the SAME `Resource` object and therefore carries the settable overflow.
        com.laosun.aluminium.state.Resource resource = holder.getResources().get(effect.getResource());
        if (resource == null) {
            resource = ctx.battle() == null ? null : ctx.battle().partyResource(effect.getResource());
        }
        if (resource == null) {
            throw new IllegalStateException("RAISE_RESOURCE_CAP names the resource '" + effect.getResource()
                    + "', which this unit neither declares nor has at party level");
        }
        if (Boolean.TRUE.equals(effect.getUnbounded())) {
            resource.setMaxOverflow(Integer.MAX_VALUE);
            return;
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
            com.laosun.aluminium.entity.Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
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
        com.laosun.aluminium.state.Resource party = partyCounterOrNull(ctx, holder, effect.getResource());
        // `to_max`: the counter is raised to its ceiling, so the amount becomes "however much it is short". Reader: 1503
        // Pearl (真珠)'s 充能 - the counter IS 【深度学习】's duration, so re-entering the state restores the FULL three
        // rather than adding three to the remainder. Note it is measured against the counter's MAXIMUM and not its
        // overflow ceiling: a plain gain on a counter that HAS an overflow allowance pours the spare on top, and the
        // state would then outlast the document by a use.
        if (Boolean.TRUE.equals(effect.getToMax())) {
            com.laosun.aluminium.state.Resource counter = party != null ? party
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
        // The battle's SKILL POINTS: a counter the battle owns outright (`Battle.getSkillPointPolicy()`), not a
        // `Resource`, so `partyCounterOrNull` cannot answer with it and this is where a rule reaches it. Reader:
        // 1506 Silver Wolf
        // LV.999's 【头号补给盲盒】 - 「【爆爆爆炸蛋】：恢复 2 个战技点」 - which is one of the three effects that rule picks
        // between. Measured: nothing anywhere gained or spent 战技点 before this, so the name had no readout at all.
        if (SKILL_POINT.equals(effect.getResource()) && ctx.battle() != null) {
            ctx.battle().getSkillPointPolicy().gain(amount);
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
    private static com.laosun.aluminium.state.Resource partyCounterOrNull(TriggerContext ctx, CanHit holder,
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
        int asked = percentOfOwnCounter(effect, ctx);
        if (asked <= 0) {
            asked = (int) Math.round(scaledAmount(effect, ctx));
        }
        if (asked <= 0) {
            return 0;
        }
        com.laosun.aluminium.state.Resource party = partyCounterOrNull(ctx, holder, id);
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
        com.laosun.aluminium.state.Resource partyCounter = partyCounterOrNull(ctx, holder, id);
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
        com.laosun.aluminium.state.Resource party = partyCounterOrNull(ctx, holder, counter.counter());
        com.laosun.aluminium.state.Resource store = party != null ? party
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

    /*
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
    /**
     * {@code scale: "self_resource:<id>"} + {@code percent} on {@code REMOVE_RESOURCE}: a share of one of the rule
     * owner's own counters - 「清空累计治疗数值的 50%」 (1409 Hyacine (风堇)'s 忆灵技1, which Rank 6 turns into 12%).
     *
     * <p>Why it is not a flat {@code amount}: the sentence names a FRACTION of whatever the counter holds at that
     * moment, so a fixed number would be a different claim (and a wrong one the moment the tally changes).
     *
     * @return the points to take back, or {@code -1} when this effect is not spelled that way
     */
    private static int percentOfOwnCounter(EffectSpec effect, TriggerContext ctx) {
        String scale = effect.getScale() == null ? "" : effect.getScale().trim();
        if (!scale.startsWith(SELF_RESOURCE_PREFIX)) {
            return -1;
        }
        double share = effect.getPercent() == null ? 1 : effect.getPercent();
        return (int) Math.round(TriggerMagnitudes.selfResourceOf(scale, ctx) * share);
    }

    private static double scaledAmount(EffectSpec effect, TriggerContext ctx) {
        double amount = flatAmount(effect, ctx);
        return Boolean.TRUE.equals(effect.getPerTarget()) ? amount * ctx.hitCount() : amount;
    }

    /**
     * The <b>flat</b> part of a magnitude: the literal {@code amount}, or one parameter of the rule owner's own skill
     * at that skill's <b>current level</b> ({@code amount_from_skill_param}).
     *
     * <p>Why a second source. March 7th (1001)'s Skill states a shield of 「等同于三月七 #1% 防御力 + #4」 - both #1
     * and #4 run with the Skill's level, so a rule that writes the constant as a literal freezes one row while the
     * share (read from the same row) moves with it. A hybrid of "current-level share" and "authored-level constant" is
     * a number no level of the Skill produces.
     *
     * <p>Note: This is the same reader the derived magnitudes use - {@link TriggerMagnitudes#constantTerm} - so the
     * flat and the derived paths cannot disagree about where a constant comes from.
     */
    private static double flatAmount(EffectSpec effect, TriggerContext ctx) {
        return TriggerMagnitudes.constantTerm(effect, ctx);
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
    static boolean ctxFreeCounter(EffectSpec effect) {
        return effect.getPerStack() != null && !effect.getPerStack().isBlank()
                && !COUNT_FORMS.contains(effect.getPerStack().trim());
    }

    /**
     * The counter name behind `self_stacks:<NAME>` (or the bare name), for a live reader.
     */
    static String counterName(EffectSpec effect) {
        String stated = effect.getPerStack().trim();
        return stated.startsWith("self_stacks:") ? stated.substring("self_stacks:".length()).trim() : stated;
    }

    static double perStackFactor(EffectSpec effect, CanHit target, TriggerContext ctx) {
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
                    .countBuffs(com.laosun.aluminium.buff.DotBuff.class);
            // "for each character on the field holding a shield" (cone 21043): OUR units currently holding a shield. A LIVE count,
            // read where the effect is evaluated -- it needs the battlefield because a unit does not know its own side.
            case "target_weakness_count" -> {
                int weak = target instanceof com.laosun.aluminium.enemy.Enemy enemy ? enemy.weaknessCount() : -1;
                yield Math.max(weak, 0);
            }
            case "shielded_count" -> ctx == null || ctx.battle() == null ? 0
                    : ctx.battle().allies().stream().filter(unit -> unit.getShield() > 0).count();
            default -> target.getBuffManager().stacksOf(name.trim());
        };
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
        boolean permanent = TriggerLifetimes.unticked(effect);
        int turns = permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        // "break damage taken is raised": an optional damage-type scope, spelled with the enum's own names.
        com.laosun.aluminium.enums.DamageType scope = parseDamageType(effect, "MODIFY_DAMAGE_TAKEN", null);
        // "increases the Fire DMG the target takes": the ELEMENT scope, beside the damage-type one. Each is checked by the zone
        // itself, so nothing here decides precedence.
        com.laosun.aluminium.enums.DamageElement elementScope = parseScopeElement(effect, "MODIFY_DAMAGE_TAKEN");
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            AbstractBuff zone = withSource(TriggerLifetimes.withTickOwner(TriggerLifetimes.withLifetime(percent > 0
                    // ⚠ The element scope goes to the VULNERABILITY branch only: measured, `ReductionBuff` has no scope
                    // field and no guard at all -- only the vulnerability half ever scoped.
                    ? new VulnerabilityBuff(turns, percent, permanent, scope, elementScope)
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
            // "this zone must live beside another source's zone": the `coexist` ask that MODIFY_ATTR has honoured since
            // 1408's trace was never read on THIS op, so a second `MODIFY_DAMAGE_TAKEN` from another rule evicted the
            // first. Measured: 1512's memosprite aura (+11.2%, set at SUMMONED) disappeared the moment light cone 23042's
            // +36% landed - the ratio came out (1+0.36)/(1+0.112) instead of 1+0.36+0.112, while the game's own capture
            // carries 0.64 = 0.36 + 0.28, i.e. the two sources DO add.
            if (Boolean.TRUE.equals(effect.getCoexist())) {
                zone.setKeepsSiblings(true);
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
        // `per_stack` on a BOOST_DAMAGE is a LIVE count on the event's target (cone 21001: "for each
        // debuff the enemy has, the wearer's damage +12%"). This method used to drop it, so the
        // per-stack branch silently behaved as one stack while the at-cap rule produced the only
        // increasing number.
        if (effect.getPerStack() != null && !effect.getPerStack().isBlank() && ctx.target() != null) {
            magnitude *= perStackFactor(effect, ctx.target(), ctx);
        }
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
     * {@code self_resource:<id>} - a share of <b>one of the rule owner's own counters</b> (its {@code self_resource:} is:
     * the counter belongs to the unit the sentence is about, not to the battle).
     *
     * <p><b>Why it had to exist.</b> A counter could already be GAINED, SPENT and read in a <b>condition</b>
     * ({@code self_resource:X >= 1}), and a battle-PARTY counter could already be a magnitude
     * ({@code party_resource:X}) - but there was no spelling for "the damage is X% of <b>this unit's</b> counter".
     * The reader is 1409 Hyacine (风堇)'s 忆灵技1: 「造成等同于<b>本场战斗风堇与小伊卡累计治疗数值</b> #1% 的风属性伤害并清空
     * 累计治疗数值的 <b>50%</b>」- the same spelling also buys the "clear half of it" half, because a share of a counter is
     * exactly what {@code percent} means on a {@code scale} ({@code scale: self_resource:tally, percent: 0.5} on
     * {@code REMOVE_RESOURCE}).
     *
     * <p>⚠ Not an {@link com.laosun.aluminium.enums.AttributeType}: the question "which attribute does this name" has no
     * answer, which is why every reader returns before the attribute family (see
     * {@link com.laosun.aluminium.trigger.TriggerMagnitudes}).
     */
    static final String SELF_RESOURCE_PREFIX = "self_resource:";

    /**
     * {@code party_resource:<id>} - a share of a counter that belongs to the SIDE, so it lives on the {@code Battle}.
     * The sibling of {@link #SELF_RESOURCE_PREFIX}: same arithmetic, different owner.
     */
    static final String PARTY_RESOURCE_PREFIX = "party_resource:";
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
     * Max HP / of the healer's DEF or ATK.
     *
     * <p><b>Both parts may follow a skill's level.</b> The share can be read out of the rule owner's own skill row
     * ({@code percent_from_skill_param: "<SKILLTYPE>:<index>"}) and the constant the same way
     * ({@code amount_from_skill_param}) - the two halves of 「等同于三月七 #1% 防御力 + #4」, which both run with the
     * Skill's level and would otherwise be a hybrid of one row's share and another row's constant.
     *
     * <p>Computed <b>per target</b> because {@code target_max_hp} differs from one recipient to the next: a
     * party-wide heal that restores 8% of each ally's own Max HP cannot be one number.
     *
     * @param effect the effect (already validated)
     * @param target the unit about to receive the heal or shield
     * @param ctx    the context ({@code owner_max_hp} reads the rule owner, i.e. the healer)
     */
    static double grantAmount(EffectSpec effect, CanHit target, TriggerContext ctx) {
        String scale = effect.getScale();
        if (scale == null || scale.isBlank()) {
            return scaledAmount(effect, ctx);
        }
        // Note: `shareOf`, not `percent`: the share may come from a skill row ("#1% 防御力") instead of a literal, and
        // reading the raw field would have made `percent_from_skill_param` NPE here -- the same shape that once crashed
        // MODIFY_ATTR (see PercentFromSkillParamTest) and the one 1001's shield needs, where #1 and #4 both run with level.
        double share = TriggerMagnitudes.shareOf(effect, ctx);
        double flat = flatAmount(effect, ctx);
        return switch (scale.trim()) {
            case "target_max_hp" -> target.getMaxHp() * share + flat;
            // "restore HP equal to X% of each one's own lost HP" (cone 21023): "each one's own" is what
            // `target` already means here -- the heal is granted per recipient -- so the reading is `target`'s max HP minus
            // its current HP, never the healer's and never a battle-wide total (that is 1205's separate cumulative idea).
            case "target_lost_hp" -> Math.max(0, target.getMaxHp() - target.getCurrentHp()) * share + flat;
            case "owner_max_hp" ->
                    TriggerMagnitudes.ownerAttributeOf(ctx, "owner_max_hp", AttributeType.HEALTH) * share + flat;
            // "consume HP equal to 35% of Mydei (万敌)'s current HP" (reader: 1404's two enhanced skills). The
            // vocabulary had max-HP shares and a lost-HP share, and neither says "a share of what is left": 35% of MAX on a
            // wounded unit is a plausible-looking wrong number, which is exactly the kind this engine refuses to guess.
            // Note: `target` is the unit being resolved (for CONSUME_HP, the spender), matching the `target_*` family.
            case "target_current_hp" -> target.getCurrentHp() * share + flat;
            // March 7th (三月七) 100102: a shield of "5% of DEF + 60" -- a share of the maker's DEFENCE plus a constant.
            case "owner_def" ->
                    TriggerMagnitudes.ownerAttributeOf(ctx, "owner_def", AttributeType.DEFENCE) * share + flat;
            // ? 1414's shield is "20.00% of ATK + 400": the same derived shape, off ATTACK. Seven documents state
            // an ATK-scaled shield or heal and the vocabulary had no name for it.
            case "owner_attack" ->
                    TriggerMagnitudes.ownerAttributeOf(ctx, "owner_attack", AttributeType.ATTACK) * share + flat;
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
     * <p>Note: A plain {@link com.laosun.aluminium.buff.StateBuff} reports {@code isDebuff() == false} and carries no
     * {@code debuffClass}, so it meets <b>no class resistance</b> - correct for a state no document calls control or DOT,
     * and the honest limit of what this spelling can say.
     *
     * @return {@code true} when the state was attached (or when the rule stated no chance at all)
     */
    static boolean attachRolled(Battle battle, CanHit target, AbstractBuff buff, EffectSpec effect,
                                TriggerContext ctx) {
        if (effect.getBaseChance() == null) {
            target.getBuffManager().addBuff(buff);
            return true;
        }
        return battle.tryApplyDebuff(ctx.owner(), target, buff, effect.getBaseChance(), null);
    }


    /*
     * Reads an optional {@code damage_type} and turns it into the engine's own spelling.
     *
     * <p>Note: A misspelling that silently meant "all damage types" would be a wrong number with no symptom - the same hole the
     * closed-set checks elsewhere in this interpreter exist to close, so the error names every legal value.
     */
    /**
     * Reads an optional {@code scope_element} for a damage-taken zone and turns it into the engine's own spelling.
     *
     * <p>The sibling of {@link #parseDamageType}, closed the same way: a misspelling that silently meant "every element" would be a
     * wrong number with no symptom, so the error names every legal value.
     *
     * <p>⚠ {@code scope_element}, not {@code element}: the latter belongs to {@code DAMAGE} / {@code APPLY_DOT} /
     * {@code APPLY_CONTROL}, which use it for the element of what they produce. A zone's scope is a different fact.
     *
     * @param effect the effect
     * @param op     the op, for the message
     * @return the element, or {@code null} when the rule names none
     */
    static com.laosun.aluminium.enums.DamageElement parseScopeElement(EffectSpec effect, String op) {
        String raw = effect.getScopeElement();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return com.laosun.aluminium.enums.DamageElement.fromString(raw.trim());
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the scope element '" + raw + "', which is not one this engine settles; known: "
                            + java.util.Arrays.toString(com.laosun.aluminium.enums.DamageElement.values()));
        }
    }

    static com.laosun.aluminium.enums.DamageType parseDamageType(EffectSpec effect, String op, TriggerSpec spec) {
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
        // ⚠ The fact is scoped to THIS firing: `Battle.fire` saves and restores the resource name.
        // Without it, a rule delivered for this event that moves ANOTHER resource (ordinary - 1506's
        // forwarding clause grants 隐藏分 for a 笑点 move) overwrites the name, and the rules that come
        // AFTER it in the same firing read the wrong one: their `resource_changed:<this one>` condition
        // is false and they silently do not fire. The outer event's rules are about the outer resource.
        battle.fire(TriggerPayload.ResourceChanged.of(TriggerEvent.RESOURCE_CHANGED, holder, resource, delta));
    }

    /**
     * The authored DOT layer ceiling, or {@code 0} when the rule states none.
     */
    static int dotStackCap(EffectSpec effect) {
        return effect.stackCap() == null ? 0 : effect.stackCap();
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
        int delta = (int) Math.round(effect.getAmount());
        if (Boolean.TRUE.equals(effect.getMemosprite())) {
            // 「忆灵技等级+1」: the raise is filed on the OWNER (the summoner's kit), not on the memosprite, because
            // the memosprite does not exist yet at BATTLE_START - it is summoned later, possibly more than once. The
            // slot is its DATA SLOT (`skill_id`), which no SkillType names. `CanHit.skillLevel` reads the owner's map
            // when it resolves a memosprite's skill, so every summon of this character inherits the raise.
            owner.raiseMemospriteSkillLevel(effect.getSkillId(), delta);
            return;
        }
        owner.raiseSkillLevel(SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT)), delta);
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
     * {@link #fire} (per-turn cap) and {@link TriggerEffects#applyControl} (base chance) - both read the amendment
     * only when the rule they are running carries the id it was filed under.
     *
     * <p>Note: Which number moves is decided by <b>which field is stated</b>: {@code amount} raises a count, {@code
     * percent} raises a probability, {@code cooldown_delta} moves a cooldown. The load-time validator refuses
     * both-at-once, neither, an out-of-range value, a non-integer count, any other field, a non-{@code BATTLE_START}
     * event, an unknown rule id, and a named rule that does not state the number being raised.
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
        } else if (effect.getCooldownDelta() != null) {
            // "天赋的冷却时间减少 1 回合" (1002's Eidolon 2): a delta on the number the named rule states. Read where the
            // cooldown is recorded, so the rule keeps saying "2" and the Eidolon is the correction.
            owner.addRuleCooldownDelta(target, effect.getCooldownDelta());
        } else {
            owner.addRuleBaseChanceBonus(target, effect.getPercent());
        }
    }

    /**
     * 「并使「长夜」消失」: takes a summon off the field <b>without a death</b>.
     *
     * <p>Reader: 11413/7 「迷梦，流失，如露」 - 「施放后，消耗全部【忆质】和生命值，并使「长夜」消失」. {@code perish()} is the
     * engine's "it went away, nobody killed it" (its own javadoc), and the death sweep then announces
     * {@link TriggerEvent#SUMMON_LEFT}, which is what 风堇's aura and 长夜's own departure rules listen for - so this op
     * reuses that seam rather than inventing a second removal path.
     *
     * <p>⚠ A resolved target that is <b>not</b> a summon is a data error, not a silent no-op: "dismiss" applied to a
     * character would read as working (nothing happens) while the sentence is about something else entirely.
     */
    private static void dismissSummon(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (!(target instanceof com.laosun.aluminium.entity.Summon summon)) {
                throw new IllegalStateException("DISMISS_SUMMON resolved "
                        + (target == null ? "null" : target.getName()) + ", which is not a summon");
            }
            if (!summon.isDeath()) {
                summon.perish();
            }
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
        // kind may be absent: RESIST_DEBUFF without a class means ANY negative state (1409s immunity clause).
        boolean permanent = TriggerLifetimes.unticked(effect);
        int turns = permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            AbstractBuff resist = withSource(TriggerLifetimes.withTickOwner(TriggerLifetimes.withLifetime(
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
    static AbstractBuff withSource(AbstractBuff buff, TriggerContext ctx) {
        buff.setSource(ctx.owner());
        // ...and WHICH RULE created it, so a condition can ask "the shield provided by the Skill" rather than "the shield March 7th (三月七) gave" (see
        // AbstractBuff.ruleId). Stamped here, at the one place every buff-creating op already passes through.
        buff.setRuleId(ctx.ruleId());
        return buff;
    }


    /**
     * The modifier a {@code MODIFY_ATTR} effect produces, as a buff or a debuff according to the sign.
     *
     * <p>See {@link TriggerEffects#modifyAttr} for why a ratio attribute gets a flat modifier and a base attribute
     * gets an additive percentage. Splitting it out keeps the "which modifier kind" decision in one
     * readable place instead of a nested conditional at the call site.
     *
     * @param attribute the attribute to touch
     * @param percent   the magnitude; {@code < 0} produces a debuff
     * @param turns     how long it lasts (validated at load time; ignored when {@code permanent})
     * @param permanent {@code true} = "for the rest of the battle", i.e. never ticked
     * @param maxStacks how many copies may accumulate; {@code 1} = replace on re-application
     */
    static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                         boolean permanent, int maxStacks) {
        return statModifier(attribute, percent, turns, permanent, maxStacks, false);
    }

    /**
     * The same, for a magnitude that is already an <b>absolute</b> number in the attribute's own units.
     *
     * <p>See {@link TriggerEffects#modifyAttr} for why a literal on a ratio attribute gets a flat modifier and a literal on a
     * base attribute gets an additive percentage - and why a <b>derived</b> magnitude ({@code scale}) is flat even
     * on a base attribute: "the raise is equal to X" states a value, so treating it as "X% of the target's base" would
     * multiply the derived number by the target's own stat.
     */
    static StatModifierBuff statModifier(AttributeType attribute, double percent, int turns,
                                         boolean permanent, int maxStacks, boolean absolute) {
        // A negative number is a debuff for every attribute EXCEPT the ones whose NAME is already the negative
        // direction. `RESISTANCE_REDUCTION` is the one that has bitten: the formula SUBTRACTS it from the victim's
        // resistance (`rawResist -= defender.getAttribute(RESISTANCE_REDUCTION).get()`), so a POSITIVE value there
        // means "their resistance is lower" -- a debuff on the victim. Reading the sign alone filed all seven
        // shipped readers as BUFFS, so "解除敌方 N 个增益效果" could strip the very reduction the same cast had just
        // applied. Measured: 罗刹's Ultimate dispelled his own 星魂 6.
        //
        // Note: Fixed here, at the single place that decides the role, rather than by asking content to write a
        // negative percent -- that spelling would be true to the role but false to the attribute's documented
        // direction, and every future reader would have to remember it.
        boolean negativeDirection = attribute == AttributeType.RESISTANCE_REDUCTION;
        boolean debuff = negativeDirection ? percent > 0 : percent < 0;
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
    static double laughFactor(TriggerContext ctx, EffectSpec effect) {
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
     * The whole steps a {@code "<counter>:<step>"} spelling has reached, for the {@code steps} number provider.
     *
     * <p>Same reader as {@code per_steps} ({@link CounterSteps#wholeSteps}), so a rule that repeats an effect per
     * milestone and one that states the count as a provider agree by construction.
     *
     * @param written the spelling ({@code "<counter>:<step>"})
     * @param ctx     the firing context
     * @return {@code floor(value / step)}
     */
    static int stepsOfCounter(String written, TriggerContext ctx) {
        return parseCounterSteps(written, "steps provider").wholeSteps(ctx);
    /**
     * One parsed {@code "<counter>:<step>"} specification: which counter is read, and how wide one step is.
     *
     * <p>Three spellings share this shape and therefore this parser - the {@code steps:} scale ("every N points of a
     * counter", as a magnitude), {@code per_steps} (the same count, as a repeat count) and {@code SPEND_STEPS}'s
     * {@code steps} (the same count, consumed). One string, one meaning.
     */
    }

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
        // `"attacker": "summon"`: the sentence says the MEMOSPRITE deals it (1409's 忆灵技1: 「小伊卡对敌方全体造成…」).
        // Everything downstream - which unit's damage-dealt buffs apply, whose ATK a row-based scale would read - follows
        // this one choice, so it is resolved once here rather than patched per zone.
        if (effect.getAttacker() != null && !effect.getAttacker().isBlank()) {
            String named = effect.getAttacker().trim();
            if (!"summon".equalsIgnoreCase(named)) {
                throw new IllegalStateException("a DAMAGE states \"attacker\": \"" + named
                        + "\", and the only subject this engine can name is \"summon\" (the rule owner's memosprite)");
            }
            CanHit owner = ctx.owner();
            CanHit summon = owner == null || ctx.battle() == null ? null : ctx.battle().summonOf(owner);
            if (summon == null) {
                throw new IllegalStateException("a DAMAGE states \"attacker\": \"summon\", but " 
                        + (owner == null ? "the rule has no owner" : owner.getName() + " has no summon on the field")
                        + "; gate the rule with `self_summon_count >= 1` so it only fires while the memosprite is out");
            }
            attacker = summon;
        }
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
        // ⚠ A rule whose `skill` is TALENT deals a Talent's damage, and stating it here is what lets a screen tell it from a
        // counter or an ultimate's extra hits -- measured, all three arrive as ADDITIONAL and a front end cannot separate them
        // from the rule ordering (six attempts, each breaking a different case). The rule says so, so the engine stamps it.
        // A rule that names its own `damage_type` keeps it: that is a deliberate statement about the instance.
        if (damageType == null && "TALENT".equalsIgnoreCase(effect.getSkill())) {
            damageType = DamageType.TALENT;
        }
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
