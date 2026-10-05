"""Mutations for 1217's clock reading, one at a time so each can be attributed.

  a: `turns: 3`               -- the reading about "two of her turns spend it";
  b: `ticks_on: "self"` gone  -- whether the field is load-bearing or states what the default already does;
  off: both; on: restore.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1217.json"
TALISMAN = "禳命"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "c", "off", "on"):
    sys.exit("usage: mut_huohuo_clock.py a|b|off|on")


def load():
    with io.open(CHAR, encoding="utf-8") as handle:
        return json.load(handle)


def save(doc):
    with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def effect_of(doc):
    for rule in doc:
        for effect in rule.get("do", []):
            if effect.get("buff") == TALISMAN:
                return effect
    sys.exit("REFUSING: no rule grants the state")


def restore(effect):
    effect["turns"] = 2
    target = effect.pop("target", "self")
    effect["ticks_on"] = "self"
    effect["target"] = target


doc = load()
effect = effect_of(doc)
if mode == "on":
    restore(effect)
    save(doc)
    print("restored: turns 2, ticks_on self")
    raise SystemExit(0)

restore(effect)
if mode in ("a", "off"):
    effect["turns"] = 3
if mode in ("b", "off"):
    effect.pop("ticks_on")
if mode == "c":
    effect["ticks_on"] = "summon"
save(doc)
print("MUTATION %s: turns=%r ticks_on=%r" % (mode, effect.get("turns"), effect.get("ticks_on")))
