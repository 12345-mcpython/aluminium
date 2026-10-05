"""Patch 4 of the deferred-death capability, on its own: commit the held death at the carrier's own turn.

The first script refused this site because its anchor matched twice; this one anchors on the following comment line as
well, which is unique, and it is idempotent (it reports "already applied" instead of failing) so it can be re-run.

⚠ Placement is the point: the commit sits BEFORE the buff tick and BEFORE TURN_START, i.e. before the carrier's turn
happens -- which is exactly 「若行动后、下一次回合开始前…否则将立即陷入无法战斗状态」.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/Battle.java"
OLD = ("        if (actor.isDeath()) {\n"
       "            return;\n"
       "        }\n"
       "        // Order matters: settlement above runs *before* this tick")
NEW = ("        if (actor.isDeath()) {\n"
       "            return;\n"
       "        }\n"
       "        // ⭐ 「（若未回复）否则将立即陷入无法战斗状态」 (1407's 月茧之庇): a death that a deferring state\n"
       "        // held is committed HERE -- at the carrier's own turn, before that turn happens -- if nothing removed the\n"
       "        // state in between. ⚠ Before the buff tick and before TURN_START, so a heal arriving on this very turn is\n"
       "        // too late, which is what 「下一次回合开始前」 states.\n"
       "        if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {\n"
       "            actor.perish();\n"
       "            return;\n"
       "        }\n"
       "        // Order matters: settlement above runs *before* this tick")

text = io.open(PATH, encoding="utf-8").read()
if NEW in text:
    print("skip Battle.beforeMove: already applied")
    raise SystemExit(0)
count = text.count(OLD)
if count != 1:
    sys.stderr.write("REFUSING: the anchor appears %d times\n" % count)
    raise SystemExit(1)
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(OLD, NEW))
print("ok   Battle.beforeMove: the held death is committed at the carrier's turn")
