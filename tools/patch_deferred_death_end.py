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

INSERTED = ("        // ⭐ 「（若未回复）否则将立即陷入无法战斗状态」 (1407's 月茧之庇): a death that a deferring state\n"
            "        // held is committed HERE -- at the carrier's own turn, before that turn happens -- if nothing removed the\n"
            "        // state in between. ⚠ Before the buff tick and before TURN_START, so a heal arriving on this very turn is\n"
            "        // too late, which is what 「下一次回合开始前」 states.\n"
            "        if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {\n"
            "            actor.perish();\n"
            "            return;\n"
            "        }\n")

ANCHOR = "        fireTriggers(TriggerEvent.TURN_END, actor, actor, 0, 0);\n"
NEW = ANCHOR + (
    "        // ⭐ 「（若未回复）否则将立即陷入无法战斗状态」 (1407's 月茧之庇): a death that a deferring state held\n"
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
