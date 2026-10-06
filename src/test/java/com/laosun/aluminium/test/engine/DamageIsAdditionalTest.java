package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * `damage_is_additional`: the instance being settled IS additional damage.
 *
 * <p>The complement of `damage_is_attack`, and it has to be a keyword of its own: that one is stated positively on purpose (`!` is only for party
 * conditions), and `TriggerTable` records what happened when the negation lived inside it instead -- cone 23008 energy clause read +0.0, because
 * every ordinary attack failed the guard it was written to pass.
 *
 * <p>The reading is TWO-SIDED on purpose: the guard must fire for an additional instance and must NOT fire for an ordinary attack. Either half
 * alone is satisfied by a condition that always answers the same thing.
 */
public class DamageIsAdditionalTest {
    private static final int LEVEL = 80;
    private static final int OWNER = 1402;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;

    @Test
    public void theGuardSeparatesAdditionalFromOrdinary() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        owner = battle.characters.getFirst();

        // the guard is observed through a heal of 1: harmless, and its own magnitude is not what is being read
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "HEAL");
        TriggerSpecs.set(effect, "amount", 1.0);
        TriggerSpecs.set(effect, "target", "self");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(
                TriggerSpecs.rule("DAMAGE_SETTLED", List.of("damage_is_additional"), effect))));

        // damage the owner first, so a heal is visible
        battle.applyDamage(owner, new Damage(battle.enemies.getFirst(), owner, DamageElement.ICE, DamageType.NORMAL, 400));
        double before = owner.getCurrentHp();

        battle.applyAdditionalDamage(owner, battle.enemies.getFirst(), DamageElement.ICE, 10);
        double afterAdditional = owner.getCurrentHp();

        battle.applyDamage(battle.enemies.getFirst(),
                new Damage(owner, battle.enemies.getFirst(), DamageElement.ICE, DamageType.NORMAL, 10));
        double afterOrdinary = owner.getCurrentHp();

        System.out.println("[is_additional] before = " + before + " ; after an ADDITIONAL instance = " + afterAdditional
                + " ; after an ORDINARY one = " + afterOrdinary);

        Assertions.assertTrue(afterAdditional > before,
                "the guard fires for an additional instance");
        Assertions.assertEquals(afterAdditional, afterOrdinary, 1e-9,
                "and NOT for an ordinary attack -- that half is what makes it two-sided");
    }
}
