package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;

import java.util.Locale;

/**
 * A **generic** attribute buff / debuff: "stat X becomes X ⊕ value for N turns".
 *
 * <p>Before this class, every stat modifier needed its own Java class ({@code SpeedBoostBuff},
 * {@code BoostDamageBuff}, …), which is exactly the shape this project is trying to get away from:
 * 93 characters' buffs are content, not code. One class parameterised by
 * (attribute, modifier type, value, source, duration) covers all of them, and the per-character part
 * stays in data.
 *
 * <p><b>Three things decide how it behaves</b>:
 * <ul>
 *   <li>{@code attribute} — which {@link AttributeType} is touched. Percentage attributes
 *       ({@code ATTACK_PERCENT} and friends) are deliberately <b>not</b> usable here: the builder
 *       merges those into the base attribute's modifier list, so a buff must name the base
 *       attribute ({@code ATTACK}) and pass an {@code ADD_PERCENT} modifier.</li>
 *   <li>{@code modifierType} — {@code ADD_PERCENT} (summed with other add-percent modifiers),
 *       {@code MULTIPLY_PERCENT} (multiplied independently) or {@code PURE_VALUE} (flat, added
 *       last). See {@link DoubleValue} for the formula.</li>
 *   <li>{@code sourceRole} — {@code BUFF} or {@code DEBUFF}. It only decides which half of the
 *       attribute a modifier lands in, so an "ATK +50%" buff and an "ATK −30%" debuff can be on the
 *       same character at the same time and be removed independently.</li>
 * </ul>
 *
 * <p><b>Identity ({@link #isSameKind})</b> is {@code (attribute, modifierType, sourceRole)} rather
 * than "the class is the same". That matters: with the inherited class-only rule every stat buff
 * would be the same kind, so a {@code +ATK%} buff would silently evict a {@code +DEF%} buff. The
 * tuple still keeps "the same buff cast again" replacing rather than stacking, which
 * {@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking} pins.
 *
 * <p><b>Stacking is opt-in and lives beside that identity, not inside it.</b> An instance built with
 * {@code maxStacks > 1} reports {@link #isStackable()} {@code true} and a
 * {@link #stackGroupKey()} equal to the same {@code (attribute, modifierType, sourceRole)} tuple.
 * {@link BuffManager#addBuff} then keeps the older instances — up to the cap — instead of evicting
 * them, while {@link #isSameKind} is untouched, so the replace-on-same-kind tests keep meaning what
 * they always meant. Each stack is an ordinary buff with its own {@code id}, so every stack carries
 * its own modifier and can be removed on its own; expiry is therefore still exact (the attribute
 * returns to base because the last modifier with that id is gone, not because something was
 * recomputed).
 *
 * <p><b>"For the rest of the battle" is the {@code permanent} flag</b>, not a large turn count; see
 * {@link AbstractBuff#isPermanent()}.
 *
 * <p><b>{@code SPEED} is special</b>: the action queue caches the cycle time
 * ({@code Queue.cycleTime()}), so a speed change must be announced through
 * {@link CanHit#notifySpeedChanged()} or the turn order silently keeps the old speed.
 * {@link #applyEffect} / {@link #removeBuff} do that, and only for {@code SPEED}.
 */
public class StatModifierBuff extends AbstractBuff {

    private final AttributeType attribute;
    private final DoubleValue.Modifier.ModifierType modifierType;
    private final double value;
    private final DoubleValue.Modifier.ModifierSource sourceRole;
    private final int maxStacks;

    /**
     * @param attribute    the attribute to modify (must not be a {@code *_PERCENT} variant)
     * @param modifierType how the value combines with the attribute's other modifiers
     * @param value        for {@code ADD_PERCENT} / {@code MULTIPLY_PERCENT} a <b>decimal</b>
     *                     (0.5 means +50%); for {@code PURE_VALUE} an absolute amount
     * @param sourceRole   {@code BUFF} or {@code DEBUFF}
     * @param duration     turns; ticks on {@code afterMove} unless {@code early} is set
     * @param early        {@code true} = expire on {@code beforeMove} of the owner, {@code false} =
     *                     on {@code afterMove}. Buffs granted by an ally default to late, matching
     *                     {@code SpeedBoostBuff}.
     */
    private StatModifierBuff(AttributeType attribute,
                             DoubleValue.Modifier.ModifierType modifierType,
                             double value,
                             DoubleValue.Modifier.ModifierSource sourceRole,
                             int duration,
                             boolean early) {
        this(attribute, modifierType, value, sourceRole, duration, early, false, 1);
    }

    /**
     * The full constructor, used by the factories that expose the new arguments.
     *
     * @param permanent {@code true} = no turn limit ("for the rest of the battle"); the buff is never
     *                  ticked, so {@code duration} is then only a placeholder
     * @param maxStacks how many copies may accumulate; {@code 1} = the classic replace behaviour
     */
    private StatModifierBuff(AttributeType attribute,
                             DoubleValue.Modifier.ModifierType modifierType,
                             double value,
                             DoubleValue.Modifier.ModifierSource sourceRole,
                             int duration,
                             boolean early,
                             boolean permanent,
                             int maxStacks) {
        super(duration, early, permanent);
        if (attribute == null) {
            throw new IllegalArgumentException("StatModifierBuff needs an attribute");
        }
        if (attribute.isPercentVariant()) {
            throw new IllegalArgumentException(
                    "StatModifierBuff cannot target the builder-only attribute " + attribute
                            + "; name the base attribute and use an ADD_PERCENT modifier instead");
        }
        if (modifierType == null) {
            throw new IllegalArgumentException("StatModifierBuff needs a modifier type");
        }
        if (maxStacks < 1) {
            throw new IllegalArgumentException(
                    "StatModifierBuff needs a positive stack cap but got " + maxStacks);
        }
        this.attribute = attribute;
        this.modifierType = modifierType;
        this.value = value;
        this.sourceRole = sourceRole == null
                ? DoubleValue.Modifier.ModifierSource.BUFF : sourceRole;
        this.maxStacks = maxStacks;
    }

    // ------------------------------------------------------------------
    // Factories: the names say the intent, so call sites do not have to
    // remember which modifier type means "percent".
    // ------------------------------------------------------------------

    /**
     * "ATK +50%" — additive percentage, i.e. {@code pct = 0.5}.
     */
    public static StatModifierBuff percentBuff(AttributeType attribute, double pct, int duration) {
        return new StatModifierBuff(attribute, DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                pct, DoubleValue.Modifier.ModifierSource.BUFF, duration, false);
    }

    /**
     * "ATK −30%" — additive percentage with a negative value.
     */
    public static StatModifierBuff percentDebuff(AttributeType attribute, double pct, int duration) {
        return new StatModifierBuff(attribute, DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                pct, DoubleValue.Modifier.ModifierSource.DEBUFF, duration, false);
    }

    /**
     * "SPD +36" — a flat amount, added after all percentage maths.
     */
    public static StatModifierBuff flatBuff(AttributeType attribute, double amount, int duration) {
        return new StatModifierBuff(attribute, DoubleValue.Modifier.ModifierType.PURE_VALUE,
                amount, DoubleValue.Modifier.ModifierSource.BUFF, duration, false);
    }

    /**
     * "SPD −20" — a flat negative amount.
     */
    public static StatModifierBuff flatDebuff(AttributeType attribute, double amount, int duration) {
        return new StatModifierBuff(attribute, DoubleValue.Modifier.ModifierType.PURE_VALUE,
                amount, DoubleValue.Modifier.ModifierSource.DEBUFF, duration, false);
    }

    /**
     * The escape hatch for data-driven buffs (the trigger table's {@code MODIFY_ATTR} op): the
     * modifier type and the source role come from the rule instead of a convenience factory.
     *
     * @param type   {@code "add_percent"} / {@code "multiply_percent"} / {@code "pure"}
     * @param source {@code "buff"} or {@code "debuff"}
     * @throws IllegalArgumentException when either name is unknown — a typo must not silently
     *                                  become "no modifier at all"
     */
    public static StatModifierBuff of(AttributeType attribute, String type, double value,
                                      String source, int duration, boolean early) {
        return of(attribute, type, value, source, duration, early, false, 1);
    }

    /**
     * The full data-driven factory: everything {@link #of} takes plus the two arguments the
     * stacking / "rest of the battle" primitives need.
     *
     * @param permanent {@code true} = "for the rest of the battle": no turn limit at all
     * @param maxStacks how many copies may accumulate; {@code 1} = the classic replace behaviour
     * @throws IllegalArgumentException when either name is unknown, {@code maxStacks} is below 1, or
     *                                  {@code maxStacks > 1} is combined with a non-positive
     *                                  {@code duration} while not being permanent — a stack that
     *                                  expires instantly would be a silent no-op
     */
    public static StatModifierBuff of(AttributeType attribute, String type, double value,
                                      String source, int duration, boolean early,
                                      boolean permanent, int maxStacks) {
        DoubleValue.Modifier.ModifierType modifierType = switch (normalize(type)) {
            case "add_percent" -> DoubleValue.Modifier.ModifierType.ADD_PERCENT;
            case "multiply_percent" -> DoubleValue.Modifier.ModifierType.MULTIPLY_PERCENT;
            case "pure", "flat" -> DoubleValue.Modifier.ModifierType.PURE_VALUE;
            default -> throw new IllegalArgumentException(
                    "Unknown stat modifier type '" + type + "' (add_percent / multiply_percent / pure)");
        };
        DoubleValue.Modifier.ModifierSource role = switch (normalize(source)) {
            case "", "buff" -> DoubleValue.Modifier.ModifierSource.BUFF;
            case "debuff" -> DoubleValue.Modifier.ModifierSource.DEBUFF;
            default -> throw new IllegalArgumentException(
                    "Unknown stat modifier source '" + source + "' (buff / debuff)");
        };
        if (maxStacks < 1) {
            throw new IllegalArgumentException(
                    "A stackable stat modifier needs a positive stack cap but got " + maxStacks);
        }
        if (!permanent && maxStacks > 1 && duration <= 0) {
            throw new IllegalArgumentException(
                    "A stackable stat modifier needs a positive duration (or permanent) but got "
                            + duration + "; such stacks would expire before they could accumulate");
        }
        return new StatModifierBuff(attribute, modifierType, value, role, duration, early,
                permanent, maxStacks);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------

    public AttributeType getAttribute() {
        return attribute;
    }

    public DoubleValue.Modifier.ModifierType getModifierType() {
        return modifierType;
    }

    public double getValue() {
        return value;
    }

    public DoubleValue.Modifier.ModifierSource getSourceRole() {
        return sourceRole;
    }

    /**
     * How many copies of this modifier may be attached at once.
     *
     * <p>{@code 1} — the default, and what every pre-existing factory produces — keeps the
     * replace-on-same-kind rule; anything above 1 lets {@link BuffManager} accumulate instances up to
     * this cap.
     */
    @Override
    public int maxStacks() {
        return maxStacks;
    }

    @Override
    public boolean isStackable() {
        return maxStacks > 1;
    }

    /**
     * Two stackable stat buffs are siblings exactly when they touch the same attribute in the same
     * way — the same tuple {@link #isSameKind} uses, deliberately: "stack with" and "replace" must
     * agree about what "the same buff" means, or a +ATK% stack could sit next to a +DEF% one and
     * neither could be reasoned about.
     */
    @Override
    public Object stackGroupKey() {
        if (!isStackable()) {
            return null;
        }
        return attribute.name() + "|" + modifierType.name() + "|" + sourceRole.name();
    }

    /**
     * Two stat buffs are the same kind only when they touch <b>the same attribute the same way</b>.
     *
     * <p>Without this override every stat buff would share one identity (the class), so adding a
     * {@code +ATK%} buff would evict an existing {@code +DEF%} buff. See the class Javadoc.
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        if (!(other instanceof StatModifierBuff that)) {
            return false;
        }
        return this.attribute == that.attribute
                && this.modifierType == that.modifierType
                && this.sourceRole == that.sourceRole;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        DoubleValue attributeValue = target.getAttribute(attribute);
        attributeValue.addModifier(
                new DoubleValue.Modifier(modifierType, value, sourceRole, id));
        if (attribute == AttributeType.SPEED) {
            target.notifySpeedChanged();
        }
    }

    @Override
    public void removeBuff(CanHit target) {
        DoubleValue attributeValue = target.getAttribute(attribute);
        DoubleValue.Modifier modifier = attributeValue.findFirstBySourceAndId(sourceRole, id);
        if (modifier != null) {
            attributeValue.removeModifier(modifier);
        }
        if (attribute == AttributeType.SPEED) {
            target.notifySpeedChanged();
        }
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    @Override
    public String toString() {
        return "StatModifierBuff[" + attribute + " " + modifierType + " " + value
                + " as " + sourceRole + ", "
                + (permanent ? "permanent" : remainingDuration + "t")
                + (maxStacks > 1 ? ", max " + maxStacks + " stacks" : "") + "]";
    }
}
