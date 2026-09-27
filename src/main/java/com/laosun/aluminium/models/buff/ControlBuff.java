package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;

/**
 * A <b>control state</b> (控制状态) as one buff: 冻结 / 纠缠 / 禁锢 — 「不能行动」, 「速度降低」 and, for 冻结,
 * 「每回合开始时受到冰属性附加伤害」.
 *
 * <p><b>Why one class instead of "a StunBuff plus maybe a slow".</b> P10-2 composed a control inline out of
 * existing primitives, which was right about the <i>parts</i> and wrong about the <i>identity</i>: the composition
 * lived in {@code Battle.attachBreakControl}, so a control had no name, nothing the condition DSL could read
 * ("is this unit frozen?"), and no way for a <b>skill</b> to apply one at all. Two things follow from giving it a
 * class:
 * <ul>
 *   <li>its parts are attached and removed <b>together</b> — a control that is dispelled, removed by name or
 *       expires takes its slow (and its per-turn damage) with it, instead of leaving half a state behind;</li>
 *   <li>it carries {@link #getName()} — the spelling the documents use ({@code 冻结}), which is what
 *       {@code has_state 冻结} reads ({@code BuffManager.hasState}) and what a rule's {@code "control"} argument
 *       names.</li>
 * </ul>
 *
 * <p><b>Three parts, each optional except the name:</b>
 * <ul>
 *   <li>{@code blocksAct} → {@link #canAct()} answers {@code false}, which is what {@code Battle} consults before
 *       letting a unit act (冻结);</li>
 *   <li>{@code slowPercent} → a {@code StatModifierBuff.percentDebuff(SPEED, …)} this buff attaches and removes
 *       itself, so the slow cannot outlive the state (纠缠 −20%, 禁锢 −10%);</li>
 *   <li>a <b>per-turn damage</b> ({@link #getDotElement()} / {@link #getDotDamage()}) → a {@code DotBuff} the same
 *       way, which is 冻结's 「每回合开始时受到…冰属性附加伤害」. It is <b>stated by the rule</b> rather than
 *       looked up from {@link Constant#CONTROL_EFFECTS}, because the number is the applier's (三月七's ultimate
 *       deals 「等同于三月七60%攻击力」, her 秘技 50%) — the state says what it does, the ability says how much.</li>
 * </ul>
 *
 * <p>⚠ The <b>delay</b> is deliberately not part of this buff: 禁锢/纠缠's 「行动延后」 is a one-off push of the
 * action bar, and a state that expires after one turn leaving a push behind is the data's own behaviour (the
 * existing {@code attachBreakControl} documents the same split).
 *
 * <p>{@code isSameKind} is overridden to compare the <b>control name</b>, so re-applying the same control refreshes
 * it (the engine's ordinary "same buff again replaces the old one" rule) while two different controls can coexist —
 * being both frozen and imprisoned is not the same thing as being frozen twice.
 */
public class ControlBuff extends AbstractBuff {

    private final Constant.ControlEffect control;

    /**
     * The per-turn damage attached with this state, or {@code null} when the state deals none.
     */
    private final DamageElement dotElement;

    private final double dotDamage;

    /**
     * The speed debuff this buff attached (so it can take it off again), or {@code null}.
     */
    private StatModifierBuff slow;

    /**
     * The per-turn damage this buff attached (so it can take it off again), or {@code null}.
     */
    private DotBuff dot;

    /**
     * A control from the break table: the state and nothing else.
     *
     * @param control which state (see {@link Constant#CONTROL_EFFECTS})
     * @param turns   how many of the victim's turns it lasts; the ability that applies it states this
     */
    public ControlBuff(Constant.ControlEffect control, int turns) {
        this(control, turns, null, 0);
    }

    /**
     * A control with a per-turn damage payload (an ability-applied 冻结).
     *
     * @param control    which state
     * @param turns      how many of the victim's turns it lasts
     * @param dotElement the element of the per-turn damage, or {@code null} for none
     * @param dotDamage  the damage settled each of the victim's turns (already scaled by the caller)
     */
    public ControlBuff(Constant.ControlEffect control, int turns, DamageElement dotElement, double dotDamage) {
        // Not an early buff: a control is checked when the unit tries to act, and the engine's turn boundary
        // order for a plain timed buff is the same one every StatModifierBuff uses.
        super(turns, false);
        this.control = control;
        this.dotElement = dotElement;
        this.dotDamage = dotDamage;
    }

    /**
     * The state's name as the documents spell it (冻结 / 纠缠 / 禁锢).
     */
    public String getName() {
        return control.name();
    }

    /**
     * The table entry behind this state (its resistance key, whether it blocks acting, how much it slows).
     */
    public Constant.ControlEffect getControl() {
        return control;
    }

    public DamageElement getDotElement() {
        return dotElement;
    }

    public double getDotDamage() {
        return dotDamage;
    }

    @Override
    public boolean canAct() {
        return !control.blocksAct();
    }

    /**
     * A control is a <b>negative</b> effect on its bearer: 「解除…负面效果」 removes it, and DISPEL's count sees it.
     */
    @Override
    public boolean isDebuff() {
        return true;
    }

    /**
     * 控制类: 「抵抗控制类负面状态的概率提高35%」 and 「免疫控制类负面状态」 are answers about <b>this</b> family, so a
     * control state written later is covered by them without anyone updating a list of keys.
     */
    @Override
    public com.laosun.aluminium.enums.DebuffClass debuffClass() {
        return com.laosun.aluminium.enums.DebuffClass.CONTROL;
    }

    @Override
    public void applyEffect(CanHit target) {
        if (control.slowPercent() > 0) {
            // percentDebuff takes the negative value itself, so this reads "SPEED -20%". Its duration is this
            // buff's, and removeBuff takes it off again -- the slow must not outlive the state.
            slow = StatModifierBuff.percentDebuff(AttributeType.SPEED, -control.slowPercent(),
                    remainingDuration);
            target.getBuffManager().addBuff(slow);
        }
        if (dotElement != null) {
            // The source is whoever applied the state, so the damage is credited to them (kill credit, energy,
            // and 「造成的伤害」 attribution all follow the DotBuff's source).
            dot = new DotBuff(source, dotElement, dotDamage, remainingDuration);
            target.getBuffManager().addBuff(dot);
        }
    }

    @Override
    public void removeBuff(CanHit target) {
        if (slow != null) {
            target.getBuffManager().removeBuff(slow);
            slow = null;
        }
        if (dot != null) {
            target.getBuffManager().removeBuff(dot);
            dot = null;
        }
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    /**
     * The same <b>control</b> re-applied refreshes this one; a different control is a different state and may sit
     * beside it (frozen <i>and</i> imprisoned is possible, frozen twice is not).
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof ControlBuff buff && buff.control.equals(control);
    }

    @Override
    public String toString() {
        return "ControlBuff[" + control.name() + (dotElement == null ? "" : ", " + dotElement + " "
                + dotDamage + "/turn") + ", " + remainingDuration + "t]";
    }
}
