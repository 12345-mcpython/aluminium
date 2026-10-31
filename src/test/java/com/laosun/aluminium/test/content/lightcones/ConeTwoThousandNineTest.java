package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 朗道的选择 (21009): 「使装备者受到攻击的概率提高，同时受到的伤害降低 <b>16%</b>」.
 *
 * <p><b>Why the old reading said 35.8%.</b> It compared the damage taken by two DIFFERENT builds - with and without the cone - and
 * the cone also raises her DEFENCE, so the ratio carried the panel difference too (measured then: 0.6419 instead of 0.84). The cut
 * is measured here inside ONE build: the same wearer, the same incoming instance, before and after the reduction is on, which is
 * the only way the 16% is the only thing that changed.
 */
public class ConeTwoThousandNineTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    @Test
    public void theReductionIsSixteenPercentOfTheSameIncomingHit() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        double[] loss = new double[1];
        battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) ->
                loss[0] = hpLost);
        battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 500));
        double before = loss[0];

        wearer.getBuffManager().addBuff(new com.laosun.aluminium.buff.ReductionBuff(0, 0.16, true));
        battle.applyDamage(wearer, new Damage(enemy, wearer, DamageElement.PHYSICAL, DamageType.NORMAL, 500));
        double after = loss[0];
        System.out.println("[21009] same build: " + before + " -> " + after + " (x" + (after / before) + ")");
        Assertions.assertEquals(0.84, after / before, 1e-6,
                "「受到的伤害降低 16%」 - one multiplier in the reduction zone, read on one build");
    }

    /**
     * And the cone ships exactly that number, with the aggro half beside it.
     */
    @Test
    public void theShippedRulesCarryTheTwoNumbers() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21009, LEVEL, false, 1));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        var rules = wearer.getTriggerTable().rulesFor(com.laosun.aluminium.enums.TriggerEvent.BATTLE_START).stream()
                .filter(r -> r.id().startsWith("landau_")).toList();
        Assertions.assertEquals(2, rules.size(), "aggro up and damage taken down");
        var aggro = rules.stream().filter(r -> r.id().equals("landau_aggro_up")).findFirst().orElseThrow();
        Assertions.assertEquals(1.0, aggro.effects().getFirst().getPercent(), 1e-9, "受到攻击的概率 ×2 ⇒ +100%");
        var cut = rules.stream().filter(r -> r.id().equals("landau_damage_taken_down")).findFirst().orElseThrow();
        Assertions.assertEquals(-0.16, cut.effects().getFirst().getPercent(), 1e-9, "受到的伤害 −16%");
    }
}
