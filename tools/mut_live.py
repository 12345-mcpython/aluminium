"""Toggle the live-modifier behaviour, so the mutation and the restore use one script.

  python tools/mut_live.py on   -> the live kind reads its supplier (correct)
  python tools/mut_live.py off  -> it falls back to the stored number (the mutation)

Expected under `off`: the live modifier no longer follows the count, so `LiveModifierTest.aLiveModifierFollowsTheCount`
reads 100 where it expects 114.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/DoubleValue.java"
LIVE = "            return live != null ? live.getAsDouble() : value;"
MUTATED = "            return value;"

mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        raise SystemExit("REFUSING: the live line appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: rate() ignores the supplier")
elif mode == "on":
    if text.count(MUTATED) != 1:
        raise SystemExit("REFUSING: the mutated line appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: rate() asks the supplier again")
else:
    raise SystemExit("usage: mut_live.py on|off")
