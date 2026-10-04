"""Mutation toggle for the two panels: change 1409's health share, so the judge must notice.

off -> 0.5 becomes 0.4 (a wrong share the document does not state)
on  -> back to 0.5
"""
import io
import json
import sys

PATH = "src/main/resources/memosprites/1409.json"
mode = (sys.argv[1] if len(sys.argv) > 1 else "").strip().lower()
doc = json.load(io.open(PATH, encoding="utf-8"))
entry = doc["panel"][0]
if entry.get("attribute") != "HEALTH":
    sys.exit("REFUSING: the first entry is not HEALTH")
if mode == "off":
    if entry.get("percent") != 0.5:
        sys.exit("REFUSING: expected 0.5, found %r" % entry.get("percent"))
    entry["percent"] = 0.4
elif mode == "on":
    if entry.get("percent") != 0.4:
        sys.exit("REFUSING: expected the mutated 0.4, found %r" % entry.get("percent"))
    entry["percent"] = 0.5
else:
    sys.exit("usage: mut_ica_share.py on|off")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("%s: 1409 health share is %s" % (mode, entry["percent"]))
