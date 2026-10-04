"""Probe (user question, 2026-10-02): do eidolons carry a DAMAGE CORRECTION COEFFICIENT?

Read-only. Looks at the eidolon table we ship and at the raw dataset for:
  * which fields an eidolon entry actually has;
  * which entries carry a NON-EMPTY `skill_add_level` (an eidolon that raises a skill level DOES raise that
    skill's multiplier, because multipliers are per level -- that is the closest thing to a "correction");
  * any field whose name contains damage / ratio / correct / adjust.

Written as a FILE on purpose: an inline heredoc is not supported by this shell, and inline Chinese gets mangled.
"""
import glob
import io
import json
import os

lines = []


def emit(text):
    lines.append(text)


# --- 1. our shipped copy of the eidolon table -----------------------------------------------
path = "src/main/resources/data/eidolons.json"
if os.path.exists(path):
    doc = json.load(io.open(path, encoding="utf-8"))
    emit("our table: %s | characters: %d" % (path, len(doc)))
    keys = set()
    for cid, ranks in doc.items():
        if isinstance(ranks, dict):
            for rank, entry in ranks.items():
                if isinstance(entry, dict):
                    keys.update(entry.keys())
    emit("entry keys: " + json.dumps(sorted(keys), ensure_ascii=True))

    addlevel = []
    damagey = []
    for cid, ranks in doc.items():
        if isinstance(ranks, dict):
            for rank, entry in ranks.items():
                if not isinstance(entry, dict):
                    continue
                if entry.get("skill_add_level"):
                    addlevel.append((cid, rank, json.dumps(entry.get("skill_add_level"), ensure_ascii=True)))
                for key, value in entry.items():
                    low = key.lower()
                    if any(n in low for n in ("damage", "ratio", "correct", "adjust", "coeff")):
                        damagey.append((cid, rank, key, json.dumps(value, ensure_ascii=True)[:120]))
    emit("entries with NON-EMPTY skill_add_level: %d" % len(addlevel))
    for row in addlevel[:15]:
        emit("   cid=%s rank=%s %s" % row)
    emit("entries whose field name looks like a damage/ratio/coefficient: %d" % len(damagey))
    for row in damagey[:15]:
        emit("   cid=%s rank=%s field=%s value=%s" % row)
else:
    emit("missing: " + path)

# --- 2. the raw dataset: which files mention eidolon/rank config -----------------------------
root = r"E:\turnbasedgamedata"
if os.path.isdir(root):
    hits = []
    for p in glob.glob(os.path.join(root, "**", "*.json"), recursive=True):
        base = os.path.basename(p).lower()
        if "rank" in base or "eidolon" in base:
            hits.append(p)
    emit("dataset files whose NAME mentions rank/eidolon: %d" % len(hits))
    for p in hits[:20]:
        emit("   " + os.path.relpath(p, root))

io.open("tools/_tmp_eidolon_probe.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("written", len(lines), "lines")
