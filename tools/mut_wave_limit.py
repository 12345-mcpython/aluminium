"""Mutant: the per-wave cap is never consulted, so both casts in one wave fire (round 20 of the goal)."""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_wave_limit.py on|off")

LIVE = """            if (owner != null && Boolean.TRUE.equals(rule.getOncePerWave())
                    && !owner.isWaveLimitReady(limitKey, battle.waveSequence())) {
                continue;
            }"""
MUTATED = """            if (false) {
                continue;
            }"""
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: %d occurrences" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the wave cap is not consulted")
else:
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the mutant is not in place")
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: the wave cap is consulted")
