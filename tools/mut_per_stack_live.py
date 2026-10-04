"""Toggle `per_stack_live` in the judge's effect: on = the live aura, off = the snapshot (the mutation).

Under `off` the aura keeps the 14% it was attached with, so the delta across four extra stacks is 0 where the judge wants
0.56.
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/PerStackLiveTest.java"
LIVE = '                "perStackLive", Boolean.TRUE,\n'
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        raise SystemExit("REFUSING: the line appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(LIVE, "", 1))
    print("MUTATION: the aura is a snapshot again")
elif mode == "on":
    ANCHOR = '                "perStack", "self_stacks:" + COUNTER,\n'
    if text.count(ANCHOR) != 1:
        raise SystemExit("REFUSING: the anchor appears %d times" % text.count(ANCHOR))
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(ANCHOR, ANCHOR + LIVE, 1))
    print("restored: the aura is live")
else:
    raise SystemExit("usage: mut_per_stack_live.py on|off")
