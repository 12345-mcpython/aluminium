"""Update the census in HyacineTest: her ULT_CAST and SKILL_CAST now carry two rules each (2026-10-02).

The added rule is 1415's sky ode: 「风堇施放战技/终结技后，消耗1层【献予「天空」之诗】」 -- one rule on SKILL_CAST and one on ULT_CAST, both living in her file because she is
the one casting and the layers are hers. The census test counts rules per event to catch accidental duplication, so it must learn the new expected counts rather than be
weakened.
"""
import io
import sys

P = "src/test/java/com/laosun/aluminium/test/HyacineTest.java"
s = io.open(P, encoding="utf-8").read()

ULT_NOTE = "        // ⭐ Two now: her own ultimate, and the sky ode spending a layer on a ULT_CAST (2026-10-02)."
SKILL_NOTE = "        // ⭐ Two now as well: the summon, and the sky ode spending a layer on a SKILL_CAST (2026-10-02)."

out = []
ult = skill = 0
for ln in s.split("\n"):
    if "ruleCount(TriggerEvent.ULT_CAST)" in ln and "assertEquals(1," in ln:
        out.append(ULT_NOTE)
        out.append(ln.replace("assertEquals(1,", "assertEquals(2,"))
        ult += 1
    elif "ruleCount(TriggerEvent.SKILL_CAST)" in ln and "assertEquals(1," in ln:
        out.append(SKILL_NOTE)
        out.append(ln.replace("assertEquals(1,", "assertEquals(2,"))
        skill += 1
    else:
        out.append(ln)

print("ULT_CAST census lines updated: %d ; SKILL_CAST: %d" % (ult, skill))
if ult == 0:
    sys.exit("REFUSING: no ULT_CAST census line was found")
io.open(P, "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written")
