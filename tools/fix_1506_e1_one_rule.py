"""E1's "keeps a fifth" cannot be a SECOND rule: `previousCredited` is per-RULE, not per-event.

Measured from the source: `TriggerInterpreter.apply` starts `double previousCredited = 0;` and walks ONE rule's effects. So
the base clear (one rule) and the E1 share-of-what-was-cleared (another rule) can never see each other's amount - the
share is 20% of 0. A judge read 0 where it wanted 12, and this is why.

The fix is to stop splitting them. The base rule clears, and the E1 rule does the whole job itself: it reads her 隐藏分
BEFORE anything clears it, takes a fifth, and spends the rest. `Resource.spend` is the clamped take, so "spend
value - kept" leaves exactly the kept fifth, whatever that is.

What that needs is a way to say "spend all but 20%", i.e. an amount that is a share of the CURRENT value read at this
moment. `percent_from_resource` is that shape but takes a resource's value as basis points, not a share of the amount.
So the E1 rule is written as: gain nothing, spend `self_resource:隐藏分` minus a fifth - expressed with the two ops that
exist:

    SPEND_RESOURCE  amount_from_attr  = the current value
    GAIN_RESOURCE  amount            = a fifth of it

i.e. clear then restore, inside ONE rule, which `previousCredited` DOES connect.
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1506.json")
BACKUP = TARGET.with_suffix(".json.bak")
BASE = "p1506_leaving_godmode_clears_hidden"
KEEP = "p1506_e1_keeps_a_fifth_of_hidden"
HIDDEN = "隐藏分"


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup")
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored")
        return 0

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written")

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    idx = {r["id"]: i for i, r in enumerate(data["rules"])}
    if BASE not in idx or KEEP not in idx:
        print("one of the two rules is missing")
        return 2

    # The clear now carries the Eidolon condition too, so BOTH routes are one rule with one `previousCredited`.
    base = data["rules"][idx[BASE]]
    base["note"] = (
        "⚠⚠ **清空与「星魂 1 保留 20%」必须在同一条规则里** ✓ —— **实测**：`previousCredited` **是每条规则独立、从 0 开始** ✓"
        "（`TriggerInterpreter.apply` 里 `double previousCredited = 0;` ✓，它只遍历**一条规则**的效果 ✓）"
        "⇒ 拆成两条规则时，保留那条**看不到**清空那条花掉了多少 ✓ ⇒ 它按 **0.2 × 0** 恢复 ✓（判据读到 **0** 而非 **12** ✓）。"
        "⭐ 现在这一条**一次做完**：先按**资源当前值**取走，再按**前一个效果实际入账的量**恢复五分之一 ✓ —— "
        "`previousCredited` **在同一条规则内是通的** ✓。"
        "⚠ 星魂门**写在效果上**（`min_eidolon` 在效果层 ✓），**不是规则层** ✓ —— 规则层的门会让整条规则消失 ✓，"
        "而基础清空必须**始终**发生 ✓。")
    base["do"] = [
        {
            "op": "GAIN_RESOURCE",
            "resource": HIDDEN,
            "amount": 0,
            "target": "self"
        },
        {
            "op": "SPEND_RESOURCE",
            "resource": HIDDEN,
            "spendAll": True,
            "target": "self"
        },
        {
            "op": "GAIN_RESOURCE",
            "resource": HIDDEN,
            "amountFromPrevious": True,
            "amountPercent": 0.2,
            "min_eidolon": 1,
            "target": "self"
        },
    ]

    # The separate rule is gone: it could never see the clear's amount.
    data["rules"] = [r for r in data["rules"] if r["id"] != KEEP]
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    landed = next((r for r in check["rules"] if r["id"] == BASE), None)
    if landed is None or len(landed["do"]) != 3:
        print("WRITE CHECK FAILED")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print(f"rules: {len(data['rules']) + 1} -> {len(check['rules'])}")
    for effect in landed["do"]:
        print("   ", {k: v for k, v in effect.items() if k != "target"})
    return 0


if __name__ == "__main__":
    sys.exit(main())
