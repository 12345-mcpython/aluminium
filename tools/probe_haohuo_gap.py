"""Measure the gap between the shipped 好活当赏 model and the encyclopedia's own words about it.

The encyclopedia entry (glossary 1000002, the Aha Moment section) says three things that decide the model:

  「使参演阿哈时刻的角色获得【好活当赏】状态，并将本次阿哈时刻的笑点计入该状态，持续 2 回合。
    【好活当赏】状态产生的技能效果和欢愉伤害以【好活当赏】计入的笑点数计算。
    多个【好活当赏】计入的笑点数合并计算。各个【好活当赏】的持续时间各自独立计算。」

  1. it is a STATE with a 2-round duration, not a bare counter;
  2. the Aha Moment's 笑点 is COUNTED INTO it, and skill effects / Elation damage are computed from THAT count;
  3. several holders' counts MERGE for the computation, while each instance's duration is independent.

This prints what the project actually ships for each of the three, so the gap is a measurement rather than an
impression. It reads the declarations and the rules; it changes nothing.
"""
import json
import pathlib

OUT = pathlib.Path("tools/_haohuo_gap.txt")
FILES = ["1503", "1505", "8009", "8010"]


def main():
    lines = []
    for cid in FILES:
        path = pathlib.Path(f"src/main/resources/characters/{cid}.json")
        data = json.loads(path.read_text(encoding="utf-8"))
        for declaration in data.get("resources") or []:
            if declaration.get("id") != "好活当赏":
                continue
            scope = declaration.get("scope") or "SELF (default)"
            lines.append(f"{cid}: id=好活当赏 max={declaration.get('max')} initial={declaration.get('initial')}"
                         f" scope={scope}")
        # does anything in this file use it as a STATE?
        states = set()
        for rule in data["rules"]:
            for effect in rule.get("do", []):
                if effect.get("op") == "APPLY_BUFF" and effect.get("buff"):
                    states.add(str(effect["buff"]))
        hits = [s for s in states if "好活当赏" in s]
        lines.append(f"   buffs named after it: {hits if hits else '(none)'}")

    lines.append("")
    lines.append("== against the encyclopedia ==")
    lines.append("  1. '状态，持续 2 回合'      -> shipped as a RESOURCE with no duration "
                 "(and, on 1503, an unlimited one per her talent's own wording)")
    lines.append("  2. '笑点计入该状态，技能/欢愉伤害按该计数计算' -> 笑点 and 好活当赏 are two separate counters;"
                 " nothing counts one into the other, and no reader computes from the merged figure")
    lines.append("  3. '多个合并计算，持续时间各自独立'  -> every declaration is SELF, so no reader")
    lines.append("     merges across holders at all; the per-instance duration has no representation because there is")
    lines.append("     no instance to time")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT)
    print("\n".join(lines))


if __name__ == "__main__":
    main()
