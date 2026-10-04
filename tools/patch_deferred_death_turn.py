"""Patch 4 of the deferred-death capability, on its own: commit the held death at the carrier's own turn.

The first script refused this site because its anchor matched twice; this one anchors on the following comment line as
well, which is unique, and it is idempotent (it reports "already applied" instead of failing) so it can be re-run.

\u26a0 Placement is the point: the commit sits BEFORE the buff tick and BEFORE TURN_START, i.e. before the carrier's turn
happens -- which is exactly \u300c\u82e5\u884c\u52a8\u540e\u3001\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u2026\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d.
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
       "        // \u2b50 \u300c\uff08\u82e5\u672a\u56de\u590d\uff09\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (1407's \u6708\u8327\u4e4b\u5e87): a death that a deferring state\n"
       "        // held is committed HERE -- at the carrier's own turn, before that turn happens -- if nothing removed the\n"
       "        // state in between. \u26a0 Before the buff tick and before TURN_START, so a heal arriving on this very turn is\n"
       "        // too late, which is what \u300c\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u300d states.\n"
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
