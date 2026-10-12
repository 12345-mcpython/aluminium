package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.ai.TargetSelector;
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
 *  <li>A damaging skill only against a target that is actually weak to its element; otherwise the
 *      basic attack, which also earns the skill point back.</li>
 * </ul>
 *
 * <p>The skill point reserve is a strategy, not an engine rule, and is configurable here because the
 * demo needed it to alternate between spending and earning points.
 */
public final class AutoPolicy implements SkillPolicy {

    /**
     * Skill points kept in hand before an ally is willing to spend one.
     */
    private final int skillPointReserve;

    /**
     * A policy that keeps one point in reserve, as the demo did.
     */
    public AutoPolicy() {
        this(1);
    }

    /**
     * @param skillPointReserve points to keep before casting a skill; 0 spends freely
     */
    public AutoPolicy(int skillPointReserve) {
        this.skillPointReserve = Math.max(0, skillPointReserve);
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
            // Healing and shielding first: the demo's ordering, and the reason is that these are the
            // actions worth spending a scarce point on.
            if (category == SkillEffectType.Category.HEAL || category == SkillEffectType.Category.BUFF) {
                List<CanHit> friendly = aimedAt(battle, actor, skill, actor);
                return Decision.of(skill, friendly, category == SkillEffectType.Category.HEAL
                        ? "healing the party" : "shielding the party");
            }
            // Otherwise spend only against a weakness, so the point buys something. Weakness lives on an
            // Enemy, not on CanHit: only a monster has a toughness bar to be weak against, so the check
            // is guarded rather than assumed.
            if (category == SkillEffectType.Category.DAMAGE
                    && skill.getData().getElement() != null
                    && main instanceof Enemy foe
                    && foe.isWeakTo(skill.getData().getElement())) {
                return Decision.of(skill, aimedAt(battle, actor, skill, main), "target is weak to it");
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
     * Pick the main target with the engine's own selector, so taunts and aggro weigh as they do elsewhere.
     *
     * @param battle    the battle
     * @param actor     the acting unit
     * @param foes      the legal candidates
     * @param intent    the targeting shape
     * @return the chosen target, or {@code null} if none
     */
    private CanHit mainTarget(Battle battle, CanHit actor, List<CanHit> foes, TargetSelector.Intent intent) {
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
