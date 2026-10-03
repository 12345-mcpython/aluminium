"""Wire the capped-credit chain into TriggerInterpreter (2026-10-02).

This is the second half of the `amountFromPrevious` capability -- the first half (the `EffectSpec` field and its copy line)
is already in the tree. Every anchor below was read from the file, and `applyOne` has exactly ONE call site
(TriggerInterpreter:918), so widening its signature is safe.

Reader (>= 2): 1505's eidolon 「触发行迹…的获得好活当赏效果时，额外获得等同于本次获得的【好活当赏】50%/100% 的
【好活当赏】」 -- the 50% and the 100% are the two readers.

Design note: the repo's own comment beside that call site says to pass per-rule data DOWN rather than keep shared state
("a nested firing would clobber shared state"), so this threads a loop local through `applyOne` into `gainResource`
instead of using a field, a ThreadLocal or a new TriggerContext component.

The amount is measured from the holder before and after the effect runs, so the declared cap is included by construction:
150 energy -> the trace credits 100 (capped) -> the eidolon's 50% is 50 -> 150 in total.

Run:  python tools/patch_credit_chain.py
Then: full suite must stay green (no content uses the new branch yet), and the next step is the 1505 rule + its judge.
ASCII only in the code; the one Chinese comment mirrors the rule it serves.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

LOOP_LOCAL_ANCHOR = "        int effectIndex = 0;\n"
CALL_SITE = "            applyOne(battle, effect, effectCtx);\n"
APPLY_ONE_SIG = ("    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx) {")
DISPATCH = "                gainResource(effect, ctx);"
GAIN_SIG = "    private static void gainResource(EffectSpec effect, TriggerContext ctx) {"
BRANCH_ANCHOR = "        if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {"

NEW_CALL_SITE = '''            if ("GAIN_RESOURCE".equals(normalizeOp(effect, null))) {
                // \u2b50 2026-10-02: the amount this effect ACTUALLY credits (after the cap) is what the next effect may
                // take a share of. Measured from the holder itself, so the cap is included by construction.
                CanHit holder = resolveTarget(effect, effectCtx);
                String resourceId = effect.getResource();
                int creditedBefore = holder.getResources().has(resourceId)
                        ? holder.getResources().value(resourceId) : 0;
                applyOne(battle, effect, effectCtx, previousCredited);
                int creditedAfter = holder.getResources().has(resourceId)
                        ? holder.getResources().value(resourceId) : 0;
                previousCredited = creditedAfter - creditedBefore;
            } else {
                applyOne(battle, effect, effectCtx, previousCredited);
            }
'''

NEW_BRANCH = '''        if (Boolean.TRUE.equals(effect.getAmountFromPrevious())) {
            // \u2b50 2026-10-02 \u8bfb\u8005\uff1a1505 \u661f\u9b42 \u300c\u989d\u5916\u83b7\u5f97\u7b49\u540c\u4e8e\u672c\u6b21\u83b7\u5f97\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u301150%/100%\u300d\u3002
            // \u53d6\u7684\u662f\u524d\u4e00\u6761\u6548\u679c\u5df2\u7ecf\u8fc7\u4e0a\u9650\u622a\u65ad\u7684\u5165\u8d26\u91cf\u3002
            amount = (int) Math.round(previousCredited
                    * (effect.getAmountPercent() == null ? 1 : effect.getAmountPercent()));
        } else ''' + BRANCH_ANCHOR

src = io.open(PATH, encoding="utf-8").read()
if "previousCredited" in src:
    print("already patched")
    sys.exit(0)

for needed in (LOOP_LOCAL_ANCHOR, CALL_SITE, APPLY_ONE_SIG, DISPATCH, GAIN_SIG, BRANCH_ANCHOR):
    if needed not in src:
        sys.exit("anchor missing: " + needed[:60])

src = src.replace(LOOP_LOCAL_ANCHOR, LOOP_LOCAL_ANCHOR + "        double previousCredited = 0;\n", 1)
src = src.replace(CALL_SITE, NEW_CALL_SITE, 1)
src = src.replace(APPLY_ONE_SIG,
                  "    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx,\n"
                  "            double previousCredited) {", 1)
src = src.replace(DISPATCH, "                gainResource(effect, ctx, previousCredited);", 1)
src = src.replace(GAIN_SIG,
                  "    private static void gainResource(EffectSpec effect, TriggerContext ctx, double previousCredited) {", 1)
src = src.replace(BRANCH_ANCHOR, NEW_BRANCH, 1)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(src)
print("ok   TriggerInterpreter: the credit chain is wired")
