"""Is 好活当赏 a personal counter or a shared one? Four files declare it - read all four declarations.

This decides how Pearl's 「该额外回合开始时，【美学底本】获得 30 点【好活当赏】」 has to be written, and the first run of
her judge exposed the question: the clause pays the ARCHETYPE, but the counter is declared on PEARL (and on 1505, 8009,
8010), so a gain aimed at the ally lands nowhere - silently, because GAIN_RESOURCE on a unit that has no such resource
falls back to the party pool and then does nothing.

So: is it one pool per owner (SELF), or a team counter (PARTY) that any of them can add to?
"""
import json
import pathlib

OUT = pathlib.Path("tools/_haohuo_scope.txt")
FILES = ["1503", "1505", "8009", "8010"]


def main():
    lines = []
    for cid in FILES:
        path = pathlib.Path(f"src/main/resources/characters/{cid}.json")
        if not path.exists():
            lines.append(f"{cid}: no file")
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for declaration in data.get("resources") or []:
            if declaration.get("id") != "好活当赏":
                continue
            lines.append(f"== {cid} ==")
            lines.append("   " + json.dumps({k: v for k, v in declaration.items() if k != "note"},
                                            ensure_ascii=False))
            note = declaration.get("note")
            if note:
                lines.append("   note: " + note[:400])
        # Who gains it, and with what target?
        lines.append(f"   -- {cid}: every GAIN_RESOURCE of 好活当赏 --")
        for rule in data["rules"]:
            for effect in rule.get("do", []):
                if effect.get("op") == "GAIN_RESOURCE" and effect.get("resource") == "好活当赏":
                    lines.append(f"      {rule['id']:58s} target={effect.get('target', '(default self)'):12s}"
                                 f" amount={effect.get('amount')}")
        lines.append("")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
