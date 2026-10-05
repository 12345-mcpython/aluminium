"""Dump every content entity's PascalCased English name, for classifying the tests (2026-10-02, read-only).

`AcheronTest` and `AglaeaFissureTest` are per-character tests but carry no id, so a keyword rule cannot see them. Matching the test name against the entity names the game data itself provides
is data-driven and needs no guessing:
    kind TAB PascalName      e.g.  character<TAB>Acheron   lightcone<TAB>QuidProQuo   relic<TAB>EverGloriousMagicalGirl
"""
import io
import json
import os
import re

ROOT = r"E:\turnbasedgamedata"
EX = os.path.join(ROOT, "ExcelOutput")
EN = json.load(io.open(os.path.join(ROOT, "TextMap", "TextMapEN.json"), encoding="utf-8"))


def load(fn):
    try:
        d = json.load(io.open(os.path.join(EX, fn), encoding="utf-8"))
    except Exception:
        return []
    return d if isinstance(d, list) else []


def name(v):
    if isinstance(v, dict):
        h = v.get("Hash")
        return EN.get(str(h), "") if h is not None else ""
    return v if isinstance(v, str) else ""


def pascal(s):
    s = re.sub(r"<[^>]*>", "", s)
    s = re.sub(r"\{[^}]*\}", "", s)
    s = re.sub(r"[^0-9A-Za-z]+", " ", s)
    return "".join(w[:1].upper() + w[1:] for w in s.split())


rows = []
for r in load("AvatarConfig.json"):
    n = pascal(name(r.get("AvatarName")))
    if n and not n.startswith("NICKNAME"):
        rows.append(("character", n))
for i in range(8001, 8009):
    rows.append(("character", "Trailblazer"))
for r in load("EquipmentConfig.json"):
    n = pascal(name(r.get("EquipmentName")))
    if n:
        rows.append(("lightcone", n))
for r in load("RelicSetConfig.json"):
    n = pascal(name(r.get("SetName")))
    if n:
        rows.append(("relic", n))
for r in load("AvatarServantConfig.json"):
    n = pascal(name(r.get("ServantName")))
    if n:
        rows.append(("memosprite", n))

seen = set()
out = ["# kind\tPascalName"]
for k, n in sorted(set(rows), key=lambda kv: (kv[0], kv[1])):
    if (k, n) in seen or len(n) < 3:
        continue
    seen.add((k, n))
    out.append("%s\t%s" % (k, n))
io.open("tools/content_classes.tsv", "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")
counts = {}
for k, _ in seen:
    counts[k] = counts.get(k, 0) + 1
print("classes: %s" % counts)
