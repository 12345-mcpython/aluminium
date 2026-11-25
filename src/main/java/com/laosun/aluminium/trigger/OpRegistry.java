package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The one place an op is registered: its handler, by its {@link TriggerOperator}.
 *
 * <p><b>Why a registry and not the switch.</b> The dispatcher used to be a 466-line {@code switch} whose arms were
 * the only statement that "this op is performed"; the load-time half was a second {@code switch} with a different arm
 * set (51 against 63), so an op could be performed and not validated, or the other way round, with nothing to notice.
 * Registering the handler makes both halves the same object.
 *
 * <p><b>The two invariants this class will hold once the migration is finished</b> - stated here because they are
 * what the transitional guard checks:
 * <ol>
 *   <li>every wired op has exactly one handler ({@link TriggerOperator#wiredNames()});</li>
 *   <li>an op is dispatched <b>either</b> by its handler <b>or</b> by the legacy switch, never both. Two live paths
 *       for one op is how a "refactor" ships two behaviours and nobody can tell which one ran.</li>
 * </ol>
 *
 * <p><b>State of the migration.</b> Handlers are added one at a time; {@link #handledNames()} is the list of the ones
 * that have moved, and the interpreter still owns the rest. The registry is deliberately not mutable at run time: a
 * handler set that could be added to after startup would let a test change what an op means.
 */
public final class OpRegistry {

    /**
     * Every handler that has been migrated, in no particular order.
     *
     * <p>One line per op on purpose: the diff that adds an op to this list is the diff that adds the op.
     */
    private static final List<OpHandler<?>> HANDLERS = List.of(
            new GainSkillPointOp(),
            new RaiseSkillPointCapOp(),
            new AdvanceOp(),
            new DelayOp(),
            new DepartOp(),
            new ReturnOp(),
            new ModifyAttrOp(),
            new ApplyBuffOp(),
            new RemoveStackOp(),
            new DispelOp(),
            new RemoveBuffOp(),
            new TickDotOp(),
            new SetStacksOp(),
            new RemoveStateOp(),
            new ApplyControlOp(),
            new ApplyDotOp(),
            new AddStackOp(),
            new TauntOp(),
            new ExtendBuffOp(),
            new ReplaceSkillOp(),
            new DismissSummonOp(),
            new BoostDotOp(),
            new ResistDebuffOp(),
            new ModifyRuleOp(),
            new RaiseSkillLevelOp(),
            new RaiseSkillHitsOp(),
            new StartCountdownOp(),
            new SpendStepsOp(),
            new ExtraTurnOp(),
            new InsertActionOp(),
            new IgnoreWeaknessOp(),
            new ResetResourceCapOp(),
            new ResetRuleChanceOp(),
            new ApplyRegenOp(),
            new SuperBreakOp(),
            new BoostToughnessOp(),
            new RaiseResourceCapOp(),
            new RaiseResourceMaxOp(),
            new RaiseEnergyBankOp(),
            new DrawEnergyBankOp(),
            new ModifyDamageTakenOp(),
            new BoostDamageOp(),
            new ShareDamageOp(),
            new DelegateDamageOp(),
            new RegisterHealBonusOp(),
            new AddDamageOp(),
            new CommandSummonOp(),
            new CastSkillOp(),
            new SummonServantOp());

    /** {@link #HANDLERS} indexed by op, built once. */
    private static final Map<TriggerOperator, OpHandler<?>> BY_OP = index();

    private OpRegistry() {
    }

    /**
     * The handler for an op, when it has been migrated.
     *
     * @param op the op
     * @return the handler, or {@code null} when the legacy switch still owns that op
     */
    static OpHandler<?> find(TriggerOperator op) {
        return BY_OP.get(op);
    }

    /**
     * The ops the registry performs, as names - the shape a guard compares against.
     *
     * @return an immutable, ordered set of op names
     */
    public static Set<String> handledNames() {
        return new TreeSet<>(BY_OP.keySet().stream().map(Enum::name).toList());
    }

    /**
     * Runs an op's load-time half: parse once, then refuse what could never work.
     *
     * <p>The parse result is returned so the caller can keep it - that is the whole point of parsing at load time.
     * An op that still lives in the legacy switch answers {@link OpArgs#NONE}; the caller stores it and the switch
     * never looks at it.
     *
     * @param effect the effect as written
     * @param rule   the rule it belongs to
     * @return the parsed shape, never {@code null}
     */
    static OpArgs parseAndValidate(EffectSpec effect, TriggerSpec rule) {
        TriggerOperator op = TriggerOperator.parse(TriggerInterpreter.normalizeOp(effect, rule));
        OpHandler<?> handler = op == null ? null : find(op);
        if (handler == null) {
            return OpArgs.NONE;
        }
        return validateWith(handler, effect, rule);
    }

    /**
     * Runs an op's run-time half.
     *
     * @param handler the handler
     * @param ctx     the battlefield, firing context and effect
     * @param args    the parsed shape from load time, or {@code null} to parse now (an amended copy of the effect -
     *                {@code MODIFY_RULE} rewrites a rule's own numbers, so the load-time shape no longer matches)
     */
    static void apply(OpHandler<?> handler, OpContext ctx, OpArgs args) {
        runApply(handler, ctx, args == null ? handler.parse(ctx.effect()) : args);
    }

    /**
     * The generic bridge: {@code OpHandler<?>} cannot call its own typed methods without a cast, and the cast is safe
     * because the args always came from the same handler's {@code parse}.
     *
     * @param handler the handler
     * @param effect  the effect
     * @param rule    the rule
     * @param <A>     the parsed shape
     * @return the parsed shape
     */
    private static <A extends OpArgs> OpArgs validateWith(OpHandler<A> handler, EffectSpec effect, TriggerSpec rule) {
        A args = handler.parse(effect);
        handler.validate(args, effect, rule);
        return args;
    }

    /**
     * The run-time half of the same bridge.
     *
     * @param handler the handler
     * @param ctx     the context
     * @param args    the parsed shape, from this handler's {@code parse}
     * @param <A>     the parsed shape
     */
    private static <A extends OpArgs> void runApply(OpHandler<A> handler, OpContext ctx, OpArgs args) {
        handler.apply(ctx, castTo(args));
    }

    /**
     * @param args any parsed shape
     * @param <A>  the shape the caller's handler expects
     * @return the same object
     */
    @SuppressWarnings("unchecked")
    private static <A extends OpArgs> A castTo(OpArgs args) {
        return (A) args;
    }

    /**
     * Builds the index, refusing two handlers for one op at class-initialisation time rather than letting the later
     * one win.
     *
     * @return op to handler
     */
    private static Map<TriggerOperator, OpHandler<?>> index() {
        Map<TriggerOperator, OpHandler<?>> byOp = new TreeMap<>();
        for (OpHandler<?> handler : HANDLERS) {
            OpHandler<?> previous = byOp.put(handler.op(), handler);
            if (previous != null) {
                throw new IllegalStateException("two handlers claim " + handler.op() + ": "
                        + previous.getClass().getSimpleName() + " and " + handler.getClass().getSimpleName()
                        + "; an op has one meaning");
            }
            if (!handler.op().isWired()) {
                throw new IllegalStateException(handler.op() + " has a handler but is not wired; a handler performs"
                        + " what the engine can do, so it cannot be registered for a planned op");
            }
        }
        return byOp;
    }
}
