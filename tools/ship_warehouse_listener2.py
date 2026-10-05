"""The warehouse load point: a listen-only table that is asked but never acts (round 17 of the goal).

Located last round: the dispatch core asks `for (Character ally : characters)`, and `characters` IS the roster that acts (assigned
from the queue at L520), so a listen-only unit cannot go there. The clean shape is a SECOND loop over listen-only tables in the same
dispatch.

Readers (2): 1407's 月茧之庇 and 1506's 999安全卫士 -- both sentences begin 「获得该角色即生效，无需上场」 and the data files them under
`AvatarGlobalBuffConfig` as global support skills. Their OWN effects still need other pieces (a delayed down; a per-wave limit), so this
ships the load point itself: the dispatch half, judged on its own two claims -- a registered listener IS asked, and it does NOT act.
"""
import io
import sys

BATTLE = "src/main/java/com/laosun/aluminium/Battle.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/WarehouseListenerTest.java"


def patch(old, new, label, count=1):
    text = io.open(BATTLE, encoding="utf-8").read()
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    io.open(BATTLE, "w", encoding="utf-8", newline="\n").write(text.replace(old, new, count))
    print("ok   %s" % label)


patch(
    "    private String lastChangedResource;",
    "    private String lastChangedResource;\n\n"
    "    /**\n"
    "     * ⭐ Units that are OWNED but NOT DEPLOYED, whose rules are asked without them ever acting (2026-10-02).\n"
    "     *\n"
    "     * <p>Two documents start 「获得该角色即生效，无需上场」 (1407's 月茇之庇, 1506's 999安全卫士), and the data files them under\n"
    "     * {@code AvatarGlobalBuffConfig} as global support skills. ⚠ Their tables cannot ride in `characters`: that list is the\n"
    "     * roster the QUEUE is built from (L520), so a listener put there would take turns of its own. They are asked by a second\n"
    "     * loop instead, and because they are in neither `allies` nor the queue they are never targeted and never act.\n"
    "     */\n"
    "    private final java.util.List<CanHit> warehouseListeners = new java.util.ArrayList<>();\n\n"
    "    /**\n"
    "     * Registers a unit whose rules are asked although it is not on the field (see {@link #warehouseListeners}).\n"
    "     *\n"
    "     * <p>⚠ The caller sets the table: this is deliberately about WHOSE rules are heard, not about which rules exist, so a listener\n"
    "     * can carry exactly the global support clauses instead of its whole battle kit.\n"
    "     */\n"
    "    public void registerWarehouseListener(CanHit listener) {\n"
    "        if (listener != null) {\n"
    "            warehouseListeners.add(listener);\n"
    "        }\n"
    "    }",
    "the warehouse list and its registration",
)

patch(
    "            return fired;\n        } finally {\n            triggerDepth--;",
    "            // ⭐ 获得该角色即生效，无需上场 (2026-10-02). ⚠ A SECOND pass, not extra entries in `characters`: that list is what\n"
    "            // the queue is built from, so a listener in it would act. ⚠ The owner handed to the context is the listener itself,\n"
    "            // which is what makes 「self」 mean “the character who owns this warehouse clause” rather than whoever is fighting.\n"
    "            for (CanHit listener : warehouseListeners) {\n"
    "                if (listener == null || listener.isDeath()) {\n"
    "                    continue;\n"
    "                }\n"
    "                TriggerTable table = listener.getTriggerTable();\n"
    "                if (table == null || table.isEmpty()) {\n"
    "                    continue;\n"
    "                }\n"
    "                fired += TriggerInterpreter.fire(this, table, event,\n"
    "                        new TriggerTable.TriggerContext(listener, actor, target, hitCount, amount, damage, this,\n"
    "                                fromCast).withSkillId(skillId).withWeakHitCount(weakHitCount)\n"
    "                                .withAttackHitTargets(attackHitTargets));\n"
    "            }\n"
    "            return fired;\n        } finally {\n            triggerDepth--;",
    "the second dispatch pass",
)

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 「获得该角色即生效，无需上场」 -- the load point (2026-10-02).
 *
 * <p>Two claims, and both matter: a registered listener IS asked (its rule moves its own panel), and it does NOT take a turn of its
 * own (the queue never contains it). The second is why the listener cannot simply be added to `characters`.
 */
public class WarehouseListenerTest {
    private static final int FIGHTER = 1002;
    private static final int OWNED = 1407;
    private static final int MONSTER = 1002011;

    /** The listener's rule fires; the fighter's panel is untouched; the listener never acts. */
    @Test
    public void theListenerIsHeardAndNeverActs() {
        Character fighter = CharacterFactory.create(FIGHTER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(fighter),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));

        Character owned = CharacterFactory.create(OWNED, 80, false, null, null, 0);
        EffectSpec mark = new EffectSpec();
        TriggerSpecs.set(mark, "op", "MODIFY_ATTR");
        TriggerSpecs.set(mark, "attribute", "ATTACK");
        TriggerSpecs.set(mark, "percent", 0.5);
        TriggerSpecs.set(mark, "permanent", Boolean.TRUE);
        TriggerSpecs.set(mark, "target", "self");
        // ⚠ A BATTLE_START rule with NO condition: that event carries no actor and no target, and the loader refuses a condition
        // that asks about either.
        owned.setTriggerTable(new TriggerTable(OWNED,
                List.of(TriggerSpecs.rule("BATTLE_START", List.of(), mark)), List.of()));
        battle.registerWarehouseListener(owned);

        double ownedAttackBefore = owned.getAttribute(AttributeType.ATTACK).get();
        double fighterAttackBefore = fighter.getAttribute(AttributeType.ATTACK).get();
        battle.startBattle();
        battle.processRequests();
        double ownedGain = owned.getAttribute(AttributeType.ATTACK).get() - ownedAttackBefore;
        double fighterGain = fighter.getAttribute(AttributeType.ATTACK).get() - fighterAttackBefore;
        boolean ownedInQueue = battle.queue.snapshot().stream()
                .anyMatch(signal -> signal.getCanHit() == owned);
        System.out.println("[warehouse] listener gain=" + ownedGain + " fighter gain=" + fighterGain
                + " listener in queue=" + ownedInQueue);

        Assertions.assertTrue(ownedGain > 0,
                "「获得该角色即生效」-- the listener's own rule ran although it is not on the field");
        Assertions.assertEquals(0.0, fighterGain, 1e-9,
                "and the mark landed on the LISTENER's panel, whose 「self」 is the character that owns the clause");
        Assertions.assertFalse(ownedInQueue,
                "「无需上场」-- it must not take a turn, which is why it cannot ride in `characters`");
    }
}
''')
print("ok   the judge is written")
