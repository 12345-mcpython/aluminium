"""Correct the two notes in 1415.json: the LATER writer is the one that must be stackable (2026-10-02).

Measured chain, now closed:
  * `BuffManager.addBuff` branches on the INCOMING buff: `if (buff.isStackable()) { addStackable(buff); return; }`,
    and only a NON-stackable incomer walks the `isSameKind` replace loop;
  * `StatModifierBuff.isStackable()` is exactly `maxStacks > 1`;
  * so the later writer needs `max_stacks >= 2`; the earlier one's value does not matter (measured: mutating the talent's
    value to 1 left the reading at 0.4).
Earlier notes here said both sides must state it, and one blamed `permanent`. Both claims are withdrawn and replaced.

The reading this note has to agree with: 0.2 below her threshold, 0.4 past it.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1415.json"

NEW_TALENT_NOTE = (
    " \u2b50 2026-10-02 \u5b9e\u6d4b\uff1a`\"max_stacks\": 2` \u2713 \u2014\u2014 \u540c\u5c5e\u6027\u591a\u6765\u6e90\u80fd\u4e0d\u80fd\u76f8\u52a0\uff0c"
    "**\u770b\u7684\u662f\u540e\u5199\u7684\u90a3\u4e00\u6761** \u2713\uff08`BuffManager.addBuff` \u5148\u5224 `buff.isStackable()` \u2713\uff0c"
    "\u53ea\u6709**\u4e0d\u53ef\u53e0\u52a0**\u7684\u6765\u8005\u624d\u8d70 `isSameKind` \u66ff\u6362\u5faa\u73af \u2713\uff09\uff1b"
    "\u2b50 \u800c `StatModifierBuff.isStackable()` \u5c31\u662f `maxStacks > 1` \u2713\u3002"
    "\u26a0 \u672c\u6761\uff08\u5165\u573a\u66f4\u65e9\u7684\u90a3\u4e2a\uff09\u5199\u591a\u5c11**\u4e0d\u5f71\u54cd**\u7ed3\u679c \u2713\uff1a"
    "\u628a\u5b83\u6539\u6210 1 \u540e\u8bfb\u6570**\u4ecd\u662f 0.4** \u2713\uff08\u5df2\u6d4b \u2713\uff09\u3002")

NEW_TRACE_NOTE = (
    "\u2b50 \u4e0e\u5929\u8d4b\u540c\u5c5e\u6027 \u2713 \u21d2 **\u5b83\u662f\u540e\u5199\u7684\u90a3\u4e00\u6761** \u2713 \u21d2 \u5fc5\u987b `\"max_stacks\": 2` \u2713"
    "\uff08\u5b9e\u6d4b\uff1a\u5b83\u5199 1 \u65f6\u8bfb\u6570\u56de\u5230 **0.2** \u2717 \u2014\u2014 \u4e0d\u53ef\u53e0\u52a0\u7684\u6765\u8005\u4f1a\u628a\u5929\u8d4b\u90a3\u6761**\u66ff\u6362\u6389** \u2713\uff09\u3002"
    "\u26a0 \u4e0e `permanent` **\u65e0\u5173** \u2717\uff08\u8be5\u731c\u6d4b\u5df2\u8bc1\u4f2a \u2713\uff09\u3002")

doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
changed = 0

for r in rules:
    if not isinstance(r, dict):
        continue
    if r.get("id") == "talent_party_damage":
        note = r.get("note") or ""
        marker = " \u2b50 2026-10-02"
        if marker in note:
            note = note[:note.index(marker)]
        r["note"] = note + NEW_TALENT_NOTE
        changed += 1
    elif r.get("id") == "trace_party_damage_at_speed_180":
        r["note"] = NEW_TRACE_NOTE
        changed += 1

print("notes rewritten:", changed)
json.dump(doc, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1415.json notes carry the corrected mechanism")
