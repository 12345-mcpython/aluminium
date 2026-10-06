"""Replace the tolerance rule's APPROXIMATED gate with the real one.

The rule was shipped gated on `self has_state 深度学习` with a note saying the text's actual gate
("持有【好活当赏】时") could not be written because 好活当赏 is a numeric RESOURCE here rather than a
state. That claim is wrong, and the repository contradicts it: `1505.json` ships THREE rules using
`self_resource:好活当赏 >= 1` for exactly this sentence shape, and `1201`/`1003` use the same condition
family. So the approximation is replaced with the documented spelling.

The event stays `ULT_CAST` and the effect stays applied once: the condition family answers "does she have
any of it", which is monotone (it only becomes true), so the first ULT_CAST after she has any of it installs
the buff permanently - which is what a `permanent: true` aura means here.

Usage:
    python tools/fix_pearl_tolerance_gate.py
    python tools/fix_pearl_tolerance_gate.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
RULE_ID = "trace_tolerance_raises_effect_resistance_while_deep"
OLD_WHEN = "self has_state 深度学习"
NEW_WHEN = "self_resource:好活当赏 >= 1"
OLD_ID = "trace_tolerance_raises_effect_resistance_while_deep"
NEW_ID = "trace_tolerance_raises_effect_resistance_while_holding"

NOTE = (
    "⭐⭐ **门改对了**（本轮）：原文的门是「**持有【好活当赏】时**」✓，而它**有现成写法** —— "
    "`self_resource:好活当赏 >= 1` ✓。⚠ 我上一版写成 `self has_state 深度学习` 并断言「持有它这个条件今天写不出来」 ✗ —— "
    "**那句话是错的** ✓，而且仓库里就有反例：`1505.json` **三条已出货规则**用的正是这个条件拼写（`self_resource:好活当赏 >= 1` ✓，"
    "`1201`/`1003` 同族 ✓）。⇒ 近似已撤下 ✓。⭐ 两条口径：① **`>= 1` 就是「持有」** ✓（数值资源没有「状态」可问，"
    "而「有没有」就是 `>= 1` ✓）；② **条件单调**（只会从假变真 ⇒ 第一次满足时挂上、之后一直成立 ✓），"
    "所以 `permanent: true` 的光环语义与这句话一致 ✓。⚠ 仍**未**表达的是同句里没有的那部分："
    "原文只要求「持有」，并没说什么时候撤下 —— 而【好活当赏】在她的声明里是**持续时间无限** ✓（`resources[0]` 的 source 原话 ✓）⇒ 不撤下是对的 ✓。"
)


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    target_rule = next((rule for rule in data["rules"] if rule["id"] == OLD_ID), None)
    if target_rule is None:
        print("rule not found:", OLD_ID)
        return 2
    if target_rule["when"] != ["actor == self"]:
        print("expected when = ['actor == self'] but it is", target_rule["when"])
        return 2
    effect = target_rule["do"][0]
    if effect.get("target_when") != [OLD_WHEN]:
        print("expected target_when =", [OLD_WHEN], "but it is", effect.get("target_when"))
        return 2

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    target_rule["id"] = NEW_ID
    # The gate belongs in `when`, not in `target_when`: "while she holds it" is about the RULE's subject, not
    # about which of the candidates qualify. Written as `target_when` it would also have been evaluated once per
    # candidate with `self` still bound to the owner, i.e. it happened to work but says the wrong thing.
    effect.pop("target_when", None)
    target_rule["when"] = ["actor == self", NEW_WHEN]
    target_rule["note"] = NOTE

    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    TARGET.write_text(text, encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    fixed = next((rule for rule in check["rules"] if rule["id"] == NEW_ID), None)
    if fixed is None or fixed["when"] != ["actor == self", NEW_WHEN]:
        print("WRITE CHECK FAILED - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("when:", OLD_WHEN, "(target_when) ->", NEW_WHEN, "(when)")
    print("id  :", OLD_ID, "->", NEW_ID)
    return 0


if __name__ == "__main__":
    sys.exit(main())
