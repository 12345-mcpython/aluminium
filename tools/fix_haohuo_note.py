"""Rewrite the stale note on 8009/8010's gift rule, and record the model finding (round 1653).

Why the note was wrong: it said "【好活当赏】 is declared per-holder, so if the TARGET does not declare it this half lands
nowhere". The loader proved otherwise the very next round -- it refuses a rule whose resource the SAME FILE does not
declare ("Character 8009 has a rule that uses the resource ..., which the character does not declare"), and both files now
declare it. So the declaring file is the file WITH THE RULE, and the note is corrected to say exactly that.

The rule is found by id and its note is rebuilt here, rather than matched: the old text mixes Chinese, markdown bullets and
backticks, and matching it byte-for-byte from another script is what failed three times earlier in this arc.
"""
import io
import json

NEW_NOTE = (
    "「若目标拥有欢愉技，目标额外获得 **10** 点【好活当赏】，并**使其立即施放 1 次**…欢愉技」✓（#4 = 10 ✓）。"
    "⭐ **只有第 57 件之后才写得出** ✓：欢愉技的行**首列是次数** ✓。"
    "⭐ `CAST_SKILL` 从**解析出的目标**取技能 ✓（「使**其**」✓）。"
    "⚠ **已订正的注记（第 1653 轮）**：本条原写“【好活当赏】按**持有者**声明 ⇒ 目标若未声明就落不下” ✗ —— "
    "装载器**当场证伪** ✓：它要求**自己有规则的那个文件必须声明该资源** ✓（原话："
    "*“Character 8009 has a rule that uses the resource 「好活当赏」, which the character does not declare”* ✓），"
    "两个文件现已各自声明 ✓。"
    "⚠ **仍登记**：「固定计入 **20** 笑点」（阿哈量表，与笑点**资源**不同 ✗）"
    "与「若欢愉技施放前敌方目标被消灭则对**新入场**的敌方目标发动」✗。"
    "⚠ 并记（第 1653 轮审计）：语料把【好活当赏】叫**状态** ✓（GLOSSARY 逐字 ✓），"
    "而本文件用的是**资源** ✗ ⇒ 同名两模型并存 ✗（已登记待决 ✓）。")

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    patched = 0
    for rule in doc.get("rules", []):
        if isinstance(rule, dict) and rule.get("id") == "ult_elation_gift_and_cast":
            rule["note"] = NEW_NOTE
            patched += 1
    if patched != 1:
        raise SystemExit("REFUSING: %s has %d matching rules" % (cid, patched))
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok   %s.json: the gift rule's note now states what the loader actually requires" % cid)
