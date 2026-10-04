"""Ship 1314's live per-stack clauses (round 1665).

Her own note registered exactly this gap, verbatim: the trace 「绝当品：天赋中每层【当品】额外使翡翠的攻击力提高 0.50%」 has no
spelling, because `ATTACK` is a flat attribute and "a share of the base, times the count" did not exist -- the derived form
gives an absolute number and a plain modifier gives ONE layer's share. Both of her `per_stack` clauses have the same defect:
they snapshot the count when they are attached, so stacks gained later do not follow.

The patch is JSON-level: every effect carrying `per_stack` in this file gains `per_stack_live: true`, and the notes are left
untouched (they are the record).
"""
import io
import json
import sys

PATH = "src/main/resources/characters/1314.json"
with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)

changed = []


def walk(node, path):
    if isinstance(node, list):
        for index, item in enumerate(node):
            walk(item, "%s[%d]" % (path, index))
        return
    if not isinstance(node, dict):
        return
    if node.get("per_stack") and node.get("op") == "MODIFY_ATTR":
        if node.get("per_stack_live") is not True:
            node["per_stack_live"] = True
            changed.append("%s per_stack=%s" % (path, node["per_stack"]))
    for key, value in node.items():
        walk(value, "%s.%s" % (path, key))


walk(doc, "$")
if not changed:
    sys.exit("REFUSING: nothing to mark, or already marked")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("marked %d effect(s): %s" % (len(changed), "; ".join(changed)))
