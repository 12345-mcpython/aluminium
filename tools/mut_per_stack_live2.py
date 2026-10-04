"""Corrected toggle: the two keys share one line in the judge, so the anchor has no newline in it.

off -> remove `"perStackLive", Boolean.TRUE, ` so the aura is attached as a snapshot
on  -> put it back
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/PerStackLiveTest.java"
FRAGMENT = '"perStackLive", Boolean.TRUE, '
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(FRAGMENT) != 1:
        raise SystemExit("REFUSING: the fragment appears %d times" % text.count(FRAGMENT))
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(FRAGMENT, "", 1))
    print("MUTATION: the aura is a snapshot again")
elif mode == "on":
    ANCHOR = '"perStack", "self_stacks:" + COUNTER, '
    if text.count(ANCHOR) != 1:
        raise SystemExit("REFUSING: the anchor appears %d times" % text.count(ANCHOR))
    io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(ANCHOR, ANCHOR + FRAGMENT, 1))
    print("restored: the aura is live")
else:
    raise SystemExit("usage: mut_per_stack_live.py on|off")
