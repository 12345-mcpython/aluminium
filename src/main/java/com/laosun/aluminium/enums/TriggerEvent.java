package com.laosun.aluminium.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * The trigger sources a character mechanic can subscribe to (P8-7).
 *
 * <p>This is the data-side name of an engine event. The whole point of the trigger table is that
 * character content is expressed as "on &lt;event&gt;, if &lt;condition&gt;, do &lt;effects&gt;", so
 * this enum is the closed vocabulary of {@code "on"} values in
 * {@code resources/characters/<cid>.json}.
 *
 * <p>⚠ <b>Not every value is wired yet.</b> The ones marked ✅ have an emitter in the engine; the
 * rest are declared so the JSON vocabulary is stable and so an unwired trigger fails <b>loudly at
 * load time</b> ({@link #isWired()}) rather than silently doing nothing. See {@code engine.md} §4
 * for the events that actually fire today.
 */
public enum TriggerEvent {
    /**
     * ✅ {@code Battle.startBattle()} — delivered once to <b>every character's own table</b>, after the opening
     * hooks and before {@code processRequests}.
     *
     * <p>⚠ <b>It carries no {@code actor} and no {@code target}.</b> This used to read "once for every combatant",
     * which is true about the <i>delivery</i> and misleading about the <i>context</i>: a rule written here as
     * {@code "when": ["actor == self"]} can never fire, and it looks entirely reasonable (hand-written that way on
     * 2026-09-27, and the loader now refuses the spelling). "My own battle start" needs no condition because the
     * table being fired <b>is</b> the owner's.
     */
    BATTLE_START("BATTLE_START", true),
    /**
     * ✅ An ally finished an attack. Carries the hit-target count.
     */
    ALLY_ATTACK("ALLY_ATTACK", true),
    /**
     * ✅ An ally cast their <b>Skill</b> (the data's {@code BPSkill}) — including non-damaging ones.
     *
     * <p>⚠ <b>Narrowed on 2026-09-27.</b> This used to fire for <b>every</b> cast that was not an
     * ultimate, which silently included basic attacks, techniques, map attacks and talents — so
     * "when the wearer uses their Skill" content (relic set 109's ATK buff, Robin's 模进乐段) also
     * fired on 普攻. That is the failure this vocabulary is shaped to prevent: an over-trigger is a
     * wrong number with no error attached. The three in-battle casts now have three events
     * ({@link #BASIC_ATTACK} / this / {@link #ULT_CAST}), split at the emitter from the parsed
     * {@code SkillCategory}, because the condition DSL has no variable for the kind of cast.
     */
    SKILL_CAST("SKILL_CAST", true),
    /**
     * ✅ An ally used their <b>basic attack</b> (the data's {@code Normal} — which also covers enhanced
     * basic attacks, since the data spells both of them {@code Normal}).
     *
     * <p>This is the event for "施放普攻后 / after the wearer uses their Basic ATK". It is deliberately
     * separate from {@link #ALLY_ATTACK}, which fires for <b>any</b> attack that lands (basic attack,
     * skill, ultimate, follow-up) and is what "after an ally attacks" content wants.
     *
     * <p>⚠ Not fired for the <b>map</b> basic attack ({@code MazeNormal}): that hit happens outside
     * battle, and "after the wearer uses their basic attack" is about a battle turn. Nor for techniques,
     * assists, elation damage or talents — none of those is an in-battle cast, and inventing an event
     * for them is how this split got lost the first time.
     */
    BASIC_ATTACK("BASIC_ATTACK", true),
    /**
     * ✅ Someone's energy was credited.
     */
    ENERGY_GAINED("ENERGY_GAINED", true),
    /**
     * ✅ Someone really lost HP (shield absorption does not count).
     */
    HP_LOST("HP_LOST", true),
    /**
     * ✅ Someone was really healed.
     */
    HEALED("HEALED", true),
    /**
     * ✅ Someone was killed.
     */
    KILL("KILL", true),
    /**
     * ✅ An enemy was weakness-broken.
     */
    BREAK("BREAK", true),
    /**
     * ✅ Skill points were really spent.
     */
    SKILL_POINT_SPENT("SKILL_POINT_SPENT", true),
    /**
     * ✅ Skill points were really gained.
     */
    SKILL_POINT_GAINED("SKILL_POINT_GAINED", true),
    /**
     * ✅ A character's turn began — emitted by {@code Battle.beforeMove}, after the actor's buffs have
     * been settled and before its {@code MoveEvent.beforeMove} hook.
     *
     * <p>⚠ <b>This is a trigger-table event, not a new buff interface.</b> Turn boundaries stay
     * {@code MoveEvent.beforeMove/afterMove} for buffs — that decision is pinned by
     * {@code EventBusTest.turnBoundariesAreStillMoveEvent} and nothing here revives it. What
     * {@code TURN_START} adds is only the ability for <b>data</b> to subscribe to the same moment
     * ("at the beginning of the turn, if …"), which the buff interfaces cannot express because a JSON
     * rule is not a Java class.
     */
    TURN_START("TURN_START", true),
    /**
     * ✅ The owner was hit by an incoming damage instance.
     *
     * <p><b>Deliberately not the same fact as {@link #HP_LOST}.</b> {@code HP_LOST} means "HP was
     * really lost" (a fully shielded hit does not fire it, and neither does a hit on an invulnerable
     * target); {@code TAKING_HIT} means "an attack landed on me", which is exactly what the relic and
     * talent texts that say "after the wearer is hit / attacked" mean — those effects accumulate even
     * when a shield eats the whole hit. Emitting both from the same place with the same gate would
     * silently make one mean the other, so the two are separate events with separate conditions:
     * {@code HP_LOST} fires only when {@code hpLoss > 0}, {@code TAKING_HIT} fires once per settled
     * instance against a live, non-invulnerable target.
     *
     * <p>{@code actor} = whoever caused the damage, {@code target} = the one who took it (so "I was
     * hit" is {@code target == self}, the same convention as {@code HP_LOST}). It follows
     * {@code HP_LOST}'s broadcast policy, so it is also fired only when the subject is one of ours.
     */
    TAKING_HIT("TAKING_HIT", true),
    /**
     * ✅ A damage instance is <b>about to be settled</b>: fired from {@code Battle.assemble} before the zones
     * are evaluated, so a rule can still change <i>this</i> instance.
     *
     * <p><b>Why a pre-settlement event had to exist.</b> {@link #ALLY_ATTACK} fires <i>after</i> the whole
     * attack has been settled — correct for "after an ally attacks", useless for
     * 「对处于 X 状态的目标造成的伤害提高 Y%」, because by then the number is final and all a rule could do is
     * describe it. This event hands over the pending instance ({@code TriggerContext.damage()}), and
     * {@code BOOST_DAMAGE} is what changes it — <b>for that one instance only</b>, since the instance itself is
     * the state: there is no buff to attach, nothing to clean up, and nothing that can leak into the next hit.
     *
     * <p>{@code actor} = who deals the damage, {@code target} = who is about to take it (the same convention as
     * {@link #TAKING_HIT}, from the other side). It fires for <b>every</b> instance the engine settles — DOT
     * ticks, break and additional damage included — because those are damage too; a rule that means "attacks
     * only" says so with its own conditions.
     */
    DEALING_DAMAGE("DEALING_DAMAGE", true),
    /**
     * ✅ An ally cast their Ultimate.
     *
     * <p>Fired by {@code SkillExecutor.broadcastSkillCast} when the parsed skill data's
     * {@code attack_type} is {@code Ultra} — never inferred from a skill's name or slot. Exactly one
     * of {@link #SKILL_CAST} and this event fires per cast.
     */
    ULT_CAST("ULT_CAST", true),
    /**
     * ✅ A follow-up attack was used: an <b>additional-damage</b> instance settled through
     * {@code Battle.applyAdditionalDamage}, with the attacker as {@code actor} and the victim as
     * {@code target}.
     *
     * <p><b>Why it needs its own event rather than {@link #ALLY_ATTACK}.</b> The relic and talent
     * texts that say "when the wearer uses a Follow-Up ATK" mean that category specifically;
     * {@code ALLY_ATTACK} fires for every attack, so a rule hung on it would also fire for basic
     * attacks, skills and ultimates — a silent over-trigger, not a near miss.
     *
     * <p><b>What counts as one.</b> The engine has exactly one notion of an attack that "does not
     * count as dealing 1 attack": {@code DamageType.ADDITIONAL}, which is what a talent-driven
     * follow-up (Clara's counter, the P8-3 shape) is settled as. So this fires from that single
     * settlement point, and nothing else in the engine fires it.
     *
     * <p>It is emitted for every such instance <b>whether or not it dealt damage</b>: the texts that
     * subscribe say "when the wearer uses a Follow-Up ATK", which is the attack being <i>used</i>, and
     * one absorbed entirely by a shield or an invulnerable target was still used.
     *
     * <p>⚠ Note the recursion this creates — a rule that answers {@code FOLLOW_UP} with the
     * {@code DAMAGE} op is a follow-up responding to a follow-up; {@code Battle.MAX_TRIGGER_DEPTH}
     * stops that loudly instead of letting it run away.
     */
    FOLLOW_UP("FOLLOW_UP", true),
    /**
     * ✅ A <b>summon</b> finished an attack (2026-09-28, P9-4 忆灵): fired by {@code EnemySkill.execute}
     * after every segment of its attack has been settled, with the summon as {@code actor} and the
     * hit-target count riding along like {@link #ALLY_ATTACK}'s.
     *
     * <p><b>Why its own event rather than widening {@link #ALLY_ATTACK}.</b> "An ally attacked" is what
     * three shipped rules mean today ({@code characters/1309.json}, {@code characters/1403.json}'s
     * 「我方其他目标攻击后」, relic set 105's {@code actor == self}), and whether a memosprite counts as
     * one of those 「目标」 is <b>not</b> something the documents settle here. Widening the event would
     * have silently changed what those three rules fire on — an over-trigger is a wrong number with no
     * error attached — so the distinction is drawn at the emitter instead, exactly as
     * {@link #SKILL_CAST} / {@link #BASIC_ATTACK} / {@link #ULT_CAST} are.
     *
     * <p><b>It is fired for a summon of either camp</b> (a boss's minion attacks too); which summons a
     * rule cares about is its own question, and the condition for it is {@code actor == summon} (the
     * rule owner's own summon) — see {@code engine.md} §4.6. Without that condition a rule would also
     * fire when a <em>teammate's</em> summon attacks, which is the same over-trigger in another coat.
     *
     * <p>An attack that connected with nothing does not fire it, and neither does a summon with no
     * attack of its own: this is "an attack happened", not "a unit was on the field".
     */
    SUMMON_ATTACK("SUMMON_ATTACK", true),
    /**
     * ✅ A <b>summon entered the field</b> (2026-09-28, P9-4 忆灵): fired by {@code Battle.processRequests} for
     * everything {@code Battle.summon} / {@code Battle.summonMemosprite} placed, with the summoned unit as
     * {@code actor}.
     *
     * <p><b>Why it is fired at the settle point and not inside the summon call.</b> A summon enters the action
     * bar through {@code addRequestItems}, which {@code processRequests} drains — so during the call itself the
     * unit is on the roster but <b>not yet scheduled</b>. A rule that answers 「被召唤时」 almost always wants to
     * touch its action value ({@code ADVANCE}, i.e. 「使自身立即行动」), and an advance against a unit with no
     * signal is silently lost. ⚠ That is also why this cannot simply be fired from
     * {@code processAddRequests}: that queue is shared with <b>wave</b> entries, and a wave arriving is not a
     * summon.
     *
     * <p>Fired for a summon of either camp; {@code actor == summon} is what narrows it to the rule owner's own
     * (the same condition {@link #SUMMON_ATTACK} uses). Note that {@code SUMMON} is idempotent per summoner, so a
     * second summoning while one is already out fires nothing — which is exactly what 「若衣匠已在场，则…」
     * clauses need.
     */
    SUMMONED("SUMMONED", true),
    /**
     * ✅ A cast is <b>about to resolve</b>: fired by {@code SkillExecutor.execute} after the caster is known and
     * <b>before any damage is expanded</b>, so a rule can still change what this cast does (P11-1, M-40).
     *
     * <p><b>Why a pre-cast event exists at all.</b> Every other cast event ({@link #BASIC_ATTACK},
     * {@link #SKILL_CAST}, {@link #ULT_CAST}, {@link #ALLY_ATTACK}) fires <i>after</i> the damage has been
     * settled — correct for "after the wearer uses their Skill", useless for a rule that has to change the swing
     * itself. {@link #DEALING_DAMAGE} covers "change this damage instance"; this covers "this cast's damage is not
     * mine to deal", which is a fact about the <b>cast</b> and has to be known before the instance exists.
     *
     * <p><b>The first user.</b> 长夜月's ultimate: 141303's own generated damage rows would swing at <b>her</b>
     * attack as the base, while the document says the damage is the memosprite's (「使忆灵「长夜」对敌方全体造成
     * 等同于「长夜」#1[i]%生命上限的冰属性伤害」) — and the rule that delivers it as the memosprite's runs on
     * {@link #ULT_CAST}, i.e. too late to stop the first swing. Measured before this existed: 8818.5 from her own
     * rows plus the commanded hit, where the document describes one damage instance.
     *
     * <p>{@code actor} = the caster, {@code target} = {@code null} (nothing has been aimed at yet in the sense the
     * later events mean: the caster's selection is not re-published here, and a rule that wanted it would be
     * asking about {@link #SKILL_CAST}). The cast in progress is reachable through
     * {@code Battle.currentCast()}; the only op that reads it today is {@code DELEGATE_DAMAGE}.
     *
     * <p>⚠ Fired for <b>every</b> cast our side makes, including non-damaging ones and the enemy-side attacks that
     * have no table: a rule must narrow itself with {@code actor == self} (or the op refuses the cast that is not
     * its owner's, see {@code DELEGATE_DAMAGE}).
     */
    CAST_SETUP("CAST_SETUP", true);

    private static final Map<String, TriggerEvent> BY_NAME = new HashMap<>();

    static {
        for (TriggerEvent event : values()) {
            BY_NAME.put(event.name, event);
        }
    }

    private final String name;
    private final boolean wired;

    TriggerEvent(String name, boolean wired) {
        this.name = name;
        this.wired = wired;
    }

    /**
     * The string used in the JSON {@code "on"} field.
     */
    public String value() {
        return name;
    }

    /**
     * Whether the engine currently emits this event.
     *
     * <p>A trigger table referencing an unwired event is almost certainly a mistake (the author
     * expected something to happen and nothing ever will), so
     * {@code TriggerTable} rejects it at load time instead of ignoring it.
     */
    public boolean isWired() {
        return wired;
    }

    /**
     * Parses the JSON {@code "on"} value, case-insensitively.
     *
     * @param raw the data value
     * @return the matching event, or {@code null} when the value is unknown
     */
    public static TriggerEvent fromString(String raw) {
        return raw == null ? null : BY_NAME.get(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
