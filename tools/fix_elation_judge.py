"""Judge-only fix: `EffectSpec.amount` is a Double, so reflection will not take an Integer.

`TriggerSpecs.set` fills the bean by reflection, so `1` (an Integer) is refused with
"Can not set java.lang.Double field ... to java.lang.Integer". The JSON path accepts `1`; the reflective path must pass 1.0.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/ElationRowTest.java"
OLD = 'TriggerSpecs.set(counter, "amount", 1);'
NEW = 'TriggerSpecs.set(counter, "amount", 1.0);'

text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   judge fixed: amount is passed as a Double")
