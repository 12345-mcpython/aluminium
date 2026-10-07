"""Check Pearl's own declarations: is the counter the absorber charges to actually a resource of HERS, under the exact
name the rule uses?

Worth checking rather than assuming: the absorber reads the PAYER's resource by name, and a name that does not match
the declaration is a silent zero - the pool always reads 0, the clause blocks nothing, and no error appears anywhere.
That is the trap this project has hit more than once with resources.
"""
import json
import pathlib

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
OUT = pathlib.Path("tools/_pearl_resources.txt")


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    lines = ["== declared resources =="]
    for declaration in data.get("resources") or []:
        lines.append("  " + json.dumps(declaration, ensure_ascii=False))
    if not data.get("resources"):
        lines.append("  (none declared)")

    lines.append("")
    lines.append("== every resource named by a rule ==")
    names = set()
    for rule in data["rules"]:
        for effect in rule.get("do", []):
            resource = effect.get("resource")
            if resource:
                names.add(resource)
                lines.append(f"  {rule['id']:60s} {effect.get('op'):16s} {resource}")
    lines.append("")
    lines.append("== the absorber's own fields ==")
    for rule in data["rules"]:
        for effect in rule.get("do", []):
            if effect.get("op") == "ABSORB_DAMAGE":
                lines.append("  " + json.dumps(effect, ensure_ascii=False))
    lines.append("")
    lines.append("distinct resource names used: " + json.dumps(sorted(names), ensure_ascii=False))
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("written", OUT, "lines:", len(lines))


if __name__ == "__main__":
    main()
