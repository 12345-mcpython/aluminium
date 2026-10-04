"""The servant config entries for 11409 / 11415 (round 1672).

`ExcelOutput/AvatarServantConfig.json` carries HPBase / HPInherit / SpeedBase / SpeedInherit / SpeedSkill / Aggro per servant.
This prints the two entries plus a known one for comparison, so the panel can be written from the table rather than inferred.
"""
import io
import json

PATH = "E:/turnbasedgamedata/ExcelOutput/AvatarServantConfig.json"
doc = json.load(io.open(PATH, encoding="utf-8"))
out = []

entries = doc if isinstance(doc, list) else doc.get("ServantConfig", doc)
if isinstance(entries, dict):
    entries = list(entries.values())

wanted = {11402, 11407, 11409, 11413, 11415, 11512}
for entry in entries:
    if not isinstance(entry, dict):
        continue
    identifier = entry.get("ServantID")
    if identifier in wanted or identifier in ("11409", "11415"):
        out.append(json.dumps(entry, ensure_ascii=False, indent=2)[:1400])
        out.append("")
io.open("tools/_tmp_servant_config.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out) or "no entries")
print("written", len(out), "lines")
