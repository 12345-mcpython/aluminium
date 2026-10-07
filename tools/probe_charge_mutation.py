"""Why does the `to_max_adds` mutation survive? Look at what the rules actually say after it.

Prints the refill rule, every rule of Pearl's that touches the 充能 counter, and whether the other rules would ALSO set it
to full - if re-casting the Ultimate ends the state (the charge-0 branch) then the state is re-applied, and whatever
re-applies it may bring its own refill.
"""
import json
import pathlib
import subprocess
import sys

TARGET = pathlib.Path("src/main/resources/characters/1503.json")
COUNTER = "充能"


def dump(title):
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    print("==", title, "| rules", len(data["rules"]))
    for rule in data["rules"]:
        touched = [e for e in rule.get("do", []) if e.get("resource") == COUNTER]
        if touched:
            print(f"   {rule['id']:50s} {json.dumps(touched, ensure_ascii=False)}")
    print()


if __name__ == "__main__":
    dump("shipped")
    print(subprocess.run([sys.executable, "tools/mut_pearl_charge.py", "to_max_adds"],
                         capture_output=True, text=True).stdout.strip())
    dump("with to_max removed")
    print(subprocess.run([sys.executable, "tools/mut_pearl_charge.py", "restore"],
                         capture_output=True, text=True).stdout.strip())
    dump("restored")
