"""An INSERTED ACTION: a unit outside the action order can act now (2026-10-02).

The wall this had to get past, all of it read rather than assumed:
  * `Signal`'s constructor refuses speed <= 0, and a memosprite's speed is pinned to 0 by the game's own `SpeedOverride` (measured last round);
  * `Queue.currentActor` is a `Signal`, and `Battle.currentMove` is one too -- so an actor outside the order has no place to stand;
  * `Battle.afterMove()` calls `queue.setTopZero()`, which re-times the TOP OF THE HEAP: for an actor that is not in the heap that would re-time
    somebody else. That is the act-bar scrambling `moveExtraTurn`'s own comment warns about.
And the fact that makes the narrow fix safe: `currentMove` is only ever read as `getCanHit()` (four sites, all in `Battle`), so a synthetic signal
never needs a schedule -- it only needs to name the unit.

So: a signal that exists to NAME an actor rather than to schedule one, an inserted actor the queue hands out before the heap, and an `afterMove`
that has nothing to re-time when the actor was never in the order.
"""
import io
import sys

SIG = "src/main/java/com/laosun/aluminium/models/Signal.java"
QUE = "src/main/java/com/laosun/aluminium/Queue.java"
BAT = "src/main/java/com/laosun/aluminium/Battle.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

FACTORY = '''
    /**
     * A signal that exists to NAME an actor, not to schedule one (2026-10-02; an inserted action).
     *
     * <p>\\u26a0 It is deliberately outside the normal constructor: a unit whose speed is 0 has no action value at all, which is exactly the case
     * an inserted action has to cover (the game pins a memosprite's speed to 0 with `SpeedOverride`). Such a signal must NEVER enter the heap --
     * nothing re-times it, and `Queue` only ever hands it back as the current actor.
     */
    public static Signal inserted(CanHit moveable) {
        Signal signal = new Signal();
        signal.canHit = moveable;
        return signal;
    }

    /** For {@link #inserted(CanHit)} only. */
    private Signal() {
    }
'''

QUEUE_FIELDS = '''
    /**
     * A unit that will act BEFORE the heap is consulted (2026-10-02; an inserted action -- the game's {@code TurnInsertAction}).
     *
     * <p>\\u2b50 Why a separate slot rather than a heap entry: the heap is a schedule, and a unit with no action value cannot have one. This is
     * "who acts next regardless of the clock", which is what an inserted action is, and it is what `move()` hands out first.
     */
    private Signal insertedSignal;

    /**
     * Schedules an inserted action: the unit acts at the next {@link #move()}, and the clock does NOT move for it.
     *
     * @return whether it was accepted; a dead unit, or one already waiting, is refused rather than silently queued twice
     */
    public boolean insertAction(CanHit actor) {
        if (actor == null || actor.isDeath() || insertedSignal != null) {
            return false;
        }
        insertedSignal = Signal.inserted(actor);
        return true;
    }

    /** Whether this unit has a place in the action order (as opposed to acting through an inserted action). */
    public boolean isInActionOrder(CanHit actor) {
        if (actor == null) {
            return false;
        }
        for (Signal s : heap) {
            if (s.getCanHit() == actor) {
                return true;
            }
        }
        return false;
    }
'''

MOVE_HEAD = '''        // \u2b50 An inserted action cuts in front of everything, and the clock does not move for it (2026-10-02) -- the same "the clock does not
        // move" the extra turn states, but WITHOUT needing a place in the heap, which is what a zero-speed unit cannot have.
        if (insertedSignal != null) {
            currentActor = insertedSignal;
            insertedSignal = null;
            return 0;
        }
'''

BATTLE_DELEGATE = '''    /**
     * Makes a unit act at the next step, outside the action order (2026-10-02; the game's {@code TurnInsertAction}).
     */
    public boolean insertAction(CanHit actor) {
        return queue.insertAction(actor);
    }

'''

AFTER_MOVE_OLD = '''        if (actor.isDeath()) {
            actor.getBuffManager().clearAll();
            queue.removeCombatant(actor);
        } else {
            queue.setTopZero();
        }'''
AFTER_MOVE_NEW = '''        if (actor.isDeath()) {
            actor.getBuffManager().clearAll();
            queue.removeCombatant(actor);
        } else if (queue.isInActionOrder(actor)) {
            // \u26a0 Only an actor that HAS a place in the order has a cycle to re-time. An inserted action carries a signal that was never
            // scheduled, and `setTopZero()` would re-time the top of the heap -- i.e. somebody else (2026-10-02).
            queue.setTopZero();
        }'''

OP_ANCHOR = '            case "EXTRA_TURN" -> {'
OP_BLOCK = '''            case "INSERT_ACTION" -> {
                // \u2b50 \u300c\u5fb7\u8c2c\u6b4c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u82b1\u4e0e\u7bad\u7684\u821e\u66f2\u3011\u300d(1415 memosprite skill 10): a unit outside the action
                // order acts now -- the game's `TurnInsertAction`. \u26a0 NOT `EXTRA_TURN`: that one cuts into a queue the unit is already in, and
                // a memosprite at Speed 0 is not in it at all (the game pins it there with `SpeedOverride`).
                CanHit acting = require(resolveTarget(effect, ctx), "target", ctx);
                if (!battle.insertAction(acting)) {
                    throw new IllegalStateException("INSERT_ACTION makes " + acting.getName()
                            + " act now, but it is dead or already waiting to act");
                }
            }
'''

EDITS = [
    (SIG, "    public Signal(CanHit moveable) {", FACTORY, "before", "the Signal factory"),
    (QUE, "    private CanHit extraTurnActor;", QUEUE_FIELDS, "after", "the queue slot"),
    (QUE, "        // P7-2: first handle \"the pending restore left over from the previous extra turn\",", MOVE_HEAD, "before", "the move head"),
    (BAT, "    public boolean grantExtraTurn(CanHit actor) {", BATTLE_DELEGATE, "before", "the battle delegate"),
    (BAT, AFTER_MOVE_OLD, AFTER_MOVE_NEW, "replace", "the afterMove guard"),
    (INT, OP_ANCHOR, OP_BLOCK, "before", "the op"),
]

# ---------- phase 1 ----------
texts = {}
for path in (SIG, QUE, BAT, INT):
    texts[path] = io.open(path, encoding="utf-8").read()
for path, anchor, _block, mode, label in EDITS:
    n = texts[path].count(anchor)
    print("anchor %-22s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

# ---------- phase 2 ----------
for path, anchor, block, mode, label in EDITS:
    txt = texts[path]
    i = txt.index(anchor)
    if mode == "replace":
        texts[path] = txt.replace(anchor, block)
    else:
        line_start = txt.rfind("\n", 0, i) + 1
        line_end = txt.index("\n", i) + 1 if mode == "after" else line_start
        texts[path] = txt[:line_end] + block + txt[line_end:]
    print("ok   %s" % label)
for path, txt in texts.items():
    io.open(path, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   all four files written")
