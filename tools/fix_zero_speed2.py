"""The judge has to CAST: 1409 summons on her skill and 1415 on her ultimate, and startBattle alone casts nothing.

Pattern taken from the shipped judges (`battle.castImmediate(unit.getSkills().get(SkillType.SKILL), unit, List.of(enemy))`).
"""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/ZeroSpeedByAbilityTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();"""
NEW = """        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // ⚠ The summons live on an ACT: 1409's skill and 1415's ultimate, exactly as the sentences say.
        com.laosun.aluminium.models.enemy.Enemy victim = battle.getEnemies().getFirst();
        battle.castImmediate(master.getSkills().get(
                        cid == 1409 ? com.laosun.aluminium.enums.SkillType.SKILL
                                : com.laosun.aluminium.enums.SkillType.ULTRA),
                master, List.of(victim));
        battle.processRequests();"""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge casts the act that summons")
