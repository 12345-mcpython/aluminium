"""Print every rule in Pearl's file that touches an attribute, in file order.

The order matters: rules on one event run in the file's order, and the open question is which Elation figure the
healing link reads. Seeing what sits before it is the first thing to know.
"""
import json
import pathlib

TARGET = pathlib.Path("src/main/resources/characters/1503.json")


def main():
    data = json.loads(TARGET.read_text(encoding="utf-8"))
    for i, rule in enumerate(data["rules"]):
        writes = []
        for effect in rule.get("do", []):
            if effect.get("op") == "MODIFY_ATTR":
                writes.append(effect.get("attribute"))
            if effect.get("scale") and "ELATION" in str(effect.get("scale")):
                writes.append("reads:" + str(effect.get("scale")))
        if writes:
            print(f"{i:3d}  {rule['id']:58s} {rule['on']:14s} {writes}")
    print()
    print("total rules:", len(data["rules"]))


if __name__ == "__main__":
    main()
