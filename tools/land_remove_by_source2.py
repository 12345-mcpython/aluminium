"""Retry with whitespace-tolerant anchors (the literal ones did not match the file's indentation).

Same two changes as `land_remove_by_source.py`; only the way the interpreter's two spots are located differs. Phase 1 still validates every
anchor before anything is written.
"""
import io
import re
import sys

MGR = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

mgr = io.open(MGR, encoding="utf-8").read()
interp = io.open(INT, encoding="utf-8").read()

REMOVE_ANCHOR = "    public int removeState(String state) {"
NEW_METHOD = '''    /**
     * Takes off only the buffs <b>this source</b> applied, named by a state or by the attribute a modifier sits on (2026-10-02).
     *
     * <p>The source filter is the whole point, and it is not new: `extendBuffsFrom` has filtered by origin since it was written. Reader:
     * 1415's ode of romance -- the lifetime is 「持续至阿格莱雅退出【至高之姿】状态」 -- where removing by NAME alone also took a pre-existing
     * `ALL_DAMAGE_TYPE_BOOST` of hers with it (measured: both units went to 0.0 although both held 0.2 before the ode).
     */
    public int removeStateFrom(CanHit source, String stateName, AttributeType attribute) {
        if (source == null || (stateName == null && attribute == null)) {
            return 0;
        }
        int ending = 0;
        for (AbstractBuff carried : List.copyOf(buffs)) {
            if (carried.getSource() == source && isNamed(carried, stateName, attribute)) {
                ending++;
            }
        }
        if (ending > 0 && battle != null && stateName != null) {
            battle.fireStateEnded(instance, stateName, ending);
        }
        int removed = 0;
        for (AbstractBuff carried : List.copyOf(buffs)) {
            if (carried.getSource() == source && isNamed(carried, stateName, attribute)) {
                removeBuff(carried);
                removed++;
            }
        }
        return removed;
    }

'''

# the validation block, located by its three calls in order (any indentation)
VALIDATE_RE = re.compile(
    r"(?P<ind>[ \t]*)requireBuff\(effect, op, spec\);\n"
    r"(?P=ind)requireNoDuration\(effect, op, spec\);\n"
    r"(?P=ind)requireNoStackArguments\(effect, op, spec\);")
WORKER_RE = re.compile(
    r"(?P<ind>[ \t]*)private static void removeState\(EffectSpec effect, TriggerContext ctx\) \{.*?\n(?P=ind)\}",
    re.S)

for body, pattern, label in ((mgr, REMOVE_ANCHOR, "the manager anchor"),
                             (interp, VALIDATE_RE, "the REMOVE_STATE validation"),
                             (interp, WORKER_RE, "the removeState worker")):
    n = body.count(pattern) if isinstance(pattern, str) else len(pattern.findall(body))
    print("anchor %-30s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s matched %d times -- nothing written" % (label, n))

mgr = mgr.replace(REMOVE_ANCHOR, NEW_METHOD + REMOVE_ANCHOR)
interp = VALIDATE_RE.sub(lambda m: (
    m.group("ind") + "// `buff` (a name) OR `attribute` (what a modifier modifies), and optionally `\"kind\": \"own\"` for\n"
    + m.group("ind") + "// \"only what THIS rule's owner applied\" (2026-10-02) -- the origin filter `EXTEND_BUFF` has always had.\n"
    + m.group("ind") + "if (effect.getAttribute() != null && !effect.getAttribute().isBlank()) {\n"
    + m.group("ind") + "    requireAttribute(effect, op, spec);\n"
    + m.group("ind") + "} else {\n"
    + m.group("ind") + "    requireBuff(effect, op, spec);\n"
    + m.group("ind") + "}\n"
    + m.group("ind") + "if (effect.getKind() != null && !effect.getKind().isBlank()\n"
    + m.group("ind") + "        && !\"own\".equalsIgnoreCase(effect.getKind().trim())) {\n"
    + m.group("ind") + "    throw new IllegalArgumentException(\n"
    + m.group("ind") + "            \"Op \" + op + \" has \\\"kind\\\": \\\"\" + effect.getKind()\n"
    + m.group("ind") + "                    + \"\\\"; the only kind it knows is \\\"own\\\" (only what this rule's owner applied)\"\n"
    + m.group("ind") + "                    + \" (source: \" + spec.getSource() + \")\");\n"
    + m.group("ind") + "}\n"
    + m.group("ind") + "requireNoDuration(effect, op, spec);\n"
    + m.group("ind") + "requireNoStackArguments(effect, op, spec);"), interp, count=1)

NEW_WORKER = '''    private static void removeState(EffectSpec effect, TriggerContext ctx) {
        AttributeType byAttribute = effect.getAttribute() == null || effect.getAttribute().isBlank()
                ? null
                : AttributeType.fromString(effect.getAttribute().trim());
        boolean own = effect.getKind() != null && "own".equalsIgnoreCase(effect.getKind().trim());
        for (CanHit target : resolveTargets(ctx.battle(), effect, ctx)) {
            if (own) {
                target.getBuffManager().removeStateFrom(ctx.owner(), effect.getBuff(), byAttribute);
            } else if (byAttribute == null) {
                target.getBuffManager().removeState(effect.getBuff());
            } else {
                target.getBuffManager().removeState(byAttribute);
            }
        }
    }'''
interp = WORKER_RE.sub(lambda m: NEW_WORKER, interp, count=1)

io.open(MGR, "w", encoding="utf-8", newline="\n").write(mgr)
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp)
print("ok   written")
