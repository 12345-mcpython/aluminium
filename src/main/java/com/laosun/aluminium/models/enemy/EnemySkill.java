package com.laosun.aluminium.models.enemy;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.data.SkillData;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Enemy skill: data-driven from {@code enemy_skills.json}, not hard-coded.
 *
 * <p>Difference from character skills: it does not go through {@link SkillData}/the multiplier
 * table (that is the character-skill structure); instead it deals damage directly as
 * "attack x multiplier x hits". That is why {@link #getData()} returns {@code null} and
 * {@link #execute} is fully custom - and also why {@code SkillExecutor} must not be reused for it
 * (the character-skill toughness-reduction/shape-dispatch logic does not apply to enemies: enemies
 * do not attack the toughness bar).
 *
 * <p>Each hit goes through {@link Battle#applyDamage} independently: each hit rolls crit and
 * settles on its own (consistent with character skills).
 *
 * <p>Memosprites (忆灵): the class also serves memosprites. Their damage is written in the documents the same way
 * an enemy's is (one number times one of the caster's attributes) - "对敌方单体造成等同于'长夜'50%生命上限的
 * 冰属性伤害" - so the only thing that had to change was naming the attribute ({@link #getBaseAttribute()})
 * and taking the target side from the caster's camp rather than assuming "the enemy is casting".
 *
 * <p>Note: For the origin of the multipliers see {@link EnemySkillData}: the data source has no enemy
 * skill table, so these values are guesses.
 *
 * @param element    damage element (already resolved at construction time from the data / the
 *                   monster's {@code stance_type}, never null)
 * @param multiplier multiplier (damage base = {@code baseAttribute} x multiplier)
 * @param hits       number of hits (at least 1)
 * @param type       damage type
 */
public class EnemySkill extends Skill {

    @Getter
    private final DamageElement element;
    @Getter
    private final double multiplier;
    @Getter
    private final int hits;
    private final DamageType type;
    private final SkillEffectType effect;
    /**
     * Which of the user's attributes the multiplier applies to ({@link #getBaseAttribute()}).
     */
    @Getter
    private final AttributeType baseAttribute;
    /**
     * Toughness this attack removes per hit, or {@code 0} for "does not touch the bar".
     *
     * <p>Enemies never had a value here (they do not attack a toughness bar), so 0 is the default and their
     * behaviour is unchanged. A memosprite's attack does: the documents state "破韧值 单体 30" beside its damage.
     */
    @Getter
    private final double stanceDamage;

    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type) {
        this(element, multiplier, hits, type, SkillEffectType.SINGLE_ATTACK);
    }

    /**
     * @param effect the skill's shape; {@code null} = single target, which is what every earlier
     *               entry relied on, so the default keeps them bit-for-bit unchanged
     */
    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type,
                      SkillEffectType effect) {
        this(element, multiplier, hits, type, effect, AttributeType.ATTACK, 0);
    }

    /**
     * @param baseAttribute which of the user's attributes the multiplier applies to; {@code null} =
     *                      {@link AttributeType#ATTACK}, which is what every enemy entry means
     *                      (their damage is written as a share of their ATK), so the default keeps
     *                      them bit-for-bit unchanged. A memosprite's damage is written instead as
     *                      "等同于忆灵 X% 生命上限" - the same class, told which number to read.
     */
    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type,
                      SkillEffectType effect, AttributeType baseAttribute) {
        this(element, multiplier, hits, type, effect, baseAttribute, 0);
    }

    /**
     * @param stanceDamage toughness removed per hit; {@code 0} or less leaves the bar alone, which is what an
     *                     enemy's attack has always meant
     */
    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type,
                      SkillEffectType effect, AttributeType baseAttribute, double stanceDamage) {
        this.element = element == null ? DamageElement.PHYSICAL : element;
        this.multiplier = multiplier;
        this.hits = Math.max(1, hits);
        this.type = type == null ? DamageType.NORMAL : type;
        this.effect = effect == null ? SkillEffectType.SINGLE_ATTACK : effect;
        this.baseAttribute = baseAttribute == null ? AttributeType.ATTACK : baseAttribute;
        this.stanceDamage = stanceDamage;
    }

    @Override
    public int getLevel() {
        return 1;
    }

    /**
     * {@inheritDoc}
     *
     * @return always {@code null}: enemy skills do not use the character multiplier table; the
     * execution logic lives entirely in {@link #execute}
     */
    @Override
    public SkillData getData() {
        return null;
    }

    /**
     * Applies the skill to whoever its shape says it reaches: the primary target ({@code SingleAttack}),
     * the user's whole opposing camp ({@code AoEAttack}), or the primary target plus its neighbours
     * ({@code Blast}). {@link #hits} segments land on <b>each</b> of them, each settling independently
     * (so each rolls crit on its own).
     *
     * <p>Before this dispatch existed, every enemy skill hit the primary target - a multi-target enemy skill in the
     * data had no way to reach a second character, so an AoE would silently under-hit. The dispatch
     * mirrors {@code SkillExecutor}'s character-skill shapes so the two sides read the same way, without
     * sharing code: enemies do not reduce toughness and do not expand parameters, which is why
     * {@link #execute} stays custom (see the class Javadoc).
     *
     * @param battle the battle in progress
     * @param user   the applier (an enemy, or a memosprite)
     * @param target the list chosen by the caller (only the first is used, as the main target)
     */
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        CanHit victim = target.getFirst();
        if (victim == null || victim.isDeath()) {
            return;
        }
        Set<CanHit> hitTargets = new LinkedHashSet<>();
        double total = 0;
        for (CanHit struck : struckBy(victim, user, battle)) {
            total += strike(battle, user, struck, hitTargets);
        }
        // Memosprites (忆灵) / a summon's attack is an attack, so our side hears about it exactly once, after
        // every segment has been settled -- the same notification a character's skill raises. Otherwise a
        // memosprite's attack tells nobody, so a buff on the memosprite itself
        // ({@code "target": "summon"} + {@code "until": "next_attack"}) is never consumed and simply stays.
        battle.fireAfterAttack(user, victim, hitTargets, total);
        // ...and the data-facing half of the same fact: a rule can subscribe to "a summon attacked"
        // (TriggerEvent.SUMMON_ATTACK), which is what "装备者的忆灵攻击时" needs. Fired for a summon of
        // either camp -- it is delivered to every character's table, so `actor == summon` is what narrows it
        // to the rule owner's own. Not raised when nothing was hit: "an attack happened" is not "a unit
        // existed".
        if (!hitTargets.isEmpty()) {
            battle.fireTriggers(TriggerEvent.SUMMON_ATTACK, user, null, hitTargets.size(), 0);
            // ...and the enemy's own half of the same fact. An enemy action runs its own path, so before this a
            // clause of the form "when an enemy attacks" had no event at all. The victim is passed as the target
            // (unlike the summon event, which passes null), because "an enemy attacked ME" is the clause that gets
            // written; a memosprite attacking through this same class raises SUMMON_ATTACK only.
            if (user instanceof com.laosun.aluminium.models.enemy.Enemy) {
                battle.fireTriggers(TriggerEvent.ENEMY_ATTACK, user, victim, hitTargets.size(), 0);
            }
        }
    }

    /**
     * Who this skill reaches, given the caller's main target.
     */
    private List<CanHit> struckBy(CanHit mainTarget, CanHit user, Battle battle) {
        // Note: Deliberately NOT filtered to the living here. A dead unit settles nothing and is not recorded as
        // hit, because strike answers both questions itself (it returns 0 without touching hitTargets) --
        // and that is the one guard a test can reach. An earlier version filtered here too, and mutation
        // testing showed the outer filter changed no observable outcome (removing it left every test green),
        // i.e. it was an untestable second guard for the same fact. One guard, exercised.
        // Note: The OPPOSING CAMP of the user, not `battle.allies` (the friendly half). An enemy AOE
        // has to reach a player-side summon too, and a memosprite's AOE has to reach the enemy
        // camp. Hard-coding `allies` made every AOE one-sided: it read right while only enemies cast
        // this skill, and silently hit nothing the moment our own summon did.
        List<CanHit> team = new ArrayList<>();
        for (CanHit unit : battle.getOpponents(user)) {
            if (unit != null) {
                team.add(unit);
            }
        }
        return switch (effect) {
            case AOE_ATTACK -> List.copyOf(team);
            case BLAST -> {
                int center = team.indexOf(mainTarget);
                if (center < 0) {
                    yield List.of(mainTarget);       // not one of ours: fall back to the single target
                }
                List<CanHit> reached = new ArrayList<>();
                reached.add(team.get(center));
                if (center > 0) {
                    reached.add(team.get(center - 1));
                }
                if (center < team.size() - 1) {
                    reached.add(team.get(center + 1));
                }
                yield reached;
            }
            default -> List.of(mainTarget);
        };
    }

    /**
     * Lands {@link #hits} segments on one character, recording what it reached and returning what it settled.
     *
     * <p><b>Which number the multiplier applies to</b> is {@link #baseAttribute}: an enemy's attack scales off
     * its ATK (the historical behaviour, and the default), while a memosprite's damage is written as
     * "等同于忆灵 X% <b>生命上限</b>" - so the same class serves both by naming the attribute instead of
     * assuming ATK. Only this one line ever cared which it was.
     *
     * <p>A target already down is not recorded and settles nothing: "the targets this attack connected with"
     * follows the same convention {@code SkillExecutor.hit} uses (a target that dies <em>during</em> the attack
     * still counts - it was hit).
     *
     * @param hitTargets collects the targets this segment reached, in hit order
     * @return the sum of the settled values of this target's segments
     */
    private double strike(Battle battle, CanHit user, CanHit victim, Set<CanHit> hitTargets) {
        if (victim == null || victim.isDeath()) {
            return 0;
        }
        hitTargets.add(victim);
        double base = user.getAttribute(baseAttribute).get() * multiplier;
        double total = 0;
        for (int i = 0; i < hits; i++) {
            if (victim.isDeath()) {
                break;                               // once killed mid-way, stop hitting (no overkill on a corpse)
            }
            total += battle.applyDamage(victim, new Damage(user, victim, element, type, base));
            // After the damage, like SkillExecutor.hit does: the bar comes off a hit that landed, and a break it
            // causes is settled by Battle.reduceToughness itself. Only a real toughness bar takes one -- an attack
            // on a character has none, which is why the enemy path has always carried 0 here.
            if (stanceDamage > 0 && victim instanceof Enemy enemy) {
                battle.reduceToughness(user, enemy, element, stanceDamage);
            }
        }
        return total;
    }
}
