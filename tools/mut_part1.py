"""Mutations for part 1's four assertions (round 1693).

  A: 1513's reward loses the spending effect      -> the laughs stay 4 instead of 0
  B: 1408's seed grant 3 becomes 2                -> the seeds read 2 short
  C: 1408's end-of-transformation speed 15% -> 10% -> both units' deltas shrink

All three at once: the three readings live in two suites, and each mutant must turn its own assertion red.
"""
import io
import json
import sys

CHAR1513 = "src/main/resources/characters/1513.json"
CHAR1408 = "src/main/resources/characters/1408.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("on", "off"):
    sys.exit("usage: mut_part1.py on|off")

# A
with io.open(CHAR1513, encoding="utf-8") as handle:
    doc = json.load(handle)
reward = next(rule for rule in doc["rules"]
              if isinstance(rule, dict) and rule.get("id") == "elation_moment_reward")
spends = [effect for effect in reward["do"] if effect.get("op") == "SPEND_RESOURCE"]
if mode == "off":
    if len(spends) != 1:
        sys.exit("REFUSING: expected one spending effect, found %d" % len(spends))
    reward["do"] = [effect for effect in reward["do"] if effect.get("op") != "SPEND_RESOURCE"]
    print("MUTATION A: the spending effect is gone")
else:
    if spends:
        sys.exit("REFUSING: the spending effect is still there")
    reward["do"].append({"op": "SPEND_RESOURCE", "resource": "笑点", "spendAll": True, "target": "self"})
    print("restored A")
with io.open(CHAR1513, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")

# B and C
with io.open(CHAR1408, encoding="utf-8") as handle:
    doc = json.load(handle)
seeds = next(rule for rule in doc["rules"]
             if isinstance(rule, dict) and rule.get("id") == "trace_worlds_end_three_seeds")
speed = next(rule for rule in doc["rules"]
             if isinstance(rule, dict) and rule.get("id") == "transformation_end_speeds_the_party")
if mode == "off":
    if seeds["do"][0].get("amount") != 3 or speed["do"][0].get("percent") != 0.15:
        sys.exit("REFUSING: unexpected values %r / %r" % (seeds["do"][0].get("amount"), speed["do"][0].get("percent")))
    seeds["do"][0]["amount"] = 2
    speed["do"][0]["percent"] = 0.1
    print("MUTATION B+C: seeds 3 -> 2, speed 15% -> 10%")
else:
    if seeds["do"][0].get("amount") != 2 or speed["do"][0].get("percent") != 0.1:
        sys.exit("REFUSING: the mutants are not in place")
    seeds["do"][0]["amount"] = 3
    speed["do"][0]["percent"] = 0.15
    print("restored B+C")
with io.open(CHAR1408, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
