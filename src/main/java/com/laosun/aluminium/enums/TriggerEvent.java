package com.laosun.aluminium.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * The trigger sources a character mechanic can subscribe to.
 *
 * <p>This is the data-side name of an engine event. The whole point of the trigger table is that
 * character content is expressed as "on &lt;event&gt;, if &lt;condition&gt;, do &lt;effects&gt;", so
 * this enum is the closed vocabulary of {@code "on"} values in
 * {@code resources/characters/<cid>.json}.
 *
 * <p>Note: <b>Not every value is wired yet.</b> The ones marked have an emitter in the engine; the
 * rest are declared so the JSON vocabulary is stable and so an unwired trigger fails <b>loudly at
 * load time</b> ({@link #isWired()}) rather than silently doing nothing. See {@code engine.md} §4
 * for the events that actually fire today.
 */
public enum TriggerEvent {
    /**
     * The wearer successfully applied a debuff to a target.
     *
     * <p>Emitted from `Battle.tryApplyDebuff`, the one chokepoint every landed debuff passes through (controls and DoTs alike).
     * Note: It also fires when an ENEMY debuffs our side, with the enemy as the actor, so content written here should say `actor == self`
     * to mean "mine". Two readers: relic 11/4's third clause and relic 132/4's second.
     */
    DEBUFF_APPLIED("DEBUFF_APPLIED", true),

    /**
     * A named state has just left the unit that carried it.
     *
     * <p>Note: <b>The name must ride on the event, not be read off the carrier</b>: by the time this fires the state is
     * already gone (the removal happens first), so a {@code has_state} condition on the carrier can never be true.
     * Readers: 1211's "when [生息] ends ...", 1505's "when a teammate's [好活当赏] ends ...", 1408's three "when the transformation ends ...",
     * 1501's two "when the Aha moment ends ..." and the light cone's "when the coup de main ends ...".
     */
    STATE_ENDED("STATE_ENDED", true),

    /**
     * The end of a cast that a RULE commanded -- "an inserted cast".
     *
     * <p>Note: A commanded cast IS a real cast: it announces CAST_SETUP / SKILL_CAST and settles its own energy, so no existing
     * event tells it apart from the original it was copied from. This is the moment that names it, and the reader is
     * 1412's "after the coup de main ends, spend 6 points of charge to turn [爵位] back into [军功]" -- "after the INSERTED one ended", not "after a skill was cast".
     *
     * <p>Note: <b>A commanded cast may not command another one</b> (the same sentence: "a coup de main will not trigger a coup de main again"). The guard
     * lives in {@code TriggerInterpreter.castSkill} and reads the cast stack the engine already keeps:
     * {@code Battle.currentCast().outer() != null} means the open cast is itself a commanded one, which is exactly the
     * "inserting inside an insert" that would otherwise recurse forever -- the same shape as {@code castUltra}'s refusal
     * during an extra turn ("inserting another ultimate inside an extra turn turns 'extra' into infinite chain").
     */
    INSERTED_CAST_END("INSERTED_CAST_END", true),

    /**
     * An incoming hit that WOULD kill the target, announced before it is applied.
     *
     * <p>"when taking a lethal attack it will not fall into the unable-to-fight state, but instead restore health equal to 20%/50% of its own Max HP" - two readers, both registered
     * before this existed (1408's transformed form, and 1104, whose own file records the sentence as missing).
     *
     * <p>The semantics the sentence states, and why "heal and then take the hit" is NOT it: the text says the unit does
     * <b>not</b> fall, so the killing blow is <b>cancelled</b> when a listener answers -- if the heal were simply applied first,
     * any hit larger than the heal would still kill, which is the opposite of "will not fall into the unable-to-fight state".
     *
     * <p>Fired from {@code Battle.applyDamage} right before {@code takeDamage}, which is the single place a target is hurt.
     */
    LETHAL_DAMAGE("LETHAL_DAMAGE", true),
    /**
     * {@code Battle.startBattle()} - delivered once to <b>every character's own table</b>, after the opening
     * hooks and before {@code processRequests}.
     *
     * <p>Note: <b>It carries no {@code actor} and no {@code target}.</b> Reading it as "once for every combatant" is
     * true about the <i>delivery</i> and misleading about the <i>context</i>: a rule written here as
     * {@code "when": ["actor == self"]} can never fire, and it looks entirely reasonable (hand-written that way
     * once, and the loader now refuses the spelling). "My own battle start" needs no condition because the
     * table being fired <b>is</b> the owner's.
     */
    BATTLE_START("BATTLE_START", true),
    /**
     * An ally finished an attack. Carries the hit-target count.
     */
    ALLY_ATTACK("ALLY_ATTACK", true),
    /**
     * An ally cast their <b>Skill</b> (the data's {@code BPSkill}) - including non-damaging ones.
     *
     * <p>Note: <b>The Skill cast is the narrow one.</b> A single cast event for everything that was not an
     * ultimate, which silently included basic attacks, techniques, map attacks and talents - so
     * "when the wearer uses their Skill" content (relic set 109's ATK buff, Robin's Sequential Passage (模进乐段)) also
     * fired on a basic attack. That is the failure this vocabulary is shaped to prevent: an over-trigger is a
     * wrong number with no error attached. The three in-battle casts now have three events
     * ({@link #BASIC_ATTACK} / this / {@link #ULT_CAST}), split at the emitter from the parsed
     * {@code SkillCategory}, because the condition DSL has no variable for the kind of cast.
     */
    SKILL_CAST("SKILL_CAST", true),
    /**
     * An ally used their <b>basic attack</b> (the data's {@code Normal} - which also covers enhanced
     * basic attacks, since the data spells both of them {@code Normal}).
     *
     * <p>This is the event for "after casting a basic attack / after the wearer uses their Basic ATK". It is deliberately
     * separate from {@link #ALLY_ATTACK}, which fires for <b>any</b> attack that lands (basic attack,
     * skill, ultimate, follow-up) and is what "after an ally attacks" content wants.
     *
     * <p>Note: Not fired for the <b>map</b> basic attack ({@code MazeNormal}): that hit happens outside
     * battle, and "after the wearer uses their basic attack" is about a battle turn. Nor for techniques,
     * assists, elation damage or talents - none of those is an in-battle cast, and inventing an event
     * for them is how this split got lost the first time.
     */
    BASIC_ATTACK("BASIC_ATTACK", true),
    /**
     * Someone's energy was credited.
     */
    ENERGY_GAINED("ENERGY_GAINED", true),
    /**
     * Someone really lost HP (shield absorption does not count).
     */
    HP_LOST("HP_LOST", true),

    /**
     * <b>A damage instance was SETTLED</b> - "how much did this hit I dealt actually settle".
     *
     * <p><b>Why it had to exist.</b> The engine already announced damage <i>before</i> it was settled
     * ({@link #DEALING_DAMAGE}, deliberately: that is where a rule can still change the instance, and its
     * {@code amount} is therefore the <b>base</b> - measured: 1093.02 where the victim really lost 260.23584).
     * The post-settlement number existed for <b>our own</b> units only: {@link #TAKING_HIT} carries {@code settled},
     * but it is fired through {@code fireTriggersForAlly}, whose first line returns 0 for any target that is not on
     * our side - so an enemy taking a hit never announced it. Himeko (姬子)'s eidolon 6
     * "the ultimate additionally deals 2 instances of damage, each dealing fire damage equal to <b>40% of the original damage</b> to a random enemy" needs exactly that
     * number about an <b>enemy</b> victim, and "(this/that/original) damage x X%" is a whole family (14 + 6 documents).
     *
     * <p>Note: It is the <b>dealer's</b> event, not the victim's: {@code actor} = whoever dealt it, {@code target} =
     * whoever took it, both sides. That is the honest reading of "the damage I dealt", and it is why this is a new event
     * rather than "let TAKING_HIT reach enemies" - that one is the victim's fact and stays ours (the enemy side owns monsters).
     *
     * <p>Its {@code amount} is the <b>settled</b> value, which is what {@code scale: "original_damage"} reads.
     */
    DAMAGE_SETTLED("DAMAGE_SETTLED", true),

    /**
     * "HP consumed": HP paid as a PRICE. The texts list it separately from "taking damage"
     * ("after the wearer is attacked <b>or</b> has HP consumed by one of our targets", 113/4) because a hit can be shielded and can kill, and a price can do neither.
     */
    HP_CONSUMED("HP_CONSUMED", true),
    /**
     * Someone was really healed.
     */
    HEALED("HEALED", true),
    /**
     * A <b>shield was granted</b> to one of our characters.
     *
     * <p>{@code actor} = <b>who provided it</b>, {@code target} = who received it - the same convention
     * {@link #HEALED} uses, so "when receiving healing <b>or a shield provided by a teammate</b>" is
     * {@code target == self} + {@code actor is_ally} + {@code actor != self} with no new vocabulary at all.
     *
     * <p><b>Why it had to exist.</b> The engine had {@code HEALED} but no "a shield was given" event - shields
     * were only an <i>op</i> ({@code SHIELD}). Dahlia (大丽花)'s trace "when Dahlia receives healing <b>or a shield</b> provided by a teammate ..."
     * subscribes to both, so writing only the healing half would leave the trace silent exactly when the shield
     * half applies: an effect that is too weak, with nothing anywhere reporting a problem.
     *
     * <p>Note: <b>Fired from two places, deliberately.</b> A shield reaches the field either as a raw grant
     * ({@code Battle.grantShield}, no {@code turns}) or through a
     * {@link com.laosun.aluminium.models.buff.ShieldBuff} (timed). The buff is applied by the buff manager, which
     * has no {@code Battle} handle, so the interpreter fires this from the timed arm itself - the two paths
     * disagree about who owns the lifetime, not about the fact being announced.
     *
     * <p>Note: <b>Only a grant that leaves a shield standing fires it</b> ({@code value > 0}), the same way
     * {@link #HEALED} only fires when HP was really restored. A grant of {@code <= 0} is the engine's spelling of
     * "clear the shield" and is not a grant. And a raw grant states <b>no provider</b>, so {@code actor} is
     * {@code null} and {@code actor is_ally} is false - the safe direction: a shield nobody is credited with must
     * not answer a question about who provided it.
     */
    SHIELD_GRANTED("SHIELD_GRANTED", true),
    /**
     * Someone was killed.
     */
    KILL("KILL", true),
    /**
     * An enemy was weakness-broken.
     */
    BREAK("BREAK", true),
    /**
     * Skill points were really spent.
     */
    SKILL_POINT_SPENT("SKILL_POINT_SPENT", true),
    /**
     * Skill points were really gained.
     */
    SKILL_POINT_GAINED("SKILL_POINT_GAINED", true),
    /**
     * Skill points that were ASKED for but NOT credited, because the pool was already at its cap (readers:
     * cone 23021's "the skill points that overflow on restore are also counted" and character 1306's "if skill points overflow, record the number of overflowed skill points").
     *
     * <p>Without it the swallowed points are invisible: {@code SKILL_POINT_GAINED} only fires when something was really
     * credited ("gained > 0"), so a gain at the cap is indistinguishable from no gain at all. The amount carried here is
     * exactly {@code asked - credited}, computed by {@code Battle.gainSkillPoint}, which is where both numbers are at hand.
     */
    SKILL_POINT_OVERFLOWED("SKILL_POINT_OVERFLOWED", true),
    /**
     * A character's turn began - emitted by {@code Battle.beforeMove}, after the actor's buffs have
     * been settled and before its {@code MoveEvent.beforeMove} hook.
     *
     * <p>Note: <b>This is a trigger-table event, not a new buff interface.</b> Turn boundaries stay
     * {@code MoveEvent.beforeMove/afterMove} for buffs - that decision is pinned by
     * {@code EventBusTest.turnBoundariesAreStillMoveEvent} and nothing here revives it. What
     * {@code TURN_START} adds is only the ability for <b>data</b> to subscribe to the same moment
     * ("at the beginning of the turn, if ..."), which the buff interfaces cannot express because a JSON
     * rule is not a Java class.
     */
    TURN_START("TURN_START", true),
    /**
     * A unit's turn ended.
     *
     * <p>"at the end of each of our targets' turns, remove 1 stack of [鸣弦号令] from Yukong (驭空)" (120 Yukong) needed this moment, and nothing else could stand
     * in for it: the *next* unit's {@code TURN_START} is a different fact (the last turn of a fight has no next unit), and
     * a buff's duration tick is not an event at all.
     *
     * <p>Fired after the actor's own {@code afterMove} hook and the late buff tick - i.e. once the turn is really over, so
     * a rule on it sees the state the turn ended in. {@code actor} and {@code target} are both the unit whose turn it was,
     * the same convention {@code TURN_START} uses.
     */
    TURN_END("TURN_END", true),
    /**
     * The owner was hit by an incoming damage instance.
     *
     * <p><b>Deliberately not the same fact as {@link #HP_LOST}.</b> {@code HP_LOST} means "HP was
     * really lost" (a fully shielded hit does not fire it, and neither does a hit on an invulnerable
     * target); {@code TAKING_HIT} means "an attack landed on me", which is exactly what the relic and
     * talent texts that say "after the wearer is hit / attacked" mean - those effects accumulate even
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
     * A damage instance is <b>about to be settled</b>: fired from {@code Battle.assemble} before the zones
     * are evaluated, so a rule can still change <i>this</i> instance.
     *
     * <p><b>Why a pre-settlement event had to exist.</b> {@link #ALLY_ATTACK} fires <i>after</i> the whole
     * attack has been settled - correct for "after an ally attacks", useless for
     * "increases the damage dealt to a target in state X by Y%", because by then the number is final and all a rule could do is
     * describe it. This event hands over the pending instance ({@code TriggerContext.damage()}), and
     * {@code BOOST_DAMAGE} is what changes it - <b>for that one instance only</b>, since the instance itself is
     * the state: there is no buff to attach, nothing to clean up, and nothing that can leak into the next hit.
     *
     * <p>{@code actor} = who deals the damage, {@code target} = who is about to take it (the same convention as
     * {@link #TAKING_HIT}, from the other side). It fires for <b>every</b> instance the engine settles - DOT
     * ticks, break and additional damage included - because those are damage too; a rule that means "attacks
     * only" says so with its own conditions.
     */
    DEALING_DAMAGE("DEALING_DAMAGE", true),
    /**
     * An ally cast their Ultimate.
     *
     * <p>Fired by {@code SkillExecutor.broadcastSkillCast} when the parsed skill data's
     * {@code attack_type} is {@code Ultra} - never inferred from a skill's name or slot. Exactly one
     * of {@link #SKILL_CAST} and this event fires per cast.
     */
    ULT_CAST("ULT_CAST", true),
    /**
     * A follow-up attack was used: an <b>additional-damage</b> instance settled through
     * {@code Battle.applyAdditionalDamage}, with the attacker as {@code actor} and the victim as
     * {@code target}.
     *
     * <p><b>Why it needs its own event rather than {@link #ALLY_ATTACK}.</b> The relic and talent
     * texts that say "when the wearer uses a Follow-Up ATK" mean that category specifically;
     * {@code ALLY_ATTACK} fires for every attack, so a rule hung on it would also fire for basic
     * attacks, skills and ultimates - a silent over-trigger, not a near miss.
     *
     * <p><b>What counts as one.</b> The engine has exactly one notion of an attack that "does not
     * count as dealing 1 attack": {@code DamageType.ADDITIONAL}, which is what a talent-driven
     * follow-up (Clara's counter, the shape) is settled as. So this fires from that single
     * settlement point, and nothing else in the engine fires it.
     *
     * <p>It is emitted for every such instance <b>whether or not it dealt damage</b>: the texts that
     * subscribe say "when the wearer uses a Follow-Up ATK", which is the attack being <i>used</i>, and
     * one absorbed entirely by a shield or an invulnerable target was still used.
     *
     * <p>Note: Note the recursion this creates - a rule that answers {@code FOLLOW_UP} with the
     * {@code DAMAGE} op is a follow-up responding to a follow-up; {@code Battle.MAX_TRIGGER_DEPTH}
     * stops that loudly instead of letting it run away.
     */
    FOLLOW_UP("FOLLOW_UP", true),
    /**
     * A <b>summon</b> finished an attack ( memosprites): fired by {@code EnemySkill.execute}
     * after every segment of its attack has been settled, with the summon as {@code actor} and the
     * hit-target count riding along like {@link #ALLY_ATTACK}'s.
     *
     * <p><b>Why its own event rather than widening {@link #ALLY_ATTACK}.</b> "An ally attacked" is what
     * three shipped rules mean today ({@code characters/1309.json}, {@code characters/1403.json}'s
     * "after another of our targets attacks", relic set 105's {@code actor == self}), and whether a memosprite counts as
     * one of those "targets" is <b>not</b> something the documents settle here. Widening the event would
     * have silently changed what those three rules fire on - an over-trigger is a wrong number with no
     * error attached - so the distinction is drawn at the emitter instead, exactly as
     * {@link #SKILL_CAST} / {@link #BASIC_ATTACK} / {@link #ULT_CAST} are.
     *
     * <p><b>It is fired for a summon of either camp</b> (a boss's minion attacks too); which summons a
     * rule cares about is its own question, and the condition for it is {@code actor == summon} (the
     * rule owner's own summon) - see {@code engine.md} §4.6. Without that condition a rule would also
     * fire when a <em>teammate's</em> summon attacks, which is the same over-trigger in another coat.
     *
     * <p>An attack that connected with nothing does not fire it, and neither does a summon with no
     * attack of its own: this is "an attack happened", not "a unit was on the field".
     */
    SUMMON_ATTACK("SUMMON_ATTACK", true),
    /**
     * A <b>summon entered the field</b> ( memosprites): fired by {@code Battle.processRequests} for
     * everything {@code Battle.summon} / {@code Battle.summonMemosprite} placed, with the summoned unit as
     * {@code actor}.
     *
     * <p><b>Why it is fired at the settle point and not inside the summon call.</b> A summon enters the action
     * bar through {@code addRequestItems}, which {@code processRequests} drains - so during the call itself the
     * unit is on the roster but <b>not yet scheduled</b>. A rule that answers "when summoned" almost always wants to
     * touch its action value ({@code ADVANCE}, i.e. "make itself act immediately"), and an advance against a unit with no
     * signal is silently lost. Note: That is also why this cannot simply be fired from
     * {@code processAddRequests}: that queue is shared with <b>wave</b> entries, and a wave arriving is not a
     * summon.
     *
     * <p>Fired for a summon of either camp; {@code actor == summon} is what narrows it to the rule owner's own
     * (the same condition {@link #SUMMON_ATTACK} uses). Note that {@code SUMMON} is idempotent per summoner, so a
     * second summoning while one is already out fires nothing - which is exactly what "if the 衣匠 is already present, then ..."
     * clauses need.
     */
    SUMMONED("SUMMONED", true),
    /**
     * A cast is <b>about to resolve</b>: fired by {@code SkillExecutor.execute} after the caster is known and
     * <b>before any damage is expanded</b>, so a rule can still change what this cast does.
     *
     * <p><b>Why a pre-cast event exists at all.</b> Every other cast event ({@link #BASIC_ATTACK},
     * {@link #SKILL_CAST}, {@link #ULT_CAST}, {@link #ALLY_ATTACK}) fires <i>after</i> the damage has been
     * settled - correct for "after the wearer uses their Skill", useless for a rule that has to change the swing
     * itself. {@link #DEALING_DAMAGE} covers "change this damage instance"; this covers "this cast's damage is not
     * mine to deal", which is a fact about the <b>cast</b> and has to be known before the instance exists.
     *
     * <p><b>The first user.</b> Evernight (长夜月)'s ultimate: 141303's own generated damage rows would swing at <b>her</b>
     * attack as the base, while the document says the damage is the memosprite's ("make the memosprite '长夜' deal to all enemies
     * ice damage equal to #1[i]% of '长夜''s Max HP") - and the rule that delivers it as the memosprite's runs on
     * {@link #ULT_CAST}, i.e. too late to stop the first swing. Measured before this existed: 8818.5 from her own
     * rows plus the commanded hit, where the document describes one damage instance.
     *
     * <p>{@code actor} = the caster, {@code target} = {@code null} (nothing has been aimed at yet in the sense the
     * later events mean: the caster's selection is not re-published here, and a rule that wanted it would be
     * asking about {@link #SKILL_CAST}). The cast in progress is reachable through
     * {@code Battle.currentCast()}; the only op that reads it today is {@code DELEGATE_DAMAGE}.
     *
     * <p>Note: Fired for <b>every</b> cast our side makes, including non-damaging ones and the enemy-side attacks that
     * have no table: a rule must narrow itself with {@code actor == self} (or the op refuses the cast that is not
     * its owner's, see {@code DELEGATE_DAMAGE}).
     */
    CAST_SETUP("CAST_SETUP", true),
    /**
     * A <b>countdown</b> unit's turn began: fired from {@code Battle.beforeMove} with the
     * countdown as {@code actor} (and as the subject), so a rule can answer "at the start of the countdown's turn ...".
     *
     * <p><b>Why the moment needs an event at all.</b> Robin (知更鸟)'s [协奏] lasts "until its countdown's turn arrives", which is a fact about
     * the <b>action order</b> rather than about anybody's turn count - so the state cannot be given a `turns` and the
     * arrival has to be announced. The countdown is an ordinary {@link com.laosun.aluminium.models.Countdown} scheduled
     * by the queue, which is what makes advances/breaks move it for free.
     *
     * <p>Note: <b>{@code actor} is the countdown, not the character whose state it ends.</b> The rule that reacts is
     * written on the character and reads its own state ("exiting [协奏]" is `REMOVE_STATE` plus `EXTRA_TURN self`), so the actor is only ever used
     * to recognise the moment - and a rule that wants "my own countdown" says so by naming it in its own table.
     */
    COUNTDOWN_TURN("COUNTDOWN_TURN", true),

    /**
     * A damage instance that CRIT.
     *
     * <p>{@code DEALING_DAMAGE} fires BEFORE the crit zone is read (the event at Battle:2542, the roll at
     * 254), so "after the wearer crits an enemy target" was not expressible: at the moment a rule could see the
     * instance, nobody knew whether it would crit. This one fires right AFTER the zone and only when it did,
     * so a rule needs no predicate -- and its SUBJECT is the critter, not the victim.
     */
    CRIT_DEALT("CRIT_DEALT", true),
    /**
     * A <b>wave entered the field</b> (cones 23011 and 23064, "at the start of each wave").
     *
     * <p>Fired by {@code WaveManager.nextWave} right after the wave's monsters are spawned, and with neither actor nor
     * subject -- the same shape as {@code BATTLE_START}, because a wave arriving is a fact about the battle rather than
     * about one unit. That class already documented this exact spot as the extension point ("if between-wave config ever
     * appears, the extension point is inside nextWave").
     */
    WAVE_START("WAVE_START", true),
    /**
     * A <b>resource changed</b> (cone 20024's "when the [笑点] held is >= 10 ...").
     *
     * <p>Fired by {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} with the <b>holder</b> as the actor: a battle is at hand
     * there, while {@code ResourceManager} owns none and so cannot raise a trigger itself. Note: The consequence is stated
     * rather than hidden -- a resource moved by anything other than those two ops does not announce itself yet.
     */
    RESOURCE_CHANGED("RESOURCE_CHANGED", true),

    /**
     * An attack has FINISHED: settlement complete, hit set frozen.
     *
     * <p>{@code Battle.fireAfterAttack} already decides "an attack happened" and holds the whole set; this
     * exposes that boundary to content. A per-hit event cannot assemble it (Note: as {@code weakHitCount} notes).
     */
    ATTACK_FINISHED("ATTACK_FINISHED", true),

    /**
     * The wearer added a weakness to an enemy target (light cone 23050, "At Will" (随心): "when the wearer adds a weakness to an enemy target,
     * restore 1 skill point"). Note: Three readers: 23050, 1405's talent, 1006's Skill.
     * Note: Fired in {@code ADD_ELEMENTAL_WEAKNESS}'s success branch, so it only fires when one was really added.
     */
    WEAKNESS_ADDED("WEAKNESS_ADDED", true);

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
