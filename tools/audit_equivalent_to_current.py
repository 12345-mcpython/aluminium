"""Audit (2026-10-02, item 51): sentences that say 「提高数值**等同于当前** X 的 Y%」 must use the DERIVED spelling.

Measured in item 50: an ADDITIVE percentage on a base attribute is a share of the BASE, while
`scale: "self_attr:<X>"` + `percent` adds a FLAT `percent x current X`. So a rule whose own text says
「等同于**当前**…」 but whose effect is a plain `percent` on a base attribute is a BUG -- and the point of this
script is to find any that shipped before that was known.

\u26a0 Written as a FILE on purpose: an inline `python -c` mangles the Chinese literals (the console is GBK), which
already produced a false "0 hits" once.
"""
import glob
import io
import json
import os

BASE = {"HEALTH", "ATTACK", "DEFENCE", "SPEED"}
STRICT = ["等同于当前", "等同于其当前", "等同于自身当前", "等于当前"]
# \u26a0 The wider net, REPORTED SEPARATELY: a sentence may mean the CURRENT value without writing \u300c\u5f53\u524d\u300d
# (\u300c\u63d0\u9ad8\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650\u7684 6%\u300d). These need a human reading, so they are not mixed in with the strict hits.
WIDE = ["\u7b49\u540c\u4e8e\u81ea\u8eab\u751f\u547d\u4e0a\u9650", "\u7b49\u540c\u4e8e\u5176\u751f\u547d\u4e0a\u9650",
        "\u7b49\u540c\u4e8e\u81ea\u8eab\u9632\u5fa1\u529b", "\u7b49\u540c\u4e8e\u81ea\u8eab\u653b\u51fb\u529b"]

suspects = []
wide = []
for path in glob.glob("src/main/resources/**/*.json", recursive=True):
    if "/data/" in path.replace(os.sep, "/"):
        continue
    try:
        doc = json.load(io.open(path, encoding="utf-8"))
    except Exception:
        continue
    if isinstance(doc, dict) and isinstance(doc.get("rules"), list):
        rules = doc["rules"]
    elif isinstance(doc, list):
        rules = doc
    else:
        continue
    for rule in rules:
        if not isinstance(rule, dict):
            continue
        text = (rule.get("source") or "") + " " + (rule.get("note") or "")
        strict = any(n in text for n in STRICT)
        if not strict and not any(n in text for n in WIDE):
            continue
        for eff in (rule.get("do") or []):
            if not isinstance(eff, dict):
                continue
            if eff.get("op") == "MODIFY_ATTR" and eff.get("attribute") in BASE:
                row = (os.path.basename(path), rule.get("id"),
                       eff.get("attribute"), eff.get("percent"), eff.get("amount"),
                       eff.get("scale"), eff.get("buff"))
                (suspects if strict else wide).append(row)

print("STRICT (\u300c\u7b49\u540c\u4e8e\u5f53\u524d\u2026\u300d) base-attribute modifiers:", len(suspects))
for s in suspects:
    print("   file=%s rule=%s attr=%s percent=%s amount=%s scale=%s buff=%s" % s)
print("WIDE (needs a human reading) base-attribute modifiers:", len(wide))
for s in wide[:10]:
    print("   file=%s rule=%s attr=%s percent=%s amount=%s scale=%s buff=%s" % s)
