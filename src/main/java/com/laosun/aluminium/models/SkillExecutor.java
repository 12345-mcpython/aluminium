package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.data.SkillEffects;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.buffs.SuperBreakBuff;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.IO.println;

/**
 * Turns one skill activation into the right number of {@link Damage} objects.
 *
 * <p>A skill is always "N hits on M targets"; every hit is settled independently through
 * {@link Battle#applyDamage(CanHit, Damage)} (each hit rolls crit and settles on its own).
 *
 * <p><b>Targeting:</b> the caller only picks the <b>main target</b>
 * ({@code targets.getFirst()}); how many targets actually get hit is a property of the
 * effect, not of the caller:
 * <ul>
 *   <li>{@code SINGLE_ATTACK} / {@code MAZE_ATTACK} → the main target only</li>
 *   <li>{@code AOE_ATTACK} → every targetable enemy on the field</li>
 *   <li>{@code BLAST} → the main target plus its battlefield neighbours (left / right)</li>
 *   <li>{@code BOUNCE} → N hits (N = the second skill param), re-targeting a living enemy each hit</li>
 * </ul>
 *
 * <p>Non-damaging effects (heal / shield / buff / control / summon) return immediately and
 * are dispatched in later phases — importantly, their params are <i>not</i> damage
 * multipliers (e.g. cid 1001 slot 2 is a shield whose first param is a shield ratio).
 *
 * <p>After the segments are settled the attack-level event
 * {@link com.laosun.aluminium.models.event.AttackEvent} is broadcast to every ally, carrying
 * the actually-hit targets and the total settled damage — that is where Robin's (知更鸟) 【协奏】
 * (Concerto) / Tribbie's (缇宝) field spawn additional damage / true damage from someone else's
 * attack.
 *
 * @see SkillEffectType#isDamaging()
 */
public final class SkillExecutor {

    private SkillExecutor() {
    }

    /**
     * Expands one skill activation into hits, settles them and broadcasts the attack event.
     *
     * <p>This is the **single hook for skill energy gain** (P3-2): no matter whether the skill deals
     * any damage (buffs/shields/heals also gain energy) and no matter whether the params are empty,
     * the caster is settled once through
     * {@link Battle#grantSkillEnergy(CanHit, Skill, Set)} when the cast finishes.
     *
     * @param battle  the running battle (targets are taken from {@code battle.targetableEnemies()})
     * @param skill   the skill being used (its {@link SkillData} decides the shape)
     * @param user    the caster
     * @param targets the caller's selection; only the first entry (main target) is used
     */
    public static void execute(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets) {
        Set<CanHit> hitTargets = new LinkedHashSet<>();   // targets actually hit (including those that died on the spot)
        resolveHits(battle, skill, user, targets, hitTargets);
        // P8-6: the skill **cast** event — placed between "damage has been expanded" and "energy has
        // been settled", so a listener gets both "what was cast" and "who was actually hit".
        // **Non-damaging skills fire it too** (hitTargets empty), which is exactly the trigger source
        // for effects like "restore skill points after casting a skill".
        broadcastSkillCast(battle, user, skill, hitTargets, targets);
        battle.grantSkillEnergy(user, skill, hitTargets);
    }

    /**
     * Fires {@link com.laosun.aluminium.models.event.SkillCastEvent} (P8-6) —
     * same convention as {@link #broadcastAfterAttack}: **every one of our members** receives it,
     * and interested parties receive it directly. Deliberately does not require {@code hitTargets} to
     * be non-empty (non-damaging skills fire it too).
     */
    private static void broadcastSkillCast(Battle battle, CanHit user, Skill skill,
                                           Set<CanHit> hitTargets, List<? extends CanHit> targets) {
        List<CanHit> hits = List.copyOf(hitTargets);
        List<CanHit> chosen = targets == null ? List.of() : List.copyOf(targets);
        for (Character ally : battle.characters) {
            ally.onSkillCast(battle, user, skill, hits, chosen);
        }
        // P8-7: the same moment, delivered to the data-driven trigger tables.
        //
        // Two events are derived from a cast, because characters distinguish them in their text:
        //   SKILL_CAST   "when <someone> casts a skill"  -- owner filters with `actor == self`
        //   ALLY_ATTACK  "after an ally attacks"          -- owner filters with `actor != self`,
        //                                                     and can count `hit_count`
        // They fire together here because a cast is the only attack the engine performs today;
        // if a non-attack cast (a heal, say) ever needs to stay out of ALLY_ATTACK, the split
        // belongs here.
        // `actor` = the caster. `target` is left null on purpose: a cast can hit several targets at
        // once, so there is no single subject to hand over -- rules that care about who was hit use
        // `hit_count`, and the per-target events (HP_LOST etc.) carry their own subject.
        battle.fireTriggers(TriggerEvent.SKILL_CAST, user, null, hits.size(), 0);
        if (!hits.isEmpty()) {
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, user, null, hits.size(), 0);
        }
    }

    /**
     * Switch for the **undispatched diagnostic log** of non-damaging skills (item 3 of the P8-2 plan).
     *
     * <p>{@link #resolveHits} **silently returns** when "the skill is not a damaging one" — that is,
     * after a heal/shield/buff/control/summon skill is cast **nothing at all happens** (only the
     * energy gain is still given). That is very hard to notice in a real team: in the log the skill
     * "went off", there was just no effect. So a toggleable diagnostic is provided here.
     *
     * <p>Why it is **off** by default: {@code Main}'s demo casts heals and shields every turn, and
     * having it on by default would flood the output. The plan originally wanted a bare
     * {@code IO.println}, but in practice that polluted the demo output, so it was changed to an
     * explicit switch.
     */
    private static boolean logNotDispatched = false;

    /**
     * Turns the "non-damaging skill was not dispatched" diagnostic log on/off. Turn it on for tests
     * and troubleshooting; keep it off for real runs.
     */
    public static void setLogNotDispatched(boolean enabled) {
        logNotDispatched = enabled;
    }

    /**
     * Records that "this skill was not fired". It also labels **which phase** implements it — so that
     * seeing "the cast succeeded but had no effect" does not leave you without a lead.
     */
    private static void logNotDispatched(Skill skill, CanHit user, SkillEffectType effect,
                                        List<? extends CanHit> targets) {
        if (!logNotDispatched) {
            return;
        }
        String phase = switch (effect.getCategory()) {
            case HEAL -> "P6-2 implemented (goes through Battle.heal, not this executor)";
            case BUFF -> "P10-3 Buff system";
            case CONTROL -> "P10-6 Debuff / control";
            case SUMMON -> "P9-4 summons";
            case PASSIVE -> "pure passive, should never be cast as an action";
            case DAMAGE -> "damaging type (should not reach here)";
        };
        var data = skill.getData();
        println("[SkillExecutor] NOT DISPATCHED: " + data.getSkillType() + " / " + effect
                + " (" + user.getName() + ", "
                + (targets == null ? "null" : targets.size() + " target(s)")
                + ") → owned by " + phase);
    }

    /**
     * Runs a non-damaging skill's effect (P10-3) instead of doing nothing.
     *
     * <p><b>What this replaced.</b> This method used to not exist: {@code resolveHits} simply returned,
     * so a heal / shield / buff / control / summon skill was cast, cost its skill point, granted its
     * energy and changed nothing. The demo's "healing" only worked because {@code Main} had grown a
     * second, hand-rolled dispatch path of its own — the engine quietly depending on its caller.
     *
     * <p><b>Which effects are actually covered.</b> Only {@code Restore} and {@code Defence}, and only
     * for skills that have an entry in {@code data/skill_effects.json}. The table cannot be derived
     * from {@code skills.json} alone, so anything missing keeps the old behaviour <i>and</i> keeps the
     * diagnostic — {@link #logNotDispatched} names the phase that owns it. Treating "no entry" as
     * "nothing to do" silently is exactly the bug being fixed here, so the distinction is preserved.
     *
     * @param battle the running battle
     * @param skill  the skill being cast (its identity keys the table)
     * @param user   the caster (the {@code healer_*} scales are theirs)
     * @param targets who the caller selected
     * @param effect the parsed effect category, for the diagnostic
     */
    private static void dispatchNonDamaging(Battle battle, Skill skill, CanHit user,
                                            List<? extends CanHit> targets, SkillEffectType effect) {
        SkillEffectSpec spec = SkillEffects.forSkill(skill);
        boolean supported = spec != null
                && ("Restore".equals(spec.getEffect()) || "Defence".equals(spec.getEffect()))
                && !isAmbiguous(spec);
        if (!supported || targets == null || targets.isEmpty()) {
            logNotDispatched(skill, user, effect, targets);
            return;
        }
        for (CanHit target : targets) {
            double amount = effectAmount(skill, spec, user, target);
            if ("Restore".equals(spec.getEffect())) {
                battle.heal(user, target, amount);
            } else {
                battle.grantShield(target, amount);
            }
        }
    }

    /**
     * Whether an entry mixes several things together so that its parameters cannot be summed.
     *
     * <p>⚠ <b>This is an interim guard, not a design.</b> Some heal skills carry more than one effect
     * in the same parameter row, and the table — which is derived from the description's {@code #N}
     * placeholders — currently lists them all:
     *
     * <pre>
     * Natasha 1105 skill  params [0.07, 0.048, 2, 70, 48]  →  0% + 3 flat  (the heal)
     *                                                          1% + 4 flat  (a heal-over-time)
     * </pre>
     *
     * <p>Summing those would heal for the direct amount <i>and</i> the per-turn amount at once — a
     * number that looks entirely plausible and is wrong. So anything with more than one percentage
     * term is refused and reported, exactly like an unsupported effect.
     *
     * <p>The real fix belongs in the generator: split the description at the clause that introduces
     * the per-turn part and emit only the <b>immediate</b> terms (with the rest stored separately for
     * the heal-over-time that does not exist yet). Until then, refusing is the honest answer — a
     * refused heal is visible, a wrong heal is not.
     */
    private static boolean isAmbiguous(SkillEffectSpec spec) {
        if (spec.getParams() == null) {
            return true;
        }
        int percents = 0;
        int flats = 0;
        for (SkillEffectSpec.Param param : spec.getParams()) {
            if ("percent".equals(param.getKind())) {
                percents++;
            } else {
                flats++;
            }
        }
        return percents != 1 || flats > 1;
    }

    /**
     * Sums the terms of an effect: each parameter is either a percentage of {@link #scaleValue} or a
     * flat addition.
     *
     * <p>The row is chosen by the skill's <b>current level</b>, the same rule as damaging skills and
     * as {@code TriggerInterpreter.multiplierOf} — never hardcoded to max level.
     *
     * @throws IllegalStateException when the table names a parameter the skill row does not have,
     *                               which means the generated table and the data have drifted apart
     */
    private static double effectAmount(Skill skill, SkillEffectSpec spec, CanHit user, CanHit target) {
        List<List<Double>> levels = skill.getData().getSkills();
        int row = skill.getLevel() - 1;
        if (row < 0 || row >= levels.size() || spec.getParams() == null) {
            return 0;
        }
        List<Double> params = levels.get(row);
        double scale = scaleValue(spec.getScale(), user, target);
        double total = 0;
        for (SkillEffectSpec.Param param : spec.getParams()) {
            if (param.getIndex() < 0 || param.getIndex() >= params.size()) {
                throw new IllegalStateException(
                        "skill_effects.json for cid " + skill.getCid() + " slot " + skill.getSkillSlot()
                                + " names parameter " + param.getIndex() + ", but the row has only "
                                + params.size() + " (source: " + spec.getSource() + ")");
            }
            double raw = params.get(param.getIndex());
            total += "percent".equals(param.getKind()) ? raw * scale : raw;
        }
        return total;
    }

    /**
     * The value a percentage term scales off.
     *
     * <p>{@code healer_*} is the caster's and {@code target_*} the recipient's. That distinction is the
     * whole reason the table has separate names for it: "of Natasha's Max HP" and "of their respective
     * Max HP" both read as "Max HP", and getting them backwards heals for a plausible-looking but
     * wrong number.
     *
     * @throws IllegalStateException on a scale this engine does not know, rather than quietly
     *                               returning 0 — the vocabulary is fixed by the generator, so an
     *                               unknown value means the engine is behind the data
     */
    private static double scaleValue(String scale, CanHit user, CanHit target) {
        if (scale == null) {
            return 0;
        }
        return switch (scale) {
            case "healer_max_hp" -> user.getMaxHp();
            case "target_max_hp" -> target.getMaxHp();
            case "atk" -> user.getAttribute(AttributeType.ATTACK).get();
            case "def" -> user.getAttribute(AttributeType.DEFENCE).get();
            case "target_missing_hp" -> Math.max(0, target.getMaxHp() - target.getCurrentHp());
            // flat-only effects have nothing to scale off
            case "base" -> 0;
            default -> throw new IllegalStateException(
                    "skill_effects.json uses an unknown scale '" + scale + "'");
        };
    }

    /**
     * Expands one skill activation into N hits and settles them (energy is not given here, see
     * {@link #execute}).
     *
     * @param hitTargets output parameter: the set actually hit
     */
    private static void resolveHits(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets,
                                    Set<CanHit> hitTargets) {
        SkillData data = skill.getData();
        SkillEffectType effect = data.getEffect();

        // 1) first decide whether it is a damaging skill: for shield/heal/buff skills the first param is not a damage multiplier
        if (!effect.isDamaging() || targets == null || targets.isEmpty()) {
            dispatchNonDamaging(battle, skill, user, targets, effect);
            return;
        }

        // 2) a damaging skill must have an element; a missing one is a data error — fail fast is
        // better than letting this hit silently vanish
        DamageElement element = data.getElement();
        if (element == null) {
            throw new IllegalStateException("Damaging skill without element: "
                    + data.getSkillType() + " (" + effect + ")");
        }

        // 3) then take the multiplier: empty params really do exist (cid 1001 slot 6 has param_list = [[]])
        List<List<Double>> levels = data.getSkills();
        int index = skill.getLevel() - 1;
        if (index < 0 || index >= levels.size()) {
            return;
        }
        List<Double> params = levels.get(index);
        if (params == null || params.isEmpty()) {
            return;
        }
        double base = user.getAttribute(AttributeType.ATTACK).get() * params.getFirst();
        CanHit mainTarget = targets.getFirst();

        double totalDamage = 0;

        switch (effect) {
            case SINGLE_ATTACK, MAZE_ATTACK ->
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets,
                            data.getStanceList().single());

            case AOE_ATTACK -> {
                double stance = data.getStanceList().all();
                for (Enemy target : battle.targetableEnemies()) {
                    totalDamage += hit(battle, data, user, element, base, target, hitTargets, stance);
                }
            }

            case BLAST -> {
                List<Enemy> alive = battle.targetableEnemies();
                int center = alive.indexOf(mainTarget);      // position order = battle.enemies order
                double centreStance = data.getStanceList().single();
                double neighbourStance = data.getStanceList().spread();
                if (center < 0) {
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets, centreStance);
                } else {
                    totalDamage += hit(battle, data, user, element, base, alive.get(center), hitTargets, centreStance);
                    if (center > 0) {
                        totalDamage += hit(battle, data, user, element, base, alive.get(center - 1),
                                hitTargets, neighbourStance);
                    }
                    if (center < alive.size() - 1) {
                        totalDamage += hit(battle, data, user, element, base, alive.get(center + 1),
                                hitTargets, neighbourStance);
                    }
                }
            }

            case BOUNCE -> {
                int hits = params.size() > 1 ? (int) (double) params.get(1) : 1;   // hit count defaults to 1
                // H-3: for a bounce, `single` is the **total toughness reduction of the whole skill**,
                // so it MUST be spread evenly over the hits; otherwise more hits means more reduction
                double perHitStance = stanceValue(data, true) / Math.max(1, hits);
                for (int i = 0; i < hits; i++) {
                    // re-fetch the living targets for each hit: if one is killed mid-way, switch
                    // target instead of wasting hits on a corpse
                    List<Enemy> alive = battle.targetableEnemies();
                    if (alive.isEmpty()) {
                        break;                                     // all dead → the remaining hits are forfeited
                    }
                    totalDamage += hit(battle, data, user, element, base,
                            alive.get(battle.getRng().nextInt(alive.size())), hitTargets, perHitStance);
                }
            }

            default -> {
            }
        }

        broadcastAfterAttack(battle, user, mainTarget, hitTargets, totalDamage);
    }

    /**
     * Fires {@link com.laosun.aluminium.models.event.AttackEvent} on every ally (this is how Robin's
     * (知更鸟) / Tribbie's (缇宝) "after one of our attacks" effects carried on themselves receive it).
     * Additional damage / true damage does not go through here, so it does not recurse.
     */
    private static void broadcastAfterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                            Set<CanHit> hitTargets, double totalDamage) {
        if (hitTargets.isEmpty()) {
            return;                                  // not a single hit landed → does not count as an attack
        }
        List<CanHit> targets = List.copyOf(hitTargets);
        for (Character ally : battle.characters) {
            ally.afterAttack(battle, attacker, mainTarget, targets, totalDamage);
        }
    }

    /**
     * Settles one hit, reduces toughness (P4-2) and accumulates it into the attack summary.
     *
     * <p>Toughness reduction follows the "two chains share one nominal value" convention (P4-6):
     * {@link Battle#reduceToughness} hands back both the amount actually reduced and the excess, and
     * the latter turns into one instance of super break damage when the caster carries
     * {@link SuperBreakBuff}.
     *
     * @param stanceDamage the toughness points this hit should reduce (already computed by the caller
     *                     according to the skill shape and hit count: AOE uses {@code all}, BLAST's
     *                     center uses {@code single} / its neighbours {@code spread}, BOUNCE spreads
     *                     the total evenly over the hits)
     * @return the settled damage of this hit (0 if the target was dead / invulnerable)
     */
    private static double hit(Battle battle, SkillData data, CanHit user, DamageElement element, double base,
                              CanHit target, Set<CanHit> hitTargets, double stanceDamage) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        hitTargets.add(target);                      // the fact of hitting (including targets that die afterwards) — "each time 1 target is attacked"
        // the 4-arg constructor → DamageType.NORMAL; after P8-2 wires up real slots, map by basic attack / skill / ultimate
        Damage damage = new Damage(user, target, element, base);
        double settled = battle.applyDamage(target, damage);
        settled += applyStanceDamage(battle, user, element, damage, target, stanceDamage);
        return settled;
    }

    /**
     * Toughness reduction (P4-2) + super break (P4-6): only damage that "counts as one attack"
     * reduces toughness.
     *
     * <p>Measured against the data (a full tally of {@code skills.json}): single-target/technique/bounce
     * use {@code single} (30 = 1 unit, 60 = 2, 90 = 3), AOE uses {@code all}, and **blast uses
     * {@code single} (center) + {@code spread} (neighbours)** — example: Himeko's (姬子) skill =
     * {@code 60/0/30}. For a bounce, {@code single} is the **total** for the whole skill and is
     * spread evenly over the hits (H-3).
     *
     * @param stanceDamage the points this hit actually reduces (0 = this shape does not reduce toughness)
     * @return the damage **additionally** settled by this hit (break damage + super break damage;
     *         0 = neither). They are settled inside {@code Battle.reduceToughness} / in
     *         {@link #applySuperBreak}, so they must be accumulated via the return value into this
     *         attack's total — otherwise {@code AttackEvent.totalDamage} would miss the entire break
     *         chain.
     *         <p>Both of these are **derived hits** (already marked {@code notCountsAsAttack()}): they
     *         do not grant energy to the defender (one attack action grants energy only once, handled
     *         by the main hit), but a kill still grants energy to the attacker
     */
    private static double applyStanceDamage(Battle battle, CanHit user, DamageElement element,
                                            Damage damage, CanHit target, double stanceDamage) {
        if (stanceDamage <= 0 || !damage.isCountsAsAttack() || !(target instanceof Enemy enemy)) {
            return 0;                                // additional damage / true damage does not reduce toughness
        }
        Battle.StanceResult stance = battle.reduceToughness(user, enemy, element, stanceDamage);
        return stance.breakDamage() + applySuperBreak(battle, user, enemy, element, stance.superBreakStance());
    }

    /**
     * Super break (P4-6): converts "the part of the toughness-reduction value that cannot go into
     * the toughness bar" into one instance of {@link DamageType#SUPER_BREAK} damage.
     *
     * <p>There are only two trigger conditions: the caster carries {@link SuperBreakBuff} (a pure
     * marker), and {@code superBreakStance > 0} (the enemy was already broken, or this hit breaks it).
     *
     * <p>Note that {@code superBreakStance} is the **excess** and not the whole toughness-reduction
     * value: in the hit that breaks the toughness, the first half of the reduction was already used
     * for the break ({@link BreakDamageCalculator}), so only the excess half may be used here;
     * otherwise the same nominal toughness-reduction value would be spent twice.
     *
     * @param superBreakStance the part of the toughness-reduction value that exceeds the remaining toughness
     * @return the super break damage settled by this hit (0 = not triggered)
     */
    private static double applySuperBreak(Battle battle, CanHit user, Enemy enemy, DamageElement element,
                                          double superBreakStance) {
        if (superBreakStance <= 0 || !user.getBuffManager().hasBuff(SuperBreakBuff.class)) {
            return 0;
        }
        Damage superBreak = BreakDamageCalculator.buildSuperBreak(user, enemy, element, superBreakStance);
        return battle.applyDamage(enemy, superBreak);
    }

    /**
     * The toughness-reduction points of **one hit** of this skill on one target (excluding the bounce's
     * per-hit spreading, which is done in the {@code BOUNCE} branch).
     */
    private static double stanceValue(SkillData data, boolean mainTarget) {
        // note: StanceList lives in beans.Skill (same simple name as models.Skill but a different
        // package), so the fully-qualified name is used here
        com.laosun.aluminium.beans.Skill.StanceList stance = data.getStanceList();
        return switch (data.getEffect()) {
            case AOE_ATTACK -> stance.all();
            case BLAST -> mainTarget ? stance.single() : stance.spread();
            default -> stance.single();
        };
    }
}
