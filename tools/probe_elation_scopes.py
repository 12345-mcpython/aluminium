"""Which scope do the two Elation counters have, per shipped file, and what can remove a party resource?

Two questions this answers, both of which decide how 1503's extra-turn reward can be written:
  1. is 好活当赏 declared SELF or PARTY anywhere (the text gives its 30 points to the ARCHETYPE, not to Pearl);
  2. does `SPEND_RESOURCE` expose a "spend all" form, and does it reach a PARTY-scoped resource.
"""
import json
import pathlib
import re

CHARS = pathlib.Path("src/main/resources/characters")
SPEC = pathlib.Path("src/main/java/com/laosun/aluminium/beans/EffectSpec.java")
INTERP = pathlib.Path("src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java")


def scopes():
    print("== declarations of 好活当赏 / 笑点 ==")
    for path in sorted(CHARS.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        resources = data.get("resources") if isinstance(data, dict) else None
        if not isinstance(resources, list):
            continue
        for spec in resources:
            if isinstance(spec, dict) and spec.get("id") in ("好活当赏", "笑点"):
                print(f"  {path.name}: {spec['id']} scope={spec.get('scope')} max={spec.get('max')}")


def spend_forms():
    print("== SPEND_RESOURCE fields ==")
    text = SPEC.read_text(encoding="utf-8")
    for line in text.splitlines():
        if "spendAll" in line or "spendOverflow" in line.lower():
            print("  EffectSpec:", line.strip())
    text = INTERP.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(text):
        if "spendAll" in line or "SpendAll" in line:
            print(f"  TriggerInterpreter:{i + 1}: {line.strip()}")


def party_write_path():
    print("== how a PARTY resource is reached from an op ==")
    text = INTERP.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(text):
        if "partyResource" in line or "ResourceScope.PARTY" in line:
            print(f"  TriggerInterpreter:{i + 1}: {line.strip()}")


if __name__ == "__main__":
    scopes()
    spend_forms()
    party_write_path()
