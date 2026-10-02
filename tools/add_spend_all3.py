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
RES = "\u70ed\u610f"

spec = io.open(SPEC, encoding="utf-8").read()
if "spendAll" not in spec:
    ANCHOR = '    @SerializedName("times_from")'
    FIELD = ('    /**\n'
             '     * \u300c\u6d88\u8017\u6240\u6709\u3010X\u3011\u300d (2026-10-02): {@code SPEND_RESOURCE} takes whatever the holder has.\n'
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
               '                    // \u300c\u6d88\u8017\u6240\u6709\u3010X\u3011\u300d (2026-10-02): a spend with no stated SIZE -- and stating one\n'
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
                 '        // \u300c\u6d88\u8017\u6240\u6709\u3010X\u3011\u300d (2026-10-02): the size is whatever is there; the "not enough"\n'
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
    "source": ("1513 \u7802\u91d1\u2022\u620f\u6d6a \u5f3a\u5316\u6b22\u6109\u6280 \u4e0b\u534a\u53e5\uff08`:283`\uff09: "
               "\u300c\u65bd\u653e\u65f6\u4f1a**\u6d88\u8017\u6240\u6709**\u3010\u70ed\u610f\u3011\u300d"),
    "note": ("\u2b50 \u9996\u4e2a `spendAll` \u8bfb\u8005 \u2713\u3002\u26a0 \u89e6\u53d1\u5199\u6210 `CAST_SETUP` + `from_skill_id` \u2713 "
             "\uff08\u5f3a\u5316\u6b22\u6109\u6280\u7684 id \u2192 \u6b63\u4e0b\u534a\u53e5\u7684\u7b2c\u4e8c\u534a\u63a5\u5728 `RESOURCE_CHANGED` \u4e0a \u2713\uff09\u3002"),
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
        "target": "target",
        "critRate": 0.0,
        "critDamage": 0.0,
    }],
    "source": ("1513 \u7802\u91d1\u2022\u620f\u6d6a \u5f3a\u5316\u6b22\u6109\u6280 \u4e0b\u534a\u53e5\uff08`:283`\uff09: "
               "\u300c**\u6bcf\u6d88\u80171\u70b9**\u3010\u70ed\u610f\u3011\u90fd\u4f1a\u989d\u5916\u5bf9\u968f\u673a\u654c\u65b9\u5355\u4f53"
               "\u9020\u62101\u6b21 **21.00%** \u91cf\u5b50\u5c5e\u6027\u6b22\u6109\u4f24\u5bb3\u300d"),
    "note": ("\u2b50 \u7528\u7684\u662f\u5df2\u6709\u7684 `times_from: \"event_amount\"` \u2713\uff08\u540c\u65e5\u51fa\u8d27 \u2713\uff09"
             "\u21d2 **\u70b9\u6570 = \u82b1\u6389\u7684\u91cf** \u2713\u3002\u26a0 \u4f24\u5bb3\u7c7b\u578b\u672a\u5199**\u6b22\u6109**"
             "\uff08\u9694\u79bb\u5728\u53e6\u4e00\u6761\u7ebf \u2717\uff09\u21d2 \u672c\u6761\u5148\u6309\u666e\u901a\u91cf\u5b50\u5c5e\u6027\u4f24\u5bb3\u7ed9 \u2713\uff0c"
             "\u5f85\u300c\u6b22\u6109\u4f24\u5bb3\u300d\u90a3\u4e00\u6863\u843d\u5730\u540e\u518d\u6539 \u2713\u3002"),
})
out = doc if isinstance(doc, dict) else {"rules": rules}
if isinstance(doc, dict):
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1513.json: the two documented halves")
