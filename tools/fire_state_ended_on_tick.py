"""STATE_ENDED, step 3b: announce the state BEFORE removing it (2026-10-02).

The removal is the moment the state is no longer on the carrier, so the announcement has to happen on the line before:
a reader asking "am I in state X" would already see the wrong answer.

`battle` may be null (a unit built outside a battle) and then there is simply nobody to tell; `getState()` is assumed
Lombok-generated unless the compiler says otherwise (the caller reverts on a red suite).
ASCII only.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
ANCHOR = ("            if (buff.duration() <= 0) {\n"
          "                // `remove` by identity: AbstractBuff does not override equals, and a buff that already\n"
          "                // removed itself during the tick simply is not there any more.\n")
NEW = ("            if (buff.duration() <= 0) {\n"
       "                // ⭐ Announce BEFORE the removal (2026-10-02): the event carries the state's name, because a\n"
       "                // reader that looked for the state on this unit would already see it gone. `battle` is null for a\n"
       "                // unit built outside a battle, and then there is nobody to tell.\n"
       "                if (battle != null && buff instanceof StateBuff ended) {\n"
       "                    battle.fireStateEnded(instance, ended.getState());\n"
       "                }\n"
       "                // `remove` by identity: AbstractBuff does not override equals, and a buff that already\n"
       "                // removed itself during the tick simply is not there any more.\n")

text = io.open(PATH, encoding="utf-8").read()
if "fireStateEnded" in text:
    print("skip: already announces")
    raise SystemExit(0)
if text.count(ANCHOR) != 1:
    print("FAIL: expiry anchor matched %d times" % text.count(ANCHOR))
    sys.exit(1)
if "import com.laosun.aluminium.models.buff.StateBuff;" not in text:
    print("NOTE: StateBuff is in the same package, so no import is needed")
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(ANCHOR, NEW))
print("ok   tickBuff announces STATE_ENDED before removing")
