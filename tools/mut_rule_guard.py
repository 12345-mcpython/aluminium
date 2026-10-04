"""Mutations for the rule guard (round 1694).

  A: the "neither on nor on_any" check becomes unreachable -> the fixture loads, so `aRuleWithoutAnEventIsRefused` fails;
  B: `@SerializedName("on_any")` is dropped -> the Gson reading fails AND 1205.json (the file that writes `on_any`) stops loading.
"""
import io
import sys

TABLES = "src/main/java/com/laosun/aluminium/data/TriggerTables.java"
SPEC = "src/main/java/com/laosun/aluminium/beans/TriggerSpec.java"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_rule_guard.py on|off")

LIVE_A = """            boolean hasEvent = object.has("on") || object.has("on_any");
            if (!hasEvent) {"""
MUT_A = """            boolean hasEvent = true;
            if (!hasEvent) {"""
LIVE_B = '    @SerializedName("on_any")\n    private java.util.List<String> onAny;'
MUT_B = "    private java.util.List<String> onAny;"

tables = io.open(TABLES, encoding="utf-8").read()
spec = io.open(SPEC, encoding="utf-8").read()
if mode == "off":
    if tables.count(LIVE_A) != 1 or spec.count(LIVE_B) != 1:
        sys.exit("REFUSING: anchors not found (%d / %d)" % (tables.count(LIVE_A), spec.count(LIVE_B)))
    io.open(TABLES, "w", encoding="utf-8", newline="\n").write(tables.replace(LIVE_A, MUT_A, 1))
    io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec.replace(LIVE_B, MUT_B, 1))
    print("MUTATION A: the event check is unreachable; MUTATION B: on_any lost its spelling")
else:
    if tables.count(MUT_A) != 1 or spec.count(MUT_B) != 1:
        sys.exit("REFUSING: mutated anchors not found")
    io.open(TABLES, "w", encoding="utf-8", newline="\n").write(tables.replace(MUT_A, LIVE_A, 1))
    io.open(SPEC, "w", encoding="utf-8", newline="\n").write(spec.replace(MUT_B, LIVE_B, 1))
    print("restored A and B")
