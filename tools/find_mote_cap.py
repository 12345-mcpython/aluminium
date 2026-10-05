"""Look for Evernight's 【忆质】 cap by its NUMERIC signature, not by key name (2026-10-02).

Two name-based searches failed: her ability file's only `MaxCount` is a summoner energy bar, and her character config has
no max/count/sp-ish scalar key at the levels walked. The document gives three numbers for this resource -- 16 (the
threshold to cast the ultimate), 4 (one extra instance per 4 points) and 40 (the cap on how many are counted) -- so this
looks for those numbers next to resource-ish names in both files.
ASCII only.
"""
import io
import json
import re

FILES = [
    "ConfigCharacter/Avatar/Avatar_Evernight_00_Config.json",
    "ConfigAbility/Avatar/Avatar_Evernight_00_Ability.json",
]
ROOT = "E:/turnbasedgamedata/Config/"

KEYS = ("max", "count", "sp", "special", "mote", "point", "resource")

for rel in FILES:
    text = io.open(ROOT + rel, encoding="utf-8", errors="replace").read()
    print("==", rel, "len", len(text))
    for pat in ("MaxCount", "SpecialSP", "Special", "SPMax"):
        print("   ", pat, "occurrences:", len(re.findall(re.escape(pat), text)))
    # every JSON object that carries both a resource-ish key and one of the three numbers
    for m in re.finditer(r"\{[^{}]{0,400}\}", text):
        seg = m.group(0)
        low = seg.lower()
        if any(k in low for k in KEYS) and re.search(r"\b(16|40)\b", seg):
            print("    cand:", re.sub(r"\s+", " ", seg)[:220])
