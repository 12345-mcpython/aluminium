"""Discriminator: is the rule firing at all, or is the `stackable` spelling the problem?

Adds a PLAIN state to the same BATTLE_START rule as a control. If the control attaches and the stackable one does not,
the spelling (or the arm that builds StackableStateBuff) is at fault; if neither attaches, the rule never fires.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/StackableStateTest.java"
OLD = '''        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "turns", 9, "stackable", Boolean.TRUE,
                "maxStacks", 9, "target", "self");'''
NEW = '''        EffectSpec apply = effect("APPLY_BUFF", "buff", STATE, "turns", 9, "stackable", Boolean.TRUE,
                "maxStacks", 9, "target", "self");
        // control: the same op without the new spelling
        EffectSpec control = effect("APPLY_BUFF", "buff", "controlState", "turns", 9, "target", "self");'''
if io.open(PATH, encoding="utf-8").read().count(OLD) != 1:
    raise SystemExit("REFUSING: anchor")
io.open(PATH, "w", encoding="utf-8", newline="").write(
    io.open(PATH, encoding="utf-8").read().replace(OLD, NEW, 1))

OLD2 = '''                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply, apply, apply),'''
NEW2 = '''                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), control, apply, apply, apply),'''
text = io.open(PATH, encoding="utf-8").read()
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

OLD3 = '''        System.out.println("[diag] hasState=" + owner.getBuffManager().hasState(STATE)'''
NEW3 = '''        System.out.println("[diag] control=" + owner.getBuffManager().hasState("controlState")
                + " hasState=" + owner.getBuffManager().hasState(STATE)'''
text = text.replace(OLD3, NEW3, 1)
io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   control state added")
