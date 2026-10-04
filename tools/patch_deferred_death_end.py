"""Move the deferred-death commit from the carrier's turn START to its turn END.

Why: the sentence says the carrier 「**可以正常行动**」 and the judgement happens 「若**行动后**、下一次回合开始前…否则将立即陷入」.
Committing at the turn start kills it before it acts, which contradicts the first half -- measured by reading the two
halves against each other, not by running anything.

This script removes the block the previous patch inserted at `beforeMove` and inserts the same check right after
`fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);` -- i.e. once the turn is over, and still before the next turn
begins. Idempotent: it reports what it did and refuses on an ambiguous anchor.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/Battle.java"

INSERTED = ("        // \u2b50 \u300c\uff08\u82e5\u672a\u56de\u590d\uff09\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (1407's \u6708\u8327\u4e4b\u5e87): a death that a deferring state\n"
            "        // held is committed HERE -- at the carrier's own turn, before that turn happens -- if nothing removed the\n"
            "        // state in between. \u26a0 Before the buff tick and before TURN_START, so a heal arriving on this very turn is\n"
            "        // too late, which is what \u300c\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u300d states.\n"
            "        if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {\n"
            "            actor.perish();\n"
            "            return;\n"
            "        }\n")

ANCHOR = "        fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);\n"
NEW = ANCHOR + (
    "        // \u2b50 \u300c\uff08\u82e5\u672a\u56de\u590d\uff09\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (1407's \u6708\u8327\u4e4b\u5e87): a death that a deferring state held\n"
    "        // is committed HERE -- the carrier's turn is over (so it really did 「正常行动」), and the next turn has not begun\n"
    "        // (so a heal or a shield up to this point still saves it). Nothing removed the state, so it falls now.\n"
    "        if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {\n"
    "            actor.perish();\n"
    "        }\n")

text = io.open(PATH, encoding="utf-8").read()
if INSERTED in text:
    count = text.count(INSERTED)
    if count != 1:
        sys.stderr.write("REFUSING: the old block appears %d times\n" % count)
        raise SystemExit(1)
    text = text.replace(INSERTED, "")
    print("ok   removed the turn-start commit")
if ANCHOR not in text:
    sys.stderr.write("REFUSING: the TURN_END anchor is absent\n")
    raise SystemExit(1)
if NEW in text:
    print("skip the turn-end commit: already applied")
else:
    if text.count(ANCHOR) != 1:
        sys.stderr.write("REFUSING: the TURN_END anchor appears %d times\n" % text.count(ANCHOR))
        raise SystemExit(1)
    text = text.replace(ANCHOR, NEW, 1)
    print("ok   the held death is committed at the carrier's TURN_END")
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
