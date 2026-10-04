"""Make the cost reading able to tell the two shares apart, and write the mutants (round 5 of the goal).

Weakness found by inspection: at full HP, "35% of current" and "35% of maximum" are the SAME number, so the reading as written
cannot fail for the old share -- it would pass whether the new one works or not. Wounding him first separates them: the
current-based spend is smaller, and an assertion that the spend is NOT the max-based one makes the old share fail.
"""
import io
import sys

JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
MUTATOR = "tools/mut_1404_cost.py"

text = io.open(JUDGE, encoding="utf-8").read()
OLD = """        double hpBefore = him.getCurrentHp();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);"""
NEW = """        // \\u26a0 Wound him first. At full HP "35% of CURRENT" and "35% of MAXIMUM" are the same number, so a reading taken there
        // would pass for either share -- the first version of this test did exactly that. Half health makes them differ.
        battle.applyTrueDamage(battle.enemies.getFirst(), him, DamageElement.ICE, him.getCurrentHp() * 0.5);
        battle.processRequests();
        Assertions.assertTrue(him.getCurrentHp() < him.getMaxHp(), "precondition: he is wounded");

        double hpBefore = him.getCurrentHp();
        double maxHp = him.getMaxHp();
        double enemyBefore = battle.enemies.getFirst().getCurrentHp();
        spendTurnOf(battle, him);"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
text = text.replace(OLD, NEW, 1)

OLD2 = """        Assertions.assertEquals(hpBefore * 0.65, hpAfter, hpBefore * 1e-6,
                "\\u300c\\u6d88\\u8017\\u7b49\\u540c\\u4e8e\\u4e07\\u654c\\u5f53\\u524d\\u751f\\u547d\\u503c 35% \\u7684\\u751f\\u547d\\u503c\\u300d-- and it is the CURRENT value, not the maximum");"""
NEW2 = """        Assertions.assertEquals(hpBefore * 0.65, hpAfter, hpBefore * 1e-6,
                "\\u300c\\u6d88\\u8017\\u7b49\\u540c\\u4e8e\\u4e07\\u654c\\u5f53\\u524d\\u751f\\u547d\\u503c 35% \\u7684\\u751f\\u547d\\u503c\\u300d-- the CURRENT value");
        Assertions.assertTrue(hpAfter > hpBefore - 0.35 * maxHp,
                "and NOT a share of the maximum (" + (hpBefore - 0.35 * maxHp) + " would be the wrong number, "
                        + hpAfter + " is the right one)");"""
if text.count(OLD2) != 1:
    sys.exit("REFUSING: the assertion anchor appears %d times" % text.count(OLD2))
text = text.replace(OLD2, NEW2, 1)
text = text.replace("import com.laosun.aluminium.enums.SkillType;",
                    "import com.laosun.aluminium.enums.DamageElement;\nimport com.laosun.aluminium.enums.SkillType;", 1)
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   the cost reading wounds him first, so the two shares differ")

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutants for the cost reading (round 5 of the goal).

  a: the CONSUME_HP effect is dropped            -> nothing is paid, so the equality fails;
  b: the share becomes `owner_max_hp` (35% of MAX) -> the spend is too big, so the "NOT a share of the maximum" half fails.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "off", "on"):
    sys.exit("usage: mut_1404_cost.py a|b|off|on")

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
cost = [effect for effect in rule["do"] if effect.get("op") == "CONSUME_HP"]

if mode == "on":
    if not cost:
        rule["do"].insert(1, {"op": "CONSUME_HP", "scale": "target_current_hp", "percent": 0.35, "target": "self"})
    else:
        cost[0]["scale"] = "target_current_hp"
    print("restored: the cost is back and reads the current value")
else:
    if not cost or cost[0].get("scale") != "target_current_hp":
        sys.exit("REFUSING: the cost is not in its shipped shape")
    if mode in ("a", "off"):
        rule["do"] = [effect for effect in rule["do"] if effect.get("op") != "CONSUME_HP"]
    if mode in ("b", "off"):
        cost[0]["scale"] = "owner_max_hp"
    print("MUTATION %s" % mode)

with io.open(CHAR, "w", encoding="utf-8", newline="\\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\\n")
''')
print("ok   the mutator is written")
