"""1415's ode of passage, second sentence -- and the follow-up mark that makes it decidable (2026-10-02).

Verbatim (1141515): 「…**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害。**」 #1 is 1 at every level.

The two ends that meet here:
  * his own follow-up (`talent_followup_on_other_ult`) now STATES `cast_category: "FOLLOW_UP"`, which the DAMAGE op stamps onto the instance;
  * the ode's clause ASKS it with `damage_is_follow_up`, plus `self has_state <the ode>` -- the mark the ode's first clause already applies.

\u2b50 And the mark is also the door that stops the recursion: the clause's own extra instance states no category, so `damage_is_follow_up` is FALSE for it and it cannot
re-trigger itself. (Without a word for this, a clause hanging on additional damage and dealing additional damage loops forever -- the engine's `Trigger recursion
exceeded 8 levels`, which it did raise while this was being written.)
"""
import io
import json
import sys

TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])

follow = [r for r in rules if r.get("id") == "talent_followup_on_other_ult"]
if len(follow) != 1:
    sys.exit("REFUSING: the follow-up rule is not unique (%d)" % len(follow))
follow = follow[0]
if any(e.get("cast_category") for e in follow.get("do", [])):
    sys.exit("REFUSING: the follow-up already states a category")

# \u2b50 the ode's own name, taken from the mark its first clause applies -- no second spelling
odes = [r for r in rules if r.get("id") == "memosprite_ode_of_passage_makes_his_damage_ignore_defence"]
if len(odes) != 1:
    sys.exit("REFUSING: the first clause is not unique (%d)" % len(odes))
marks = [e.get("buff") for e in odes[0].get("do", []) if e.get("op") == "APPLY_BUFF" and e.get("buff")]
if len(marks) != 1:
    sys.exit("REFUSING: the first clause applies no single mark (%s)" % marks)
ODE = marks[0]
print("the ode's mark is %r" % ODE)

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"]["15"].get("param_list") or []
if not rows or any(r[0] != 1 for r in rows):
    sys.exit("REFUSING: #1 is not 1 at every level")
print("ok   #1 is 1 at all %d levels of 11415/15" % len(rows))

# 1) the follow-up declares itself
for e in follow["do"]:
    if e.get("op") == "DAMAGE":
        e["cast_category"] = "FOLLOW_UP"
follow["note"] = (follow.get("note", "") +
                  " \u2b50 2026-10-02\uff1a\u5b83\u58f0\u660e\u4e86 `cast_category: \"FOLLOW_UP\"` \u2014\u2014 "
                  "\u56e0\u4e3a 1415 \u7684\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u8981\u95ee\u201c\u8fd9\u662f\u5426\u662f**\u8ffd\u52a0\u653b\u51fb**\u201d\uff0c\u800c\u8fd9\u4e2a\u6807\u8bb0\u540c\u65f6\u4e5f\u662f**\u9632\u6b62\u9012\u5f52**\u7684\u95e8\u3002")

RULE_ID = "ode_of_passage_extra_instance"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "FOLLOW_UP",
    "when": ["actor == self", "damage_is_follow_up", "self has_state " + ODE],
    "do": [{
        "op": "DAMAGE",
        "times": 1,                                              # \u300c\u989d\u5916\u9020\u6210 #1 \u6b21\u300d, and #1 is 1 at EVERY level
        "scale": "owner_max_hp",
        "percent_from_skill_param": "ULTRA:2",                    # \u540c\u4e00\u7b14\u9644\u52a0\u4f24\u5bb3\uff1a#3 \u00d7 \u751f\u547d\u4e0a\u9650
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c\u732e\u4e88\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 15\uff0cSkillID 1141515\uff09\uff1a"
               "\u300c**\u7f07\u5b9d\u65bd\u653e\u8ffd\u52a0\u653b\u51fb\u89e6\u53d1\u7f07\u5b9d\u7684\u7ed3\u754c\u7684\u9644\u52a0\u4f24\u5bb3\u65f6\uff0c\u4f1a\u989d\u5916\u9020\u6210 #1 \u6b21\u9644\u52a0\u4f24\u5bb3**\u3002\u300d"),
    "note": ("\u2b50 \u4e09\u4ef6\u90fd\u662f\u672c\u8f6e\uff0f\u8fd1\u51e0\u8f6e\u51fa\u8d27\u7684\uff1a`SkillCategory.FOLLOW_UP`\uff08**\u65b0\u8bcd**\uff09\u3001`cast_category`\uff08DAMAGE op \u628a\u5b83\u5370\u5728\u5b9e\u4f8b\u4e0a\uff09\u3001"
             "`damage_is_follow_up`\uff08\u6761\u4ef6\uff09\u3002\u2b50 **\u800c\u8fd9\u4e2a\u8bcd\u540c\u65f6\u5c31\u662f\u9632\u9012\u5f52\u7684\u95e8**\uff1a\u672c\u6761\u9020\u7684\u90a3\u7b14\u5b9e\u4f8b"
             "**\u4e0d\u58f0\u660e\u7c7b\u522b**\uff0c\u6240\u4ee5 `damage_is_follow_up` \u5bf9\u5b83\u4e3a**\u5047**\uff0c\u5b83\u89e6\u53d1\u4e0d\u4e86\u81ea\u5df1\u3002"
             "\u26a0\uff08\u6ca1\u6709\u8fd9\u4e2a\u8bcd\u65f6\uff0c\u6302\u5728\u9644\u52a0\u4f24\u5bb3\u4e0a\u3001\u53c8\u4ea7\u51fa\u9644\u52a0\u4f24\u5bb3\u7684\u89c4\u5219\u4f1a**\u6c38\u8fdc\u5faa\u73af**\uff0c"
             "\u5f15\u64ce\u5f53\u65f6\u786e\u5b9e\u62a5\u4e86 `Trigger recursion exceeded 8 levels`\u3002\uff09"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules) and its follow-up declares itself" % (RULE_ID, len(rules)))
