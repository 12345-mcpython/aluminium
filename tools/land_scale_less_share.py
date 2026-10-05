"""A magnitude that is a share from the cast skill and has NO scale, plus the share check (2026-10-02).

Both pieces were found last round by writing 1415's ode of romance, and both are narrow on purpose:

  * `derived` is NOT "has a scale" -- it is "(has a scale) OR (no percent)", and `derivedMagnitude` handles the flat-`amount` case through that
    second half. Changing the flag broke three shipped judges (50 -> 5500 on Asta, 12 -> 1164 and 12 -> 1212 on two cones), so the flag stays
    EXACTLY as it was and the new case is handled INSIDE `derivedMagnitude`, guarded so it cannot touch the flat-amount path.
  * the "exactly one of percent / amount" check read only `percent`; `percent_from_cast_param` is a second way to state the same share, so a
    rule that states it and no scale was refused as "neither".
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

# ---- 1) the share check (same edit as last round; it was right, the derived change was not) ----
CHECK = "                } else if ((effect.getPercent() == null) == (effect.getAmount() == null)) {"
CHECK_NEW = ("                // \u26a0 `percent_from_cast_param` is a SECOND way to state the share (2026-10-02): a share from the cast skill with\n"
             "                // no `scale` is exactly as well-formed as one with `percent`, and this check used to call it \"neither\".\n"
             "                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null)\n"
             "                        == (effect.getAmount() == null)) {")

# ---- 2) the early branch inside derivedMagnitude, guarded on the cast-skill share ----
HEAD = "    private static double derivedMagnitude(EffectSpec effect, TriggerContext ctx) {"
EARLY = '''        // \u2b50 A share from the cast skill, with NO scale (2026-10-02). This method is entered whenever `derived` is true, and `derived`
        // is "(has a scale) OR (no percent)" -- so the flat-`amount` case also arrives here, and this branch must not touch it. Reader:
        // 1415's ode of romance states `percent_from_cast_param` on two modifiers that have no scale at all.
        if ((effect.getScale() == null || effect.getScale().isBlank())
                && effect.getPercentFromCastParam() != null) {
            return shareOf(effect, ctx) + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
'''

for old, new, label in ((CHECK, CHECK_NEW, "the share check"), (HEAD, EARLY + HEAD, "the early branch")):
    n = txt.count(old)
    print("anchor %-20s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))
    txt = txt.replace(old, new)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   the share check and the early branch are in; `derived` is untouched")
