package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.trigger.TriggerTable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

/**
 * One trigger rule from {@code resources/game/characters/<cid>.json}:
 * "on &lt;event&gt;, when &lt;conditions&gt;, do &lt;effects&gt;".
 *
 * <p>This is the unit that replaces a `XxxTalent.java` class. The engine only interprets it --
 * it never learns which character it belongs to, which is the whole point of the three-way
 * split.
 *
 * <p>Note: Read the conditions as follows: the owning character is called <b>self</b> in
 * {@link #when}; the unit that caused the event is <b>actor</b>. So
 * {@code "when": ["actor != self"]} means "someone else on my side acted" -- which is how
 * Robin's "after an ally attacks" talent is expressed.
 */
@Getter
@ToString
@NoArgsConstructor
public class TriggerSpec {

    /**
     * The event name, matching {@link com.laosun.aluminium.enums.TriggerEvent}.
     */
    @SerializedName("on")
    private String on;

    /**
     * Extra events this rule also listens to (one rule listening to several events), e.g. "when casting the Skill and the Ultimate".
     *
     * <p>Note: The primary {@link #on} stays REQUIRED even when this is present, on purpose: four separate places parse it with
     * {@code TriggerEvent.fromString(spec.getOn())} to validate which event a clause may hang on, and leaving them alone is what makes this addition
     * behaviour-preserving for every rule that ships today.
     */
    @SerializedName("on_any")
    private java.util.List<String> onAny;

    /**
     * Conditions that must all hold, in the small DSL understood by
     * {@link TriggerTable}. An empty or absent list means
     * "always".
     */
    @SerializedName("when")
    private List<String> when;

    /**
     * An optional <b>name for this rule</b>, so that another rule can raise one of its numbers
     * ({@code MODIFY_RULE}'s {@code "rule"} field).
     *
     * <p><b>Why it exists.</b> An Eidolon (星魂) or a trace (行迹) can say "the number of times the talent's counter effect can trigger each turn is <b>increased by 1</b>" or
     * "the base chance to freeze the enemy target is <b>increased by 15%</b>" - sentences that modify a number that already exists on another rule
     * in the same file. Without a name there is nothing to point at, and the two ways to fake it are both wrong: a
     * second rule with the raised number <i>adds</i> firings (a {@code per_turn: 3} rule next to the {@code per_turn: 2}
     * one = five per turn) and a second chance rule <i>rolls twice</i> (1 − 0.5  x  0.35 = 82.5% instead of 65%).
     *
     * <p>Note: Names are unique <b>per file</b> (two rules with one id are refused at load, since a reference would be
     * ambiguous) and a reference must resolve inside the same file - a relic rule is shared by every wearer and has
     * no way to know whose table it lands in.
     */
    @SerializedName("id")
    private String id;

    /**
     * The effects to run, in order.
     */
    @SerializedName("do")
    private List<EffectSpec> doEffects;

    /**
     * How many of the <b>owner's own turns</b> must pass between two firings - the game's
     * "该效果每回合只能触发1次" is {@code 1}. Absent = no limit.
     *
     * <p>Counted in the owner's turns, not in events: the counter is decremented when the turn of the
     * character whose table this is begins. That is why a rule reacting to other people's actions still
     * means "once per <i>my</i> turn" - the limit belongs to the rule's owner, not to whoever happened
     * to trigger it. A value below 1 is rejected at load time.
     */
    @SerializedName("cooldown")
    private Integer cooldown;

    /**
     * How many times this rule may fire in <b>one of its owner's turns</b> - "this effect can trigger <b>2</b> times
     * <b>per turn</b>" is {@code per_turn: 2}. Absent = no per-turn cap.
     *
     * <p><b>Why {@code cooldown} is not enough.</b> {@code cooldown: 1} says "at most once per own turn", which
     * is the {@code N = 1} case of this field and nothing more: "每回合可触发2次" needs a <b>count</b> within one
     * turn, and before this field the only choices were to fire on every event (a wrong number with nothing to
     * see) or not to write the mechanic at all. 10 of the 9character documents state such a limit.
     *
     * <p>Note: Counted in the <b>owner's</b> turns, exactly like {@link #cooldown}: a rule that reacts to other
     * people's actions still means "twice per <i>my</i> turn", and the counter is cleared where the cooldown is
     * decremented ({@code CanHit.tickTriggerCooldowns}, at the start of the owner's own turn).
     *
     * <p>May be combined with {@code once_per_battle} (a per-turn cap and a per-battle cap are cumulative) but
     * <b>not</b> with {@code cooldown}: "at most 2 per turn, but only every other turn" has two readings that
     * disagree, so the pair is refused at load time rather than silently resolved one way.
     */
    @SerializedName("per_turn")
    private Integer perTurn;

    /**
     * Whose firings {@link #perTurn} and {@link #cooldown} count, when it is not the rule owner - the count-and-reset
     * family counts on the marked TARGET, on the TRIGGERER, or per UNIT.
     * One of {@code self} / {@code target} / {@code actor}; absent means the owner, which is what every
     * shipped rule already does, so omitting it changes nothing.
     */
    @SerializedName("per_subject")
    private String perSubject;

    /**
     * {@code true} = the rule fires at most once per battle ("单场战斗中只能触发1次").
     *
     * <p>Deliberately not the same field as {@link #cooldown}: a cooldown comes back after a few turns,
     * this never does. Stating both at once is refused at load time - the author has to mean one of
     * them, and "once per battle, but also every 2 turns" has no reading that is not a mistake.
     */
    @SerializedName("once_per_battle")
    private Boolean oncePerBattle;

    /*
     * "该效果每次攻击只可触发 1 次": at most one firing per ATTACK.
     *
     * <p><b>Why {@code per_turn} is not enough.</b> An attack can settle several instances - a blast hits three enemies, a
     * multi-hit skill connects six times - and all of them are one attack. A per-turn cap cannot tell those apart from
     * three separate attacks in the same turn, which is exactly the distinction "每次攻击只可触发 1 次" makes.
     *
     * <p>The boundary is the engine's own: {@code Battle.fireAfterAttack} ends an attack, so the next instance belongs to
     * the next one. Cumulative with the other caps (they are all upper bounds).
     */
    /**
     * [WAVE-LIMITED] at most one firing per WAVE.
     *
     * <p><b>Why neither neighbour can say it.</b> {@code per_turn} counts one unit's turns and {@code once_per_battle} covers the
     * whole fight; a wave is neither. So it gets the treatment {@code once_per_attack} got: a SEQUENCE comparison, which needs no
     * reset because a new wave is a new number ({@code Battle.waveSequence()}).
     */
    @SerializedName("once_per_wave")
    private Boolean oncePerWave;

    /**
     * The per-wave cap, as the engine reads it. Written out because the builder reads this exact name.
     */
    public Boolean getOncePerWave() {
        return oncePerWave;
    }

    @SerializedName("once_per_attack")
    private Boolean oncePerAttack;

    /**
     * "每次攻击最多通过该方式… N 次": at most N firings per ATTACK (cone 23008 states 3).
     *
     * <p>The same dimension as {@link #oncePerAttack}, with a count. Both mean "counted per attack, not per turn", so
     * stating them together would be two readings of one cap -- refused, like {@code cooldown} next to {@code per_turn}.
     */
    @SerializedName("per_attack")
    private Integer perAttack;

    /**
     * A fixed probability that the rule fires at all - "有 X% 的固定概率…" ({@code 0.35} = 35%). Absent = always.
     *
     * <p>Rolled against the battle's <b>injected</b> random source (never a fresh one), so a seeded battle stays
     * reproducible and a test can make the flip deterministic. A failed roll <b>costs nothing</b>: neither a
     * cooldown nor a once-per-battle flag is started, because nothing happened.
     *
     * <p>A fraction of 1, and it must be strictly positive: {@code chance: 1} is legal and simply means "always"
     * (it does not even consume a draw), while {@code chance: 0} would be a rule that can never do anything -
     * a mistake, not a way to spell "off".
     */
    @SerializedName("chance")
    private Double chance;

    /**
     * How many <b>independent</b> rolls of {@link #chance} one firing makes: "额外增加 2 次【剑势】的发动判定" is
     * {@code 2}, and each attempt rolls on its own so the two can come out differently.
     *
     * <p><b>Why this field and not two rules.</b> The obvious data-only spelling is a second rule with the same
     * {@code chance} - the repo even says so in passing ({@code per_turn}'s javadoc: "a second chance rule
     * <i>rolls twice</i>"). That spelling is only available when the two rules <b>differ</b> in something: a
     * symmetric pair has the same {@code on}, the same {@code when} and the same effects, which is exactly what
     * {@code ContentDuplicateGuardTest} refuses as "one sentence written twice" (measured: writing 1206's two
     * extra Sword Stance rolls as two rules turns that guard red). So the count belongs to the rule.
     *
     * <p>⚠ NOT the same thing as an effect's {@code times}: that one repeats the <b>settlement</b> of an
     * effect that has already been decided, so {@code chance: 0.33, times: 2} is "one roll, two hits". This one
     * repeats the <b>roll</b>, so {@code chance: 0.33, chance_rolls: 2} is "two rolls, each hit its own". The
     * expected number of hits is the same and the distribution is not - which is why they cannot stand in for
     * one another.
     *
     * <p>Absent or {@code 1} = the historical behaviour: one roll per firing. A value above 1 on a rule that
     * states no {@code chance} is refused at load time, because "roll twice for always" says nothing.
     */
    @SerializedName("chance_rolls")
    private Integer chanceRolls;

    /**
     * What {@link #chance} is multiplied by <b>after this rule triggers</b>: "触发该效果的初始固定概率为 100%，成功触发后下一次触发的
     * 固定概率降低至当前概率的 20%" ({@code 0.2} for that sentence).
     *
     * <p>So {@code chance} is the <b>initial</b> probability and this is the factor it decays by. The current value is
     * per-battle state on the combatant ({@code CanHit.currentRuleChance}), filed under the rule's id and cleared with the
     * other firing counters, so it cannot leak from one battle into the next. {@code RESET_RULE_CHANCE} puts it back to
     * the initial one - "将触发…的固定概率重置为初始值".
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - and it is the reason that box is ONE rule rather than two: its
     * damage and its three-way pick share one probability, so they have to share one roll.
     *
     * <p>Note: A decay is only meaningful <b>between</b> 0 and 1. A multiplier of 1 never decays (it says nothing), and 0
     * or less would leave a rule that can never trigger again - so the loader refuses both, and refuses a decay on a rule
     * that states no {@code chance} at all.
     */
    @SerializedName("chance_decay")
    private Double chanceDecay;

    /**
     * The Eidolon rank (星魂, Eidolon) this rule needs: "unlocked at Eidolon N". Absent = the rule is not gated.
     *
     * <p>An Eidolon's <b>mechanic</b> is written like every other one - as a rule in
     * {@code resources/game/characters/<cid>.json} - and this field is the whole of what makes it an Eidolon: the
     * interpreter compares it with the rank the assembly point handed to the character. So the engine still knows
     * nothing about Eidolons beyond a number, and {@code eidolons.json}'s names and descriptions stay reference
     * material for the rule's {@code source}.
     */
    @SerializedName("min_eidolon")
    private Integer minEidolon;

    /**
     * The highest Eidolon rank this rule still applies at; absent = no ceiling.
     *
     * <p>Reader: 1503 Pearl (真珠)'s Eidolon 2 - 「使除真珠和【美学底本】外的其他我方「欢愉」命途角色也触发行动提前效果」. That
     * clause does not ADD an advance, it WIDENS the one her base kit already performs: at Eidolon 0-1 the tier advances
     * the archetype, from Eidolon 2 the same tier advances every Elation ally. So the shipped rule has to stop applying
     * at rank 2, which {@link #minEidolon} alone cannot say - a floor with no ceiling leaves both rules firing and the
     * archetype advancing twice (and {@code ADVANCE} is multiplicative: twice 10% is 19%, not 20%).
     *
     * <p>With {@link #minEidolon} this makes a closed rank BAND, which is what "this clause replaces that one from rank
     * N" needs. Either end may be stated alone.
     */
    @SerializedName("max_eidolon")
    private Integer maxEidolon;

    /**
     * This rule executes ONE of its effects, chosen at random, rather than all of them.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】 - 「随机触发 1 种效果：【超超超大剑】…／【爆爆爆炸蛋】…／【怪怪怪味豆】…」
     * - and light cone 21032's 「战斗开始时以及当装备者回合开始时，随机生效 1 个效果」.
     *
     * <p>⚠ <b>Why it belongs on the RULE.</b> What is picked is one of the rule's whole EFFECTS, not a number inside one,
     * so there is nothing on an {@code EffectSpec} for it to describe. The sibling {@code min_eidolon} IS per-effect
     * because it FILTERS (several effects may survive it); this one is exclusive, which is why the two cannot be combined
     * without a second reading - see the loader's check.
     *
     * <p>⚠ <b>The pool is the effects not marked {@code always}</b> ({@link EffectSpec#getAlways()}), which is how one
     * sentence that both deals damage and picks among three effects stays ONE rule: "造成 90% 的…伤害，并随机触发 1 种效果".
     * The pool's size is what "at least two" is measured on; an {@code always} effect may even carry its own
     * {@code min_eidolon}, since nothing picks it.
     *
     * <p>The pick is made ONCE per firing, not once per effect: rolling for each would as easily execute none of them as
     * two of them, and neither is "one of".
     */
    @SerializedName("one_of")
    private Boolean oneOf;

    /**
     * Whether this rule executes one of its effects at random; see the field.
     */
    public Boolean getOneOf() {
        return oneOf;
    }

    /**
     * The <b>name</b> this rule's {@code one_of} pick is remembered under, so that the next pick cannot be the same one:
     * 「随机生效 1 个效果。该效果生效时，替换上次的效果且**本次不会与上次重复**」.
     *
     * <p><b>Why a NAME and not a flag.</b> The memory belongs to the SENTENCE, not to the rule: light cone 21032's
     * battle-start pick and its turn-start pick are one mechanism in the game - a single state modifier carries the
     * current choice from one to the other (`MEquip_21032_Sub1/2/3`, read out of the ability data) - and the document
     * says the first turn-start pick must differ from the battle-start one. Those two triggers cannot be one rule here,
     * because {@code BATTLE_START} is fired with <b>no actor at all</b>, so a rule that names the wearer cannot also
     * catch the battle's start. Two rules stating the same name therefore share one memory; a rule that states none has
     * no memory, and its pick stays uniform.
     *
     * <p>⚠ It needs {@code one_of} - without a pick there is nothing to remember - and the memory is per <b>battle</b>:
     * it is filed on the combatant next to the firing counters and cleared with them, which is what "替换上次" means
     * (this fight's last effect, not a previous run's).
     *
     * <p>Reader: light cone 21032 「秘密」. Note what it is NOT: the pick stays <b>uniform</b> among the effects that are
     * allowed - which is what the game's own script does, removing the current effect and rolling 50/50 over the other two.
     */
    @SerializedName("one_of_no_repeat")
    private String oneOfNoRepeat;

    /**
     * If this rule triggers while every opponent is <b>down</b>, it is not delivered now: it is held and delivered when a
     * wave brings enemies in - 「若触发前目标被消灭则对新入场的敌方目标触发」.
     *
     * <p>Reader: 1506 Silver Wolf LV.999's 【头号补给盲盒】, and the author's own reading of the sentence: the box waits for
     * the new wave rather than being spent.
     *
     * <p>⚠ <b>What has already happened when it is held, and what has not.</b> The conditions and the ROLL are the
     * firing's own - a held trigger consumed its fixed chance and decayed it (「成功触发后…」) - while the <b>effects</b>,
     * including which one a {@code one_of} would pick, are decided at delivery. Asserted by the judge: after a held
     * firing, the box's chance is 20%, so a later one needs a draw under 0.2.
     *
     * <p>Note: The delivery is part of the {@code WAVE_START} firing, so a rider reading "the total damage settled this
     * firing" sees the held rule's own damage - they are one firing, not two.
     */
    @SerializedName("defer_until_wave")
    private Boolean deferUntilWave;

    /**
     * Where the rule came from (trace id, talent name, character-doc reference).
     *
     * <p>Not used by the engine at all: it exists so that anyone reading the JSON -- or a failing
     * test -- can trace a number back to its source document. The project's rule is that every
     * effect states its origin.
     */
    @SerializedName("source")
    private String source;

    /**
     * Free-form note, typically "why this number" or a {@code TODO data} marker when a value is a
     * placeholder rather than a measured one.
     */
    @SerializedName("note")
    private String note;
}
