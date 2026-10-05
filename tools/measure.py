"""A one-off measurement for the status report (2026-10-02): how big is the engine's vocabulary.

ASCII only, and it lives in tools/ so it can be re-run instead of quoted into a shell again -- the quote traps have cost
this project several rounds.
"""
import io
import re

ENUMS = "src/main/java/com/laosun/aluminium/enums/"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"


def members(path):
    """Enum constants, including the ones a `@SerializedName(...)` annotation pushes onto the same line.

    ⚠ A plain `^\\s{4}NAME\\(` pattern silently MISSES those -- measured on 2026-10-02, in this very script, which is
    the mistake already recorded in GAPS ("list an enum's members by reading the file, not by one regex").
    """
    s = io.open(path, encoding="utf-8").read()
    return sorted(set(re.findall(r"([A-Z][A-Z0-9_]+)\s*\(\s*\"", s)))


print("TriggerEvent members :", len(members(ENUMS + "TriggerEvent.java")))
print("AttributeType members:", len(members(ENUMS + "AttributeType.java")))
print("DamageType           :", members(ENUMS + "DamageType.java"))
print("DamageElement        :", members(ENUMS + "DamageElement.java"))

s = io.open(INTERP, encoding="utf-8").read()

m = re.search(r"OPS\s*=\s*Set\.of\((.*?)\);", s, re.S)
if not m:
    m = re.search(r'"GAIN_RESOURCE"(.*?)\);', s, re.S)
if m:
    ops = re.findall(r'"([A-Z_]+)"', m.group(0))
    print("op vocabulary        :", len(set(ops)))

m2 = re.search(r"LIFETIMES\s*=\s*Set\.of\((.*?)\);", s, re.S)
if m2:
    print("lifetimes            :", re.findall(r'"([a-z_]+)"', m2.group(1)))
