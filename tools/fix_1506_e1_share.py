"""E1's "keeps a fifth": read the PREVIOUS effect's credited amount, not the event's.

`amount_from_event` on STATE_ENDED reads the AMOUNT slot, which `fireStateEnded` documents as "how many instances of that
state the carrier held" - not the hidden points the clear just took. So the share was 20% of 0.

The correct spelling is `amount_from_previous`, whose own reader is 1505's Eidolon: 「additionally gain 50%/100% of the
[好活当赏] gained this time」 - "the credited amount of the previous effect after the cap has truncated it". The clear runs
first (the order the shipping script asserts), so the previous effect IS that clear.
"""
import json
import pathlib

LIVE = pathlib.Path("src/main/resources/characters/1506.json")
SCRIPT = pathlib.Path("tools/ship_1506.py")
RULE_ID = "p1506_e1_keeps_a_fifth_of_hidden"

data = json.loads(LIVE.read_text(encoding="utf-8"))
rule = next(r for r in data["rules"] if r["id"] == RULE_ID)
for effect in rule["do"]:
    if effect.get("amountFromEvent"):
        effect.pop("amountFromEvent")
        effect["amountFromPrevious"] = True
rule["note"] = (
    "⚠⚠ **两条规则都在 `STATE_ENDED` 上，而基础那条没有星魂门** ✓ ⇒ 有星魂 1 时**两条都会跑** ✓。"
    "⚠ 解决办法就是**利用文件顺序** ✓：那条（先）清空 ✓、本条（后）按**前一个效果实际入账的量**恢复 20% ✓。"
    "⚠⚠ **这里必须用 `amountFromPrevious` 而不是 `amountFromEvent`** ✓ —— **实测**：`STATE_ENDED` 的 amount 槽是"
    "「该状态被持有几层」✓（`fireStateEnded` 的注释写明了 ✓），**不是**刚被清掉的隐藏分 ✓ ⇒ 用 `amountFromEvent` 会"
    "**安静地**按 20% × 0 恢复 ✓（判据读到 0 而不是 12 ✓）。⭐ `amountFromPrevious` 的既有读者是 1505 的星魂 ✓："
    "「额外获得本次获得量的 50%/100%」✓，读的正是**前一个效果截断后的入账量** ✓。"
    "⚠ 所以本条的**顺序是承重的** ✓ —— 它必须排在 `p1506_leaving_godmode_clears_hidden` **之后** ✓，"
    "而且它先清再补 ✓（否则会变成保留 120% ✓）。")
LIVE.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("live rule now:", json.dumps(rule["do"], ensure_ascii=False))

text = SCRIPT.read_text(encoding="utf-8")
old = '{"op": "GAIN_RESOURCE", "resource": HIDDEN, "amountFromEvent": True, "amountPercent": 0.2, "target": "self"}'
new = '{"op": "GAIN_RESOURCE", "resource": HIDDEN, "amountFromPrevious": True, "amountPercent": 0.2, "target": "self"}'
print("script anchor present:", old in text)
SCRIPT.write_text(text.replace(old, new), encoding="utf-8")
