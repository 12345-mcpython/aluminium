"""STATE_ENDED, step 1b: hand the battle to every unit's buff manager, in the CANONICAL constructor.

The first attempt inserted into the delegating constructor (`this(...)`), which broke the compile; the roster lives in
`Battle(List<Character>, List<? extends CanHit>, Random)`, whose last statement is `listenToSkillPointChanges();`.
`getBuffManager()` is Lombok-generated on `CanHit`, so no literal `getBuffManager` appears in the source.

ASCII only.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/Battle.java"
OLD = ("        listenToSkillPointChanges();\n"
       "    }\n")
NEW = ("        listenToSkillPointChanges();\n"
       "        // The units' buff managers hold the battle so a state that leaves a unit can be announced\n"
       "        // (2026-10-02; the STATE_ENDED event, whose reader is 1211's 「【生息】结束时…」).\n"
       "        for (Character c : characterQueue) {\n"
       "            c.getBuffManager().setBattle(this);\n"
       "        }\n"
       "        for (CanHit e : enemyQueue) {\n"
       "            e.getBuffManager().setBattle(this);\n"
       "        }\n"
       "    }\n")

text = io.open(PATH, encoding="utf-8").read()
if "setBattle(this)" in text:
    print("skip: already wired")
    raise SystemExit(0)
if text.count(OLD) != 1:
    print("FAIL: canonical-constructor tail matched %d times" % text.count(OLD))
    sys.exit(1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW))
print("ok   both rosters' buff managers now hold the battle")
