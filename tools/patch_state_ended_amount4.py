"""Put the once-per-sweep announcement back: patch3's empty-guard regex ate the block patch2 had inserted.

Measured: the current removeState removes three instances and fires nothing (`fires=0` with no condition on the reader), so
the block is simply absent. Inserted here with an anchor that is unique (`String wanted = state.trim();` followed by
`int removed = 0;` -- hasState has the first line but not the second).
"""
import io

PATH = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
ANCHOR = "        String wanted = state.trim();\n        int removed = 0;\n"
BLOCK = ANCHOR + (
    "        // \u2b50 ONE announcement per sweep, carrying how many instances it takes (2026-10-02; reader: 1505's \u300c\u5f00\u4e0d\u8d25\u300d,\n"
    "        // \u300c\u5c06\u5176\u4e2d\u7684 50% \u8f6c\u5316\u4e3a\u81ea\u8eab\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d). A sweep can take several instances of one state, and a reader that\n"
    "        // turns a share of \"it\" into something must see the total once, not three shrinking numbers.\n"
    "        int ending = 0;\n"
    "        for (AbstractBuff carried : List.copyOf(buffs)) {\n"
    "            if (carried instanceof StateBuff buff && wanted.equals(buff.getState())) {\n"
    "                ending++;\n"
    "            }\n"
    "        }\n"
    "        if (ending > 0 && battle != null) {\n"
    "            battle.fireStateEnded(instance, wanted, ending);\n"
    "        }\n")

text = io.open(PATH, encoding="utf-8").read()
if text.count(ANCHOR) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(ANCHOR))
if "fireStateEnded(instance, wanted, ending)" in text:
    raise SystemExit("already applied")
io.open(PATH, "w", encoding="utf-8", newline="\n").write(text.replace(ANCHOR, BLOCK, 1))
print("ok   the once-per-sweep announcement is back")
