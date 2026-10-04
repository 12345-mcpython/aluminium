"""The decisive measurement: three applications, and BOTH readers.

Previous readings only asked `stacksOf(name)`, which counts by `getBuffName()`. This one also counts by exact class, so
"the instances vanish" and "stacksOf cannot see them" are told apart. Three effects in one rule.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/AttachedBuffProbeTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = '''                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply))));'''
NEW = '''                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), apply, apply, apply))));'''
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor 1")
text = text.replace(OLD, NEW, 1)

OLD2 = '''        Assertions.assertTrue(stackable >= 1,
                "the attached state has to be the STACKABLE class; if asStateBuff=1 and this is 0, applyState built the plain one");'''
NEW2 = '''        Assertions.assertEquals(3, stackable,
                "three applications, three stackable instances -- unless the instances really do vanish");
        Assertions.assertEquals(3, owner.getBuffManager().stacksOf(STATE),
                "and the name-counting reader has to see the same three");'''
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   three applications, both readers")
