"""Rewrite the stale note on 8009/8010's gift rule, and record the model finding (round 1653).

Why the note was wrong: it said "【好活当赏】 is declared per-holder, so if the TARGET does not declare it this half lands
nowhere". The loader proved otherwise the very next round -- it refuses a rule whose resource the SAME FILE does not
declare ("Character 8009 has a rule that uses the resource ..., which the character does not declare"), and both files now
declare it. So the declaring file is the file WITH THE RULE, and the note is corrected to say exactly that.

The rule is found by id and its note is rebuilt here, rather than matched: the old text mixes Chinese, markdown bullets and
backticks, and matching it byte-for-byte from another script is what failed three times earlier in this arc.
"""
import io
import json

NEW_NOTE = (
    "\u300c\u82e5\u76ee\u6807\u62e5\u6709\u6b22\u6109\u6280\uff0c\u76ee\u6807\u989d\u5916\u83b7\u5f97 **10** \u70b9\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\uff0c\u5e76**\u4f7f\u5176\u7acb\u5373\u65bd\u653e 1 \u6b21**\u2026\u6b22\u6109\u6280\u300d\u2713\uff08#4 = 10 \u2713\uff09\u3002"
    "\u2b50 **\u53ea\u6709\u7b2c 57 \u4ef6\u4e4b\u540e\u624d\u5199\u5f97\u51fa** \u2713\uff1a\u6b22\u6109\u6280\u7684\u884c**\u9996\u5217\u662f\u6b21\u6570** \u2713\u3002"
    "\u2b50 `CAST_SKILL` \u4ece**\u89e3\u6790\u51fa\u7684\u76ee\u6807**\u53d6\u6280\u80fd \u2713\uff08\u300c\u4f7f**\u5176**\u300d\u2713\uff09\u3002"
    "\u26a0 **\u5df2\u8ba2\u6b63\u7684\u6ce8\u8bb0\uff08\u7b2c 1653 \u8f6e\uff09**\uff1a\u672c\u6761\u539f\u5199\u201c\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u6309**\u6301\u6709\u8005**\u58f0\u660e \u21d2 \u76ee\u6807\u82e5\u672a\u58f0\u660e\u5c31\u843d\u4e0d\u4e0b\u201d \u2717 \u2014\u2014 "
    "\u88c5\u8f7d\u5668**\u5f53\u573a\u8bc1\u4f2a** \u2713\uff1a\u5b83\u8981\u6c42**\u81ea\u5df1\u6709\u89c4\u5219\u7684\u90a3\u4e2a\u6587\u4ef6\u5fc5\u987b\u58f0\u660e\u8be5\u8d44\u6e90** \u2713\uff08\u539f\u8bdd\uff1a"
    "*\u201cCharacter 8009 has a rule that uses the resource \u300c\u597d\u6d3b\u5f53\u8d4f\u300d, which the character does not declare\u201d* \u2713\uff09\uff0c"
    "\u4e24\u4e2a\u6587\u4ef6\u73b0\u5df2\u5404\u81ea\u58f0\u660e \u2713\u3002"
    "\u26a0 **\u4ecd\u767b\u8bb0**\uff1a\u300c\u56fa\u5b9a\u8ba1\u5165 **20** \u7b11\u70b9\u300d\uff08\u963f\u54c8\u91cf\u8868\uff0c\u4e0e\u7b11\u70b9**\u8d44\u6e90**\u4e0d\u540c \u2717\uff09"
    "\u4e0e\u300c\u82e5\u6b22\u6109\u6280\u65bd\u653e\u524d\u654c\u65b9\u76ee\u6807\u88ab\u6d88\u706d\u5219\u5bf9**\u65b0\u5165\u573a**\u7684\u654c\u65b9\u76ee\u6807\u53d1\u52a8\u300d\u2717\u3002"
    "\u26a0 \u5e76\u8bb0\uff08\u7b2c 1653 \u8f6e\u5ba1\u8ba1\uff09\uff1a\u8bed\u6599\u628a\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u53eb**\u72b6\u6001** \u2713\uff08GLOSSARY \u9010\u5b57 \u2713\uff09\uff0c"
    "\u800c\u672c\u6587\u4ef6\u7528\u7684\u662f**\u8d44\u6e90** \u2717 \u21d2 \u540c\u540d\u4e24\u6a21\u578b\u5e76\u5b58 \u2717\uff08\u5df2\u767b\u8bb0\u5f85\u51b3 \u2713\uff09\u3002")

for cid in ("8009", "8010"):
    path = "src/main/resources/characters/%s.json" % cid
    doc = json.load(io.open(path, encoding="utf-8"))
    patched = 0
    for rule in doc.get("rules", []):
        if isinstance(rule, dict) and rule.get("id") == "ult_elation_gift_and_cast":
            rule["note"] = NEW_NOTE
            patched += 1
    if patched != 1:
        raise SystemExit("REFUSING: %s has %d matching rules" % (cid, patched))
    json.dump(doc, io.open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
    print("ok   %s.json: the gift rule's note now states what the loader actually requires" % cid)
