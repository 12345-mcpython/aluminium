package com.laosun.aluminium.trigger;

import com.laosun.aluminium.Battle;

import java.util.Set;

/**
 * The closed vocabularies of the trigger language.
 */
final class TriggerVocabulary {

    private TriggerVocabulary() {
    }

    /**
     * The two element sources `ADD_ELEMENTAL_WEAKNESS` accepts besides a real element name.
     * Note: A closed set on purpose: without it a misspelled element would only blow up at
     * RUN time, and only if the rule ever fired -- a wrong answer that reports nothing.
     *
     * <p>`party_first` -- "the weakness of the attribute held by our target on the field" (character 1006); its
     * skill text names the rule: "preferentially add the weakness of the attribute held by the first character in our line-up".
     * <p>`random_absent` -- "add 1 random attribute weakness, preferentially adding a weakness the target does not yet have" (character 1405).
     */
    static final Set<String> SPECIAL_ELEMENTS = Set.of("party_first", "random_absent");
    /**
     * Ops the engine can perform, derived from {@link TriggerOperator}.
     *
     * <p>Note: A view, not a second list. The constants in {@link TriggerOperator} are the
     * vocabulary -- each carries its own {@code wired} flag and the long reasoning for why the op
     * exists -- and these two sets are what the load-time check compares a character file's spelling
     * against. Keeping them derived is the whole point: the vocabulary used to be written out as
     * string literals here AND as constants there, and a second copy of one list always drifts.
     *
     * <p>Members worth knowing about, all of them here because the flag lives with the name:
     * <ul>
     *   <li>{@code ABSORB_DAMAGE} -- "每点【好活当赏】等同于 200 点抵御值，我方目标受到伤害时，
     *       真珠可消耗抵御值为我方目标抵挡 60% 的伤害" (reader: 1503 Pearl (真珠)'s talent): a share of an
     *       incoming hit paid for out of ANOTHER unit's counter.</li>
     *   <li>{@code SPEND_STEPS} -- "每使【隐藏分】提高 120 点（含初始隐藏分），获得 1 个额外回合并恢复
     *       1 次强化普攻的可施放次数" (reader: 1506 Silver Wolf LV.999's Eidolon 2): the milestones a
     *       {@code per_steps} effect paid for, consumed ONCE. Counting and spending have to be two
     *       effects, because the sentence pays two effects per milestone while the counter may be
     *       charged only once.</li>
     *   <li>{@code RAISE_RESOURCE_CAP} -- "[新蕊] can overflow to #3%" (reader: 114151, Castorice
     *       (遐蝶)): a declaration states a resource's overflow, and {@code Resource} has had both
     *       tiers all along, so this widens the second one in battle.</li>
     *   <li>{@code RAISE_SKILL_POINT_CAP} -- "while [character] is on the field the skill point cap
     *       is raised by #3" (reader: 1306, Sparkle): the cap is not a constant, so content needs a
     *       way to move it. Distinct from {@code RAISE_RESOURCE_CAP}, which widens a declared
     *       resource's OVERFLOW, not its cap.</li>
     * </ul>
     */
    static final Set<String> WIRED = TriggerOperator.wiredNames();

    /**
     * Ops declared in the roadmap whose prerequisite phase has not landed. Listing them (rather than
     * treating them as typos) lets the error message say <i>why</i> -- see {@link TriggerOperator}.
     */
    static final Set<String> PLANNED = TriggerOperator.plannedNames();
    /**
     * The ops that grant something with a duration, and therefore accept {@code permanent: true}
     * ("for the rest of the battle"). See {@link TriggerValidator#requireNoStackArguments}.
     */
    static final Set<String> OPS_WITH_DURATION =
            Set.of("MODIFY_ATTR", "APPLY_BUFF", "MODIFY_DAMAGE_TAKEN", "RESIST_DEBUFF", "REPLACE_SKILL",
                    // "真珠的【好活当赏】持续时间无限" shapes an absorber that lasts the battle; a `turns` one that runs
                    // out is equally expressible, so it belongs in this set rather than being forced to `permanent`.
                    "ABSORB_DAMAGE");
    /**
     * The selectors an effect's {@code target} may name.
     *
     * <p>This set exists to close a silent-typo hole: the resolver falls back to "the owner"
     * for anything it did not recognise, so a misspelled {@code target} behaved exactly like
     * {@code "self"} - a wrong answer that reports nothing. Same reasoning as the condition
     * variables being a closed set.
     */
    static final Set<String> TARGET_SELECTORS =
            Set.of("party_first", "next_ally", "self", "target", "attacker", "all_allies", "party", "other_allies", "summon",
                    // "使【无量忿怒】倒计时行动延后 15%" (1507 Eidolon 1): the countdown the rule owner PLACED - a real unit
                    // on the action order, addressable at last.
                    "countdown",
                    "target_and_summon", "all_enemies", "lowest_hp_ally",
                    // Note: `actor` is `attacker` under the name the condition language and the events use. Reader:
                    // 1503's extra-turn reward - "该额外回合开始时，【美学底本】获得 30 点【好活当赏】" - where the unit to be
                    // paid is the event's actor (the archetype whose extra turn just began), not the rule's owner.
                    "actor",
                    "random_enemy", "random_hit_enemy",
                    // The attack's hit set MINUS its main target: "同时对其**相邻目标**造成…" (1005 Kafka's Skill, the
                    // enhanced form). `all_hit_enemies` would include the main target and pay it twice.
                    "other_hit_enemies",
                    // "all allies lethally hit in one action" (140月茉之庇): the allies a lethal blow has landed on since
                    // this action began. A SET, not a unit -- hence the `all_` prefix, like `all_allies`.
                    // The game spells this as an EVENT, `OnWaveMonster`, so the selector is named after it -- an enemy
                    // that entered with the current wave.
                    // The SINGLE-target sibling of `wave_monsters`, for `cast_target` -- a commanded cast names ONE aim, and the
                    // game spells the source of that aim as the `OnWaveMonster` event.
                    "wave_monster",
                    "wave_monsters",
                    "all_allies_lethally_hit_this_action",
                    // "the allies who lost HP in this action" (1409 Hyacine 忆灵技2): the recipient is 「生命值降低的目标」, which
                    // is not lowest_hp_ally - the lowest-HP ally may not have been hit at all.
                    "all_allies_lost_hp_this_action",
                    // "randomly 1 of our other targets whose current energy percentage is below 50%" (light cone 21021). Note: The 50%
                    // threshold is the text's own and no tier changes it, so it is in the method and registered there.
                    "random_ally_below_half_energy",
                    // "if the target is defeated before the follow-up attack is cast, launch it at a random single enemy" (three registered readers:
                    // 1220 reason 1, 1221 reason 2, 1305). The preferred target is dead -> take a random enemy.
                    // CanHit:88-90 keeps death orthogonal to invulnerability, so this is the clause own wording.
                    "target_else_random_enemy",
                    // "the target with the highest current HP among the attacked targets" (reader: 1403 Tribbie (缇宝)'s ultimate, whose zone rider picks that unit, and 1415's
                    // ode of passage, which names that rider). The sibling of `random_hit_enemy`: same pool, a different pick.
                    "highest_hp_attack_hit",
                    // "the enemy target with the highest HP", over ALL opponents - the `random_enemy` half of the same pair, for a
                    // clause whose event carries no hit set. Reader: 1506's 【超超超大剑】.
                    "highest_hp_enemy",
                    // "raise the CRIT DMG of all of our memosprites" (reader: 1413 Evernight (长夜月)'s skill "白昼悄然离去").
                    // `summon` says only "the owner's OWN summon", so a clause that speaks of the whole side needs its own name.
                    "all_summons",
                    // EVERY enemy this attack actually hit (the frozen hit set of ATTACK_FINISHED). Reader: 1103
                    // Serval's Skill shock, whose sentence says "the enemy target(s) hit" and whose blast therefore
                    // includes the neighbours the engine's own damage row already struck.
                    "all_hit_enemies",
                    // One living enemy ADJACENT (roster order) to the current target, chosen with the
                    // battle's seeded RNG. Reader: 1103 Serval's Eidolon 1 ("one random adjacent target").
                    "random_adjacent_enemy",
                    // "the wearer and another teammate who triggered combat" (light cone 23050). The engine records who
                    // landed a map attack before the battle (`Battle.declareMapAttack`), so "triggered combat" is a fact,
                    // not a guess. This plural selector is the "other than the owner" half: a wearer who started the
                    // fight herself has no teammate in this sense, which is the fallback branch of the sentence.
                    "other_map_attackers",
                    // 「击破特攻最高的队友」 (the same cone, fallback branch): the living OTHER ally with the highest
                    // BREAKING_EFFECT. Ties keep roster order. Handled in the plural path, because "no other ally" is an
                    // ordinary battle state, not an error.
                    "highest_break_effect_ally",
                    // 「获得【卫戍】的目标，若目标拥有召唤物则造成的伤害额外提高 #3%」 (light cone 23051): the allies that
                    // OWN at least one memosprite. A filter over our side, because the condition language decides whether
                    // a rule runs, not which units its effects reach.
                    "allies_with_summons",
                    // "the rule-level pick": resolved once per firing from the rule own `pick`, so several effects can
                    // name the SAME randomly chosen unit (1111 Luka technique: damage and bleed on one target).
                    "pick");
    /**
     * The two spellings of "every one of our characters".
     */
    static final Set<String> TARGET_ALL_ALLIES = Set.of("all_allies", "party");
    /**
     * "Our side except the rule's owner" - "other than oneself".
     *
     * <p>Its first user is Robin (知更鸟)'s ultimate: "make the <b>teammates other than oneself</b> act immediately". {@code all_allies} cannot say it
     * (302 不老者的仙舟's "raise the ATK of all of us" includes the wearer, and that is pinned), and a condition cannot
     * say it either - conditions filter <b>rules</b>, not the units an effect reaches.
     */
    static final String TARGET_OTHER_ALLIES = "other_allies";
    /**
     * The prefix of the <b>state-holder</b> selector: {@code "target": "holder_of:同袍"} - "the character holding [同袍]"
     * (1414 丹恒-腾荒's trace 神秀, and the aim of his technique's auto-cast).
     *
     * <p><b>Why a parameterised selector had to exist.</b> The engine can already ask "I am in state X"
     * ({@code has_state}, a condition) and it can name a fixed <i>role</i> ({@code target} = the unit this cast was
     * aimed at, {@code attacker}, {@code summon}, {@code party_first}...), but it has no way to say <b>the unit that
     * carries a state</b> - and 1414's kit is built on exactly that: the skill marks one ally as [同袍], and both
     * the trace that buffs it and the technique that re-aims the skill speak about whoever holds it, not about a
     * slot in the roster. A condition cannot do this job: conditions decide whether a <b>rule</b> runs, not which
     * units an effect reaches.
     *
     * <p>Note: <b>How many may hold it.</b> The marker is read on the <b>owner's own camp</b> and the <b>first</b> living
     * holder answers. Keeping it to one is the content's job, and it already has the spelling for it:
     * {@code REMOVE_STATE} over {@code all_allies} followed by {@code APPLY_BUFF} on the new one ("order is meaning",
     * the same two lines 1414's own skill rule uses).
     *
     * <p>Note: <b>Nobody holding it is not an error</b> - it is a legal state of the world for these clauses ("if there
     * is a Bondmate (同袍), buff them"). So the selector answers <b>nobody</b>: a list op reaches nobody, and a single-target op
     * reports it through {@code require} like every other missing party. This is deliberately <i>not</i> the
     * {@code summon} treatment, which throws: a rule whose whole subject is the summon contradicts itself when there
     * is none, whereas these clauses are conditional by nature.
     *
     * <p>The prefix is spelled lower-case (it is <b>not</b> the data): the state name after it is read exactly as
     * written, because a state's name is data (`has_state` makes the same promise).
     */
    static final String HOLDER_OF_PREFIX = "holder_of:";
    /**
     * "The unit this cast <b>aimed at</b>, and <b>its</b> summon" - "a designated single ally <b>and their summon</b>".
     *
     * <p>Its first user is Sunday (星期日)'s Skill (131302): "make the designated single ally <b>and their summon</b> act immediately". It is a
     * <b>pair</b> and it is not the same pair as any existing selector:
     * <ul>
     *   <li>{@code target} advances the chosen ally but not its summon;</li>
     *   <li>{@code summon} is the <b>rule owner's</b> summon - the wrong unit entirely (Sunday (星期日)'s own memosprite is not
     *       what his Skill advances);</li>
     *   <li>two effects cannot express it either, because the second one would have to name "the summon of the
     *       unit the first one resolved", and a selector cannot refer to another effect's result.</li>
     * </ul>
     *
     * <p>Note: A chosen unit <b>without</b> a summon is just that unit, not an error: "and their summon" only has something
     * to add when there is one, and Sunday (星期日)'s Skill is cast on ordinary allies all the time. Note: Only the summon
     * <b>that is out</b> counts ({@code Battle.summonsOf} skips the dead ones), which is why this selector needs a
     * battle like the other group selectors.
     */
    static final String TARGET_AND_SUMMON = "target_and_summon";
    /**
     * "The whole of the <b>other</b> side" - "to all enemies".
     *
     * <p>Its first user is Himeko (姬子)'s Talent ("deal Fire damage equal to 140% of Himeko (姬子)'s ATK to all enemies"). Every other
     * group selector reads {@link Battle#allies}, so "all enemies" had no spelling at all: the only way to reach
     * the other camp was {@code target}, one unit at a time, which is not what "all" says.
     *
     * <p>Read through {@link Battle#getOpponents}, so "the enemy side" means "the camp opposing the rule's owner" rather
     * than "the {@code enemies} list" - the same answer for every character (they are all on our side), and the
     * honest one if a rule ever belongs to an enemy. Note: The dead are <b>not</b> filtered here: like
     * {@code all_allies}, the selector answers "who is on that side", and the op decides what it can do with
     * them ({@code DAMAGE} skips a dead victim on its own). It needs a battle, which is why it is resolved in
     * the list resolver and not the single-target one.
     */
    static final String TARGET_ALL_ENEMIES = "all_enemies";
    /**
     * "a <b>random</b> 1 of the ... enemy targets" (reader: 1505's ultimate rider, and the family of
     * "random 1" sentences this project had registered as unexpressible: 21029 / 21021 / 21032).
     *
     * <p>Note: It is a SINGLE target, so it answers in {@code resolveTarget}; an op that wants a list still refuses it, which is the
     * honest answer -- "random 1" is not a group.
     */
    static final String TARGET_RANDOM_ENEMY = "random_enemy";
    /**
     * "a random 1 of the enemy targets that were attacked": a random ONE of the enemies this attack hit.
     *
     * <p>Note: MEASURED SEMANTICS (see {@code Damage#hitTargets}): the set is snapshotted when each damage
     * instance is built, so a PER-HIT reader sees "so far" while a CAST-LEVEL reader (firing after the cast
     * settled) sees all of them. An empty or absent set means "unknown" and this selector FAILS rather
     * than falling back to "a random enemy" -- a silent wrong target is worse than an error.
     */
    static final String TARGET_RANDOM_HIT_ENEMY = "random_hit_enemy";
    /**
     * "The ally with the lowest HP <b>percentage</b>" - "our target with the lowest <b>current HP percentage</b>".
     *
     * <p>Its first user is March 7th (三月七)'s Eidolon (星魂) 2 ("on entering battle, grant a shield equal to 24% of March 7th (三月七)'s DEF plus
     * 320 to our target with the lowest current HP percentage"), and Huohuo (藿藿)'s [禳命] / Lingsha (灵砂)'s [浮元] heal the same unit. Before it, "the most hurt one" could
     * only be picked by a <b>condition</b> - which filters <em>rules</em>, not the units an effect reaches - so the
     * sentence had no spelling at all.
     *
     * <p>Note: <b>Percentage, not absolute HP</b>, and the difference is real: with allies at 100/1000 and 900/10000,
     * the second has fewer HP <i>points</i> (900) but the first has the lower <i>share</i> (10% vs 9%) - the two
     * questions have different answers, and the documents spell them differently ("lowest HP percentage" vs Lingsha (灵砂)'s
     * "lowest HP"). Only the percentage spelling is built, because that is the one three of the four readers use;
     * the absolute variant is registered with its reader (Lingsha (灵砂) 1222) rather than guessed at now.
     *
     * <p>Note: <b>Ties go to the earliest unit in the party order</b>, which is what makes the answer deterministic -
     * and at {@code BATTLE_START} everybody is at 100%, so every Eidolon-2-style rule hits a tie and the choice has to
     * be stated rather than left to the iteration order of a map. The dead are skipped (a corpse has no share to
     * speak of), and an empty camp resolves to <b>no target</b> rather than an error, like the other group
     * selectors.
     */
    static final String TARGET_LOWEST_HP_ALLY = "lowest_hp_ally";
    /**
     * The ops that actually read {@code damage_type} (see the guard in
     * {@link TriggerValidator#validateOne}).
     */
    static final java.util.Set<String> DAMAGE_TYPE_READERS =
            java.util.Set.of("BOOST_DAMAGE", "DAMAGE", "MODIFY_ATTR", "MODIFY_DAMAGE_TAKEN", "ADD_DAMAGE");

    /**
     * The ops that actually read {@code scope_element} (see the guard in
     * {@link TriggerValidator#validateOne}).
     *
     * <p>⚠ One op, and the set exists for the reason {@link #DAMAGE_TYPE_READERS} does: the engine has no global
     * "unknown argument" check, so a field one op reads is SILENTLY IGNORED by every other. A rule that wrote
     * {@code scope_element} on, say, {@code APPLY_BUFF} would look scoped and not be.
     */
    static final java.util.Set<String> SCOPE_ELEMENT_READERS = java.util.Set.of("MODIFY_DAMAGE_TAKEN");
}

