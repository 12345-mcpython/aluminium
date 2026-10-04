"""A stackable state can be applied N times at once (2026-10-02, item 70 -- the last piece of the bridge).

Reader: 1513's `elation_moment_reward`, which must hand 【好活当赏】 the 【笑点】 the Aha moment spent. The corpus says the state
carries that count (「将本次阿哈时刻的笑点**计入该状态**」), and 1505's clause reads the count when the state ends -- so the
number of INSTANCES is the count. Measured before: `APPLY_BUFF{stackable: true}` builds exactly one instance, and `ADD_STACK`
(which does take a count) makes a `StackBuff`, not a state, so its expiry never announces STATE_ENDED.

Consumption points of this spelling, listed before writing anything (last round's discipline):
  * `applyState` -- where the instances are built and attached (this patch);
  * validation -- `APPLY_BUFF` may refuse `amount`/`scale`; the judge will say so at load time if it does.

`times` comes from `amount` (a literal count) or from a `party_resource:` scale (item 69), whichever the rule states, and a
non-stackable state is untouched: repeating it would just refresh it, so the loop only runs more than once for a stackable
one.
"""
import io
import sys

INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
OLD = """        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            AbstractBuff buff = withSource(withTickOwner(
                    withLifetime(Boolean.TRUE.equals(effect.getStackable())
                        ? new StackableStateBuff(state, turns, permanent,
                                effect.getMaxStacks() == null ? 1 : effect.getMaxStacks())
                        : Boolean.TRUE.equals(effect.getDefersDeath())
                        ? new DeferredDeathBuff(state, turns, permanent)
                        : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);
            // \u300c\u4e0d\u4f1a\u8fdb\u5165\u81ea\u5df1\u7684\u56de\u5408\u300d rides on the state itself: a turn is not something a state could give back
            // later, so the flag and the state share one lifetime by construction.
            if (Boolean.TRUE.equals(effect.getSuspendsTurns())) {
                buff.setSuspendsTurns(true);
            }
            // \u300c\u6709 100% \u7684\u57fa\u7840\u6982\u7387\u4f7f\u654c\u65b9\u2026\u9677\u5165\u3010\u901a\u89e3\u3011\u72b6\u6001\u300d: when the rule states one, the state is ROLLED (effect resistance
            // included); when it states none, this is the plain attach it has always been -- so no existing file changes.
            attachRolled(battle, target, buff, effect, ctx);
        }
    }"""
NEW = """        // \u2b50 How many instances this application carries (2026-10-02; reader: 1513's reward, whose state's INSTANCE COUNT is
        // the \u3010\u7b26\u70b9\u3011 it spent). A plain state refreshes on re-application, so only a STACKABLE one is repeated --
        // otherwise the loop would be a no-op dressed as a feature.
        int times = 1;
        if (Boolean.TRUE.equals(effect.getStackable())) {
            if (effect.getScale() != null && effect.getScale().trim().startsWith("party_resource:")) {
                times = Math.max(1, (int) Math.round(resolveScale(effect.getScale(),
                        effect.getPercent() == null ? 1 : effect.getPercent(), ctx)));
            } else if (effect.getAmount() != null) {
                times = Math.max(1, (int) Math.round(effect.getAmount()));
            }
        }
        for (CanHit target : resolveTargets(battle, effect, ctx)) {
            for (int instance = 0; instance < times; instance++) {
                AbstractBuff buff = withSource(withTickOwner(
                        withLifetime(Boolean.TRUE.equals(effect.getStackable())
                            ? new StackableStateBuff(state, turns, permanent,
                                    effect.getMaxStacks() == null ? 1 : effect.getMaxStacks())
                            : Boolean.TRUE.equals(effect.getDefersDeath())
                            ? new DeferredDeathBuff(state, turns, permanent)
                            : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);
                // \u300c\u4e0d\u4f1a\u8fdb\u5165\u81ea\u5df1\u7684\u56de\u5408\u300d rides on the state itself: a turn is not something a state could give back
                // later, so the flag and the state share one lifetime by construction.
                if (Boolean.TRUE.equals(effect.getSuspendsTurns())) {
                    buff.setSuspendsTurns(true);
                }
                // \u300c\u6709 100% \u7684\u57fa\u7840\u6982\u7387\u4f7f\u654c\u65b9\u2026\u9677\u5165\u3010\u901a\u89e3\u3011\u72b6\u6001\u300d: when the rule states one, the state is ROLLED (effect resistance
                // included); when it states none, this is the plain attach it has always been -- so no existing file changes.
                attachRolled(battle, target, buff, effect, ctx);
            }
        }
    }"""

text = io.open(INTERP, encoding="utf-8").read()
if text.count(OLD) != 1:
    sys.stderr.write("REFUSING: the applyState block appears %d times\n" % text.count(OLD))
    raise SystemExit(1)
io.open(INTERP, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW, 1))
print("ok   applyState repeats a stackable state as many times as the rule states")
