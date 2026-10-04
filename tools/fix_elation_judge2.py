"""Judge-only fix 2: `TriggerSpecs.set` takes JAVA field names, so it is `maxStacks`, not `max_stacks`.

Measured: "cannot set max_stacks on class com.laosun.aluminium.beans.EffectSpec" -- the same rule the project already
recorded for the reflection helper (it fills fields, not JSON keys).
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/ElationRowTest.java"
OLD = 'TriggerSpecs.set(counter, "max_stacks", 99);'
NEW = 'TriggerSpecs.set(counter, "maxStacks", 99);'

text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   judge fixed: maxStacks is the Java field name")
