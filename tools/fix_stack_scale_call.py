import io, sys
P = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(P, encoding="utf-8").read()
old = "        Double fromStacks = stackScale(effect.getScale(), effect.getPercent(), ctx);"
if txt.count(old) != 1:
    sys.exit("REFUSING: the stackScale call occurs %d times" % txt.count(old))
new = ("        // ⚠⚠ `stackScale` takes a primitive, so handing it `effect.getPercent()` unboxes a null the moment a rule states\n"
       "        // its share as `percent_from_cast_param` instead. The share is what the counter multiplies, so ask for it the one way.\n"
       "        Double fromStacks = stackScale(effect.getScale(), shareOf(effect, ctx), ctx);")
io.open(P, "w", encoding="utf-8", newline="\n").write(txt.replace(old, new))
print("ok   the stackScale call takes the share")
