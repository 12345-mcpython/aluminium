"""The two pieces the romance clause needs, landed together with it this time (2026-10-02).

  * the "exactly one of percent / amount" check must count `percent_from_cast_param` as a share -- otherwise a modifier whose share comes from the
    cast skill and which states no `scale` is refused as "neither";
  * `derivedMagnitude` needs an early branch for exactly that shape (a cast-skill share with NO scale). `derived` is deliberately NOT touched: it
    is "(has a scale) OR (no percent)" and its second half is what carries the flat-`amount` case -- changing it took three shipped judges down
    (50 -> 5500, 12 -> 1164, 12 -> 1212).
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

CHECK = "                } else if ((effect.getPercent() == null) == (effect.getAmount() == null)) {"
CHECK_NEW = ("                // \u26a0 `percent_from_cast_param` is a SECOND way to state the share (2026-10-02): a share from the cast skill with\n"
             "                // no `scale` is exactly as well-formed as one with `percent`, and this check used to call it \"neither\".\n"
             "                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null)\n"
             "                        == (effect.getAmount() == null)) {")

SIGNATURE = "    private static double derivedMagnitude(EffectSpec effect, TriggerContext ctx) {"
EARLY = '''        // \u2b50 A share from the cast skill with NO scale (2026-10-02). This method is entered whenever `derived` is true, and `derived`
        // is "(has a scale) OR (no percent)", so the flat-`amount` case arrives here too -- hence the guard: only the cast-skill share, and
        // only when there is no scale to read.
        if ((effect.getScale() == null || effect.getScale().isBlank())
                && effect.getPercentFromCastParam() != null) {
            return shareOf(effect, ctx) + (effect.getAmount() == null ? 0 : effect.getAmount());
        }
'''

for old, label in ((CHECK, "the share check"), (SIGNATURE, "the signature")):
    n = txt.count(old)
    print("anchor %-18s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

txt = txt.replace(CHECK, CHECK_NEW)
i = txt.index(SIGNATURE)
after = txt.index("\n", i) + 1
txt = txt[:after] + EARLY + txt[after:]          # ⚠ INSIDE the method, after its signature line
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   both pieces are in, and the early branch sits inside the method")
