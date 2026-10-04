"""Mutation: drop the annotation, so the key becomes the field name and `max_stacks` stops landing."""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
LIVE = '    @SerializedName("max_stacks")\n    private Integer maxStacks;'
MUTATED = "    private Integer maxStacks;"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
text = io.open(PATH, encoding="utf-8").read()
if mode == "off":
    if text.count(LIVE) != 1:
        sys.exit("REFUSING: the annotated field appears %d times" % text.count(LIVE))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(LIVE, MUTATED, 1))
    print("MUTATION: the annotation is gone")
elif mode == "on":
    if text.count(MUTATED) != 1:
        sys.exit("REFUSING: the bare field appears %d times" % text.count(MUTATED))
    io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(MUTATED, LIVE, 1))
    print("restored: max_stacks is the key again")
else:
    sys.exit("usage: mut_effect_key.py on|off")
