"""STATE_ENDED, step 2a: the event itself (2026-10-02).

Purely additive: the member is declared and nothing fires it yet, so the full suite stays the judge. Firing it (and the
state-name channel it needs) is the next step.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/enums/TriggerEvent.java"
ANCHOR = '    DEBUFF_APPLIED("DEBUFF_APPLIED", true),\n'
NEW = ANCHOR + (
    "\n"
    "    /**\n"
    "     * A named state has just left the unit that carried it (2026-10-02).\n"
    "     *\n"
    "     * <p>⚠ <b>The name must ride on the event, not be read off the carrier</b>: by the time this fires the state is\n"
    "     * already gone (the removal happens first), so a {@code has_state} condition on the carrier can never be true.\n"
    "     * Readers: 1211's 「【生息】结束时…」, 1505's 「队友持有的【好活当赏】结束时…」, 1408's three 「变身结束时…」,\n"
    "     * 1501's two 「阿哈时刻结束时…」 and the light cone's 「奇袭结束后…」.\n"
    "     */\n"
    '    STATE_ENDED("STATE_ENDED", true),\n')

text = io.open(PATH, encoding="utf-8").read()
if "STATE_ENDED(" in text:
    print("skip: already declared")
    raise SystemExit(0)
if text.count(ANCHOR) != 1:
    print("FAIL: anchor matched %d times" % text.count(ANCHOR))
    sys.exit(1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(ANCHOR, NEW))
print("ok   TriggerEvent.STATE_ENDED declared")
