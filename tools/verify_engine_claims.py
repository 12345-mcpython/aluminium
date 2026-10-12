"""Check every capability claim in engine.md's "implemented vs not implemented" tables (§15).

That section is the one a reader trusts to decide whether a feature exists, so a false "not
implemented" there causes duplicate work while a false "implemented" causes a missing feature.

This is a human-readable evidence dump, not a test: it prints, for each claim, the files and
symbols that decide it, so the section can be rewritten from readings instead of from the old text.

Usage:
    python tools/verify_engine_claims.py
"""

import glob
import os
import re


def grep(pattern, roots=("src/main/java",), include="*.java"):
    hits = []
    for root in roots:
        for path in glob.glob(os.path.join(root, "**", include), recursive=True):
            rel = os.path.relpath(path)
            with open(path, encoding="utf-8", errors="replace") as fh:
                for n, line in enumerate(fh, 1):
                    if re.search(pattern, line):
                        hits.append((rel, n, line.strip()[:100]))
    return hits


def report(title, pattern, expect=""):
    hits = grep(pattern)
    verdict = "FOUND" if hits else "ABSENT"
    flag = "  <== claim says " + expect
    print(f"\n{title}")
    print(f"  pattern {pattern!r}: {len(hits)} hit(s) -> {verdict}{flag}")
    for rel, n, text in hits[:4]:
        print(f"     {rel}:{n}  {text}")


def main():
    print("=" * 78)
    print("engine.md §15 claims, checked against src/main/java")
    print("=" * 78)

    report("控制 (doc: '只有 StunBuff 一种')", r"class ControlBuff", "only StunBuff")
    report("控制状态表", r"CONTROL_(EFFECTS|STATES)")
    report("治疗乘区 (doc: '无乘区、无调用者')", r"calculateHeal", "no zones, no callers")
    report("治疗加成属性", r"OUTGOING_HEALING_BOOST|HEAL_TAKEN_RATIO")
    report("护盾量提高 (doc §18.4: '没有对应属性')", r"SHIELD_BOOST", "no attribute")
    report("削韧值提高", r"BOOST_TOUGHNESS|ToughnessBoostBuff")
    report("七系击破异常/控制 (doc: '冻结/纠缠/禁锢未做')", r"FROZEN|ENTANGLE|IMPRISON", "3 unimplemented")
    report("欢愉伤害 (doc: '只有占位，无机制')", r"ELATION_DAMAGE_BOOST|DamageType\.ELATION|elationBase", "placeholder only")
    report("忆灵指令 (doc: '仍缺指令忆灵行动')", r"COMMAND_SUMMON", "still missing")
    report("忆灵连携 (doc: '仍缺连携攻击')", r"companionAttack|jointAttack|连携")
    report("速度操纵 (doc: ✅)", r"onSpeedChanged")
    report("多韧性条 (doc: stanceCount>1 未处理)", r"stanceCount")

    print("\n" + "=" * 78)
    print("Bare line-number citations: do they still resolve?")
    print("=" * 78)
    docs = ["engine.md", "ROADMAP.md"]
    cites = []
    for doc in docs:
        with open(doc, encoding="utf-8", errors="replace") as fh:
            for n, line in enumerate(fh, 1):
                for m in re.finditer(r"`?([A-Z]\w+(?:\.java)?)[:`](\d{3,4})`?", line):
                    cites.append((doc, n, m.group(1), int(m.group(2))))
    print(f"found {len(cites)} Class:NNNN-style citations in {', '.join(docs)}")
    for doc, n, cls, line in cites[:20]:
        print(f"   {doc}:{n}  cites {cls}:{line}")


if __name__ == "__main__":
    main()
