"""Judge-only fix (rewritten with real Chinese): assert the DELTA, not the absolute value.

Measured: 1505 starts the battle holding 20 points of 【好活当赏】 already (her own technique grants them), so "starts with
none" was a wrong precondition. The sentence is about what the ULTIMATE adds, so the reading is a delta on both sides.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/UltElationBranchTest.java"
OLD = '''        Assertions.assertEquals(0, scene.target.getResources().value(GIFT), "the target starts with none");
        cast(scene);
        Assertions.assertEquals(10, scene.target.getResources().value(GIFT),
                "「目标额外获得 10 点【好活当赏】」");
        Assertions.assertEquals(0, scene.caster.getResources().value(GIFT), "而不是施放者自己的");'''
NEW = '''        // ⚠ DELTAS: 1505 already holds 20 points of it from her own technique, so an absolute reading would be wrong.
        int targetBefore = scene.target.getResources().value(GIFT);
        int casterBefore = scene.caster.getResources().value(GIFT);
        cast(scene);
        Assertions.assertEquals(10, scene.target.getResources().value(GIFT) - targetBefore,
                "「目标额外获得 10 点【好活当赏】」");
        Assertions.assertEquals(casterBefore, scene.caster.getResources().value(GIFT),
                "而不是施放者自己的");'''

text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   judge fixed: the gift is read as a delta")
