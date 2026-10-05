"""Rename map, v2: sanitise the data's own markup and resolve EVERY id in a compound name (2026-10-02).

Three repairs over v1:
  * strip the data's inline markup from a name -- `<unbreak>999</unbreak>` and `{NICKNAME}` are formatting, not part of the name;
  * the Trailblazer's row carries the literal placeholder `{NICKNAME}`, so 800x resolves to "Trailblazer" (only one numbered Trailblazer test exists, so no path suffix is needed to stay unique);
  * a compound class name names two things (`Cone20001And21000Test`), so every id is resolved and joined, instead of only the first.
"""
import io
import json
import os
import re
import subprocess

ROOT = r"E:\turnbasedgamedata"
EX = os.path.join(ROOT, "ExcelOutput")
EN = json.load(io.open(os.path.join(ROOT, "TextMap", "TextMapEN.json"), encoding="utf-8"))


def load(fn):
    try:
        d = json.load(io.open(os.path.join(EX, fn), encoding="utf-8"))
    except Exception:
        return []
    return d if isinstance(d, list) else []


def raw(v):
    if isinstance(v, dict):
        h = v.get("Hash")
        return EN.get(str(h), "") if h is not None else ""
    return v if isinstance(v, str) else ""


def clean(s):
    s = re.sub(r"<[^>]*>", "", s)          # <unbreak>999</unbreak> -> 999
    s = re.sub(r"\{[^}]*\}", "", s)        # {NICKNAME}
    s = re.sub(r"\s+", " ", s)
    return s.strip()


def en(v):
    s = clean(raw(v))
    return s


relic_sets = {}
for r in load("RelicSetConfig.json"):
    nm = en(r.get("SetName"))
    if nm:
        relic_sets[str(r.get("SetID"))] = nm

A, E, S = {}, {}, {}
for r in load("AvatarConfig.json"):
    A[str(r.get("AvatarID"))] = en(r.get("AvatarName"))
for r in load("EquipmentConfig.json"):
    E[str(r.get("EquipmentID"))] = en(r.get("EquipmentName"))
for r in load("AvatarServantConfig.json"):
    S[str(r.get("ServantID"))] = en(r.get("ServantName"))

TRAILBLAZER = {str(i): "Trailblazer" for i in range(8001, 8009)}
A.update({k: v for k, v in TRAILBLAZER.items() if not A.get(k)})

files = [f for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
         if f.startswith("src/test/java/") and f.endswith(".java")]
numbered = sorted(os.path.basename(f)[:-5] for f in files if re.search(r"\d", os.path.basename(f)))


def pascal(s):
    s = re.sub(r"[^0-9A-Za-z]+", " ", s)
    return "".join(w[:1].upper() + w[1:] for w in s.split())


def lookup(cid):
    for table, kind in ((A, "character"), (E, "lightcone"), (S, "servant"), (relic_sets, "relic")):
        if table.get(cid):
            return kind, table[cid]
    return None, None


out = ["=== rename map v2 ==="]
manual, renames, left_odd = [], {}, []
for n in numbered:
    ids = re.findall(r"\d{3,6}", n)
    parts = []
    kinds = []
    for cid in ids:
        kind, name = lookup(cid)
        if name:
            parts.append(pascal(name))
            kinds.append(kind)
    if not parts:
        manual.append((n, "no entity for %s" % (",".join(ids) or "no id")))
        continue
    stem = n[:-4] if n.endswith("Test") else n
    stem = re.sub(r"^(Cone|Cid|Character|Relic|LightCone)", "", stem)
    # the connector between two ids is not part of the suffix: it sits between digits, so `\b` alone would miss it
    stem = re.sub(r"(?<=\d)And(?=\d)", "", stem)
    stem = re.sub(r"And\b", "", stem)
    for cid in ids:
        stem = stem.replace(cid, "")
    new = "And".join(parts) + stem + "Test"
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*", new):
        manual.append((n, "would produce %r" % new))
        continue
    if len(parts) > 1 and len(ids) > len(parts):
        left_odd.append("%s -> %s (resolved %d of %d ids)" % (n, new, len(parts), len(ids)))
    renames[n] = new
    out.append("  %-44s %-10s %-7s -> %s" % (n, ",".join(kinds), ",".join(ids), new))

seen = {}
for old, new in renames.items():
    seen.setdefault(new, []).append(old)
out.append("")
out.append("=== collisions (%d) ===" % sum(1 for v in seen.values() if len(v) > 1))
for new, olds in sorted(seen.items()):
    if len(olds) > 1:
        out.append("  %s <- %s" % (new, ", ".join(olds)))
out.append("")
out.append("=== partially resolved compound names (%d) ===" % len(left_odd))
out += ["  " + x for x in left_odd]
out.append("")
out.append("=== needs a human decision (%d) ===" % len(manual))
for n, why in manual:
    out.append("  %-44s %s" % (n, why))
io.open("tools/_rename_map2.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("renames %d ; collisions %d ; manual %d ; partial %d"
      % (len(renames), sum(1 for v in seen.values() if len(v) > 1), len(manual), len(left_odd)))
