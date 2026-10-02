"""Fix the event_amount judge (2026-10-02): the resource condition needs the battle to be told, and GAIN_ENERGY's amount
does not go through `grantAmount`. Both were the JUDGE's faults, not the engine's.
"""
import io
import sys

JUDGE = "src/test/java/com/laosun/aluminium/test/EventAmountTest.java"
text = io.open(JUDGE, encoding="utf-8").read()

# 1) the resource condition reads what the BATTLE was told changed ---------------------------------------
OLD = '        double before = enemy.getCurrentHp();\n' \
      '        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, owner, 0, -spent);\n'
NEW = '        double before = enemy.getCurrentHp();\n' \
      '        // `resource_changed:<name>` reads what the battle was TOLD changed, so a hand-fired event must say so.\n' \
      '        battle.noteChangedResource("\u5145\u80fd");\n' \
      '        battle.fireTriggers(TriggerEvent.RESOURCE_CHANGED, owner, owner, 0, -spent);\n'
if text.count(OLD) != 1:
    print("FAIL: fire anchor matched %d times" % text.count(OLD))
    sys.exit(1)
text = text.replace(OLD, NEW)

# 2) prove the MAGNITUDE spelling on HEAL, which is known to route through grantAmount --------------------
OLD_CASE = text[text.index("    /** \u2b50 A magnitude off the event"):text.index("    /** \u2b50 A repeat count off the event")]
NEW_CASE = '''    /** \u2b50 A magnitude off the event: heal 1 point per point of health lost. */
    @Test
    public void theMagnitudeFollowsTheEvent() {
        Character owner = CharacterFactory.create(OWNER, LEVEL, false, null, null, 0);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "scale", "event_amount");
        TriggerSpecs.set(effect, "percent", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("HP_LOST",
                List.of("actor == self"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner), List.of(enemy), new Random(0));
        battle.startBattle();

        battle.applyTrueDamage(enemy, owner, com.laosun.aluminium.enums.DamageElement.FIRE, 50);
        double before = owner.getCurrentHp();
        Assertions.assertTrue(before < owner.getMaxHp(), "precondition: the unit is hurt");
        battle.fireTriggers(TriggerEvent.HP_LOST, owner, owner, 0, 20);
        battle.processRequests();
        Assertions.assertEquals(20, owner.getCurrentHp() - before, 1e-6, "1 point healed per point lost");
    }

'''
text = text.replace(OLD_CASE, NEW_CASE)
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text)
print("ok   judge fixed: noteChangedResource + HEAL magnitude")
