"""Fix the variant test's own assertions: C and D state a literal `amount: 4`, so four instances is the CORRECT reading.

The test was written with 7 for all four variants, which is wrong for the two that do not read the party counter -- and it was
committed in that state (it failed from the beginning; the printout, not the assertion, is what the round used).
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/StackTimesVariantsTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """        Assertions.assertEquals(7, c, "C: a literal count with an unbounded cap");
        Assertions.assertEquals(7, d, "D: a literal count with a small cap");"""
NEW = """        // C and D state a literal FOUR, so four is the correct reading -- the first version of this test asserted 7 for them too,
        // which is what made it fail from the start.
        Assertions.assertEquals(4, c, "C: a literal count of four, with an unbounded cap");
        Assertions.assertEquals(4, d, "D: a literal count of four, with a small cap");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the variant test's own assertions are right now")
