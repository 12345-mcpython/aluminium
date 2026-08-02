package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DamageCalculator;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;

import java.util.List;

/**
 * The execution context handed to a {@link SkillBehavior}: the skill's
 * parameter list, stance (toughness) values, and description, plus shared
 * helpers (damage dealing, friendly targeting, stance conversion).
 */
public final class SkillContext {

    private final List<Double> params;
    private final double stanceSingle;
    private final double stanceSpread;
    private final double stanceAll;
    private final String desc;

    public SkillContext(List<Double> params, double stanceSingle, double stanceSpread,
                        double stanceAll, String desc) {
        this.params = params;
        this.stanceSingle = stanceSingle;
        this.stanceSpread = stanceSpread;
        this.stanceAll = stanceAll;
        this.desc = desc;
    }

    // ─── Param access ──────────────────────────────────────────────────

    /** The parameter at {@code index}, or {@code fallback} if missing. */
    public double param(int index, double fallback) {
        return params != null && index >= 0 && index < params.size() ? params.get(index) : fallback;
    }

    /** The parameter at {@code index} as an int, or {@code fallback}. */
    public int intParam(int index, int fallback) {
        return (int) Math.round(param(index, fallback));
    }

    /** The first parameter (usually the damage multiplier), or 1.0. */
    public double firstParam() {
        return param(0, 1.0);
    }

    // ─── Stance (toughness) ────────────────────────────────────────────

    public double stanceSingle() {
        return stanceSingle;
    }

    public double stanceSpread() {
        return stanceSpread;
    }

    public double stanceAll() {
        return stanceAll;
    }

    // ─── Description ───────────────────────────────────────────────────

    public String desc() {
        return desc;
    }

    /**
     * Whether the skill scales with 生命上限 (max HP) instead of ATK.
     */
    public boolean isHpScaling() {
        return desc.contains("生命上限");
    }

    // ─── Shared helpers ────────────────────────────────────────────────

    /**
     * Deals damage with the skill's multiplier, using HP as the base stat for
     * 生命上限-scaling skills.
     */
    public void dealDamage(Battle battle, CanHit user, CanHit target, double multiplier) {
        DamageContext context = DamageContext.of(DamageType.NORMAL, user.getElement());
        if (isHpScaling()) {
            battle.dealAttackDamageBase(user, target, user.getMaxHp() * multiplier, context);
        } else {
            battle.dealAttackDamage(user, target, multiplier, 1.0, context);
        }
    }

    /**
     * Heal/shield/support skills always target the user's own camp
     * (HSR.md §4), including 忆灵.
     */
    public List<? extends CanHit> friendlyTargets(Battle battle, CanHit user) {
        List<? extends CanHit> allies = user.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER
                ? battle.getAlivePlayerUnits() : battle.getAliveEnemies();
        return allies.isEmpty() ? List.of(user) : allies;
    }

    /**
     * Applies a Slow debuff (speed down) to a target.
     */
    public void applySlow(Battle battle, CanHit user, CanHit target, double ratio, int turns) {
        Buff slow = new Buff("Slow", Buff.Category.DEBUFF, user, target, turns)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(-ratio,
                        DoubleValue.Modifier.ModifierSource.DEBUFF));
        battle.applyBuff(target, slow);
    }

    /**
     * Applies a shock (触电) DoT to a target.
     */
    public void applyShock(Battle battle, CanHit user, CanHit target, double atkRatio, int turns) {
        double dot = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * atkRatio : 0;
        target.applyDot(new Buff.Dot("Shock (触电)", user, target, dot, Element.THUNDER, turns));
    }

    /**
     * Applies a burn (灼烧) DoT to a target.
     */
    public void applyBurn(Battle battle, CanHit user, CanHit target, double atkRatio, int turns) {
        double dot = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * atkRatio : 0;
        target.applyDot(new Buff.Dot("Burn (灼烧)", user, target, dot, Element.FIRE, turns));
    }

    /**
     * Applies a bleed (裂伤) DoT to a target.
     */
    public void applyBleed(Battle battle, CanHit user, CanHit target, double atkRatio, int turns) {
        double dot = user.getAttribute(AttributeType.ATTACK) != null
                ? user.getAttribute(AttributeType.ATTACK).get() * atkRatio : 0;
        target.applyDot(new Buff.Dot("Bleed (裂伤)", user, target, dot, Element.PHYSICAL, turns));
    }

    /**
     * Detonates all DoTs on a target, dealing {@code ratio} × their damage.
     */
    public void detonateDots(Battle battle, CanHit user, CanHit target, double ratio) {
        if (target.isDeath() || target.getDots().isEmpty()) {
            return;
        }
        for (Buff.Dot dot : new java.util.ArrayList<>(target.getDots())) {
            double damage = DamageCalculator.calculateDotDamage(user, target,
                    dot.getDamage() * ratio, dot.getElement());
            battle.applyDamage(target, damage, user);
        }
    }

    /**
     * Whether the target is an enemy weak to the given element.
     */
    public static boolean isWeakTo(CanHit target, Element element) {
        return target instanceof Enemy enemy && enemy.isWeakTo(element);
    }
}
