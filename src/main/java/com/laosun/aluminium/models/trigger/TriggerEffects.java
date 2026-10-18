package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.*;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.buff.*;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies one effect of a rule: the {@code switch} arm bodies behind {@code applyOne}.
 *
 * <p><b>Why this is its own class.</b> The dispatcher answers "which op is this?" and this
 * answers "what does that op do?". There are nineteen of the latter and each is a paragraph of
 * engine-poking with its own history -- why {@code MODIFY_ATTR} treats a ratio attribute as a
 * flat value, why a DoT's stack cap is refused everywhere except {@code APPLY_DOT}, why a shield
 * has two grant paths. Keeping them beside the gate that reads conditions and the resolver that
 * picks targets made all three look like one subject; they are three.
 *
 * <p>These are the only effect bodies that touch engine state, so this class and
 * {@link TriggerInterpreter} are where a rule's promises actually become moves.
 *
 * <p>Note: Helpers that several appliers share -- lifetime and tick-owner assembly, the rolled
 * buff attachment, the stat-modifier constructor -- stay in {@link TriggerInterpreter}, because
 * the load-time side reads them too. They are package-private for that reason rather than
 * duplicated here.
 */
final class TriggerEffects {

    private TriggerEffects() {
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
    static void modifyAttr(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
            com.laosun.aluminium.enums.DamageType instanceScope = TriggerInterpreter.parseDamageType(effect, "MODIFY_ATTR", null);
            if (instanceScope != null && ctx.damage().getType() != instanceScope) {
                return;   // the instance belongs to another damage type: this rule does not speak about it
            }
            if (attribute == AttributeType.DEFENCE_IGNORE) {
                // Note: this read `effect.getPercent()` alone, which silently dropped a stated `amount` and `scale`.
                // Measured on 1512's Fever aura ("15% + 0.5% per 气氛值"): only the 0.5% arrived, at every gauge value.
                double instanceMagnitude = TriggerMagnitudes.magnitudeOf(effect, ctx);
                if (effect.getAmountCap() != null) {
                    instanceMagnitude = Math.min(instanceMagnitude, effect.getAmountCap());
                }
                instanceMagnitude *= ctx.target() == null ? 1 : TriggerInterpreter.perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addDefenceIgnore(instanceMagnitude);
                return;
            }
            if (attribute == AttributeType.CRIT_CHANCE) {
                // Same fix as DEFENCE_IGNORE: `percent` alone dropped a stated `amount`/`scale`.
                ctx.damage().addCritChance(TriggerMagnitudes.magnitudeOf(effect, ctx));
                return;
            }
            if (attribute == AttributeType.CRIT_ATTACK) {
                // per_stack belongs here too (reader: cone 23016's "each stack of [温驯] raises CRIT DMG"):
                // the two branches beside this one already scaled by the factor, and this one did not -- measured, a rule at
                // one layer and at two layers produced the SAME number. Same shape as DEFENCE_IGNORE / the boost channel.
                double critDamage = TriggerMagnitudes.magnitudeOf(effect, ctx);
                critDamage *= ctx.target() == null ? 1 : TriggerInterpreter.perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addCritDamage(critDamage);
                return;
            }
            // "damage dealt is raised by X%" as a property of THIS hit (reader: cone 21043's
            // "for each character on the field holding a shield"). It reuses the same boost channel BOOST_DAMAGE writes, so the
            // number this rule adds is read by the damage formula exactly like any other boost of that instance -- and
            // `per_stack` is honoured, which is what makes a LIVE count ("the field right now") expressible at all.
            // Note: Before this, such a rule had to be a written modifier, i.e. a snapshot taken at cast time.
            if (attribute == AttributeType.ALL_DAMAGE_TYPE_BOOST) {
                // Same fix as DEFENCE_IGNORE: `percent` alone dropped a stated `amount`/`scale`.
                double instanceBoost = TriggerMagnitudes.magnitudeOf(effect, ctx);
                if (effect.getAmountCap() != null) {
                    instanceBoost = Math.min(instanceBoost, effect.getAmountCap());
                }
                instanceBoost *= ctx.target() == null ? 1 : TriggerInterpreter.perStackFactor(effect, ctx.target(), ctx);
                ctx.damage().addBoost(instanceBoost);
                return;
            }
            throw new IllegalStateException("Op MODIFY_ATTR with instance=true supports DEFENCE_IGNORE, CRIT_CHANCE and CRIT_ATTACK on "
                    + "DEALING_DAMAGE; " + attribute + " has no instance-level slot yet");
        }
        // Whether the magnitude is a PRODUCT (scale x share, i.e. already an absolute number in the attribute's units)
        // or a bare SHARE of the target's base. This is the flag everything below needs: a scaled magnitude must be
        // handed to `statModifier` as "pure" (absolute), while a share - including one stated by SPELLING
        // (`percent_from_skill_param` and friends) with no scale - is a percentage of the base.
        //
        // Note: Before this, the flag passed down was `derived` (= scaled OR "no literal percent"), which is true for a
        // no-scale share as well. Measured consequence: 1002 Dan Heng (丹恒)'s technique 「攻击力提高 40%」, stated as
        // `percent_from_skill_param: "TECHNIQUE:0"`, produced a modifier of `ATTACK PURE_VALUE 0.4` - i.e. +0.4 attack
        // instead of +40% - while the literal `percent: 0.4` it replaced produced `add_percent` and the right thing.
        boolean scaled = effect.getScale() != null && !effect.getScale().isBlank();
        boolean derived = scaled || effect.getPercent() == null;
        // Whether the magnitude is already ABSOLUTE in the attribute's units: a flat `amount` ("raise SPEED by 50
        // points", 艾丝妲's ultimate) or a scaled product ("equal to X% of DEF"). A bare share - a literal `percent` or
        // one of its spellings with no scale - is a fraction of the target's base instead.
        boolean absolute = !TriggerMagnitudes.statesShare(effect) || scaled;
        // ? A plain modifier is a share OR a flat value: `amount` alone means "raise the attribute by this many points", which the
        // validator enforces as exactly one of the two.
        // Note: The share may come from the skill parameter now, so "is a share stated" is NOT `percent != null`:
        // asking only that sent a `percent_from_cast_param` modifier down the flat `amount` arm and unboxed a null. A share is
        // `percent` OR `percent_from_cast_param`; the derived flag above already covers the latter.
        double magnitude;
        // Note: `percent_from_skill_param` is the THIRD way to state a share, and this is the very condition the comment above warns
        // about: a share spelling left out of it sends the effect down the flat `amount` arm and unboxes a null. Measured: that is exactly what happened.
        // Note: The fork now asks the magnitude reader's own predicate (`TriggerMagnitudes.statesShare`), which is the
        // list of spellings `shareOf` actually reads - a copy here is the thing that kept falling one spelling behind
        // (cast_param, then skill_param, then summon_param).
        if (TriggerMagnitudes.statesShare(effect)) {
            // Note: `scaled`, not `derived` - a no-scale share is the magnitude ITSELF; its constant term (if any) is a
            // separate absolute modifier, added below (`1409`'s 「30% + 600」). Folding the constant in here turned
            // "30% + 600" into "600.3 x base" (measured: 652,900 instead of 926).
            magnitude = scaled ? TriggerMagnitudes.derivedMagnitude(effect, ctx) : TriggerMagnitudes.shareOf(effect, ctx);
        } else {
            magnitude = effect.getAmount();
        }
        boolean permanent = TriggerLifetimes.unticked(effect);
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
                applied *= TriggerInterpreter.perStackFactor(effect, target, ctx);
            }
            if (absolute) {
                applied = TriggerMagnitudes.applyDerivedCeiling(effect, ctx, applied);
            } else if (effect.getCapScale() != null || effect.getCapPercent() != null) {
                double ceiling = TriggerMagnitudes.resolveScale(effect.getCapScale(), effect.getCapPercent(), ctx);
                double base = target.getAttribute(attribute).baseValue();
                if (base > 0) {
                    applied = Math.min(applied, ceiling / base);
                }
            }
            AbstractBuff buff = TriggerInterpreter.withSource(TriggerLifetimes.withTickOwner(
                    TriggerLifetimes.withLifetime(TriggerInterpreter.statModifier(attribute, applied, turns, permanent, maxStacks, absolute), effect),
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
                if (!derivedCounter && !TriggerInterpreter.ctxFreeCounter(effect)) {
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
                        String counter = TriggerInterpreter.counterName(effect);
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
            TriggerInterpreter.attachRolled(battle, target, buff, effect, ctx);
            // "the raise is X% + Y points" (1409's 【雨过天晴】: `percent_from_skill_param` + `amount_from_skill_param`):
            // the share arm is a FRACTION of the target's base, the constant term is in the attribute's own units - two
            // different modifiers, so the constant gets its own, with the same lifetime and source. Without this the
            // constant is silently dropped (or, when both spellings share one magnitude, applied as a fraction x base).
            if (!absolute) {
                double constant = TriggerMagnitudes.constantTerm(effect, ctx);
                if (constant != 0) {
                    AbstractBuff extra = TriggerInterpreter.withSource(TriggerLifetimes.withTickOwner(
                            TriggerLifetimes.withLifetime(TriggerInterpreter.statModifier(attribute, constant, turns,
                                    permanent, maxStacks, true), effect), effect, ctx), ctx);
                    TriggerInterpreter.attachRolled(battle, target, extra, effect, ctx);
                }
            }
        }
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
    static void castSkill(Battle battle, EffectSpec effect, TriggerContext ctx) {
        CanHit actor = TriggerTargets.require(TriggerTargets.resolveTarget(effect, ctx), "target", ctx);
        SkillType slot = effect.getSkill() == null || effect.getSkill().isBlank()
                ? null
                : SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        Skill skill = slot == null ? null : actor.getSkills().get(slot);
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
    static void applyControl(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
                // ⚠ Announced on the SUCCESS branch. The attempt is not the event: the state was rolled through the resist
                // pipeline, and reporting a resisted application would claim a freeze that never happened.
                battle.announceControl(ctx.owner(), target, control.name());
            }
        }
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
    static void applyState(Battle battle, EffectSpec effect, TriggerContext ctx) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        int turns = permanent ? UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        String state = effect.getBuff().trim();
        // How many instances this application carries (reader: 1513's reward, whose state's INSTANCE COUNT is
        // the [符点] it spent). A plain state refreshes on re-application, so only a STACKABLE one is repeated --
        // otherwise the loop would be a no-op dressed as a feature.
        int times = 1;
        if (Boolean.TRUE.equals(effect.getStackable())) {
            if (effect.getScale() != null && effect.getScale().trim().startsWith(TriggerInterpreter.PARTY_RESOURCE_PREFIX)) {
                times = Math.max(1, (int) Math.round(TriggerMagnitudes.resolveScale(effect.getScale(),
                        effect.getPercent() == null ? 1 : effect.getPercent(), ctx)));
            } else if (effect.getAmount() != null) {
                times = Math.max(1, (int) Math.round(effect.getAmount()));
            }
        }
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            for (int instance = 0; instance < times; instance++) {
                AbstractBuff buff = TriggerInterpreter.withSource(TriggerLifetimes.withTickOwner(
                        TriggerLifetimes.withLifetime(Boolean.TRUE.equals(effect.getStackable())
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
                TriggerInterpreter.attachRolled(battle, target, buff, effect, ctx);
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
    static void applyDot(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
            DotBuff dot = new DotBuff(ctx.owner(), element, damage, effect.getTurns(), TriggerInterpreter.dotStackCap(effect), dotName);
            if (battle.tryApplyDebuff(ctx.owner(), target, dot, baseChance, null)) {
                // The DOT's state name is the document's name for the element (burn), from the one table that maps
                // them - the same spelling "for each 1 target made to enter burn" would count with.
                battle.recordCastApplied(BuffManager.dotStateName(element));
            }
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
    static void addStack(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
                boolean permanent = TriggerLifetimes.unticked(effect);
                AbstractBuff stack = TriggerInterpreter.withSource(
                        TriggerLifetimes.withTickOwner(
                                TriggerLifetimes.withLifetime(
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
    static void addDamageFlat(EffectSpec effect, TriggerContext ctx) {
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
        com.laosun.aluminium.enums.DamageType only = TriggerInterpreter.parseDamageType(effect, "ADD_DAMAGE", null);
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
                magnitude *= TriggerInterpreter.laughFactor(ctx, effect);
            }
        }
        damage.addFlat(magnitude);
    }

    /**
     * {@code APPLY_REGEN}: attaches a {@link com.laosun.aluminium.models.buff.RegenBuff}, whose amount is derived once
     * (the applier's panel) and settled on each of the carrier's turns by {@code Battle.tickRegens}.
     *
     * <p>Note: Its name is required, and not for bookkeeping: "the regeneration effect produced by casting the Skill is extended by 1 turn" (1105 trace 调理) and any
     * removal by name reach it through that string, exactly as a relic set's temporary buff does.
     */
    static void applyRegen(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String name = effect.getBuff() == null ? "" : effect.getBuff().trim();
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null || target.isDeath()) {
                continue;
            }
            // Note: grantAmount, not derivedMagnitude: the regeneration's magnitude is a HEAL amount, so it shares the
            // heal/shield scale vocabulary and is worked out per carrier (a 	arget_max_hp regeneration is a different
            // number for each one). The value is derived once, here, and frozen in the buff.
            double amount = TriggerInterpreter.grantAmount(effect, target, ctx);
            AbstractBuff regen = TriggerInterpreter.withSource(TriggerLifetimes.withLifetime(
                    new com.laosun.aluminium.models.buff.RegenBuff(ctx.owner(), name, amount, effect.getTurns()),
                    effect), ctx);
            target.getBuffManager().addBuff(regen);
        }
    }

    static void taunt(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            // Note: Through the resist pipeline like every other negative state: "make the target enter the taunt state" states no
            // probability, which means a 100% BASE chance - the game still runs it through Effect Hit Rate / Effect RES. Writing
            // the marker directly made the engine's taunt unconditionally certain, which no document says.
            battle.tryApplyDebuff(ctx.owner(), target, TriggerInterpreter.withSource(new TauntBuff(effect.getTurns()), ctx), 1.0, null);
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
    static void dispel(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
    static void removeStacks(Battle battle, EffectSpec effect, TriggerContext ctx) {
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

    static void removeState(EffectSpec effect, TriggerContext ctx) {
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
     * {@code REMOVE_BUFF}: "remove 1 buff effect from a designated single enemy" - takes up to {@code amount} positive, temporary buffs off
     * each resolved target.
     *
     * <p>Note: Its direction is what makes it a separate op rather than a flag on {@code DISPEL}: that one is defined as
     * "negative effects on the carrier", and the two sentences mean opposite things about the same word ("remove"). Keeping
     * them apart is also what lets each one be validated and tested on its own.
     */
    static void removeBuffs(Battle battle, EffectSpec effect, TriggerContext ctx) {
        int amount = (int) Math.round(effect.getAmount());
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target != null) {
                target.getBuffManager().removeBuffs(amount);
            }
        }
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
    static void replaceSkill(Battle battle, EffectSpec effect, TriggerContext ctx) {
        com.laosun.aluminium.models.Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx);
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
            if (target instanceof com.laosun.aluminium.models.Character character) {
                character.getBuffManager().addBuff(TriggerInterpreter.withSource(TriggerLifetimes.withLifetime(
                        new com.laosun.aluminium.models.buff.SkillSwapBuff(slot, replacement), effect), ctx));
            }
        }
    }

    /**
     * {@code TICK_DOT}: "immediately produce 1 instance of ... damage" - one extra instance of the named state, at the stated share.
     */
    static void tickDot(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
    static void extendBuff(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
    static void superBreak(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
    static void boostToughness(Battle battle, EffectSpec effect, TriggerContext ctx) {
        for (CanHit target : TriggerTargets.resolveTargets(battle, effect, ctx)) {
            if (target == null) {
                continue;
            }
            AbstractBuff boost = TriggerInterpreter.withSource(TriggerLifetimes.withLifetime(
                    new com.laosun.aluminium.models.buff.ToughnessBoostBuff(effect.getPercent(), effect.getTurns()),
                    effect), ctx);
            target.getBuffManager().addBuff(boost);
        }
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
    static void commandSummon(Battle battle, EffectSpec effect, TriggerContext ctx) {
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
     * The duration handed to a permanent modifier.
     *
     * <p>Deliberately {@code 1} and not a large number: {@link StatModifierBuff} marks the buff
     * permanent, and {@code BuffManager.processBuffTick} never counts a permanent buff down, so this
     * value is never read. It exists only because {@code AbstractBuff}'s constructor takes a turn count.
     * Spelling it as {@code Integer.MAX_VALUE} would suggest the engine relies on a big number, which
     * is exactly the misconception the flag removes.
     */
    static final int UNBOUNDED_DURATION_PLACEHOLDER = 1;
}
