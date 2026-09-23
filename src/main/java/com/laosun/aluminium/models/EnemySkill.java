package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EnemySkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;

import java.util.List;

/**
 * Enemy skill (P5-3): data-driven from {@code enemy_skills.json}, not hard-coded.
 *
 * <p>Difference from character skills: it does **not** go through {@link SkillData}/the multiplier
 * table (that is the character-skill structure); instead it deals damage directly as
 * "attack × multiplier × hits". That is why {@link #getData()} returns {@code null} and
 * {@link #execute} is fully custom — and also why {@code SkillExecutor} must not be reused for it
 * (the character-skill toughness-reduction/shape-dispatch logic does not apply to enemies: enemies
 * do not attack the toughness bar).
 *
 * <p>Each hit goes through {@link Battle#applyDamage} independently: **each hit rolls crit and
 * settles on its own** (consistent with character skills).
 *
 * <p>⚠ For the origin of the multipliers see {@link EnemySkillData}: the data source has no enemy
 * skill table, so these values are guesses.
 *
 * @param element    damage element (already resolved at construction time from the data / the
 *                   monster's {@code stance_type}, never null)
 * @param multiplier multiplier (damage base = attack × multiplier)
 * @param hits       number of hits (at least 1)
 * @param type       damage type
 */
public class EnemySkill extends Skill {

    private final DamageElement element;
    private final double multiplier;
    private final int hits;
    private final DamageType type;

    public EnemySkill(DamageElement element, double multiplier, int hits, DamageType type) {
        this.element = element == null ? DamageElement.PHYSICAL : element;
        this.multiplier = multiplier;
        this.hits = Math.max(1, hits);
        this.type = type == null ? DamageType.NORMAL : type;
    }

    public DamageElement getElement() {
        return element;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getHits() {
        return hits;
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
     * Hits the primary target {@link #hits} times in a row.
     *
     * <p>It only hits the "primary target": a multi-hit enemy skill here means "multiple hits on the
     * same target", with no splash/AOE (that would need dispatch by skill shape, left for when the
     * real skill table is wired up in P9-2).
     *
     * @param battle the battle in progress
     * @param user   the applier (an enemy)
     * @param target the target list chosen by the caller (only the first is used)
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
        double base = user.getAttribute(AttributeType.ATTACK).get() * multiplier;
        for (int i = 0; i < hits; i++) {
            if (victim.isDeath()) {
                break;                               // once killed mid-way, stop hitting (no overkill on a corpse)
            }
            battle.applyDamage(victim, new Damage(user, victim, element, type, base));
        }
    }
}
