"""Precondition ② of ①-a, written to be READ BACK afterwards (2026-10-02, round 1680).

Two edits on the current tree:
  1. `fireStateEnded(carrier, name, magnitude)` -- the overload the ready-made patch adds;
  2. `removeState` counts the matching instances FIRST, fires ONCE with that total, then removes them.

\u26a0 How this differs from the round that broke: the in-loop firing is removed by matching the exact three-line block, and
there is NO "tidy up empty guards" regex here -- that is what silently deleted the inserted block last time, with a green
compile. The script prints both regions at the end so the code can be read rather than trusted.
"""
import io
import re
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"


def patch(path, old, new, label, count=1):
    text = io.open(path, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


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
     * \u2b50 The same moment, carrying <b>how many instances of that state</b> the carrier held (2026-10-02; reader: 1505's
     * \u300c\u5f00\u4e0d\u8d25\u300d). \u26a0 The caller reads the count BEFORE removing the buffs: by the time this fires the state is
     * already gone, which is why the name itself rides on the event too.
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
patch(BATTLE, B_OLD, B_NEW, "Battle.fireStateEnded: an overload carrying the magnitude")

M_OLD = """        String wanted = state.trim();
        int removed = 0;"""
M_NEW = """        String wanted = state.trim();
        int removed = 0;
        // \u2b50 ONE announcement per sweep, carrying the total (2026-10-02; reader: 1505's \u300c\u5f00\u4e0d\u8d25\u300d). A sweep can take
        // several instances of one state, and the sentence turns a share of \"it\" into something -- so it must see the total
        // once, not three shrinking numbers.
        int ending = 0;
        for (AbstractBuff carried : List.copyOf(buffs)) {
            if (carried instanceof StateBuff buff && wanted.equals(buff.getState())) {
                ending++;
            }
        }
        if (ending > 0 && battle != null) {
            battle.fireStateEnded(instance, wanted, ending);
        }"""
patch(MANAGER, M_OLD, M_NEW, "removeState: count first, fire once with the total")

IN_LOOP = re.compile(
    r"[ \t]*if \(battle != null\) \{\s*\n"
    r"[ \t]*battle\.fireStateEnded\(instance, buff\.getState\(\), "
    r"instance\.getBuffManager\(\)\.stacksOf\(buff\.getState\(\)\)\);\s*\n"
    r"[ \t]*\}\n")
text = io.open(MANAGER, encoding="utf-8").read()
text, count = IN_LOOP.subn("", text)
if count != 1:
    sys.stderr.write("REFUSING: the in-loop block matched %d times\n" % count)
    raise SystemExit(1)
io.open(MANAGER, "w", encoding="utf-8", newline="\n").write(text)
print("ok   the in-loop announcement is gone (exact block, no tidy-up regex)")

# ---- read it back, so the code is looked at rather than trusted
manager = io.open(MANAGER, encoding="utf-8").read().split("\n")
start = next(index for index, line in enumerate(manager) if "public int removeState" in line)
print("\n--- removeState, read back ---")
for index in range(start, min(start + 34, len(manager))):
    print("%d: %s" % (index + 1, manager[index].strip()[:118]))
