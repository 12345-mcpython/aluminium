"""Two silences on the path this round's clause died on, closed (2026-10-02).

Measured, not guessed:
  * `Queue.grantExtraTurn` returns FALSE when the actor is not in the heap ("not in the queue (not yet entered / removed)"), and a memosprite at
    Speed 0 is skipped by `Queue.addCombatant` -- a fix shipped earlier this session. The `EXTRA_TURN` op dropped that return value on the floor,
    so the rule that granted it looked like it had worked: the judge read `extra turn actor = none` and nothing said why.
  * `castSkill` returns silently for an empty victim list, and `getOpponents` answers an EMPTY list for a unit with no camp -- so a commanded cast
    whose caster has no camp is another silent nothing.

Both are the same defect: an effect that does not happen and does not say so. An empty battlefield stays a legitimate no-op; an actor that cannot
hold an extra turn, or has no camp at all, is a rule pointing at a unit that cannot do what it says.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

OLD_TURN = '            case "EXTRA_TURN" -> battle.grantExtraTurn(resolveTarget(effect, ctx));'
NEW_TURN = '''            case "EXTRA_TURN" -> {
                // ⚠⚠ A refused grant used to vanish (2026-10-02): `grantExtraTurn` answers false for a unit that is not in the action
                // order -- and a memosprite at Speed 0 is exactly that -- so the rule looked like it had worked. Measured on 1415's ode of
                // genesis, whose clause grants 德谬歌 an extra turn: the judge read "extra turn actor = none" and nothing said why.
                CanHit holder = require(resolveTarget(effect, ctx), "target", ctx);
                if (!battle.grantExtraTurn(holder)) {
                    throw new IllegalStateException("EXTRA_TURN gives " + holder.getName()
                            + " an extra turn, but it is not in the action order (a memosprite at Speed 0 is skipped "
                            + "when the battle starts), so no extra turn can be granted");
                }
            }'''

OLD_CAST = '''        if (victims.isEmpty()) {
            return;                                  // nothing left to reach: an empty battlefield, not a bad rule
        }'''
NEW_CAST = '''        if (victims.isEmpty()) {
            // ⚠ An empty battlefield stays a no-op, but a caster with NO CAMP is a different thing: `getOpponents` answers an empty list
            // for it, so the commanded cast silently did nothing at all (2026-10-02). Say which one it is.
            if (actor.getCamp() == null) {
                throw new IllegalStateException("a commanded cast by " + actor.getName()
                        + " reaches nobody: the unit has no camp, so \\"its opponents\\" is an empty set rather than an empty battlefield");
            }
            return;                                  // nothing left to reach: an empty battlefield, not a bad rule
        }'''

for old, new, label in ((OLD_TURN, NEW_TURN, "the EXTRA_TURN guard"), (OLD_CAST, NEW_CAST, "the commanded cast guard")):
    n = txt.count(old)
    print("anchor %-26s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

for old, new in ((OLD_TURN, NEW_TURN), (OLD_CAST, NEW_CAST)):
    txt = txt.replace(old, new)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   both silences are closed")
