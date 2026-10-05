"""Give the autocast test the state its sentence requires (round 5 of the goal).

`CastSkillTest.hisTurnStartAutoCastDealsDamage` drives 1404's turn-start auto-cast and asserts the enemy's HP moves. It passed
before only because the rule fired unconditionally -- the sentence is 「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」, so the gate
that now exists is a CORRECTION and the scene has to enter the state. The carrier's variable name is derived from the method's own
`CharacterFactory.create(BLAST_CASTER, ...)` line rather than guessed.
"""
import io
import re
import sys

PATH = "src/test/java/com/laosun/aluminium/test/CastSkillTest.java"
text = io.open(PATH, encoding="utf-8").read()

METHOD = "public void hisTurnStartAutoCastDealsDamage()"
if METHOD not in text:
    sys.exit("REFUSING: the method is not there")
start = text.index(METHOD)
end = text.find("\n    }", start)
if end < 0:
    sys.exit("REFUSING: the method's end was not found")
body = text[start:end]

carrier = re.search(r"(\w+)\s*=\s*CharacterFactory\.create\(\s*BLAST_CASTER", body)
if carrier is None:
    sys.exit("REFUSING: the carrier's variable could not be derived from the method body")
name = carrier.group(1)
print("carrier variable: %s" % name)

if "血仇" in body:
    sys.exit("REFUSING: the scene already enters the state")

STARTER = "battle.startBattle();"
if body.count(STARTER) != 1:
    sys.exit("REFUSING: %d startBattle() calls in the method" % body.count(STARTER))
INSERT = (STARTER + "\n"
          "        // ⚠ 「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」: the gate is part of the sentence, so the scene\n"
          "        // enters the state. The rule used to fire unconditionally, which is the defect that gate corrects.\n"
          "        " + name + ".getBuffManager().addBuff(\n"
          "                new com.laosun.aluminium.models.buff.StateBuff(\"\\u8840\\u4ec7\", 9, true));\n"
          "        battle.processRequests();")
body = body.replace(STARTER, INSERT, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text[:start] + body + text[end:])
print("ok   the autocast test enters 【血仇】 first")
