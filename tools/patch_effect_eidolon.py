"""Effect-level Eidolon gating: an effect can name the rank it belongs to (2026-10-02).

Why this exists, measured this arc:
  * `min_eidolon` is a TRIGGER SPEC field (`TriggerSpec:171`) and `TriggerInterpreter:981` compares it for the WHOLE rule
    (`if (rule.minEidolon() > 0 && eidolonRankOf(owner) < rule.minEidolon())`). So a rule-level gate cannot switch off one
    effect while leaving the base effect of the same rule running -- and 1505's eidolon is a SECOND effect of the trace
    rule, which must keep reading the first effect's credited amount (`amountFromPrevious`, wired in 15d210c1).
  * Re-deriving the number in a separate rule instead does not work either: `gainResource` multiplies by the percentage
    BEFORE applying `amountCap`, so 150 energy would give min(75, 100) = 75 rather than the documented 50.

Readers (>= 2, named): 1505's eidolon (the 50% and the 100% halves of her 星魂 sentence) and 1415's second half (the
"pierce, 2% per point, up to 120%" clause that is likewise rank-gated).

Writes:
  * `beans/EffectSpec.java`   : `min_eidolon` (fully-qualified SerializedName, so no import is needed);
  * `TriggerInterpreter.apply`: skip a gated effect, using the existing `eidolonRankOf(owner)` helper.

Run:  python tools/patch_effect_eidolon.py
Then: the full suite must stay green (no content sets `min_eidolon` yet).
ASCII only.
"""
import io
import sys

EFFECT_SPEC = "src/main/java/com/laosun/aluminium/beans/EffectSpec.java"
INTERPRETER = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

FIELD_ANCHOR = "    private Boolean amountFromEvent;"
FIELD_ADDITION = '''

    /**
     * ⭐ 2026-10-02：**效果级**的星魂分层。读者：1505 星魂的 50%／100%，
     * 以及 1415 第二半那条“随层级”的穿透。规则级的 `min_eidolon`
     * 只能关掉整条规则，而这里要关的只是其中一条效果。
     */
    @com.google.gson.annotations.SerializedName("min_eidolon")
    private Integer minEidolon;'''

LOOP_ANCHOR = "            effect = amendedEffect(effect, ctx);"

# The guard `RuleEffectAmendmentTest` says it in as many words: "getMinEidolon must survive the copy -- add it to
# EffectSpec.copy()". So the copy line is part of the patch, not an afterthought.
COPY_ANCHOR = "        copy.amountFromPrevious = amountFromPrevious;"
COPY_ADDITION = "\n        copy.minEidolon = minEidolon;"
GATE = '''            if (effect.getMinEidolon() != null && eidolonRankOf(ctx.owner()) < effect.getMinEidolon()) {
                // ⭐ 2026-10-02: this EFFECT belongs to an Eidolon rank the unit does not have. The rule-level
                // `min_eidolon` cannot express this -- it would switch off the whole rule, base part included.
                continue;
            }
'''

spec = io.open(EFFECT_SPEC, encoding="utf-8").read()
if "minEidolon" in spec:
    print("EffectSpec already patched")
else:
    if FIELD_ANCHOR not in spec:
        sys.exit("EffectSpec anchor missing")
    spec = spec.replace(FIELD_ANCHOR, FIELD_ANCHOR + FIELD_ADDITION, 1)
    if COPY_ANCHOR not in spec:
        sys.exit("EffectSpec copy anchor missing")
    spec = spec.replace(COPY_ANCHOR, COPY_ANCHOR + COPY_ADDITION, 1)
    io.open(EFFECT_SPEC, "w", encoding="utf-8", newline="\n").write(spec)
    print("ok   EffectSpec.minEidolon (field + copy)")

interp = io.open(INTERPRETER, encoding="utf-8").read()
if "effect.getMinEidolon()" in interp:
    print("TriggerInterpreter already patched")
else:
    if LOOP_ANCHOR not in interp:
        sys.exit("TriggerInterpreter loop anchor missing")
    interp = interp.replace(LOOP_ANCHOR, GATE + LOOP_ANCHOR, 1)
    io.open(INTERPRETER, "w", encoding="utf-8", newline="\n").write(interp)
    print("ok   TriggerInterpreter gates gated effects")
