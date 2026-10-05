"""Judge-only fix for the 23004 cast-scope test: the lifetime appears TWICE per rank, not once.

Each rank carries two effects (effect hit and attack) and both state `until: cast_end`, so the file contains ten of them.
Measured: the first version asserted five and failed with "expected: <5> but was: <10>".
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/Cone23004CastScopeTest.java"
OLD = '        Assertions.assertEquals(5, count(raw, "\\"until\\": \\"cast_end\\""),\n' \
      '                "every one of them is scoped to the cast");'
NEW = '        // ⚠ TEN, not five: each rank carries TWO effects (effect hit and attack) and both state the lifetime.\n' \
      '        Assertions.assertEquals(10, count(raw, "\\"until\\": \\"cast_end\\""),\n' \
      '                "both effects of every rank are scoped to the cast");'

text = io.open(PATH, encoding="utf-8").read()
count = text.count(OLD)
if count != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % count)
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   judge fixed: the cast_end count is 10 (two effects per rank)")
