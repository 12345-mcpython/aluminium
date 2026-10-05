"""The "exactly one share" check must count `percent_from_cast_param` as a share (2026-10-02).

Found by writing 1415's ode of romance: two `MODIFY_ATTR`s stating `percent_from_cast_param` and no `scale` were refused with

    Op MODIFY_ATTR needs exactly one of "percent" (a share) or "amount" (a flat value) unless it states a scale; …

The check read `(percent == null) == (amount == null)`, which is right when a share can only be `percent`. `percent_from_cast_param` was shipped
later as a second way to state that share, and the DERIVED branch (which has a `scale`) skips this check -- which is why the field's own judge
never saw it. A rule with a share from the cast skill and no scale is exactly as well-formed as one with `percent`.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

ANCHOR = "                } else if ((effect.getPercent() == null) == (effect.getAmount() == null)) {"
REPLACEMENT = ("                // ⚠ `percent_from_cast_param` is a SECOND way to state the share (2026-10-02). The check used to read only\n"
               "                // `percent`, so a share from the cast skill with no `scale` was refused as \"neither\" -- and the field's own judge\n"
               "                // never saw it, because a DERIVED modifier (one with a `scale`) skips this branch entirely.\n"
               "                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null)\n"
               "                        == (effect.getAmount() == null)) {")

n = txt.count(ANCHOR)
print("anchor occurrences: %d" % n)
if n != 1:
    sys.exit("REFUSING: the check occurs %d times -- nothing written" % n)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt.replace(ANCHOR, REPLACEMENT))
print("ok   the share check counts the cast-skill share too")
