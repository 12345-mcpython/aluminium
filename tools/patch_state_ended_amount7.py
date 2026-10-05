"""`STATE_ENDED` carries the magnitude -- in the AMOUNT slot (2026-10-02, round 1681).

Round 1680's patch put the number in the wrong argument. Measured signature:

    fireTriggers(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount)

so `fireTriggers(STATE_ENDED, carrier, carrier, magnitude, 0)` set the HIT COUNT and left `ctx.amount()` at zero -- which is
exactly why all three discriminators last round read 20: the rule fired (the constant proved that) and the amount spelling
(`gainResource`: `ctx.amount() * amountPercent`) had nothing to multiply.

This script does the whole job in one place: the overload, the once-per-sweep count in `removeState`, the removal of the
in-loop announcement, and a read-back of the result -- with the arguments the right way round.
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


patch(
    BATTLE,
    """    public void fireStateEnded(CanHit carrier, String stateName) {
        String previous = lastStateEndedName;
        lastStateEndedName = stateName;
        try {
            fireTriggers(TriggerEvent.STATE_ENDED, carrier, carrier, 0, 0);
        } finally {
            lastStateEndedName = previous;
        }
    }""",
    """    public void fireStateEnded(CanHit carrier, String stateName) {
        fireStateEnded(carrier, stateName, 0);
    }

    /**
     * ⭐ The same moment, carrying <b>how many instances of that state</b> the carrier held. ⚠ The number goes in the AMOUNT
     * slot, not the hit count: measured, `fireTriggers(event, actor, target, int hitCount, double amount)`, and putting it one
     * place early left `ctx.amount()` at zero -- which is what 「将其中的 50%」 reads.
     */
    public void fireStateEnded(CanHit carrier, String stateName, int magnitude) {
        String previous = lastStateEndedName;
        lastStateEndedName = stateName;
        try {
            fireTriggers(TriggerEvent.STATE_ENDED, carrier, carrier, 0, magnitude);
        } finally {
            lastStateEndedName = previous;
        }
    }""",
    "Battle.fireStateEnded: the magnitude in the amount slot",
)

patch(
    MANAGER,
    """        String wanted = state.trim();
        int removed = 0;""",
    """        String wanted = state.trim();
        int removed = 0;
        // ⭐ ONE announcement per sweep, carrying the total, so a reader that converts a share of \"it\" sees the total once.
        int ending = 0;
        for (AbstractBuff carried : List.copyOf(buffs)) {
            if (carried instanceof StateBuff buff && wanted.equals(buff.getState())) {
                ending++;
            }
        }
        if (ending > 0 && battle != null) {
            battle.fireStateEnded(instance, wanted, ending);
        }""",
    "removeState: count first, fire once with the total",
)

text = io.open(MANAGER, encoding="utf-8").read()
BLOCK = re.compile(
    r"[ \t]*if \(battle != null\) \{\s*\n"
    r"[ \t]*battle\.fireStateEnded\(instance, buff\.getState\(\)[^;]*\);\s*\n"
    r"[ \t]*\}\n")
text, count = BLOCK.subn("", text)
if count != 1:
    sys.stderr.write("REFUSING: the in-loop block matched %d times\n" % count)
    raise SystemExit(1)
io.open(MANAGER, "w", encoding="utf-8", newline="\n").write(text)
print("ok   the in-loop announcement is gone")

lines = io.open(MANAGER, encoding="utf-8").read().split("\n")
start = next(index for index, line in enumerate(lines) if "public int removeState" in line)
io.open("tools/_tmp_readback.txt", "w", encoding="utf-8", newline="\n").write(
    "\n".join("%d: %s" % (index + 1, lines[index].strip()[:120])
              for index in range(start, min(start + 30, len(lines)))))
print("read-back written to tools/_tmp_readback.txt")
