"""Print Asta's (1009) rules and notes so the clause can be read before anything is changed."""
import io
import json

doc = json.load(io.open("src/main/resources/characters/1009.json", encoding="utf-8"))
out = []
out.append("resources: %s" % json.dumps(doc.get("resources"), ensure_ascii=False)[:300])
for rule in doc.get("rules", []):
    out.append("")
    out.append("id=%s on=%s when=%s" % (rule.get("id"), rule.get("on"), json.dumps(rule.get("when"), ensure_ascii=False)))
    out.append("   do=%s" % json.dumps(rule.get("do"), ensure_ascii=False)[:600])
    if rule.get("note"):
        out.append("   note=%s" % rule["note"][:700])
for key in ("note", "notes", "source", "unimplemented"):
    if doc.get(key):
        out.append("%s: %s" % (key, json.dumps(doc[key], ensure_ascii=False)[:900]))
io.open("tools/_tmp_1009.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(out), "lines")
