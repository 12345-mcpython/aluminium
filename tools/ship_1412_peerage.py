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
CHARGE = "充能"        # 充能
MERIT = "军功"          # 军功
PEERAGE = "爵位"        # 爵位

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
          "source": "1412 刻律德菈 战技 升变，士皆可帅 (141202): "
                    "「当充能达到 6 点时，自动使角色的【军功】"
                    "升级为【爵位】」" },
    ],
    "source": "1412 刻律德菈 战技 升变，士皆可帅 (141202, BPSkill/Support): "
              "「当充能达到 6 点时，自动使角色的【军功】升级"
              "为【爵位】并解除其控制类负面状态」",
    "note": "「当充能达到 **6** 点时，自动使角色的【军功】升级为"
            "【爵位】」—— 事件 `RESOURCE_CHANGED` ✓ （已存在）＋ "
            "`self_resource:充能 >= 6` ✓ （条件已支持资源读取）＋ "
            "`holder_of:军功` ✓ （选择器已出货）；"
            "【爵位】**不写时长** ✓ （文档没给，与【军功】同为"
            "充能驱动）。⚠ **登记**：同句的「并解除其**控制类负面状态**」"
            "✗ —— 引擎今天没有“按**类**清除负面”的能力（`DISPEL` 是摘"
            "敌方增益、`RESIST_DEBUFF` 是抗性、`REMOVE_BUFF` 是按**数**不是按类）。"
            "⚠ **另一处登记**：文档说「持有【爵位】的角色**被视为同时持有"
            "【军功】**」✓ —— 本条只**加【爵位】、不摘【军功】** ✓，"
            "那句话因此**自然成立** （两个状态同时在）✓。",
})

with io.open(PATH, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
print("appended; rules now %d" % len(rules))
