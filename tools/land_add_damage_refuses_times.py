"""`ADD_DAMAGE` does not take `times`, and must say so instead of ignoring it (2026-10-02).

Found while reading 1415's ode of passage, whose second sentence is 「缇宝施放追加攻击触发缇宝的结界的附加伤害时，会<b>额外造成 #1 次附加伤害</b>」. That
sentence counts INSTANCES, and the obvious-looking spelling would be `ADD_DAMAGE` with `times: 1` -- but `ADD_DAMAGE`'s worker is
`damage.addFlat(derivedMagnitude(...))`: it adds one amount to the damage being settled. A `times` there would be read by nobody, and the rule
would look right while delivering one flat addition instead of the instances the sentence asks for -- exactly the silence this project refuses.

So: refuse it at load time, with a message that says what the field would have meant.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

ANCHOR = """            case "ADD_DAMAGE" -> {
                requireEvent(spec, op, TriggerEvent.DEALING_DAMAGE);
                scaleAttribute(effect, op, spec);
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
            }"""
if txt.count(ANCHOR) != 1:
    sys.exit("REFUSING: the ADD_DAMAGE case does not match exactly once (%d)" % txt.count(ANCHOR))

NEW = """            case "ADD_DAMAGE" -> {
                requireEvent(spec, op, TriggerEvent.DEALING_DAMAGE);
                scaleAttribute(effect, op, spec);
                requirePercent(effect, op, spec);
                requireNoStackArguments(effect, op, spec);
                // \u26a0 A COUNT is not an amount (2026-10-02). 「额外造成 #1 次附加伤害」 counts INSTANCES, and this op adds one amount to the
                // damage being settled -- a `times` here would be read by nobody and the rule would look right while delivering one flat
                // addition. The sentence that led here is 1415's ode of passage; its second half is registered rather than approximated.
                if (effect.getTimes() != null || effect.getTimesFrom() != null) {
                    throw new IllegalArgumentException(
                            "Op " + op + " adds one amount to the damage being settled; it has no \\"times\\" / \\"times_from\\" -- those "
                                    + "count instances, and a count is not an amount (source: " + spec.getSource() + ")");
                }
            }"""

io.open(T, "w", encoding="utf-8", newline="\n").write(txt.replace(ANCHOR, NEW))
print("ok   ADD_DAMAGE refuses a count instead of ignoring it")
