package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buff.TauntBuff;
import com.laosun.aluminium.models.skill.Skill;

import java.util.ArrayList;
import java.util.List;

/**
 * The default computer opponent and ally: ultimate when ready, skill when it matters, basic attack otherwise.
 *
 * <p>This is the demo's old hard-coded turn logic, moved out of the entry point and made selectable. It
 * is deliberately simple and readable rather than clever -- its job is to let a battle finish without a
 * human, and to be the reference implementation a real policy is compared against.
 *
 * <p>The rules, in order:
 * <ul>
 *  <li>Ultimate whenever the engine says it is ready ({@link Battle#isUltraReady}), aimed at the main
 *      target. The engine decides readiness from the skill data, so this does not hard-code a bar.</li>
 *  <li>Healing and shielding skills, when the skill point reserve allows, aimed at whoever needs it
 *      most. These are cast before any damage branch for the reason the demo gave: a healer short of
 *      one point should not throw away its panic button in order to basic-attack.</li>
 *  <li>A damaging skill when the reserve allows, preferring a target its element is strong against. The basic
 *      attack is the fallback, and is also what earns the point that a skill spent.</li>
 * </ul>
 *
 * <p>The skill point reserve is a strategy, not an engine rule, and is configurable here because the
 * demo needed it to alternate between spending and earning points.
 *
 * <p>⚠ <b>This policy used to be documented as "weak on purpose, and known to be weaker than the hand-tuned demo".</b>
 * That was wrong on both counts, and the correction is worth keeping because the reasoning error is a common one
 * (ROADMAP §3.5-§3.8, P11-4):
 *
 * <ul>
 *   <li><b>It was not losing to a strategy gap.</b> Three engine rules were simply not applied on the driven path --
 *       an ultimate cost nothing, every Skill was charged twice, and a broken enemy acted anyway. Every one of them
 *       made this policy look worse than it was. With them fixed it wins the full three-on-three at 30000 HP per
 *       enemy on every seed tried.</li>
 *   <li><b>It is not weaker than the hand-written demo.</b> The demo's own decision order was replicated as a policy
 *       and run on the same content and the same seeds: it wins too, but it takes <b>more</b> turns (66 against 47 in
 *       the first seeded run at 30000 HP per enemy, with the demo's own party). The reason is that this policy is thriftier where it counts --
 *       it shields only when somebody is below the threshold and unshielded, while the demo's script spends a skill
 *       point on protection whenever it can afford one, which leaves less for the damage that ends the fight. Being
 *       slower is not the same as being weaker, but it is also not evidence that the script is the stronger
 *       strategist.</li>
 * </ul>
 *
 * <p>What remains true is that this is a <b>simple reference implementation</b>, not a good opponent: it has no
 * notion of focus fire, of holding damage for a break window, or of spending a point now for a kill next turn. A
 * loss against real content would still not be an engine defect. But "simple" is the honest word, not "weak", and
 * the difference matters -- the earlier wording sent a bug hunt after strategy when the mechanics were unwired.
 */
public final class AutoPolicy implements SkillPolicy {

    /**
     * How a main target is picked from the legal candidates.
     *
     * <p>A strategy rather than a fixed rule because the two camps genuinely differ, and the difference
     * is not cosmetic: enemy targeting is aggro-weighted and therefore <b>consumes a random roll</b>,
     * while a player's choice does not. Mixing them up shifts every later crit roll, so a battle that
     * used to be reproducible stops being so. Callers that need a deterministic scripted run (a demo
     * used as a regression anchor, for instance) select {@link #FIRST_ALIVE}.
     */
    public enum Targeting {
        /**
         * Aggro-weighted, via the engine's {@code TargetSelector}. Uses the battle's random source.
         */
        WEIGHTED,
        /**
         * The first living candidate in order. Consumes no randomness.
         */
        FIRST_ALIVE
    }

    /**
     * Skill points kept in hand before an ally is willing to spend one.
     */
    private final int skillPointReserve;

    /**
     * An ally at or below this HP fraction is worth healing.
     *
     * <p>Deliberately not "any scratch": a heal spends the turn <b>and</b> a shared point, and keeping the party
     * topped up at full price leaves the damage dealers unable to act.
     */
    private static final double HEAL_THRESHOLD = 0.75;

    /**
     * An ally at or below this HP fraction, with no shield up, is worth shielding.
     */
    private static final double SHIELD_THRESHOLD = 0.9;

    /**
     * How this policy picks a target.
     */
    private final Targeting targeting;

    /**
     * A policy that keeps one point in reserve and targets by aggro weight, as an enemy does.
     */
    public AutoPolicy() {
        this(1, Targeting.WEIGHTED);
    }

    /**
     * @param skillPointReserve points to keep before casting a skill; 0 spends freely
     */
    public AutoPolicy(int skillPointReserve) {
        this(skillPointReserve, Targeting.WEIGHTED);
    }

    /**
     * @param skillPointReserve points to keep before casting a skill; 0 spends freely
     * @param targeting        how to pick a main target
     */
    public AutoPolicy(int skillPointReserve, Targeting targeting) {
        this.skillPointReserve = Math.max(0, skillPointReserve);
        this.targeting = targeting == null ? Targeting.WEIGHTED : targeting;
    }

    @Override
    public Decision choose(Battle battle, CanHit actor) {
        // Enemies and summons act on the opposing camp. A summon's camp is not the camp of its master by
        // assumption but by asking, so the same code drives an enemy minion and one of our memosprites.
        if (!(actor instanceof com.laosun.aluminium.models.Character)) {
            return chooseForOpponent(battle, actor);
        }
        return chooseForAlly(battle, actor);
    }

    /**
     * An ally's turn: ultimate, then a healing or shielding skill, then a weakness-aware attack.
     *
     * @param battle the battle
     * @param actor  the ally whose turn it is
     * @return the action, or {@code null} when nothing is legal
     */
    private Decision chooseForAlly(Battle battle, CanHit actor) {
        List<CanHit> foes = livingOpponents(battle, actor);
        if (foes.isEmpty()) {
            return null;
        }
        CanHit main = mainTarget(battle, actor, foes, TargetSelector.Intent.SINGLE);

        Skill ultra = actor.getSkills().get(SkillType.ULTRA);
        if (ultra != null && battle.isUltraReady(actor) && main != null) {
            return Decision.of(ultra, aimedAt(battle, actor, ultra, main), "ultimate is ready");
        }

        Skill skill = actor.getSkills().get(SkillType.SKILL);
        boolean affordable = skill != null && skill.getData() != null && skill.getData().isLoaded()
                && battle.getSkillPoints() > skillPointReserve;
        if (affordable) {
            SkillEffectType.Category category = skill.getData().getEffect() == null
                    ? null : skill.getData().getEffect().getCategory();
            // A protective skill only when protection is actually MISSING.
            //
            // Note: This used to fire unconditionally, and that is what lost the demo fight -- measured on 姬子 /
            // 三月七 / 罗刹 vs 冰锋 + 基层员工·外勤 + 次元扑满: 罗刹 has no healing Skill at all (his is a
            // RESTORE row) so he basic-attacked 21 times, while 三月七 re-shielded a healthy party on every single
            // turn and drained the pool. The pool is shared, so 姬子 -- the only real damage -- was left below the
            // reserve and cast her Skill exactly ONCE in 37 turns. The party then died to enemies it could not
            // out-damage. A shield nobody needs is not worth a point that the damage dealer cannot spend.
            if (category == SkillEffectType.Category.HEAL || category == SkillEffectType.Category.BUFF) {
                if (needsProtection(battle, actor, category)) {
                    List<CanHit> friendly = aimedAt(battle, actor, skill, actor);
                    return Decision.of(skill, friendly, category == SkillEffectType.Category.HEAL
                            ? "healing the party" : "shielding the party");
                }
                // Otherwise fall through to damage: a wasted turn is better than a wasted point, and a point kept
                // is one the damage dealer can spend.
            }
            // A damaging skill: cast it, and prefer a target it is actually strong against.
            //
            // Note: This used to require a weakness, on the reasoning that "the point should buy something". That
            // read well and played badly -- 姬子's Fire skill was skipped against the two monsters that are not
            // fire-weak. Weakness now ranks the CHOICE rather than gating it: if some legal target is weak to this
            // element, aim there; otherwise still cast, at the usual main target.
            if (category == SkillEffectType.Category.DAMAGE) {
                CanHit weak = weakestMatch(foes, skill);
                CanHit aim = weak != null ? weak : main;
                return Decision.of(skill, aimedAt(battle, actor, skill, aim),
                        weak != null ? "target is weak to it" : "damaging skill");
            }
        }

        Skill basic = actor.getSkills().get(SkillType.COMMON);
        if (basic == null || main == null) {
            return null;
        }
        return Decision.of(basic, aimedAt(battle, actor, basic, main), "basic attack");
    }

    /**
     * An enemy's or summon's turn: cast whatever skill the unit has, at an aggro-weighted target.
     *
     * <p>The skill is taken from {@link Enemy#activeSkill()} when the unit is an enemy, because that is
     * what reads the HP thresholds of a phase change; falling back to the {@code COMMON} slot covers an
     * enemy with no phase behaviour and covers summons, which carry the same skill map.
     *
     * @param battle the battle
     * @param actor  the unit whose turn it is
     * @return the action, or {@code null} when nothing is legal
     */
    private Decision chooseForOpponent(Battle battle, CanHit actor) {
        List<CanHit> foes = livingOpponents(battle, actor);
        if (foes.isEmpty()) {
            return null;
        }
        CanHit main = mainTarget(battle, actor, foes, TargetSelector.Intent.SINGLE);

        Skill skill = actor instanceof Enemy enemy ? enemy.activeSkill() : null;
        String note = "phase skill";
        if (skill == null) {
            skill = actor.getSkills().get(SkillType.COMMON);
            note = "basic attack";
        }
        if (skill == null || main == null) {
            return null;
        }
        return Decision.of(skill, aimedAt(battle, actor, skill, main), note);
    }

    /**
     * The first legal target this skill's element is actually strong against, or {@code null}.
     *
     * <p>Weakness lives on an {@link Enemy}, not on {@code CanHit}: only a monster has a toughness bar to be weak
     * against, so the check is guarded rather than assumed. Returning {@code null} is the common case and is not a
     * refusal -- the caller casts anyway, at the ordinary target.
     *
     * @param foes  the legal candidates
     * @param skill the skill about to be cast
     * @return a weak target, or {@code null} when there is none
     */
    private static CanHit weakestMatch(List<CanHit> foes, Skill skill) {
        var element = skill.getData() == null ? null : skill.getData().getElement();
        if (element == null) {
            return null;
        }
        for (CanHit foe : foes) {
            if (foe instanceof Enemy enemy && enemy.isWeakTo(element)) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * Whether a protective skill is worth a skill point right now.
     *
     * <p>Two questions, and both have to be "yes" for the cast to make sense:
     * <ul>
     *   <li><b>Is protection missing?</b> A heal on a party nobody has scratched, or a shield on allies who already
     *       carry one, buys nothing. Measured threshold rather than a guess: a heal is worth it once somebody is
     *       below {@link #HEAL_THRESHOLD}, and a shield once somebody is below {@link #SHIELD_THRESHOLD} <b>and</b>
     *       unshielded.</li>
     *   <li><b>Can it be paid for?</b> The pool is shared with the damage dealers, so a protective cast that would
     *       drop the party to the reserve is competing with the damage that ends the fight. 罗刹's own pool had to
     *       stay above the reserve or the party simply out-lasts nothing.</li>
     * </ul>
     *
     * @param battle   the battle
     * @param actor    the one considering the cast
     * @param category which kind of protection (a heal and a shield look for different gaps)
     * @return {@code true} when the cast is worth its point
     */
    private boolean needsProtection(Battle battle, CanHit actor, SkillEffectType.Category category) {
        for (CanHit ally : battle.getSideOf(actor)) {
            if (ally == null || ally.isDeath() || ally.getMaxHp() <= 0) {
                continue;
            }
            double ratio = ally.getCurrentHp() / ally.getMaxHp();
            if (category == SkillEffectType.Category.HEAL) {
                if (ratio <= HEAL_THRESHOLD) {
                    return true;
                }
            } else if (ratio <= SHIELD_THRESHOLD && ally.getShield() <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The living units on the side {@code actor} is attacking.
     *
     * @param battle the battle
     * @param actor  the acting unit
     * @return its opponents, excluding the dead
     */
    private List<CanHit> livingOpponents(Battle battle, CanHit actor) {
        List<CanHit> out = new ArrayList<>();
        for (CanHit unit : battle.getOpponents(actor)) {
            if (unit != null && !unit.isDeath()) {
                out.add(unit);
            }
        }
        return out;
    }

    /**
     * Pick the main target according to this policy's {@link Targeting}.
     *
     * <p>Taunt is honoured in both modes: it is a rule, not a preference, so a taunting unit takes the
     * hit whether or not the policy would have chosen it. Only the weighted mode then rolls.
     *
     * @param battle    the battle
     * @param actor     the acting unit
     * @param foes      the legal candidates
     * @param intent    the targeting shape
     * @return the chosen target, or {@code null} if none
     */
    private CanHit mainTarget(Battle battle, CanHit actor, List<CanHit> foes, TargetSelector.Intent intent) {
        if (foes.isEmpty()) {
            return null;
        }
        if (targeting == Targeting.FIRST_ALIVE) {
            for (CanHit candidate : foes) {
                if (candidate.getBuffManager().hasBuff(TauntBuff.class)) {
                    return candidate;
                }
            }
            return foes.getFirst();
        }
        return TargetSelector.select(battle, foes, intent, battle.getRng());
    }

    /**
     * Expand a main target into the full target list the skill expects.
     *
     * <p>A single-target or blast skill gets the main target alone: {@code SkillExecutor} derives the
     * adjacent victims of a blast from the main target itself, so passing neighbours here would apply
     * the hit twice. An area skill gets the whole camp. A healing or supporting skill gets whoever is
     * hurt worst, which is the one decision a caller genuinely owns -- the amount comes from the data.
     *
     * @param battle the battle
     * @param actor  the acting unit
     * @param skill  the skill about to be cast
     * @param main   the main target
     * @return the target list, never empty
     */
    private List<CanHit> aimedAt(Battle battle, CanHit actor, Skill skill, CanHit main) {
        SkillEffectType.Category category = skill.getData() == null || skill.getData().getEffect() == null
                ? null : skill.getData().getEffect().getCategory();
        if (category == SkillEffectType.Category.HEAL || category == SkillEffectType.Category.BUFF) {
            CanHit worst = worstHurt(battle, actor);
            return List.of(worst == null ? main : worst);
        }
        if (!(skill instanceof com.laosun.aluminium.models.enemy.EnemySkill)
                && category == SkillEffectType.Category.DAMAGE
                && skill.getData() != null
                && skill.getData().getEffect() == SkillEffectType.AOE_ATTACK) {
            return livingOpponents(battle, actor);
        }
        return List.of(main);
    }

    /**
     * The ally in the worst shape, by HP ratio, or {@code null} when the camp is empty.
     *
     * <p>Ratio rather than absolute HP: a 3000-HP unit at 10% is in more danger than a 900-HP unit at
     * 90%, and an absolute comparison would heal the wrong one.
     *
     * @param battle the battle
     * @param actor  the acting unit
     * @return the ally with the lowest HP ratio, or {@code null}
     */
    private CanHit worstHurt(Battle battle, CanHit actor) {
        CanHit worst = null;
        double lowest = Double.MAX_VALUE;
        for (CanHit ally : battle.getSideOf(actor)) {
            if (ally == null || ally.isDeath() || ally.getMaxHp() <= 0) {
                continue;
            }
            double ratio = ally.getCurrentHp() / ally.getMaxHp();
            if (ratio < lowest) {
                lowest = ratio;
                worst = ally;
            }
        }
        return worst;
    }
}
