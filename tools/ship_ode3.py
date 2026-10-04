import io, json, sys

# the state name comes out of the TWIN RULE, read element by element (joining first is how it went wrong before)
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
twin = [r for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"]
if len(twin) != 1:
    sys.exit("REFUSING: the twin rule is not unique")
names = [w[len("self has_state "):] for w in twin[0].get("when", []) if isinstance(w, str) and w.startswith("self has_state ")]
print("state names in the twin rule: %s" % names)
if len(names) != 1:
    sys.exit("REFUSING: ambiguous")
bf = names[0]

J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeBloodfeudTest.java"
j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
io.open(J, "w", encoding="utf-8", newline="\n").write(j)
print("ok   the judge uses the twin rule's own state name")

if not any(r.get("id") == "memosprite_ode_commands_the_godslayer_in_bloodfeud" for r in rules):
    rules.append({
        "id": "memosprite_ode_commands_the_godslayer_in_bloodfeud",
        "on": "SKILL_CAST",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16", "self has_state " + bf],
        "do": [
            {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "turns": 1, "target": "self"},
            {"op": "CAST_SKILL", "skill": "SKILL", "target": "self"},
        ],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 8 \u300c\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 16\uff0cSkillID 1141516\uff09\uff1a"
                   "\u300c\u82e5\u4e07\u654c\u5904\u4e8e\u3010\u8840\u4ec7\u3011\u72b6\u6001\uff0c\u5219\u4f7f\u5176\u81ea\u52a8\u65bd\u653e 1 \u6b21**\u4e0d\u6d88\u8017\u5145\u80fd**\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d\u3002"
                   "\uff08\u5f62\u72b6\u7167\u62c4\u540c\u6587\u4ef6\u7684 `bloodfeud_godslayer_at_a_hundred_and_fifty`\uff1a\u540c\u4e00\u5bf9 `REPLACE_SKILL` \u52a0 `CAST_SKILL`\uff0c"
                   "\u5dee\u522b\u53ea\u5728\u90a3\u6761\u591a\u51fa\u7684 `SPEND_RESOURCE`\u2014\u2014\u6b63\u662f\u300c\u4e0d\u6d88\u8017\u5145\u80fd\u300d\u8fd9\u56db\u4e2a\u5b57\u3002\uff09"),
        "note": ("\u26a0 \u89e6\u53d1\u70b9\u5728**\u4e07\u654c\u81ea\u5df1\u7684\u8868**\u4e0a\uff0c\u7528 `target == self`\uff08\u5df2\u51fa\u8d27\u7684 20 \u6761\u540c\u5f62\u5f0f\uff09\u3002"
                 "\u2605 **\u65b0\u589e\u8c13\u8bcd `is_summon`**\uff1a`from_skill_id` \u8bfb\u7684\u662f**\u69fd\u4f4d**\uff08\u4e0d\u662f\u6570\u636e\u884c id\uff09\uff0c"
                 "\u5355\u72ec\u7528\u4f1a\u8ba9**\u4efb\u4f55\u5176\u4ed6\u5355\u4f4d\u7684 16 \u53f7\u6280\u80fd**\u6253\u4e2d\u4ed6\u65f6\u4e5f\u8bef\u89e6\u53d1\uff1b`actor is_summon` \u624d\u80fd\u628a\u201c\u65bd\u653e\u8005\u662f\u5fc6\u7075\u201d\u8bf4\u51fa\u6765\u3002"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the bloodfeud branch is on 1404's table")
