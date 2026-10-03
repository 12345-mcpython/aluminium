"""1407: 【新蕊】-- the reader is proven, and a duplicate of it is removed (2026-10-02).

Document, verbatim (1407_遐蝶.html:94): 「【新蕊】上限与场上全体角色等级有关，**我方全体每损失 1 点生命值遐蝶获得 1 点【新蕊】**，
当【新蕊】达到上限时可激活终结技。」

WHAT THIS SHIP ACTUALLY IS (measured, after nine rounds of a wrong hunt):
  * The rule already existed: `talent_newbud_per_hp_lost`, `on: HP_LOST`, `when: ["target == self"]`,
    `do: [{ "op": "GAIN_RESOURCE", "resource": "新蕊", "amountFromEvent": true }]` -- and `amountFromEvent` is exactly
    "give what the event gave", i.e. one bud per point.
  * A PREVIOUS commit of this script added a SECOND rule for the same sentence (`new_bud_on_hp_loss`, `amount: 0`). It is a
    duplicate: harmless at `amount: 0`, but it is a second reader of one sentence, and at `amount: 1` it double-counted
    (measured: 100 HP -> 101 buds, 50 HP -> 51). \u26a0 This ship DELETES it.
  * What was actually missing all along was a judge that causes HP loss. Nothing did: a bare `CanHit.takeDamage` does not
    reach the battle's HP-loss dispatch, and 40 steps of "let them fight" never touched her (probe: "hp 1629.936 -> 1629.936").

\u2b50 THE MUTATION LESSON: the first attempt's mutation changed MY rule's event and the suite stayed green -- because the
pre-existing rule was doing the work. A mutation that does not redden is not a formality; it is the only thing that tells a
duplicate from a reader. The judge below is load-bearing against `talent_newbud_per_hp_lost` itself.
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/1407.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/NewBudOnHpLossTest.java"
RES = "\u65b0\u854a"
DUPLICATE = "new_bud_on_hp_loss"
KEEPER = "talent_newbud_per_hp_lost"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc

removed = [r for r in rules if isinstance(r, dict) and r.get("id") == DUPLICATE]
rules = [r for r in rules if not (isinstance(r, dict) and r.get("id") == DUPLICATE)]

keeper = [r for r in rules if isinstance(r, dict) and r.get("id") == KEEPER]
if len(keeper) != 1:
    raise SystemExit("expected the pre-existing new-bud rule, found " + str(len(keeper)))

if is_dict:
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   1407.json: removed " + str(len(removed)) + " duplicate rule(s), kept " + KEEPER)

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407\uff1a\u300c\u6211\u65b9\u5168\u4f53\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u9050\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d (2026-10-02).
 *
 * <p>\u2b50 MEASURED, and it took a probe to see it: the enemy never touched her -- "hp 1629.936 -> 1629.936 (max 1629.936)
 * after 40 steps" -- so every earlier draft failed because NO HP LOSS EVER HAPPENED. Damage now goes through the battle's own
 * entry point ({@code Battle.applyTrueDamage}); a bare {@code CanHit.takeDamage} does not reach the battle's HP-loss dispatch.
 *
 * <p>\u2b50 The scale is asserted EXACTLY: 50 HP lost -> 50 buds. The reader is {@code talent_newbud_per_hp_lost}, which uses
 * {@code amountFromEvent: true} -- "give what the event gave". \u26a0 The mutation for this judge must change THAT rule (an
 * earlier attempt mutated a duplicate I had added, and the suite stayed green because the real reader was doing the work).
 */
public class NewBudOnHpLossTest {
    private static final int OWNER = 1407;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String RES = "\u65b0\u854a";

    /** \u2b50 One bud per point of HP she loses. */
    @Test
    public void losingHpGivesOneBudPerPoint() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertNotNull(battle.partyResource(RES),
                "the new bud must be reachable as a party resource");
        double before = battle.partyResource(RES).value();

        double dealt = battle.applyTrueDamage(battle.enemies.get(0), owner, DamageElement.ICE, 50.0);
        battle.processRequests();
        Assertions.assertEquals(50.0, dealt, "precondition: the battle really took 50 HP off her");

        Assertions.assertEquals(before + 50, battle.partyResource(RES).value(),
                "\u300c\u6bcf\u635f\u5931 1 \u70b9\u751f\u547d\u503c\u9050\u8776\u83b7\u5f97 1 \u70b9\u3010\u65b0\u854a\u3011\u300d (before=" + before + ")");
    }
}
''')
print("ok   judge written")
