"""`spendAll` + 1513's documented clause (2026-10-02), with a FILE-DRIVEN judge per the new discipline.

Reader/sentence (1513_砂金•戏浪.md:283): 「施放时会消耗所有【热意】，每消耗1点【热意】都会额外对随机敌方单体造成1次21.00%
量子属性欢愉伤害」. 【热意】 is already declared in her file (`max: 30`) and her file already gains it on `ALLY_ATTACK`
(「队友施放攻击后，砂金•戏浪获得1点【热意】」), so the judge needs NO hand-built table: fire her own ALLY_ATTACK rule
seven times to prime 7, then run the cast rule.

Shape: the clause is TWO rules by construction -- the spend fires RESOURCE_CHANGED with the negative delta, and the
second rule reads it through the shipped `times_from`. One rule cannot, because `ctx.amount()` holds the TRIGGER's amount.

Engine: `EffectSpec.spendAll` (+copy), the loader's GAIN/SPEND arm (exclusive with `amount`), and `spendResource`.
ASCII only.
"""
import io
import json
import sys

SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INTERP = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
DATA = "src/main/resources/characters/1513.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/SpendAllTest.java"
RES = "热意"

spec = io.open(SPEC, encoding="utf-8").read()
if "spendAll" not in spec:
    ANCHOR = '    @SerializedName("times_from")'
    FIELD = ('    /**\n'
             '     * 「消耗所有【X】」 (2026-10-02): {@code SPEND_RESOURCE} takes whatever the holder has.\n'
             '     * Mutually exclusive with {@code amount}, because "all of it" and "5 of it" are different claims.\n'
             '     */\n'
             '    @SerializedName("spendAll")\n'
             '    private Boolean spendAll;\n'
             '\n' + ANCHOR)
    if spec.count(ANCHOR) != 1:
        print("FAIL spec: field anchor matched %d times" % spec.count(ANCHOR))
        sys.exit(1)
    spec = spec.replace(ANCHOR, FIELD)
    COPY = "        copy.timesFrom = this.timesFrom;\n"
    if spec.count(COPY) != 1:
        print("FAIL spec: copy anchor matched %d times" % spec.count(COPY))
        sys.exit(1)
    spec = spec.replace(COPY, COPY + "        copy.spendAll = this.spendAll;\n")
    io.open(SPEC, "w", encoding="utf-8", newline="").write(spec)
    print("ok   EffectSpec.spendAll (+ copy)")

interp = io.open(INTERP, encoding="utf-8").read()
if "getSpendAll()" in interp:
    print("skip interp")
else:
    OLD_ARM = ('            case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {\n'
               '                requireAmount(effect, op, spec);\n'
               '                requireResource(effect, op, spec);\n'
               '                requireNoStackArguments(effect, op, spec);\n'
               '            }\n')
    NEW_ARM = ('            case "GAIN_RESOURCE", "SPEND_RESOURCE" -> {\n'
               '                if (Boolean.TRUE.equals(effect.getSpendAll())) {\n'
               '                    // 「消耗所有【X】」 (2026-10-02): a spend with no stated SIZE -- and stating one\n'
               '                    // beside it is a contradiction, so it is refused rather than silently preferring one.\n'
               '                    if (!"SPEND_RESOURCE".equals(op)) {\n'
               '                        throw new IllegalArgumentException(\n'
               '                                "Op " + op + " states \\"spendAll\\", which only means something for SPEND_RESOURCE "\n'
               '                                        + "(source: " + spec.getSource() + ")");\n'
               '                    }\n'
               '                    if (effect.getAmount() != null) {\n'
               '                        throw new IllegalArgumentException(\n'
               '                                "Op " + op + " states both \\"spendAll\\" and \\"amount\\": \\"all of it\\" and a fixed "\n'
               '                                        + "number are different claims (source: " + spec.getSource() + ")");\n'
               '                    }\n'
               '                } else {\n'
               '                    requireAmount(effect, op, spec);\n'
               '                }\n'
               '                requireResource(effect, op, spec);\n'
               '                requireNoStackArguments(effect, op, spec);\n'
               '            }\n')
    if interp.count(OLD_ARM) != 1:
        print("FAIL interp: arm anchor matched %d times" % interp.count(OLD_ARM))
        sys.exit(1)
    interp = interp.replace(OLD_ARM, NEW_ARM)

    OLD_SPEND = ('        int amount = (int) Math.round(scaledAmount(effect, ctx));\n'
                 '        String id = effect.getResource();\n')
    NEW_SPEND = ('        String id = effect.getResource();\n'
                 '        // 「消耗所有【X】」 (2026-10-02): the size is whatever is there; the "not enough"\n'
                 '        // failure below cannot happen for it, which is why the two spellings are kept apart.\n'
                 '        int amount = Boolean.TRUE.equals(effect.getSpendAll())\n'
                 '                ? (holder.getResources().has(id) ? holder.getResources().value(id) : 0)\n'
                 '                : (int) Math.round(effect.getScaledOrLiteralAmount(ctx));\n')
    NEW_SPEND = NEW_SPEND.replace("effect.getScaledOrLiteralAmount(ctx)", "scaledAmount(effect, ctx)")
    if interp.count(OLD_SPEND) != 1:
        print("FAIL interp: spend anchor matched %d times" % interp.count(OLD_SPEND))
        sys.exit(1)
    interp = interp.replace(OLD_SPEND, NEW_SPEND)
    io.open(INTERP, "w", encoding="utf-8", newline="").write(interp)
    print("ok   loader + spendResource accept spendAll")

# --- content: the two halves of her sentence, both documented ------------------------------
doc = json.load(io.open(DATA, encoding="utf-8"))
rules = doc["rules"] if isinstance(doc, dict) else doc
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") in
                                  ("elation_spend_all_fervor", "fervor_extra_hit_per_point"))]
rules.append({
    "on": "CAST_SETUP",
    "id": "elation_spend_all_fervor",
    "when": ["from_skill_id:151307"],
    "do": [{"op": "SPEND_RESOURCE", "resource": RES, "spendAll": True, "target": "self"}],
    "source": ("1513 砂金•戏浪 强化欢愉技 下半句（`:283`）: "
               "「施放时会**消耗所有**【热意】」"),
    "note": ("⭐ 首个 `spendAll` 读者 ✓。⚠ 触发写成 `CAST_SETUP` + `from_skill_id` ✓ "
             "（强化欢愉技的 id → 正下半句的第二半接在 `RESOURCE_CHANGED` 上 ✓）。"),
})
rules.append({
    "on": "RESOURCE_CHANGED",
    "id": "fervor_extra_hit_per_point",
    "when": ["resource_changed:" + RES],
    "do": [{
        "op": "DAMAGE",
        "scale": "self_attr:ATTACK",
        "percent": 0.21,
        "times_from": "event_amount",
        "element": "Quantum",
        "target": "random_enemy",
        # ⚠ 真名是 `crit_rate` / `crit_damage` (EffectSpec)，而 `crit_rate` 是**固定暴击**的意思
        # (`Damage.fixedCrit`)，不是概率暴击 -- 所以不写它们。
    }],
    "source": ("1513 砂金•戏浪 强化欢愉技 下半句（`:283`）: "
               "「**每消耗1点**【热意】都会额外对随机敌方单体"
               "造成1次 **21.00%** 量子属性欢愉伤害」"),
    "note": ("⭐ 用的是已有的 `times_from: \"event_amount\"` ✓（同日出货 ✓）"
             "⇒ **点数 = 花掉的量** ✓。⚠ 伤害类型未写**欢愉**"
             "（隔离在另一条线 ✗）⇒ 本条先按普通量子属性伤害给 ✓，"
             "待「欢愉伤害」那一档落地后再改 ✓。"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: the two documented halves")
