package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `damage_is_follow_up`, and `cast_category: "FOLLOW_UP"` on a DAMAGE effect (2026-10-02).
 *
 * <p>Reader: 1415's ode of passage, whose second sentence is 「缇宝施放<b>追加攻击</b>触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害」. The engine's own notion of a
 * follow-up attack is `Battle.applyAdditionalDamage` -- "this is the engine's one and only notion of a follow-up attack" -- but an instance could not SAY it was one, and
 * `SkillCategory` did not even have the value.
 *
 * <p>Two readings: an instance stamped FOLLOW_UP satisfies the keyword, and one that is not does not. Either half alone is satisfied by a condition that always answers
 * the same thing.
 */
public class DamageIsFollowUpTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1403;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void onlyAStampedInstanceAnswersYes() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner = battle.characters.getFirst();

        // the guard heals by 1, so "did it fire" is observable and its magnitude is not what is being read
        EffectSpec heal = new EffectSpec();
        TriggerSpecs.set(heal, "op", "HEAL");
        TriggerSpecs.set(heal, "amount", 1.0);
        TriggerSpecs.set(heal, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("DAMAGE_SETTLED", List.of("damage_is_follow_up"), heal))));

        var enemy = battle.enemies.getFirst();
        battle.applyDamage(owner, new com.laosun.aluminium.models.Damage(
                enemy, owner, DamageElement.ICE, com.laosun.aluminium.enums.DamageType.NORMAL, 400));
        double before = owner.getCurrentHp();

        // an instance that IS a follow-up
        battle.applyAdditionalDamage(owner, enemy, DamageElement.QUANTUM, 10, null, null, null, SkillCategory.FOLLOW_UP);
        double afterStamped = owner.getCurrentHp();

        // and one that is not
        battle.applyAdditionalDamage(owner, enemy, DamageElement.QUANTUM, 10);
        double afterPlain = owner.getCurrentHp();

        System.out.println("[is_follow_up] before = " + before + " ; after a STAMPED instance = " + afterStamped
                + " ; after a plain one = " + afterPlain);

        Assertions.assertTrue(afterStamped > before, "\u300c\u8ffd\u52a0\u653b\u51fb\u300d-- a stamped instance answers yes");
        Assertions.assertEquals(afterStamped, afterPlain, 1e-9,
                "and an ordinary additional instance does NOT -- that half is what makes it two-sided");
    }
}
