package com.laosun.aluminium.models.skill;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.SkillEffectSpec;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.data.SkillEffects;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.BreakDamageCalculator;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.buff.SuperBreakBuff;
import com.laosun.aluminium.models.enemy.Enemy;

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
        // P11-1 (M-40): the **pre-cast hook**, and the only moment at which a rule can still change what this cast
        // does. It fires before the damage is expanded because "this cast's damage is not mine to deal" is a fact
        // about the CAST: a rule that learns it afterwards (on ULT_CAST, say) can no longer stop the swing.
        // \u2605 Which cast this is, computed ONCE and early (2026-09-30): `CAST_SETUP` is the pre-cast hook and the only
        // place a rule can raise something the cast's OWN heal must see, so it has to know the slot -- cones 20001 /
        // 21000 state exactly that. The expression is the same one the after-events use, so the two cannot disagree.
        SkillCategory category = skill == null || skill.getData() == null
                ? SkillCategory.UNSPECIFIED
                : skill.getData().getCategory();
        Battle.PendingCast cast = battle.beginCast(skill, user);
        try {
            battle.fireTriggers(TriggerEvent.CAST_SETUP, user, null, 0, 0, category);
            if (!cast.damageDelegated()) {
                resolveHits(battle, skill, user, targets, hitTargets);
            }
        } finally {
            battle.endCast(cast);
        }
        // P8-6: the skill **cast** event — placed between "damage has been expanded" and "energy has
        // been settled", so a listener gets both "what was cast" and "who was actually hit".
        // **Non-damaging skills fire it too** (hitTargets empty), which is exactly the trigger source
        // for effects like "restore skill points after casting a skill".
        //
        // ⚠ A delegated cast fires it with an EMPTY hit set, and that is the point: the swing happens later, as
        // whatever the rules deliver it with (for 长夜月's ultimate, `COMMAND_SUMMON` on this very event). So a
        // delegated cast is "no damage of mine", never "no cast happened".
        try {
            broadcastSkillCast(battle, user, skill, hitTargets, targets);
            battle.grantSkillEnergy(user, skill, hitTargets);
        } finally {
            // The cast's own events have been delivered, so its "what did it actually apply" record goes away: a
            // rule firing later must not read this cast's counts as if they were its own (see Battle.endCastOutcome).
            battle.endCastOutcome();
        }
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
        for (CanHit ally : battle.allies) {
            ally.onSkillCast(battle, user, skill, hits, chosen);
        }
        // P8-7: the same moment, delivered to the data-driven trigger tables.
        //
        // Three events are derived from a cast, because the game's text distinguishes them:
        //   ULT_CAST      "after the wearer uses their Ultimate"   -- owner filters with `actor == self`
        //   SKILL_CAST    "when <someone> uses their Skill"        -- owner filters with `actor == self`
        //   BASIC_ATTACK  "after the wearer uses their Basic ATK"  -- owner filters with `actor == self`
        //   ALLY_ATTACK   "after an ally attacks"                  -- owner filters with `actor != self`,
        //                                                             and can count `hit_count`
        // "Which kind of cast is this" is read from the **parsed skill data** (`SkillCategory` from
        // `skills.json`'s attack_type), never from a skill's name or slot: the data is the only place
        // that knows, and a new skill must not need an engine change.
        //
        // The split has to be made here rather than in the data, because the condition DSL has no
        // variable for the kind of cast (it knows actor / target / hit_count only). ⚠ It is also the
        // bug that this switch exists to close (2026-09-27): before it, SKILL_CAST meant "any cast that
        // is not an ultimate", so "when the wearer uses their Skill" rules -- relic set 109's ATK buff,
        // Robin's 模进乐段 -- also fired on basic attacks. An over-trigger is a wrong number with no
        // error attached, which is exactly what this project treats as the worst failure mode.
        //
        // Everything else (technique, map basic attack, assist, elation damage, talents with an empty
        // attack_type) fires none of the three: those are not an in-battle cast. A rule that needs one
        // of them must ask for its own event, and until it exists that is a loud load-time failure
        // rather than a rule that quietly never runs.
        //
        // ALLY_ATTACK is **not** part of the split: any attack that lands is still an attack, ultimate
        // included.
        //
        // The buff-level broadcast above is deliberately *not* narrowed the same way: it hands the
        // listener the Skill object, so a buff reads the category itself when it cares (SkillCastEvent).
        //
        // `actor` = the caster. `target` = the unit the caller AIMED AT -- its main target, i.e. `targets`'s
        // first entry, which for a support cast is the ally chosen. ⚠ It is NOT "everything the effect
        // reached": an AOE reaches several units and only the first of them is named here, so a rule that
        // cares about coverage still counts `hit_count`, and the per-target events (HP_LOST etc.) carry their
        // own subject. The distinction is what 「使指定我方单体…」 needs (2026-09-28, M-35): before this the
        // field was null for every cast, and "the ally I chose" was unexpressible.
        CanHit aimed = chosen.isEmpty() ? null : chosen.getFirst();

        // \u2705 How many of the targets this attack CONNECTED WITH carry its own element's weakness (2026-09-30; reader:
        // cone 21040). Counted here because this is the only place holding the whole set; the per-target events cannot
        // reconstruct it, since a multi-target attack fires them one target at a time.
        int weakHitCount = 0;
        if (skill != null && skill.getData() != null && skill.getData().getElement() != null) {
            Object element = skill.getData().getElement();
            for (CanHit hit : hits) {
                if (hit instanceof com.laosun.aluminium.models.enemy.Enemy enemy
                        && enemy.isWeakTo((com.laosun.aluminium.enums.DamageElement) element)) {
                    weakHitCount++;
                }
            }
        }
        // A skill with no data (an EnemySkill, a hand-made placeholder constructed by a test) has no
        // attack_type to testify and is treated as UNSPECIFIED: it fires none of the three events.
        // Guessing from the slot instead would make the answer depend on how the skill was built.
        SkillCategory category = skill == null || skill.getData() == null
                ? SkillCategory.UNSPECIFIED
                : skill.getData().getCategory();
        switch (category) {
            case ULTRA -> battle.fireTriggers(TriggerEvent.ULT_CAST, user, aimed, hits.size(), 0, category, skill.getSkillSlot(), weakHitCount);
            case BPSKILL -> battle.fireTriggers(TriggerEvent.SKILL_CAST, user, aimed, hits.size(), 0, category, skill.getSkillSlot(), weakHitCount);
            case NORMAL -> battle.fireTriggers(TriggerEvent.BASIC_ATTACK, user, aimed, hits.size(), 0, category, skill.getSkillSlot(), weakHitCount);
            default -> {
                // not an in-battle cast: see above
            }
        }
        if (!hits.isEmpty()) {
            // ⚠ Deliberately NOT handed the aim, unlike the three cast events above: ALLY_ATTACK fires for one of
            // OUR attacks, so the unit it was aimed at is always on the other side -- no rule of ours could ask
            // about it (`target == self` would be permanently false, and there is no selector for "an enemy").
            // Passing it would be information with no reader, which is the shape this project keeps refusing.
            // ⚠ category rides along (2026-09-28): 「施放 2 次普攻/战技/终结技」 must tell the three slots apart, and this
            // is the one event that fires once per CAST (DEALING_DAMAGE would count hits).
            battle.fireTriggers(TriggerEvent.ALLY_ATTACK, user, aimed, hits.size(), 0, category, skill.getSkillSlot(), weakHitCount);
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
     * @param battle  the running battle
     * @param skill   the skill being cast (its identity keys the table)
     * @param user    the caster (the {@code healer_*} scales are theirs)
     * @param targets who the caller selected
     * @param effect  the parsed effect category, for the diagnostic
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
                // `user` is the provider, so a Shield skill is boosted by ITS owner's 「提供的护盾量提高」 — the same
                // rule the SHIELD op follows (see Battle.grantShield).
                battle.grantShield(user, target, amount);
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
     * <p>The row is chosen by the skill's <b>current level</b> — its own level from the character file plus this
     * battle's raises ({@code CanHit.skillLevel}, M-32) — never hardcoded to max level, and the same resolver the
     * damaging path and {@code TriggerInterpreter.multiplierOf} use.
     *
     * @throws IllegalStateException when the table names a parameter the skill row does not have,
     *                               which means the generated table and the data have drifted apart
     */
    private static double effectAmount(Skill skill, SkillEffectSpec spec, CanHit user, CanHit target) {
        List<List<Double>> levels = skill.getData().getSkills();
        int row = user.skillLevel(skill) - 1;
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
        int index = user.skillLevel(skill) - 1;
        if (index < 0 || index >= levels.size()) {
            return;
        }
        List<Double> params = levels.get(index);
        if (params == null || params.isEmpty()) {
            return;
        }
        // ? The description names the base (2026-09-29): 「防御力」 -> DEF, 「生命上限」 -> Max HP, otherwise ATK. 18 documents scale a DAMAGE clause
        // off Max HP and 3 off DEF, and multiplying ATTACK for those dealt the wrong damage.
        AttributeType baseAttribute = data.damageBaseAttribute();
        double base = user.getAttribute(baseAttribute).get() * params.getFirst();
        // ? A blast states TWO multipliers: the centre and the neighbours (2026-09-29). 1008's ultimate row is
        // `[1.92, 0.96]`..`[3.2, 1.6]` — the second exactly half the first, matching 「320%…and 160% to enemies adjacent to it」.
        // Until this, the centre value was applied to the neighbours too, i.e. double the documented damage on every
        // blast skill in the corpus.
        double neighbourBase = params.size() > 1
                ? user.getAttribute(baseAttribute).get() * params.get(1)
                : base;
        CanHit mainTarget = targets.getFirst();

        double totalDamage = 0;

        switch (effect) {
            case SINGLE_ATTACK, MAZE_ATTACK ->
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets,
                            data.getStanceList().single(), skill.getSkillSlot());

            case AOE_ATTACK -> {
                double stance = data.stanceFor(true);
                for (CanHit target : battle.targetableEnemies()) {
                    totalDamage += hit(battle, data, user, element, base, target, hitTargets, stance, skill.getSkillSlot());
                }
            }

            case BLAST -> {
                List<CanHit> alive = battle.targetableEnemies();
                int center = alive.indexOf(mainTarget);      // position order = battle.enemies order
                double centreStance = data.stanceFor(true);
                double neighbourStance = data.stanceFor(false);
                if (center < 0) {
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets, centreStance, skill.getSkillSlot());
                } else {
                    totalDamage += hit(battle, data, user, element, base, alive.get(center), hitTargets, centreStance, skill.getSkillSlot());
                    if (center > 0) {
                        totalDamage += hit(battle, data, user, element, neighbourBase, alive.get(center - 1),
                                hitTargets, neighbourStance, skill.getSkillSlot());
                    }
                    if (center < alive.size() - 1) {
                        totalDamage += hit(battle, data, user, element, neighbourBase, alive.get(center + 1),
                                hitTargets, neighbourStance, skill.getSkillSlot());
                    }
                }
            }

            case BOUNCE -> {
                // ? Bounce rows disagree on layout (measured 2026-09-29): 1009 `[0.25]` (count in prose), 1108 `[4, 0.28]`
                // (count first), 1004 `[0.36, 0.65, 0.1, 2]` (count last). Reading a fixed index made shipped Welt compute
                // `(int) 0.65` = 0 hits, i.e. no damage at all — so both numbers now come from the description's placeholders.
                Double bounceShare = data.bounceDamageShare();
                double bounceBase = bounceShare == null
                        ? base
                        : user.getAttribute(baseAttribute).get() * bounceShare;
                Integer additional = data.bounceAdditionalHits(bounceShare);
                int hits = (additional == null ? 0 : additional) + 1;   // 「额外造成 N 次」: the total is N + 1
                // H-3: for a bounce, `single` is the **total toughness reduction of the whole skill**,
                // so it MUST be spread evenly over the hits; otherwise more hits means more reduction
                double perHitStance = data.stanceFor(true) / Math.max(1, hits);
                for (int i = 0; i < hits; i++) {
                    // re-fetch the living targets for each hit: if one is killed mid-way, switch
                    // target instead of wasting hits on a corpse
                    List<CanHit> alive = battle.targetableEnemies();
                    if (alive.isEmpty()) {
                        break;                                     // all dead → the remaining hits are forfeited
                    }
                    totalDamage += hit(battle, data, user, element, bounceBase,
                            alive.get(battle.getRng().nextInt(alive.size())), hitTargets, perHitStance, skill.getSkillSlot());
                }
            }

            default -> {
            }
        }

        // The attack-level event (P8-6). Single implementation lives on Battle, because a summon's attack
        // (EnemySkill) is an attack too and must raise the same notification -- see Battle.fireAfterAttack for
        // which attacks qualify and why derived hits deliberately do not.
        battle.fireAfterAttack(user, mainTarget, hitTargets, totalDamage);
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
    /**
     * \u2705 Which {@link DamageType} a cast produces, read from the parsed skill data (2026-09-30).
     *
     * <p>\u2605 The only distinction today is \u6b22\u6109: the data spells it {@code ElationDamage} on the attack type, and
     * {@link SkillCategory} has parsed that value since the type table was written -- so a skill the game calls Elation damage
     * settles as {@link DamageType#ELATION}. Everything else stays NORMAL, exactly as before.
     * \u26a0 Its boost is folded into the base in slice 1b (the type is deliberately not boostable).
     */
    public static DamageType damageTypeOf(SkillData data) {
        if (data != null && data.getCategory() == SkillCategory.ELATION_DAMAGE) {
            return DamageType.ELATION;
        }
        return DamageType.NORMAL;
    }

    private static double hit(Battle battle, SkillData data, CanHit user, DamageElement element, double base,
                              CanHit target, Set<CanHit> hitTargets, double stanceDamage, int skillKey) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        hitTargets.add(target);                      // the fact of hitting (including targets that die afterwards) — "each time 1 target is attacked"
        // The cast's own category rides along (P10-4): it is what `Battle.assemble` reads to apply a scoped
        // DMG boost ("普攻/战技/终结技造成的伤害提高 X%"), which the damage *type* cannot express -- every
        // in-battle cast produces DamageType.NORMAL. `data` is null for a hand-made or placeholder skill, and
        // then the instance has no scoped boost rather than a guessed one.
        // \u2705 The DAMAGE TYPE follows the data (2026-09-30; readers: the nine `ElationDamage` skills -- 1501/1502/1505/1506/
        // 8009/8010/1513, four of them already shipped and until now settling their \u6b22\u6109 damage as NORMAL).
        Damage damage = new Damage(user, target, element, damageTypeOf(data), base,
                data == null ? SkillCategory.UNSPECIFIED : data.getCategory());
        // ? Which skill caused it (2026-09-28): DEALING_DAMAGE is where a target-bearing clause can ask, and this is the
        // only place that knows -- the caller holds the Skill, the instance carries the answer.
        damage.setSkillKey(skillKey);
        // ⚠ The intended toughness reduction rides on the instance (2026-09-28): a `DEALING_DAMAGE` rule is handed this
        // damage, and 「本次伤害的**削韧值**」 has to be readable there -- 1321/8006's super-break clauses are exactly that.
        // ⚠ Set BEFORE the settlement below: `DEALING_DAMAGE` is fired from inside `battle.applyDamage`.
        damage.setStance(stanceDamage);
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
     * 0 = neither). They are settled inside {@code Battle.reduceToughness} / in
     * {@link #applySuperBreak}, so they must be accumulated via the return value into this
     * attack's total — otherwise {@code AttackEvent.totalDamage} would miss the entire break
     * chain.
     * <p>Both of these are **derived hits** (already marked {@code notCountsAsAttack()}): they
     * do not grant energy to the defender (one attack action grants energy only once, handled
     * by the main hit), but a kill still grants energy to the attacker
     */
    private static double applyStanceDamage(Battle battle, CanHit user, DamageElement element,
                                            Damage damage, CanHit target, double stanceDamage) {
        if (stanceDamage <= 0 || !damage.isCountsAsAttack() || !(target instanceof Enemy enemy)) {
            return 0;                                // additional damage / true damage does not reduce toughness
        }
        // 「使本次攻击的削韧值提高100%」 (2026-09-28): the reduction this instance causes is multiplied by the attacker's
        // toughness boosts -- read HERE, the one place that turns a nominal reduction into a settled one, and NOT inside
        // Battle.reduceToughness (which enemy skills and the demo script also call).
        double effectiveStance = stanceDamage * (1 + user.getBuffManager().toughnessBoost());
        // The instance is passed on: a weakness break caused by THIS cast has to be attributable to it
        // (「施放战技…造成弱点击破时」), and damage is the only thing carrying the cast's category.
        Battle.StanceResult stance = battle.reduceToughness(user, enemy, element, effectiveStance, damage.getCastCategory());
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
}
