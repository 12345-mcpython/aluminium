"""One batched sweep of tbgd for two questions (2026-10-02, round 62).

  (1) the CAP of `MServant_CyreneServant_00_AmazingBuff_Hyacine` -- the last blocker on slot 19's 「获得2层」;
  (2) the definition of 「奇袭」 -- the blocker on slot 23's 「奇袭结束后，使刻律德菈获得 #2 点充能」.

Both were previously chased one hop per call. This does it in one pass: walk the tree, and for every file that mentions the name, print the count-ish keys that sit near it.
"""
import io
import json
import os
import re

TB = "E:/turnbasedgamedata"
TEXTMAP = TB + "/TextMap/TextMapCHS.json"
chs = json.load(io.open(TEXTMAP, encoding="utf-8"))

out = []

# ---------- (1) the cap ----------
NEEDLE = "AmazingBuff_Hyacine"
COUNT_KEYS = re.compile(r'"(MaxLayer|LayerAddWhenStack|MaxStack|StackLimit|MaxCount|LayerLimit)"\s*:')
hits = []
for root, dirs, files in os.walk(TB):
    if ".git" in root:
        continue
    for f in files:
        if not f.endswith(".json"):
            continue
        p = os.path.join(root, f)
        try:
            t = io.open(p, encoding="utf-8", errors="ignore").read()
        except Exception:
            continue
        if NEEDLE not in t:
            continue
        rel = os.path.relpath(p, TB)
        near = []
        for m in re.finditer(re.escape(NEEDLE), t):
            window = t[max(0, m.start() - 3000):m.start() + 3000]
            for k in COUNT_KEYS.finditer(window):
                near.append(window[k.start():k.start() + 120].replace("\n", " "))
        hits.append((rel, len(re.findall(re.escape(NEEDLE), t)), near[:4], len(t)))

out.append("=== files mentioning %s: %d" % (NEEDLE, len(hits)))
for rel, n, near, size in hits:
    out.append("  %s (%d mentions, %d bytes)" % (rel, n, size))
    for s in near:
        out.append("     cap-ish: %s" % s.strip()[:150])

# ---------- (2) 奇袭 ----------
want = "\u5947\u88ad"                      # 奇袭
hashes = [k for k, v in chs.items() if isinstance(v, str) and want in v]
out.append("")
out.append("=== TextMap entries containing %s: %d" % (want, len(hashes)))
for h in hashes[:6]:
    out.append("  hash %s -> %s" % (h, chs[h][:90]))
# where is that name DEFINED as a status?
files_with = []
for root, dirs, files in os.walk(TB):
    if ".git" in root:
        continue
    for f in files:
        if not f.endswith(".json"):
            continue
        p = os.path.join(root, f)
        try:
            t = io.open(p, encoding="utf-8", errors="ignore").read()
        except Exception:
            continue
        if any(h in t for h in hashes[:2]):
            files_with.append(os.path.relpath(p, TB))
out.append("=== files referencing those hashes: %s" % files_with[:10])

io.open("tools/_sweep.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("files with the Hyacine mention: %d ; files referencing 奇袭: %d" % (len(hits), len(files_with)))
