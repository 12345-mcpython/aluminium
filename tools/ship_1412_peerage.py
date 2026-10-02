"""Ship 1412's 【军功】->【爵位】 upgrade (2026-10-02).

The sentence: 「当充能达到 6 点时，自动使角色的【军功】升级为【爵位】并解除其控制类负面状态」.
The upgrade half is expressible with vocabulary that already ships: RESOURCE_CHANGED + self_resource:<name> +
holder_of:<state>, reusing the argument keys the file's own 军功 rule already uses -- so nothing here is guessed.
The dispel half is registered instead (no "clear a class of debuffs" capability exists).

Appends the rule to 1412.json, copying key names from the existing rule.
"""
import io
import json

PATH = "src/main/resources/characters/1412.json"
CHARGE = "\u5145\u80fd"        # 充能
MERIT = "\u519b\u529f"          # 军功
PEERAGE = "\u7235\u4f4d"        # 爵位

with io.open(PATH, encoding="utf-8") as handle:
    doc = json.load(handle)

rules = doc["rules"]
existing = next(r for r in rules if r.get("id") == "skill_grants_military_merit")
by_op = {step["op"]: step for step in existing["do"]}
state_key = next(k for k in by_op["REMOVE_STATE"] if k not in ("op", "target"))
buff_key = next(k for k in by_op["APPLY_BUFF"] if k not in ("op", "target"))
print("copied keys: REMOVE_STATE.%s, APPLY_BUFF.%s" % (state_key, buff_key))

if any(r.get("id") == "peerage_upgrade_at_six_charge" for r in rules):
    print("already shipped")
    raise SystemExit(0)

holder = "holder_of:" + MERIT
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": "peerage_upgrade_at_six_charge",
    "when": ["actor == self", "self_resource:%s >= 6" % CHARGE],
    "do": [
        { "op": "APPLY_BUFF", buff_key: PEERAGE, "target": holder,
          "source": "1412 \u523b\u5f8b\u5fb7\u83c8 \u6218\u6280 \u5347\u53d8\uff0c\u58eb\u7686\u53ef\u5e05 (141202): "
                    "\u300c\u5f53\u5145\u80fd\u8fbe\u5230 6 \u70b9\u65f6\uff0c\u81ea\u52a8\u4f7f\u89d2\u8272\u7684\u3010\u519b\u529f\u3011"
                    "\u5347\u7ea7\u4e3a\u3010\u7235\u4f4d\u3011\u300d" },
    ],
    "source": "1412 \u523b\u5f8b\u5fb7\u83c8 \u6218\u6280 \u5347\u53d8\uff0c\u58eb\u7686\u53ef\u5e05 (141202, BPSkill/Support): "
              "\u300c\u5f53\u5145\u80fd\u8fbe\u5230 6 \u70b9\u65f6\uff0c\u81ea\u52a8\u4f7f\u89d2\u8272\u7684\u3010\u519b\u529f\u3011\u5347\u7ea7"
              "\u4e3a\u3010\u7235\u4f4d\u3011\u5e76\u89e3\u9664\u5176\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001\u300d",
    "note": "\u300c\u5f53\u5145\u80fd\u8fbe\u5230 **6** \u70b9\u65f6\uff0c\u81ea\u52a8\u4f7f\u89d2\u8272\u7684\u3010\u519b\u529f\u3011\u5347\u7ea7\u4e3a"
            "\u3010\u7235\u4f4d\u3011\u300d\u2014\u2014 \u4e8b\u4ef6 `RESOURCE_CHANGED` \u2713 \uff08\u5df2\u5b58\u5728\uff09\uff0b "
            "`self_resource:\u5145\u80fd >= 6` \u2713 \uff08\u6761\u4ef6\u5df2\u652f\u6301\u8d44\u6e90\u8bfb\u53d6\uff09\uff0b "
            "`holder_of:\u519b\u529f` \u2713 \uff08\u9009\u62e9\u5668\u5df2\u51fa\u8d27\uff09\uff1b"
            "\u3010\u7235\u4f4d\u3011**\u4e0d\u5199\u65f6\u957f** \u2713 \uff08\u6587\u6863\u6ca1\u7ed9\uff0c\u4e0e\u3010\u519b\u529f\u3011\u540c\u4e3a"
            "\u5145\u80fd\u9a71\u52a8\uff09\u3002\u26a0 **\u767b\u8bb0**\uff1a\u540c\u53e5\u7684\u300c\u5e76\u89e3\u9664\u5176**\u63a7\u5236\u7c7b\u8d1f\u9762\u72b6\u6001**\u300d"
            "\u2717 \u2014\u2014 \u5f15\u64ce\u4eca\u5929\u6ca1\u6709\u201c\u6309**\u7c7b**\u6e05\u9664\u8d1f\u9762\u201d\u7684\u80fd\u529b\uff08`DISPEL` \u662f\u6458"
            "\u654c\u65b9\u589e\u76ca\u3001`RESIST_DEBUFF` \u662f\u6297\u6027\u3001`REMOVE_BUFF` \u662f\u6309**\u6570**\u4e0d\u662f\u6309\u7c7b\uff09\u3002"
            "\u26a0 **\u53e6\u4e00\u5904\u767b\u8bb0**\uff1a\u6587\u6863\u8bf4\u300c\u6301\u6709\u3010\u7235\u4f4d\u3011\u7684\u89d2\u8272**\u88ab\u89c6\u4e3a\u540c\u65f6\u6301\u6709"
            "\u3010\u519b\u529f\u3011**\u300d\u2713 \u2014\u2014 \u672c\u6761\u53ea**\u52a0\u3010\u7235\u4f4d\u3011\u3001\u4e0d\u6458\u3010\u519b\u529f\u3011** \u2713\uff0c"
            "\u90a3\u53e5\u8bdd\u56e0\u6b64**\u81ea\u7136\u6210\u7acb** \uff08\u4e24\u4e2a\u72b6\u6001\u540c\u65f6\u5728\uff09\u2713\u3002",
})

with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
print("appended; rules now %d" % len(rules))
