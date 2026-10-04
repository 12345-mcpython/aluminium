"""Diagnostic: print what is actually on the carrier, so the judge's 0 can be explained rather than guessed.

hypothesis under test: the stackable state IS attached (hasState true) but `stacksOf` does not see it -- or it is not
attached at all. ASCII-only, judge-only.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/StackableStateTest.java"
OLD = '''        Character owner = owner();
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "three applications, three instances");'''
NEW = '''        Character owner = owner();
        System.out.println("[diag] hasState=" + owner.getBuffManager().hasState(STATE)
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " sameClassCount=" + owner.getBuffManager().countBuffs(
                        com.laosun.aluminium.models.buff.StackableStateBuff.class));
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "three applications, three instances");'''

text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   diagnostic added")
