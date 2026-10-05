import io
P = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
lines = io.open(P, encoding="utf-8").read().split("\n")
print("=== L795..L815 before the change ===")
for i in range(794, 815):
    print("%4d| %s" % (i + 1, lines[i][:116]))
old = "        double amount = effect.getAmount();"
if lines.count(old) != 1:
    raise SystemExit("REFUSING: the unguarded unbox occurs %d times" % lines.count(old))
i = lines.index(old)
lines[i] = ("        // \u26a0 Guarded (2026-10-02): with `percent_from_cast_param` a derived modifier may state NO `percent` and NO `amount`,\n"
            "        // and this unboxed read turned that into a NullPointerException instead of a magnitude.\n"
            "        double amount = effect.getAmount() == null ? 0 : effect.getAmount();")
io.open(P, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the unbox is guarded now")
