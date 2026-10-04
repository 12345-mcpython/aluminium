"""Which slot is 【弑神登神】? (round 3 of the goal)

The clause needs `CAST_SKILL{skill: <slot>}`, so the slot has to come from the document rather than from a guess. His page's skill
headings carry the names, and each row has an id.
"""
import io
import re

path = "E:/turnbasedgamedata/aluminium_texts/1404_\u4e07\u654c.md"
flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", io.open(path, encoding="utf-8", errors="replace").read()))
out = []

out.append("=== skill headings (### ...) ===")
for match in re.finditer(r"###\s*([^#]{0,60})", flat):
    out.append("   " + match.group(1).strip()[:80])

out.append("")
out.append("=== every id-looking token near a skill name ===")
for term in ("\u5f3a\u5316", "\u5f3a\u5316\u666e\u653b", "\u5f3a\u5316\u6218\u6280"):
    for match in list(re.finditer(term, flat))[:6]:
        out.append("   [%s] ...%s..." % (term, flat[max(0, match.start() - 120):match.start() + 170]))

out.append("")
out.append("=== rows mentioning 弑王成王 or 弑神登神, with ids ===")
for term in ("\u5f11\u738b\u6210\u738b", "\u5f11\u795e\u767b\u795e"):
    for match in list(re.finditer(term, flat))[:4]:
        out.append("   [%s] ...%s..." % (term, flat[max(0, match.start() - 200):match.start() + 230]))

out.append("")
out.append("=== the 4-6 digit ids her page carries (first 24) ===")
ids = re.findall(r"\(?`?(1404\d{2,3})`?\)?", flat)
out.append("   " + ", ".join(sorted(set(ids))[:24]))

io.open("tools/_probe_his_skills.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written %d lines" % len(out))
