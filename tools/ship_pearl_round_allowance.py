"""Ship the per-round allowance for 感知容差's gain, and its reset.

「我方目标回合开始时，真珠获得 5 点【好活当赏】，最多获得 50 点【好活当赏】，真珠回合开始时重置可获得【好活当赏】数量」
(trace 1503102).

Three things this has to get right, and each is a decision rather than a transcription:

1. THE ALLOWANCE IS PER ROUND, not a second cap on the counter. The counter's own cap is 50 and already lives in its
   declaration; this is "at most 50 may be handed out in a round". The engine expresses it with `max_per_round` on the
   gain and `RESET_RESOURCE_CAP` to start a new round.

2. THE EXISTING GAIN RULE IS AMENDED, not duplicated. It already fires on every ally's turn (`actor is_ally`) and pays
   5; adding a SECOND rule with the allowance would pay 5 twice. So the shipped rule gains the field and keeps being
   the one and only source of these 5 points.

3. THE RESET HAS TO RUN BEFORE THE GAIN ON PEARL'S OWN TURN. `actor is_ally` counts Pearl herself, so on her turn both
   the reset and the gain fire, and file order decides whether her own 5 points fall inside the new round or the old
   one. The document's phrasing is 「回合开始时重置可获得数量」 - the allowance is refreshed at the start of her turn,
   which reads as "before anything is handed out this round" - so the reset rule is placed immediately BEFORE the gain
   rule. Measured, not assumed: with the two the other way round, the first ally turn of a round spends allowance that
   was just reset, and over a full round the total differs by those 5 points.

Usage:
    python tools/ship_pearl_round_allowance.py
    python tools/ship_pearl_round_allowance.py restore
"""
import json
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
BACKUP = TARGET.with_suffix(".json.bak")
GAIN_ID = "trace_gains_on_an_ally_turn_start"
RESET_ID = "trace_resets_the_round_allowance_on_her_turn"

SOURCE = ("1503 真珠 行迹 感知容差 (1503102): 「我方目标回合开始时，真珠获得 5 点【好活当赏】，**最多获得 50 点**"
          "【好活当赏】，**真珠回合开始时重置可获得【好活当赏】数量**」。")

RESET_RULE = {
    "on": "TURN_START",
    "id": RESET_ID,
    "when": [
        "actor == self"
    ],
    "do": [
        {"op": "RESET_RESOURCE_CAP", "resource": "好活当赏"}
    ],
    "source": SOURCE,
    "note": "⭐⭐ **一个每回合的软上限** ✓（不是「给计数再加一个上限」✓ —— 计数自己的上限 50 早在声明里 ✓）："
            "数值写在 `GAIN_RESOURCE` 的 **`max_per_round`** 上 ✓，本条负责**开新的一轮** ✓。"
            "⚠⚠ **位置是有意义的** ✓：`actor is_ally` **包含真珠自己** ✓ ⇒ 她自己的回合上**重置与获得都会触发** ✓，"
            "而**文件顺序**决定她自己的那 5 点算进**新一轮还是旧一轮** ✓。原文的语气是「回合开始时**重置**可获得数量」✓ "
            "⇒ 读数上应理解为「本轮**先**刷新额度、**再**发放」✓ ⇒ **本条排在获得那条之前** ✓（实测：反过来时一整轮的总量差 5 点 ✓）。"
            "⚠ 计数本身**不变** ✓ —— 重置的只是「这一轮还能发多少」✓。",
}


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    data = json.loads(TARGET.read_text(encoding="utf-8"))
    rules = data["rules"]
    ids = [r["id"] for r in rules]
    if RESET_ID in ids:
        print("already present")
        return 2
    if GAIN_ID not in ids:
        print("the shipped gain rule is missing:", GAIN_ID)
        return 3

    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)

    before = len(rules)
    # 1) the shipped gain rule takes the per-round allowance
    gain = next(r for r in rules if r["id"] == GAIN_ID)
    for effect in gain["do"]:
        if effect.get("op") == "GAIN_RESOURCE":
            effect["max_per_round"] = 50
    gain["source"] = SOURCE
    gain["note"] = ((gain.get("note") or "")
                    + "\n  ⭐ **已加 `max_per_round: 50`** ✓（随感知容差出货 ✓）：这一条是这些 5 点的**唯一来源** ✓，"
                      "所以上限加在**它自己**身上 ✓ —— 若另加一条带额度的规则 ✓，每一轮会**发两次 5 点** ✓。"
                      "⚠ 超额时**不再发放** ✓（额度用尽 ⇒ 本次 `amount` 归零 ⇒ 直接返回 ✓），"
                      "而不是发放后被钳掉 ✓ —— 两者对「这一轮发了多少」含义不同 ✓。")

    # 2) the reset rule goes immediately BEFORE the gain rule, so on her own turn the round starts first
    rules.insert(rules.index(gain), RESET_RULE)
    TARGET.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    check = json.loads(TARGET.read_text(encoding="utf-8"))
    order = [r["id"] for r in check["rules"]]
    problems = []
    if RESET_ID not in order:
        problems.append("the reset rule did not land")
    if order.index(RESET_ID) > order.index(GAIN_ID):
        problems.append(f"the reset rule sits AFTER the gain ({order.index(RESET_ID)} > {order.index(GAIN_ID)})")
    check_gain = next(r for r in check["rules"] if r["id"] == GAIN_ID)
    allowance = [e.get("max_per_round") for e in check_gain["do"] if e.get("op") == "GAIN_RESOURCE"]
    if allowance != [50]:
        problems.append(f"the gain rule's allowance reads {allowance}, expected [50]")
    if problems:
        print("WRITE CHECK FAILED:", problems, "- restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 4
    print(f"rules: {before} -> {len(order)}")
    print(f"  reset index {order.index(RESET_ID)} < gain index {order.index(GAIN_ID)}")
    print("  gain allowance:", allowance)
    return 0


if __name__ == "__main__":
    sys.exit(main())
