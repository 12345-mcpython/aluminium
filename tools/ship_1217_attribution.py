"""Attribution for the 1217 mutants, and the checklist sync (round 2 of the goal).

The pair bit, but a pair cannot say WHICH half the reading is about. So the mutator gains single-mutant modes:
  `a` -- restore, then only `turns: 3`
  `b` -- restore, then only drop `ticks_on`
and `sync_expression_1217.py` moves the stale §3 row into §2 with the judge that now reads it.
"""
import io
import os
import sys

MUTATOR = "tools/mut_huohuo_clock.py"
SYNC = "tools/sync_expression_1217.py"

io.open(MUTATOR, "w", encoding="utf-8", newline="\n").write('''"""Mutations for 1217's clock reading, one at a time so each can be attributed.

  a: `turns: 3`               -- the reading about "two of her turns spend it";
  b: `ticks_on: "self"` gone  -- whether the field is load-bearing or states what the default already does;
  off: both; on: restore.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1217.json"
TALISMAN = "\\u79b3\\u547d"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
if mode not in ("a", "b", "off", "on"):
    sys.exit("usage: mut_huohuo_clock.py a|b|off|on")


def load():
    with io.open(CHAR, encoding="utf-8") as handle:
        return json.load(handle)


def save(doc):
    with io.open(CHAR, "w", encoding="utf-8", newline="\\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\\n")


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
save(doc)
print("MUTATION %s: turns=%r ticks_on=%r" % (mode, effect.get("turns"), effect.get("ticks_on")))
''')
print("ok   the mutator takes single mutants now")

io.open(SYNC, "w", encoding="utf-8", newline="\n").write('''"""The stale §3 row becomes a §2 row (2026-10-02, round 2 of the goal).

§3 said 1217's 「使【禳命】的持续回合数 −1」 needs "a shorten-duration spelling, because EXTEND_BUFF only takes positive turns".
Measured: the clause is not about shortening at all -- 「藿藿**每回合开始时**持续回合数减 1」 is about WHOSE CLOCK spends the duration, and
`ticks_on: "self"` already says it (the same field 8006's 伴舞, 星期日's 蒙福者 and the 结界 half use).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\\n")
PREFIX = "| \\u300c\\u4f7f\\u3010\\u79b3\\u547d\\u3011\\u7684\\u6301\\u7eed\\u56de\\u5408\\u6570"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the stale row is out of §3")

ROW = ("| **\\u300c\\u67d0\\u4e2a\\u72b6\\u6001\\u7684\\u6301\\u7eed\\u56de\\u5408\\u6570\\u5728**\\u5b83\\u81ea\\u5df1**\\u7684\\u56de\\u5408\\u5f00\\u59cb\\u65f6\\u51cf 1\\u300d** "
       "| `APPLY_BUFF` \\uff0b `turns` \\uff0b **`ticks_on: \\"self\\"`**\\uff08\\u8c01\\u7684\\u56de\\u5408\\u82b1\\u6389\\u65f6\\u957f \\u2713\\uff09 "
       "| `src/main/resources/characters/1217.json` "
       "| `HuohuoTalismanDurationTest` |")
ANCHOR = "| **\\u961f\\u53cb\\u7684\\u72b6\\u6001\\u7ed3\\u675f\\u65f6\\u53d6\\u4e00\\u90e8\\u5206**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\\n").write("\\n".join(lines))
print("ok   it is a §2 row now")
''')
print("ok   the sync script is written")
