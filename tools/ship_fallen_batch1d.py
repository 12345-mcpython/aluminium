"""Batch 1, part 3: the "a rule must state on or on_any" check gets its own reading (round 1694).

`src/test/resources/characters/` already holds the loader's own fixtures (9001-9004, documented in `CharacterResourceTest`), so
the new reading uses the same trick: a fixture whose rule states no event at all, and a test that the load fails with a message
naming what is missing. The mutator drops the check and the annotation in turn.
"""
import io
import os
import sys

FIXTURE = "src/test/resources/characters/9005.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/RuleKeyGuardTest.java"
MUTATOR = "tools/mut_rule_guard.py"

if os.path.exists(FIXTURE):
    sys.exit("REFUSING: the fixture is already there")
io.open(FIXTURE, "w", encoding="utf-8", newline="\n").write("""[
  {
    "id": "fixture_rule_without_an_event",
    "when": [
      "self has_state probe"
    ],
    "do": [
      {
        "op": "GAIN_RESOURCE",
        "resource": "probeSeed",
        "amount": 1,
        "target": "self"
      }
    ]
  }
]
""")
print("ok   the fixture rule states no event")

text = io.open(JUDGE, encoding="utf-8").read()
TEST = """
    /** And a rule that can never fire is refused AT LOAD, by name -- not left to fail later as 'Unknown trigger event'. */
    @Test
    public void aRuleWithoutAnEventIsRefused() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(9005),
                "the fixture's rule states no event, so the load must refuse it");
        System.out.println("[rule-keys] fixture: " + failure.getMessage());
        Assertions.assertTrue(String.valueOf(failure.getMessage()).contains("neither"),
                "the message says what is missing: " + failure.getMessage());
    }
}
"""
if "aRuleWithoutAnEventIsRefused" in text:
    sys.exit("REFUSING: the reading is already there")
text = text.rstrip()
if not text.endswith("}"):
    sys.exit("REFUSING: unexpected judge tail")
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text[:-1].rstrip() + "\n" + TEST)
print("ok   the reading is written")

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutations for the rule guard (round 1694).

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

tables = io.open(TABLES, encoding="utf-8").read()
LIVE_A = """            boolean hasEvent = object.has("on") || object.has("on_any");
            if (!hasEvent) {"""
MUT_A = """            boolean hasEvent = true;
            if (!hasEvent) {"""
spec = io.open(SPEC, encoding="utf-8").read()
LIVE_B = '    @SerializedName("on_any")\\n    private java.util.List<String> onAny;'
MUT_B = "    private java.util.List<String> onAny;"

if mode == "off":
    if tables.count(LIVE_A) != 1 or spec.count(LIVE_B) != 1:
        sys.exit("REFUSING: anchors not found (%d / %d)" % (tables.count(LIVE_A), spec.count(LIVE_B)))
    io.open(TABLES, "w", encoding="utf-8", newline="\\n").write(tables.replace(LIVE_A, MUT_A, 1))
    io.open(SPEC, "w", encoding="utf-8", newline="\\n").write(spec.replace(LIVE_B, MUT_B, 1))
    print("MUTATION A: the event check is unreachable; MUTATION B: on_any lost its spelling")
else:
    if tables.count(MUT_A) != 1 or spec.count(MUT_B) != 1:
        sys.exit("REFUSING: mutated anchors not found")
    io.open(TABLES, "w", encoding="utf-8", newline="\\n").write(tables.replace(MUT_A, LIVE_A, 1))
    io.open(SPEC, "w", encoding="utf-8", newline="\\n").write(spec.replace(MUT_B, LIVE_B, 1))
    print("restored A and B")
''')
print("ok   the mutator is written")
