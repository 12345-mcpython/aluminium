"""Fix the control's reading: `stacksOf` counts NAMED instances, and a plain state carries no name (measured: 0).

The control's point is "a plain state is not repeated", which `hasState` answers directly.
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/StackTimesTest.java"
text = io.open(PATH, encoding="utf-8").read()
OLD = """        Character owner = owner(false);
        System.out.println("[stack-times] plain instances=" + owner.getBuffManager().stacksOf(STATE));
        Assertions.assertEquals(1, owner.getBuffManager().stacksOf(STATE),
                "only a stackable state can hold a count");"""
NEW = """        Character owner = owner(false);
        // \\u26a0 Measured: `stacksOf` counts NAMED instances, and a plain StateBuff carries no name -- so it answers 0 for the
        // control either way. `hasState` is what the control's claim is about: the state is there, once, unrepeated.
        System.out.println("[stack-times] plain stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " hasState=" + owner.getBuffManager().hasState(STATE));
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "the plain state is on the unit");
        Assertions.assertEquals(0, owner.getBuffManager().stacksOf(STATE),
                "and it holds no named instance -- which is why a plain state cannot carry a count");"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the control reads through hasState")
