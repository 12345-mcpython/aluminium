"""Mutant: the data stops stating the per-wave cap, so both casts in one wave fire (round 22 of the goal).

It mutates the DATA SPELLING the judge feeds through Gson, so a green run proves the key `once_per_wave` carries the behaviour.
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/WaveLimitCapTest.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_wave_limit_cap.py on|off")

LIVE = '"once_per_wave":true'
MUTATED = '"once_per_wave":false'
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: %d occurrences" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the data drops the per-wave cap")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutant is not in place")
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the data states the per-wave cap")
