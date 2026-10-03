"""Wire the capped-credit chain into TriggerInterpreter (2026-10-02) -- slice-based, self-asserting.

The first attempt at this patch failed to compile, and the compiler named the reason exactly:

    error: cannot find symbol
        amount = (int) Math.round(previousCredited
       symbol:   variable amount

Both `amount` and `previousCredited` were out of scope, which means the insert landed OUTSIDE `gainResource`. The cause:
the anchor text `if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {` occurs TWICE in the file -- at :1315 (not
ours) and at :1347 (ours) -- and a plain `str.replace(..., 1)` takes the first. So this version slices the source at the
`gainResource` definition and edits only what follows it, and every replacement asserts that it changed something.

Reader (>= 2): 1505's eidolon 「触发行迹…的获得好活当赏效果时，额外获得等同于本次获得的【好活当赏】50%/100% 的
【好活当赏】」 -- the 50% and the 100% are the two readers.

Design note: the repo's own comment beside the `applyOne` call site says to pass per-rule data DOWN rather than keep
shared state ("a nested firing would clobber shared state"), so a loop local is threaded through `applyOne` into
`gainResource`. The credited amount is measured from the holder before and after the effect runs, so the declared cap is
included by construction: 150 energy -> the trace credits 100 (capped) -> the eidolon's 50% is 50 -> 150 in total.

Run:  python tools/patch_credit_chain.py
Then: full suite must stay green (no content uses the new branch yet).
ASCII only in the code; the one Chinese comment mirrors the rule it serves.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"

GAIN_DEF = "    private static void gainResource(EffectSpec effect, TriggerContext ctx) {"
LOOP_LOCAL_ANCHOR = "        int effectIndex = 0;\n"
CALL_SITE = "            applyOne(battle, effect, effectCtx);\n"
APPLY_ONE_SIG = "    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx) {"
DISPATCH = "                gainResource(effect, ctx);"
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


def must_replace(text, old, new, label):
    if old not in text:
        sys.exit("anchor missing: " + label)
    return text.replace(old, new, 1)


src = io.open(PATH, encoding="utf-8").read()
if "previousCredited" in src:
    print("already patched")
    sys.exit(0)

if GAIN_DEF not in src:
    sys.exit("the gainResource definition is missing")
cut = src.index(GAIN_DEF)
head, body = src[:cut], src[cut:]

# The loop local and the call site live BEFORE gainResource; the signature and the new branch live inside it.
head = must_replace(head, LOOP_LOCAL_ANCHOR, LOOP_LOCAL_ANCHOR + "        double previousCredited = 0;\n", "loop local")
head = must_replace(head, CALL_SITE, NEW_CALL_SITE, "call site")
head = must_replace(head, APPLY_ONE_SIG,
                    "    private static void applyOne(Battle battle, EffectSpec effect, TriggerContext ctx,\n"
                    "            double previousCredited) {", "applyOne signature")
head = must_replace(head, DISPATCH, "                gainResource(effect, ctx, previousCredited);", "dispatch")

body = must_replace(body, GAIN_DEF,
                    "    private static void gainResource(EffectSpec effect, TriggerContext ctx,\n"
                    "            double previousCredited) {", "gainResource signature")
body = must_replace(body, BRANCH_ANCHOR, NEW_BRANCH, "the amountFromEvent branch")

out = head + body
if out.count("previousCredited") < 6:
    sys.exit("the patch looks incomplete")
io.open(PATH, "w", encoding="utf-8", newline="\n").write(out)
print("ok   TriggerInterpreter: the credit chain is wired (slice-based)")
