"""Mutant: the listener is never asked (round 17)."""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/Battle.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_warehouse.py on|off")

LIVE = "            for (Character listener : warehouseListeners) {"
MUTATED = "            for (Character listener : java.util.List.<Character>of()) {"
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: %d occurrences" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the second pass asks nobody")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutant is not in place")
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the second pass asks the listeners")
