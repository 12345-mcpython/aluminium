"""Engine: `STATE_ENDED` now carries HOW MANY instances of that state were on the carrier (2026-10-02, item 59).

Why: 1505's 「开不败」 is 「队友持有的【好活当赏】结束时，绯英会将其中的 **50%** 转化为自身的【好活当赏】」. The event already carries the
state's NAME (so a reader can tell which state ended), but its amount was hard-coded 0 -- and "50% of it" has nowhere to
read the "it" from. The corpus says the state carries the count itself (「将本次阿哈时刻的笑点**计入该状态**」), which in this
engine is N stacked instances of the state, i.e. exactly what `BuffManager.stacksOf(name)` counts.

⚠ THE MAGNITUDE MUST BE READ BEFORE THE REMOVAL -- the state is gone by the time the event fires (that is why the name rides on
the event in the first place), so reading the count inside the firing would always give 0. Both call sites compute it at the
call, while the buffs are still there:

  * the spent-duration path fires for the one instance that expired, with the count as it stands (N);
  * the explicit-removal path sweeps every matching instance and fires per instance, so its FIRST firing carries the full
    total and the later ones one less each. ⚠ Stated, not hidden: a reader wants the first, and single-instance states (every
    reader that exists today) see exactly one firing with 1.
"""
import io
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"

# 1. the signature and the firing
B_OLD = """    public void fireStateEnded(CanHit carrier, String stateName) {
        String previous = lastStateEndedName;
        lastStateEndedName = stateName;
        try {
            fireTriggers(TriggerEvent.STATE_ENDED, carrier, carrier, 0, 0);
        } finally {
            lastStateEndedName = previous;
        }
    }"""
B_NEW = """    public void fireStateEnded(CanHit carrier, String stateName) {
        fireStateEnded(carrier, stateName, 0);
    }

    /**
     * ⭐ The same moment, carrying <b>how many instances of that state</b> the carrier held (2026-10-02; reader: 1505's
     * 「开不败」, 「队友持有的【好活当赏】结束时…将其中的 50% 转化为自身的【好活当赏】」).
     *
     * <p>⚠ The caller reads the count BEFORE removing the buffs: by the time this fires the state is already gone, which is
     * why the name itself rides on the event too.
     */
    public void fireStateEnded(CanHit carrier, String stateName, int magnitude) {
        String previous = lastStateEndedName;
        lastStateEndedName = stateName;
        try {
            fireTriggers(TriggerEvent.STATE_ENDED, carrier, carrier, magnitude, 0);
        } finally {
            lastStateEndedName = previous;
        }
    }"""

# 2. the two call sites: read the count while the buffs are still there
M_OLD_1 = "battle.fireStateEnded(instance, ended.getState());"
M_NEW_1 = "battle.fireStateEnded(instance, ended.getState(), instance.getBuffManager().stacksOf(ended.getState()));"
M_OLD_2 = "battle.fireStateEnded(instance, buff.getState());"
M_NEW_2 = "battle.fireStateEnded(instance, buff.getState(), instance.getBuffManager().stacksOf(buff.getState()));"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times (want %d)\n" % (label, found, count))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(BATTLE, B_OLD, B_NEW, "Battle.fireStateEnded: an overload carrying the magnitude")
patch(MANAGER, M_OLD_1, M_NEW_1, "BuffManager: the spent-duration path passes the count")
patch(MANAGER, M_OLD_2, M_NEW_2, "BuffManager: the explicit-removal path passes the count")
