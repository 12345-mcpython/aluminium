"""Probe v2 (tbgd, 2026-10-02): eidolons and a possible damage correction coefficient.

v1 assumed a dict and read almost nothing -- `AvatarRankConfig.json` is a LIST. This version handles both shapes and
also opens `ExtraEffectConfig.json`, which is where `extra_effect_id` points and therefore the likeliest home of any
"correction". Read-only, ASCII only, output to a UTF-8 file.
"""
import io
import json
import os

ROOT = r"E:\turnbasedgamedata"
EXCEL = os.path.join(ROOT, "ExcelOutput")
lines = []


def emit(text):
    lines.append(text)


def load(name):
    path = os.path.join(EXCEL, name)
    if not os.path.exists(path):
        emit("missing: %s" % path)
        return None
    try:
        return json.load(io.open(path, encoding="utf-8"))
    except Exception as exc:
        emit("FAILED to load %s: %s" % (path, exc))
        return None


def entries(doc):
    """Yield dict entries from a list-shaped or dict-shaped config."""
    if isinstance(doc, list):
        for item in doc:
            if isinstance(item, dict):
                yield item
    elif isinstance(doc, dict):
        for item in doc.values():
            if isinstance(item, dict):
                yield item


def describe(name, doc, sample_limit=2):
    items = list(entries(doc))
    keys = set()
    for item in items:
        keys.update(item.keys())
    emit("== %s: %d entries" % (name, len(items)))
    emit("   fields: %s" % json.dumps(sorted(keys), ensure_ascii=True))
    suspect = sorted(k for k in keys
                     if any(n in k.lower() for n in ("damage", "ratio", "correct", "adjust", "coeff", "param")))
    emit("   damage-ish fields: %s" % json.dumps(suspect, ensure_ascii=True))
    for item in items[:sample_limit]:
        emit("   sample: %s" % json.dumps(item, ensure_ascii=True)[:700])


rank = load("AvatarRankConfig.json")
if rank is not None:
    describe("AvatarRankConfig", rank, sample_limit=1)
    # A known case: rank 3 of 1001, whose text is 「战技等级+1，普攻等级+1」
    for item in entries(rank):
        if str(item.get("AvatarID")) == "1001" and str(item.get("Rank")) == "3":
            emit("   1001/3 full entry: %s" % json.dumps(item, ensure_ascii=True)[:700])

extra = load("ExtraEffectConfig.json")
if extra is not None:
    describe("ExtraEffectConfig", extra, sample_limit=1)
    for item in entries(extra):
        ids = json.dumps(item, ensure_ascii=True)
        if "10000007" in ids:
            emit("   entry mentioning 10000007: %s" % ids[:700])
            break

io.open("tools/_tmp_tbgd_rank2.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("written", len(lines), "lines")
