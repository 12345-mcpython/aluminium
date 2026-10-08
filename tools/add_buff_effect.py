"""The Buff slice's engine half + its data row (2026-10-02; reader: 1412's skill marking an ally with a named state).

Four patches, each with an assertion so a silent no-op cannot pass as a change:
  1. SkillEffectSpec gains the `buff` field the data row needs.
  2. deliverableSpec accepts "Buff" (and skips the parameter-summing guard, which does not apply to a named state).
  3. dispatchNonDamaging attaches the state (BuffManager.addBuff, the public plain-attach path).
  4. skill_effects.json gains 1412's slot 2.
Run from the repository root. ASCII only, on purpose.
"""
import io
import json
import sys

ROOT = "."


def patch(path, old, new, label):
    with io.open(path, encoding="utf-8") as handle:
        text = handle.read()
    if new in text and old not in text:
        print("skip %s: already applied" % label)
        return
    count = text.count(old)
    if count != 1:
        print("FAIL %s: anchor matched %d times" % (label, count))
        sys.exit(1)
    with io.open(path, "w", encoding="utf-8", newline="") as handle:
        handle.write(text.replace(old, new))
    print("ok   %s" % label)


# 1) the bean field -------------------------------------------------------------------------------------
patch(
    ROOT + "/src/main/java/com/laosun/aluminium/beans/SkillEffectSpec.java",
    '    @SerializedName("formula")',
    '    @SerializedName("buff")\n'
    '    private String buff;\n'
    '    @SerializedName("formula")',
    "SkillEffectSpec.buff",
)

# 2) what counts as deliverable, and 3) the attach --------------------------------------------------------
SKILL_EXECUTOR = ROOT + "/src/main/java/com/laosun/aluminium/models/skill/SkillExecutor.java"

patch(
    SKILL_EXECUTOR,
    'import com.laosun.aluminium.Battle;',
    'import com.laosun.aluminium.Battle;\n'
    'import com.laosun.aluminium.models.buff.StateBuff;',
    "SkillExecutor import StateBuff",
)

patch(
    SKILL_EXECUTOR,
    '                && ("Restore".equals(spec.getEffect()) || "Defence".equals(spec.getEffect()))\n'
    '                && !isAmbiguous(spec);',
    '                && ("Restore".equals(spec.getEffect()) || "Defence".equals(spec.getEffect())\n'
    '                        || "Buff".equals(spec.getEffect()))\n'
    '                // A Buff row names a state instead of carrying numbers, so the parameter-summing guard -- which\n'
    '                // exists for heal rows that mix several terms -- has nothing to sum and must not refuse it.\n'
    '                && ("Buff".equals(spec.getEffect()) || !isAmbiguous(spec));',
    "deliverableSpec accepts Buff",
)

patch(
    SKILL_EXECUTOR,
    '        for (CanHit target : targets) {\n'
    '            double amount = effectAmount(skill, spec, user, target);\n',
    '        for (CanHit target : targets) {\n'
    '            if ("Buff".equals(spec.getEffect())) {\n'
    '                // The one non-damaging shape that carries a NAME rather than an amount (2026-10-02; reader: 1412\n'
    '                // marking an ally with a charge-driven state). No duration is stated by the text, so the state is\n'
    '                // attached as permanent -- an unbounded turn count, exactly like a rule that omits `turns`.\n'
    '                target.getBuffManager().addBuff(new StateBuff(spec.getBuff(), Integer.MAX_VALUE, true));\n'
    '                continue;\n'
    '            }\n'
    '            double amount = effectAmount(skill, spec, user, target);\n',
    "dispatchNonDamaging attaches Buff",
)

# 4) the data row -----------------------------------------------------------------------------------------
EFFECTS = ROOT + "/src/main/resources/data/skill_effects.json"
with io.open(EFFECTS, encoding="utf-8") as handle:
    effects = json.load(handle)
if "1412" in effects:
    print("FAIL skill_effects.json: 1412 already present")
    sys.exit(1)
effects["1412"] = {
    "2": {
        "effect": "Buff",
        "buff": "\u519b\u529f",
        "turns_param": None,
        "formula": None,
        "source": "1412 \u523b\u5f8b\u5fb7\u83c8 \u6218\u6280 \u5347\u53d8\uff0c\u58eb\u7686\u53ef\u5e05 (141502, BPSkill/Support) "
                  "\u7b2c\u4e00\u53e5\uff1a\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u89d2\u8272\u83b7\u5f97\u3010\u519b\u529f\u3011\u300d "
                  "+ SkillDesc \u69fd\u4f4d",
    }
}
with io.open(EFFECTS, "w", encoding="utf-8", newline="\n") as handle:
    # Match the file's own layout exactly (4-space indent, no trailing newline) so the diff is one entry.
    json.dump(effects, handle, ensure_ascii=False, indent=4)
print("ok   skill_effects.json 1412 slot 2 (keys: %d)" % len(effects))
