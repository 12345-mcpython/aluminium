import io, sys
P = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(P, encoding="utf-8").read()
old = """        double magnitude = effect.getPercent() != null
                ? (derived ? derivedMagnitude(effect, ctx) : effect.getPercent())
                : effect.getAmount();"""
if txt.count(old) != 1:
    sys.exit("REFUSING: the magnitude ternary occurs %d times" % txt.count(old))
new = """        // ⚠⚠ The share may come from the skill parameter now (2026-10-02), so "is a share stated" is NOT `percent != null`:
        // asking only that sent a `percent_from_cast_param` modifier down the flat `amount` arm and unboxed a null. A share is
        // `percent` OR `percent_from_cast_param`; the derived flag above already covers the latter.
        double magnitude;
        if (effect.getPercent() != null || effect.getPercentFromCastParam() != null) {
            magnitude = derived ? derivedMagnitude(effect, ctx) : effect.getPercent();
        } else {
            magnitude = effect.getAmount();
        }"""
io.open(P, "w", encoding="utf-8", newline="\n").write(txt.replace(old, new))
print("ok   the magnitude ternary no longer unboxes a null amount")
