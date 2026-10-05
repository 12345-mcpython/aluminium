"""The judge must cast the skill the rule is gated on (2026-10-02).

The rule now carries `from_skill_id == 19` -- faithful to the ability data, which puts that modifier in 19's own TaskList -- so a judge that casts slot 1 reads 0 layers. Cast 19.
"""
import io
import sys

P = "src/test/java/com/laosun/aluminium/test/SkyOdeStackTest.java"
s = io.open(P, encoding="utf-8").read()
OLD = """        Assertions.assertNotNull(demiurge.skillAt(1), "precondition: slot 1 exists");

        SkillExecutor.execute(battle, demiurge.skillAt(1), demiurge, List.of(battle.enemies.getFirst()));"""
NEW = """        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: the memosprite carries slot 19 -- the skill the sentence belongs to");

        SkillExecutor.execute(battle, ode, demiurge, List.of(hyacine));"""
if s.count(OLD) != 1:
    sys.exit("REFUSING: the cast block occurs %d times" % s.count(OLD))
io.open(P, "w", encoding="utf-8", newline="\n").write(s.replace(OLD, NEW))
print("the judge casts slot 19 now")
