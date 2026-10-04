"""Correct the judge's expected number, with the breakdown (round 1678).

Measured reading: 3. That is the engine being right, not wrong:
  6 charge at the start
  + 1  the real skill cast grants a charge (1412's own skill_grants_military_merit)
  + 1  the commanded COPY casts the same skill again -- which is what 「复制一次即将施放的技能并提前施放，随后施放原技能」 says
  - 6  the coup's end spends six
  + 1  1415's ode pays one back
  = 3

So the assertion is rewritten with the arithmetic spelled out, and without 1415's rule the same battle ends at 2.
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/OdeToLawChargeTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        Assertions.assertEquals(1, after, "six spent by the coup, one paid by 1415's ode -- 6 - 6 + 1");"""
NEW = """        // \\u2b50 The arithmetic the engine produces, and every term of it is a sentence: 6 + 1 (her own skill grants one) +
        // 1 (the COMMANDED COPY casts the same skill again -- 「复制一次即将施放的技能并提前施放，随后施放原技能」) - 6 (the coup's end
        // spends six) + 1 (1415's ode pays one) = 3.
        Assertions.assertEquals(3, after,
                "6 + 1 + 1 - 6 + 1: her skill twice (the copy is the coup), the coup's six, and the ode's one");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge states the whole arithmetic")
