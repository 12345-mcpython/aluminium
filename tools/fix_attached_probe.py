"""Fixed instrument: count by exact class instead of listing the base class.

`allBuffsOf(AbstractBuff.class)` compares classes EXACTLY (a documented property of that helper), so it lists nothing for
a subclass and the previous probe's emptiness proved only that. `countBuffs(X.class)` is exact too, which is what makes
the pair informative: if the attached instance counts as StateBuff and NOT as StackableStateBuff, then applyState built the
plain class -- i.e. `effect.getStackable()` was null by the time the effect ran.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"
OLD = '''        List<AbstractBuff> attached = owner.getBuffManager().allBuffsOf(AbstractBuff.class);
        System.out.println("[probe] attached=" + attached.size()
                + " stackableClass=" + owner.getBuffManager().countBuffs(StackableStateBuff.class)
                + " hasState=" + owner.getBuffManager().hasState(STATE));
        for (AbstractBuff buff : attached) {
            System.out.println("[probe] buff class=" + buff.getClass().getSimpleName()
                    + " stackable=" + buff.isStackable()
                    + " key=" + buff.stackGroupKey()
                    + " maxStacks=" + buff.maxStacks()
                    + " name=" + buff.getBuffName());
        }
        Assertions.assertFalse(attached.isEmpty(), "something has to be on the unit");'''
NEW = '''        int plain = owner.getBuffManager().countBuffs(
                com.laosun.aluminium.models.buff.StateBuff.class);
        int stackable = owner.getBuffManager().countBuffs(StackableStateBuff.class);
        System.out.println("[probe] asStateBuff=" + plain
                + " asStackableStateBuff=" + stackable
                + " stacksOf=" + owner.getBuffManager().stacksOf(STATE)
                + " hasState=" + owner.getBuffManager().hasState(STATE)
                + " specStackable=" + apply.getStackable());
        Assertions.assertTrue(stackable >= 1,
                "the attached state has to be the STACKABLE class; if asStateBuff=1 and this is 0, applyState built the plain one");'''
text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor")
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   instrument fixed")
