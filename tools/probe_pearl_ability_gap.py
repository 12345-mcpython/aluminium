"""Is the ability-script gap general, or is Pearl the only character missing one?

The corpus says "当前数据版本未包含该角色的能力脚本 (Config/ConfigAbility)". Measured just now: the directory
Config/ConfigAbility/Avatar EXISTS and is populated, but has no Pearl file at all - so the claim is either "this
character is newer than the dump" or "ability scripts are absent generally". Those are different facts, and only the
second one would mean the mechanic is unknowable from data forever.

This compares Pearl against characters this project has already shipped, and against the newest-looking ids in the
dump, so the answer is a count rather than an impression.
"""
import pathlib
import re

AVATAR_DIR = pathlib.Path(r"E:\turnbasedgamedata\Config\ConfigAbility\Avatar")
OUT = pathlib.Path("tools/_pearl_ability_gap.txt")
# Characters this project has engine content for, plus Pearl.
PROBES = {
    "1503 Pearl": "Pearl",
    "1502 Yao Guang": "YaoGuang",
    "1505 Evanescia": "Evanescia",
    "1413 (memosprite owner)": "Cipher",
    "1001 March 7th": "Mar_7th",
    "1309 Robin": "Robin",
}


def main():
    lines = []
    files = sorted(p.name for p in AVATAR_DIR.glob("*.json"))
    lines.append(f"== ConfigAbility/Avatar holds {len(files)} files ==")
    lines.append("  first 10: " + ", ".join(files[:10]))
    lines.append("  last 10 : " + ", ".join(files[-10:]))
    lines.append("")

    lines.append("== name probes ==")
    for label, stem in PROBES.items():
        matches = [f for f in files if stem.lower() in f.lower()]
        lines.append(f"  {label:28s} stem~{stem:12s} -> {len(matches)} file(s) {matches[:3]}")
    lines.append("")

    lines.append("== do the newest characters have ability files? (id-sorted tail) ==")
    numeric = []
    for f in files:
        match = re.search(r"(\d{4})", f)
        if match:
            numeric.append((int(match.group(1)), f))
    numeric.sort()
    for avatar_id, name in numeric[-12:]:
        lines.append(f"  {avatar_id} {name}")
    lines.append("")
    lines.append(f"== does 1503 appear in ANY filename under ConfigAbility? ==")
    hits = [str(p) for p in AVATAR_DIR.rglob("*1503*")]
    lines.append(f"  {len(hits)} hit(s)")
    for h in hits[:10]:
        lines.append("  " + h)

    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
