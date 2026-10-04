"""Audit probe (round 1658): objectives ② (1412's peerage chain) and ③ (the memosprite channel).

Reads, per character: every rule's event, conditions, ops and whether a duration/Eidolon gate is stated -- plus which
judges exist by name. ASCII-safe printing (ops/ids are ASCII); Chinese notes are counted, not printed.
"""
import io
import json
import glob
import os

out = []


def rules_of(doc):
    if isinstance(doc, dict) and "rules" in doc:
        return doc["rules"]
    return doc if isinstance(doc, list) else []


for cid in ("1412", "1407", "1512"):
    path = "src/main/resources/characters/%s.json" % cid
    out.append("=== %s (%s)" % (cid, "exists" if os.path.exists(path) else "MISSING"))
    if not os.path.exists(path):
        continue
    doc = json.load(io.open(path, encoding="utf-8"))
    rules = rules_of(doc)
    out.append("   rules: %d" % len(rules))
    for rule in rules:
        if not isinstance(rule, dict):
            continue
        ops = []
        for effect in rule.get("do", []):
            tag = effect.get("op")
            extra = effect.get("buff") or effect.get("resource") or effect.get("attribute") or effect.get("skill") or ""
            if effect.get("turns") is not None:
                extra = (extra + " turns=%s" % effect.get("turns")).strip()
            ops.append(("%s:%s" % (tag, extra)) if extra else tag)
        out.append("   %-42s on=%-18s when=%-46s do=%s" % (
            rule.get("id"), rule.get("on"), json.dumps(rule.get("when"), ensure_ascii=True), ",".join(ops)))
    if isinstance(doc, dict) and doc.get("resources"):
        for res in doc["resources"]:
            out.append("   RESOURCE id=%s max=%s scope=%s" % (res.get("id"), res.get("max"), res.get("scope")))

out.append("")
out.append("=== judges whose name mentions the relevant mechanisms ===")
for pattern in ("*Peerage*", "*Coup*", "*Memosprite*", "*Panel*", "*Cheer*", "*Summon*"):
    for path in sorted(glob.glob("src/test/java/com/laosun/aluminium/test/" + pattern + ".java")):
        out.append("   " + os.path.basename(path))

io.open("tools/_tmp_audit2.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
