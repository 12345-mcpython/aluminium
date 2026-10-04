"""Fix the judge's enemy accessor: hold the created enemy in a variable, the way the shipped judges do."""
import io
import sys

PATH = "src/test/java/com/laosun/aluminium/test/ZeroSpeedByAbilityTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = """        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        Battle battle = new Battle(List.of(master),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // \\u26a0 The summons live on an ACT: 1409's skill and 1415's ultimate, exactly as the sentences say.
        com.laosun.aluminium.models.enemy.Enemy victim = battle.getEnemies().getFirst();"""
NEW = """        Character master = CharacterFactory.create(cid, LEVEL, false, null, null, 0);
        com.laosun.aluminium.models.enemy.Enemy victim = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(master), List.of(victim), new Random(0));
        battle.startBattle();
        battle.processRequests();
        // \\u26a0 The summons live on an ACT: 1409's skill and 1415's ultimate, exactly as the sentences say."""
if text.count(OLD) != 1:
    sys.exit("REFUSING: the anchor appears %d times" % text.count(OLD))
io.open(PATH, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the enemy is a variable now")
