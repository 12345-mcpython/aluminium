package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Answers "which units does this effect reach?" -- the half of the trigger language that
 * turns an effect's {@code target} into combatants.
 *
 * <p><b>Why this is its own class.</b> A rule says what happens; a selector says to whom.
 * The two are independent enough that the same selector is read by dozens of ops
 * ({@code resolveTargets} alone has 21 callers), and the vocabulary that spells them --
 * plain names like {@code all_allies}, parameterised ones like {@code holder_of:同袍}, and
 * {@code cid:} / {@code allies_of_path:} -- is a language of its own, with its own reasons
 * for each spelling.
 *
 * <p><b>The selectors are a closed set on purpose.</b> An unrecognised target used to fall
 * back to "the rule's owner", so a typo behaved exactly like {@code self}: a wrong answer
 * that reports nothing. The set is checked at load time (see {@link TriggerValidator}), and
 * everything here assumes it already passed.
 *
 * <p>Note: Resolution needs a {@link Battle} for the group selectors -- "all enemies" is a
 * question about a camp, not about an effect -- while the single-target selectors answer
 * from the context alone. Both shapes live here because they share the vocabulary and the
 * {@code holder_of:} parsing that decides which one is being asked for.
 */
final class TriggerTargets {

    private TriggerTargets() {
    }


    static CanHit resolveTarget(EffectSpec effect, TriggerContext ctx) {
        return resolveSelector(normalizeTarget(effect), effect, ctx);
    }

    /**
     * Who an effect applies to, as a list, for the ops that can reach more than one party member.
     *
     * <p>{@code "all_allies"} (alias {@code "party"}) is what makes "all allies' ATK +X%" expressible.
     * It matters more than it looks: most buff talents in this game's data are party-wide, so without
     * this selector the trigger table would only cover self-buffs. It is resolved against
     * {@link Battle#allies} - the camp, <b>not</b> {@code characters} - so a player-side summon counts as
     * "our side" and receives the party buff too; and it therefore needs a battle, which the single-target
     * selectors do not.
     *
     * @param battle the running battle (may be {@code null} only when the selector is single-target)
     * @param effect the effect
     * @param ctx    the context
     * @return the entities the effect applies to (never empty for the single-target selectors)
     * @throws IllegalStateException when {@code all_allies} is used without a battle
     */
    static List<CanHit> resolveTargets(Battle battle, EffectSpec effect, TriggerContext ctx) {
        List<CanHit> resolved = resolveTargetsUnfiltered(battle, effect, ctx);
        if (ctx.targetFilter().isEmpty()) {
            return resolved;
        }
        List<CanHit> filtered = new ArrayList<>();
        for (CanHit candidate : resolved) {
            if (candidate != null && ctx.passesTargetFilter(candidate)) {
                filtered.add(candidate);
            }
        }
        return List.copyOf(filtered);
    }

    /**
     * The same resolution, for a selector that is not the effect's own {@code target}
     * ({@code cast_target},) - one switch, so the two spellings cannot drift apart.
     *
     * @param selector the selector token (already lower-cased and trimmed)
     *                 "to Hyacine (风堇)...": a selector that names a character by cid.
     */
    static CanHit resolveSelector(String selector, EffectSpec effect, TriggerContext ctx) {
        if (selector.startsWith(ALLY_CID_PREFIX)) {
            int cid = Integer.parseInt(selector.substring(ALLY_CID_PREFIX.length()).trim());
            return require(allyWithCid(ctx, cid), selector, ctx);
        }
        return switch (selector) {
            case "self" -> ctx.owner();
            case "target" -> require(ctx.target(), "target", ctx);
            case "attacker" -> require(ctx.actor(), "attacker", ctx);
            // Note: `actor` is the same unit as `attacker`, under the name the CONDITION language and the event
            // vocabulary already use (`actor == self`, "the event's actor"). It was missing here, so an op could read
            // the actor in its conditions but had no way to ADDRESS it - which is why 1503's extra-turn reward ("that
            // extra turn begins: the ARCHETYPE gains 30") could not be written even though EXTRA_TURN_STARTED fires
            // with exactly that unit as its actor.
            case "actor" -> require(ctx.actor(), "actor", ctx);
            case "summon" -> requireSummon(ctx);
            // "randomly 1 enemy target": the roll is the battle's own seeded one, so the same seed picks the
            // same unit -- and the judge can therefore pin both determinism and genuine variation.
            // "our next other target to act"(cone 21025): the unit that will act after this one, walking the
            // queue forward and wrapping, skipping the other side and the owner itself. The text says
            // "next to act" -- the NEXT to act, not the party's first ally (which would be the one who just
            // acted). Read from the queue; the head alone is not enough (measured: peekNext() can be the
            // acting unit itself).
            case "next_ally" -> require(nextAllyToAct(ctx), "next_ally", ctx);
            // "first place in the party"(relic 31): the FIRST CHARACTER of the party, in party order -- read from
            // battle.characters, not battle.allies (summons are appended to that one).
            case "party_first" -> require(partyFirst(ctx), "party_first", ctx);
            case "random_ally_below_half_energy" ->
                    require(randomAllyBelowHalfEnergy(ctx), "random_ally_below_half_energy", ctx);
            // The enemy that entered with the current wave: a commanded cast needs ONE aim, and `wave_monsters` is the set.
            // Fails loudly on an empty wave -- an aim that resolves to nobody is the silence this engine refuses.
            case "wave_monster" -> require(
                    ctx.battle() == null || ctx.battle().waveMonsters().isEmpty()
                            ? null
                            : ctx.battle().waveMonsters().getFirst(),
                    "wave_monster", ctx);
            case TriggerVocabulary.TARGET_RANDOM_HIT_ENEMY ->
                    require(randomHitEnemy(ctx), TriggerVocabulary.TARGET_RANDOM_HIT_ENEMY, ctx);
            // Among the units this attack hit, the one with the highest CURRENT HP.
            case "highest_hp_attack_hit" -> require(highestHitTarget(ctx), "highest_hp_attack_hit", ctx);
            // The fallback: "if the target is defeated ..., launch it at a random single enemy". The preferred target
            // is the trigger's own, and CanHit has a real "defeated" flag orthogonal to invulnerability (CanHit:88-90),
            // so a dead preferred target -- and only that -- falls through to the battle's seeded random opponent.
            case "target_else_random_enemy" -> {
                CanHit preferred = ctx.target();
                yield preferred != null && !preferred.isDeath()
                        ? preferred
                        : require(ctx.battle() == null ? null
                        : ctx.battle().randomOpponent(ctx.owner()), "target_else_random_enemy", ctx);
            }
            case TriggerVocabulary.TARGET_RANDOM_ENEMY ->
                    require(ctx.battle() == null ? null : ctx.battle().randomOpponent(ctx.owner()),
                            TriggerVocabulary.TARGET_RANDOM_ENEMY, ctx);
            // "the enemy target with the highest HP" - ALL of the opponents, not the ones this attack happened to hit.
            // Reader: 1506 Silver Wolf LV.999's 【超超超大剑】 - 「对生命值最高的敌方目标额外造成…真实伤害」 - which fires from a
            // Skill-Point event that carries no hit set at all, so `highest_hp_attack_hit` (same pool as
            // `random_hit_enemy`) cannot answer it. The pair is exactly like `random_hit_enemy` / `random_enemy`.
            case "highest_hp_enemy" -> require(highestHpEnemy(ctx), "highest_hp_enemy", ctx);
            // "the character holding [同袍]": the single-target path asks the same question the list path does, and a
            // single-target op has to name ONE unit -- so "nobody holds it" surfaces through `require`'s message
            // rather than silently applying to nobody (the list path's empty answer is documented on the prefix).
            default -> {
                if (selector.startsWith(TriggerVocabulary.HOLDER_OF_PREFIX)) {
                    List<CanHit> holders = holderOf(ctx.battle(), selector, ctx);
                    yield require(holders.isEmpty() ? null : holders.getFirst(), selector, ctx);
                }
                throw new IllegalStateException(
                        "Effect names the target selector '" + selector + "', which can reach several units: it needs "
                                + "an op that takes a list, not one that resolves a single target");
            }
        };
    }

    static List<CanHit> resolveTargetsUnfiltered(Battle battle, EffectSpec effect, TriggerContext ctx) {
        String selector = normalizeTarget(effect);
        if (selector.startsWith(TriggerVocabulary.HOLDER_OF_PREFIX)) {
            return holderOf(battle, effect.getTarget(), ctx);
        }
        if (selector.startsWith(ALLIES_OF_PATH_EXCEPT_PREFIX)) {
            // Note: Before the plain `allies_of_path:` branch below, which would otherwise read this spelling's path
            // out of the middle of the exclusion list.
            return List.copyOf(alliesOfPathExcept(selector, ctx));
        }
        switch (selector) {
            case "wave_monsters" -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"wave_monsters\" but no battle was supplied to read the current wave");
                }
                return battle.waveMonsters();
            }
            case "all_summons" -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"all_summons\" but no battle was supplied to take the summons from");
                }
                // EVERY memosprite on our side, not just the rule owner's: the clause is "raise the CRIT DMG of all of our memosprites", and `summonsOf` is keyed
                // by master -- so each of our characters is asked. `all_allies` answers "who is on that side" the same way.
                List<CanHit> everySummon = new ArrayList<>();
                for (com.laosun.aluminium.models.Character ally : battle.characters()) {
                    everySummon.addAll(battle.summonsOf(ally));
                }
                return List.copyOf(everySummon);
            }
            case "all_allies_lost_hp_this_action" -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets the allies that lost HP, but no battle was supplied to read that set");
                }
                return battle.lostHpThisAction();
            }
            case "all_allies_lethally_hit_this_action" -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"all_allies_lethally_hit_this_action\" but no battle was supplied to read its lethal set");
                }
                return battle.lethallyHitThisAction();
            }
        }

        if (TriggerVocabulary.TARGET_ALL_ALLIES.contains(selector) || TriggerVocabulary.TARGET_OTHER_ALLIES.equals(selector)) {
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + selector
                                + "\" but no battle was supplied to take the party from");
            }
            List<CanHit> party = new ArrayList<>();
            for (CanHit ally : battle.allies()) {
                // `other_allies` is the same camp minus the rule's owner ("other than oneself"); the order is the
                // roster's either way, so a group effect reaches units in a stable order.
                if (TriggerVocabulary.TARGET_OTHER_ALLIES.equals(selector) && ally == ctx.owner()) {
                    continue;
                }
                party.add(ally);
            }
            return List.copyOf(party);
        }
        if (selector.startsWith(ALLIES_OF_PATH_PREFIX)) {
            // ALL of them, not the first (reader: 1141518 "raise the ATK of all 'Erudition' path characters..."): the sentence says "all", so the answer is the
            // whole path in roster order. Note: An unknown path name is a refused SPELLING, not an empty answer -- the validation refuses it at load, and this mirrors that.
            String rest = selector.substring(ALLIES_OF_PATH_PREFIX.length()).trim();
            Path wanted = pathNamed(rest);
            if (wanted == null) {
                throw new IllegalStateException("Effect targets \"" + selector + "\" but \"" + rest
                        + "\" is not a path (write its Chinese name, or the enum's)");
            }
            if (battle == null) {
                throw new IllegalStateException(
                        "Effect targets \"" + selector + "\" but no battle was supplied to take the party from");
            }
            List<CanHit> ofPath = new ArrayList<>();
            for (CanHit ally : battle.allies()) {
                if (ally instanceof com.laosun.aluminium.models.Character character && character.getPath() == wanted) {
                    ofPath.add(ally);
                }
            }
            return List.copyOf(ofPath);
        }
        if (selector.startsWith(ALLY_CID_PREFIX)) {
            // "grant Hyacine (风堇) ...": a selector that NAMES a character is handled HERE, in the plural path, because absence is not an error --
            // it is a battle that does not contain that character, and the clause simply does nothing. The singular path's convention is the opposite (it throws), which is why
            // this must not be left to it. Same shape as `lowest_hp_ally` just below.
            int cid = Integer.parseInt(selector.substring(ALLY_CID_PREFIX.length()).trim());
            CanHit named = allyWithCid(ctx, cid);
            return named == null ? List.of() : List.of(named);
        }
        switch (selector) {
            case TriggerVocabulary.TARGET_LOWEST_HP_ALLY -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"" + TriggerVocabulary.TARGET_LOWEST_HP_ALLY
                                    + "\" but no battle was supplied to read the party's health from");
                }
                CanHit lowest = null;
                double lowestShare = Double.MAX_VALUE;
                for (CanHit ally : battle.allies()) {
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
            case TriggerVocabulary.TARGET_ALL_ENEMIES -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"" + TriggerVocabulary.TARGET_ALL_ENEMIES
                                    + "\" but no battle was supplied to take the opposing camp from");
                }
                // `getOpponents` is the one predicate for "the other side" (it also answers correctly for an enemy
                // owner), and it does not filter the dead -- the op does, because only the op knows whether a dead
                // unit is something it can act on.
                return List.copyOf(battle.getOpponents(ctx.owner()));
                // `getOpponents` is the one predicate for "the other side" (it also answers correctly for an enemy
                // owner), and it does not filter the dead -- the op does, because only the op knows whether a dead
                // unit is something it can act on.
            }
            case TriggerVocabulary.TARGET_AND_SUMMON -> {
                if (battle == null) {
                    throw new IllegalStateException(
                            "Effect targets \"" + TriggerVocabulary.TARGET_AND_SUMMON
                                    + "\" but no battle was supplied to look for that unit's summon");
                }
                CanHit chosen = require(ctx.target(), TriggerVocabulary.TARGET_AND_SUMMON, ctx);
                List<CanHit> pair = new ArrayList<>();
                pair.add(chosen);
                // `summonsOf` is the one predicate for "the summons this unit owns" (it already drops the dead ones),
                // so "and their summon" means the same thing here as it does for the `summon` selector.
                pair.addAll(battle.summonsOf(chosen));
                return List.copyOf(pair);
            }

            // "randomly 1 of ... our other targets whose current energy percentage is below 50%" (light cone 21021): this selector may
            // legitimately have NO candidate (the wearer is excluded, and everyone else may be full), and then the
            // clause does nothing. Note: Same shape as lowest_hp_ally just above -- empty list, not an error.
            // Note: Only THIS name is allowed to be empty: every other selector still goes through require(), so a
            // misspelling keeps failing loudly.
            case "random_ally_below_half_energy" -> {
                CanHit one = randomAllyBelowHalfEnergy(ctx);
                return one == null ? List.of() : List.of(one);
            }
        }
        return List.of(resolveTarget(effect, ctx));
    }

    static String normalizeTarget(EffectSpec effect) {
        // `target` lives on the effect as an optional selector; absent means "self".
        return effect.getTarget() == null ? "self" : effect.getTarget().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The party member whose cid is this one, or {@code null} when nobody matches.
     * The party member whose cid is this one, or {@code null} when nobody matches.
     * The path a selector names, or {@code null} if it names none. Both spellings are accepted because the selector arrives lowercased: the Chinese name through
     * {@link Path#fromNameOrNull}, the enum's own through {@code valueOf} on the upper-cased text.
     * The allies of a Path, minus the units the selector excludes: {@code allies_of_path_except:欢愉:self,target}.
     *
     * <p>Shared by both resolution paths (one unit and a set), so a clause that names one of these cannot behave
     * differently from the same clause used as a group target.
     *
     * @throws IllegalStateException when the path is unknown or the selector names nobody to exclude - both are
     *                               SPELLING mistakes, and this vocabulary is closed on purpose (see
     *                               {@link TriggerValidator#requireSelectorSpelling})
     */
    static List<CanHit> alliesOfPathExcept(String selector, TriggerContext ctx) {
        String rest = selector.substring(ALLIES_OF_PATH_EXCEPT_PREFIX.length()).trim();
        int split = rest.lastIndexOf(':');
        if (split < 0) {
            throw new IllegalStateException("Effect targets \"" + selector
                    + "\" but no exclusion list follows the Path; write \""
                    + ALLIES_OF_PATH_EXCEPT_PREFIX + "<path>:self,target\"");
        }
        Path wanted = pathNamed(rest.substring(0, split).trim());
        if (wanted == null) {
            throw new IllegalStateException("Effect targets \"" + selector + "\" but \""
                    + rest.substring(0, split).trim() + "\" is not a path (write its Chinese name, or the enum's)");
        }
        List<CanHit> excluded = new ArrayList<>();
        for (String who : rest.substring(split + 1).split(",")) {
            String name = who.trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            CanHit unit = switch (name) {
                case "self", "owner" -> ctx.owner();
                case "target" -> ctx.target();
                case "actor", "attacker" -> ctx.actor();
                default -> throw new IllegalStateException("Effect targets \"" + selector
                        + "\" but \"" + name + "\" is not a unit this vocabulary knows; use self / target / actor");
            };
            if (unit != null) {
                excluded.add(unit);
            }
        }
        if (excluded.isEmpty()) {
            throw new IllegalStateException("Effect targets \"" + selector
                    + "\" but its exclusion list names nobody that exists in this event; a selector that excludes"
                    + " nothing is a different clause (use " + ALLIES_OF_PATH_PREFIX + " instead)");
        }
        if (ctx.battle() == null) {
            throw new IllegalStateException(
                    "Effect targets \"" + selector + "\" but no battle was supplied to take the party from");
        }
        List<CanHit> selected = new ArrayList<>();
        for (CanHit ally : ctx.battle().allies()) {
            if (ally instanceof com.laosun.aluminium.models.Character character && character.getPath() == wanted && !excluded.contains(ally)) {
                selected.add(ally);
            }
        }
        return selected;
    }

    static Path pathNamed(String text) {
        Path byName = Path.fromNameOrNull(text);
        if (byName != null) {
            return byName;
        }
        try {
            return Path.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notAPath) {
            return null;
        }
    }

    static CanHit allyWithCid(TriggerContext ctx, int cid) {
        if (ctx.battle() == null) {
            return null;
        }
        for (com.laosun.aluminium.models.Character member : ctx.battle().characters()) {
            if (member.getCid() == cid) {
                return member;
            }
        }
        return null;
    }

    static List<CanHit> holderOf(Battle battle, String rawSelector, TriggerContext ctx) {
        String raw = rawSelector.trim();
        String state = raw.substring(TriggerVocabulary.HOLDER_OF_PREFIX.length()).trim();
        if (battle == null || ctx.owner() == null) {
            throw new IllegalStateException(
                    "Effect targets \"" + raw + "\" but this rule was evaluated without a battlefield, so the "
                            + "units that could hold 【" + state + "】 cannot be listed");
        }
        CanHit holder = holderOfOrNull(battle, state, ctx.owner());
        return holder == null ? List.of() : List.of(holder);
    }

    /**
     * The single unit on {@code querier}'s side carrying the state, or {@code null} when nobody does.
     *
     * <p>The one place "who holds it" is answered, shared by the {@code holder_of:<STATE>} target selector (where
     * "nobody" is a legal state of the world and the caller turns it into an empty list or a loud miss) and by the
     * {@code holder_attr:<STATE>:<ATTRIBUTE>} scale (where "nobody" cannot be turned into a number at all, so the
     * caller throws). The DEAD are skipped - a corpse holding a marker is not the unit the sentence is about.
     * First match wins, in the side's own order, so the answer is stable.
     *
     * @param battle  the battlefield, or {@code null} (then nobody can be found)
     * @param state   the state name
     * @param querier the unit whose SIDE is searched
     * @return the holder, or {@code null}
     */
    static CanHit holderOfOrNull(Battle battle, String state, CanHit querier) {
        if (battle == null || querier == null || state == null) {
            return null;
        }
        for (CanHit ally : battle.getSideOf(querier)) {
            if (ally != null && !ally.isDeath() && ally.getBuffManager().hasState(state)) {
                return ally;
            }
        }
        return null;
    }

    /**
     * The state named by a {@code holder_attr:<STATE>:<ATTRIBUTE>} scale.
     *
     * <p>The split is on the <b>last</b> colon, not the first: a state name may itself contain one
     * ({@code holder_attr:渲染理性，解构欢愉:DEFENCE} is a real state name of 1503's), so splitting on the first
     * colon would read the attribute out of the middle of the state and the state as a truncated prefix.
     *
     * @param effect the effect whose scale is being read
     * @return the state name (never blank; the loader refuses a blank one)
     */
    static String holderAttrState(EffectSpec effect) {
        String raw = effect.getScale() == null ? "" : effect.getScale().trim();
        String rest = raw.substring(TriggerTable.HOLDER_ATTR_PREFIX.length());
        int lastColon = rest.lastIndexOf(':');
        if (lastColon < 0) {
            throw new IllegalStateException(
                    "the scale \"" + raw + "\" must state <STATE>:<ATTRIBUTE>, e.g. holder_attr:同袍:DEFENCE; the "
                            + "state name alone does not say which of the holder's stats to read");
        }
        return rest.substring(0, lastColon).trim();
    }

    /**
     * The attribute named by a {@code holder_attr:<STATE>:<ATTRIBUTE>} scale (the part after the last colon).
     */
    static AttributeType holderAttrAttribute(EffectSpec effect) {
        String raw = effect.getScale() == null ? "" : effect.getScale().trim();
        String rest = raw.substring(TriggerTable.HOLDER_ATTR_PREFIX.length());
        int lastColon = rest.lastIndexOf(':');
        String name = (lastColon < 0 ? rest : rest.substring(lastColon + 1)).trim();
        return AttributeType.fromString(name);
    }

    /**
     * The owner's summon, for the {@code "summon"} selector.
     *
     * <p>A missing summon is an {@link IllegalStateException} rather than a quiet no-op, and the message says
     * what to do about it: an author who writes "装备者及其忆灵" without a {@code self_summon_count >= 1}
     * condition has a rule that fires exactly when the unit it names is not there. Silence would be a wrong
     * state; the loud version is a one-line fix in the rule file.
     */
    static CanHit requireSummon(TriggerContext ctx) {
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

    static CanHit require(CanHit entity, String what, TriggerContext ctx) {
        if (entity == null) {
            throw new IllegalStateException(
                    "Effect targets \"" + what + "\" but this event has no such party");
        }
        return entity;
    }

    /**
     * The first character of the party (relic 31), or null when there is no battle.
     */
    static CanHit partyFirst(TriggerContext ctx) {
        Battle battle = ctx.battle();
        if (battle == null || battle.characters().isEmpty()) {
            return null;
        }
        return battle.characters().getFirst();
    }

    /**
     * The next unit on OUR side to act after the current one, walking the queue forward and wrapping.
     *
     * <p>For cone 21025's "raise the damage dealt by our next other target to act". "Next to act" is read from the queue order, and the
     * walk starts AFTER the acting unit: the head alone is wrong (measured -- {@code peekNext()} returns the head, which
     * can be the acting unit itself), and taking the party's first ally is wrong too (that is the unit who just acted).
     */
    static CanHit nextAllyToAct(TriggerContext ctx) {
        Battle battle = ctx.battle();
        if (battle == null || battle.queue() == null) {
            return null;
        }
        List<CanHit> order = new java.util.ArrayList<>();
        for (var signal : battle.getQueueSnapshot()) {
            order.add(signal.getCanHit());
        }
        if (order.isEmpty()) {
            return null;
        }
        int start = order.indexOf(ctx.owner()) + 1;
        for (int i = 0; i < order.size(); i++) {
            CanHit candidate = order.get((start + i) % order.size());
            if (candidate != ctx.owner() && battle.allies().contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * "randomly 1 of our other targets whose current energy percentage is below 50%" (light cone 21021).
     *
     * <p>Note: FILTER FIRST, ROLL SECOND - the same order `randomHitEnemy` documents: a roll landing on an
     * excluded ally would be dropped rather than re-rolled, which is a wrong answer that reports nothing.
     * Note: The 50% is written in rather than carried by a field: the text states it once and no tier changes
     * it (the five tiers differ only in how much energy is restored).
     */
    static CanHit randomAllyBelowHalfEnergy(TriggerContext ctx) {
        Battle battle = ctx.battle();
        if (battle == null) {
            return null;
        }
        List<CanHit> eligible = new java.util.ArrayList<>();
        for (CanHit ally : battle.allies()) {
            if (ally == ctx.owner() || !(ally instanceof Character character)) {
                continue;
            }
            double max = character.getMaxEnergy();
            if (max > 0 && character.getCurrentEnergy() / max < 0.5) {
                eligible.add(character);
            }
        }
        return eligible.isEmpty() ? null
                : eligible.get(battle.getRng().nextInt(eligible.size()));
    }

    /**
     * The unit with the highest <b>current HP</b> among the ones the attack behind this context hit.
     *
     * <p>The pool is the same one {@link #randomHitEnemy} reads: an {@code ATTACK_FINISHED} context carries the
     * attack's frozen hit set, while a per-hit context ({@code DEALING_DAMAGE}) only has its instance's snapshot.
     * Reading {@code attackHitTargets} alone answers "empty" on precisely the event this selector is for.
     *
     * <p>The comparison is current HP -- absolute, not a percentage -- and the pool is filtered to the owner's
     * opponents, because the sentence deals damage to the unit it picks. Returns {@code null} when nothing
     * qualifies; the caller turns that into an error.
     */
    static CanHit highestHitTarget(TriggerContext ctx) {
        if (ctx.battle() == null) {
            return null;
        }
        java.util.Set<CanHit> pool = !ctx.attackHitTargets().isEmpty()
                ? new java.util.LinkedHashSet<>(ctx.attackHitTargets())
                : (ctx.damage() == null ? java.util.Set.of() : ctx.damage().hitTargets());
        CanHit highest = null;
        for (CanHit hit : pool) {
            if (hit == null || hit.isDeath()
                    || !ctx.battle().getOpponents(ctx.owner()).contains(hit)
                    || !ctx.passesTargetFilter(hit)) {
                continue;
            }
            if (highest == null || hit.getCurrentHp() > highest.getCurrentHp()) {
                highest = hit;
            }
        }
        return highest;
    }

    /**
     * The enemy with the highest CURRENT HP, over <b>all</b> of the owner's opponents.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 【超超超大剑】 - 「对生命值最高的敌方目标额外造成等同于本次总伤害值 20% 的真实伤害」. Its
     * trigger is a spent Skill Point, which carries no hit set, so {@link #highestHitTarget} - the ATTACK-HIT pool - cannot
     * answer it; this is the same pair of pools `random_enemy` and `random_hit_enemy` already distinguish. Like those, it
     * fails loudly when nothing qualifies rather than silently aiming at nobody.
     */
    static CanHit highestHpEnemy(TriggerContext ctx) {
        if (ctx.battle() == null) {
            return null;
        }
        CanHit highest = null;
        for (CanHit enemy : ctx.battle().getOpponents(ctx.owner())) {
            if (enemy == null || enemy.isDeath() || !ctx.passesTargetFilter(enemy)) {
                continue;
            }
            if (highest == null || enemy.getCurrentHp() > highest.getCurrentHp()) {
                highest = enemy;
            }
        }
        return highest;
    }

    static CanHit randomHitEnemy(TriggerContext ctx) {
        if (ctx.battle() == null) {
            return null;
        }
        // TWO carriers, one selector: an ATTACK_FINISHED context carries the attack's FROZEN hit set,
        // while a per-hit context (DEALING_DAMAGE) only has its instance's snapshot -- so the attack-level set wins and
        // the instance is the fallback. Note: That is why the doc says what a reader sees depends on its event.
        java.util.Set<CanHit> pool = !ctx.attackHitTargets().isEmpty()
                ? new java.util.LinkedHashSet<>(ctx.attackHitTargets())
                : (ctx.damage() == null ? java.util.Set.of() : ctx.damage().hitTargets());
        if (pool.isEmpty()) {
            return null;                      // unknown or nothing hit: the caller turns this into an error
        }
        // FILTER FIRST, ROLL SECOND: `resolveTargets` resolves and only then applies the
        // effect's `target_when`, so a roll landing on an excluded unit would simply be dropped instead of
        // re-rolled -- "a random one of the hit targets NOT holding X" needs the exclusion inside the roll.
        List<CanHit> hitEnemies = pool.stream()
                .filter(ctx.battle().getOpponents(ctx.owner())::contains)
                .filter(ctx::passesTargetFilter)
                .toList();
        return hitEnemies.isEmpty() ? null : hitEnemies.get(ctx.battle().getRng().nextInt(hitEnemies.size()));
    }

    /**
     * The prefix of a selector that names an ally outright by cid:
     * {@code "target": "ally_cid:1006"}.
     *
     * <p>Unlike the closed set of role selectors, this is a parameterised family: the part after the colon is the
     * character's cid, read as written. A cid that is not in the party resolves to nobody — the plural path answers
     * an empty list, while the single-target path reports it through {@code require}.
     */
    static final String ALLY_CID_PREFIX = "ally_cid:";

    /**
     * A second selector FAMILY, admitted by prefix (reader: 1141518 "raise the ATK of all 'Erudition' path characters"):
     * every ALLY on the named path.
     *
     * <p>The name may be the path's Chinese name ("智识") or the enum's own ("ERUDITION"), because the selector is
     * lowercased on the way in and neither spelling is the other's.
     */
    static final String ALLIES_OF_PATH_PREFIX = "allies_of_path:";

    /**
     * The same family with an <b>exclusion list</b>: {@code allies_of_path_except:<PATH>:<WHO>,<WHO>}.
     *
     * <p>Reader: 1503 Pearl (真珠)'s Eidolon 2 - 「使除真珠和【美学底本】外的其他我方「欢愉」命途角色也触发行动提前效果」 - "the
     * allies of that Path OTHER THAN these two". The plain {@code allies_of_path:} cannot say it, and neither can a
     * condition: the exclusion is about <b>which units the effect reaches</b>, which is what a target selector is for.
     *
     * <p>The names after the path are the same subjects the identity conditions use ({@code self} = the rule's owner,
     * {@code target} = what the event happened to), so a clause that says "other than me and the one I aimed at" reads
     * the same way as the conditions around it.
     */
    static final String ALLIES_OF_PATH_EXCEPT_PREFIX = "allies_of_path_except:";
}

