"""Fix the judge's arithmetic: compare against `baseValue()`, the number a percentage share scales off.

`get()` includes every modifier and any pure values the unit carries, so deriving a base from `oneStack / 1.14` bakes those
in. `baseValue()` is exactly "the value a percentage share scales off" (the capped-modifier path uses it for the same
reason), so the two readings are compared as shares of it.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/PerStackLiveTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        double oneStack = owner.getAttribute(AttributeType.ATTACK).get();
        double base = oneStack / 1.14;
        System.out.println("[per_stack_live] one stack -> " + oneStack + " (base " + base + ")");"""
NEW = """        double oneStack = owner.getAttribute(AttributeType.ATTACK).get();
        double base = owner.getAttribute(AttributeType.ATTACK).baseValue();
        System.out.println("[per_stack_live] one stack -> " + oneStack + " (baseValue " + base + ")"
                + " share=" + ((oneStack - base) / base));"""
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor 1")
text = text.replace(OLD, NEW, 1)

OLD2 = """        double fiveStacks = owner.getAttribute(AttributeType.ATTACK).get();
        System.out.println("[per_stack_live] five stacks -> " + fiveStacks + " (expected " + base * 1.70 + ")");
        Assertions.assertEquals(base * 1.70, fiveStacks, base * 1e-6,
                "the aura has to follow the count, with nothing re-attached");"""
NEW2 = """        double fiveStacks = owner.getAttribute(AttributeType.ATTACK).get();
        double oneShare = (oneStack - base) / base;
        double fiveShare = (fiveStacks - base) / base;
        System.out.println("[per_stack_live] five stacks -> " + fiveStacks + " share=" + fiveShare);
        Assertions.assertEquals(0.14, oneShare, 1e-6, "one stack is 14% of the base");
        Assertions.assertEquals(0.70, fiveShare, 1e-6,
                "the aura has to follow the count, with nothing re-attached");"""
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   the judge compares shares of baseValue()")
