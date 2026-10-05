package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1504's "头狼" (Alpha Wolf) trace (1504103): "不死途在场时，我方目标造成的暴击伤害提高 40%，我方目标追加攻击造成的暴击伤害额外提高 80%" (while Ashveil (不死途) is on the field, our targets' CRIT DMG is raised by 40%, and our targets' follow-up attacks' CRIT DMG is raised by a further 80%).
 *
 * <p>Both halves are party-wide, and the fixture proves it with an ALLY: the ally's crit damage rises (first half) and its follow-up rises further (second half). The
 * control battle is the same ally without 1504 in the party.
 */
public class AshveilAuraTest {
    private static final int OWNER = 1504;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** One ally, with or without 1504 in the party; both readings fixed to crit via CRIT_CHANCE. */
    private double[] readings(boolean withOwner) {
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        List<Character> party = withOwner
                ? List.of(CharacterFactory.create(OWNER, LEVEL), ally)
                : List.of(ally);
        Battle battle = new Battle(party, List.of(enemy), new Random(0));
        battle.startBattle();
        ally.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(2.0, DoubleValue.Modifier.ModifierSource.BUFF, 150401));
        Assertions.assertTrue(ally.getAttribute(AttributeType.CRIT_CHANCE).get() >= 1.0,
                "precondition: the ally always crits");
        double normal = battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        double followUp = battle.applyAdditionalDamage(ally, enemy, DamageElement.QUANTUM, 1000, null, null);
        return new double[] {normal, followUp};
    }

    @Test
    public void theRulesCarryTheirScopesAndShares() {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(owner, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        // The follow-up half is two mutually exclusive rules; both are asked in-battle, one per actor.
        int seen = 0;
        for (var actor : List.of(owner, ally)) {
            for (var rule : owner.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                    new TriggerTable.TriggerContext(owner, actor, enemy, 0, 0))) {
                for (var effect : rule.effects()) {
                    if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                        seen++;
                        System.out.println("[1504] followup rule id=" + rule.id() + " actor=" + (actor == owner ? "self" : "ally")
                                + " scope=" + effect.getDamageType() + " percent=" + effect.getPercent());
                        Assertions.assertEquals("ADDITIONAL", effect.getDamageType(), "follow-up scoped");
                        Assertions.assertEquals(0.8, effect.getPercent(), 1e-9, "the data row states 0.8");
                    }
                }
            }
        }
        // Note: Only the `actor == self` half is asked here. Party predicates (`actor is_ally` / `is_other_ally`) are resolved by the
        // Battle, so a hand-built TriggerContext cannot see those rules -- measured: `is_ally` and `is_other_ally` both return
        // nothing while the very same rule fires in a real battle. That half is therefore covered behaviourally, in the test below.
        Assertions.assertEquals(1, seen, "the self half is visible to a hand-built context");
        double plain = -1;
        for (var rule : owner.getTriggerTable().matching(TriggerEvent.BATTLE_START,
                new TriggerTable.TriggerContext(owner, owner, enemy, 0, 0))) {
            for (var effect : rule.effects()) {
                if ("MODIFY_ATTR".equals(effect.getOp()) && "CRIT_ATTACK".equals(effect.getAttribute())) {
                    plain = effect.getPercent();
                    System.out.println("[1504] aura rule id=" + rule.id() + " percent=" + plain
                            + " target=" + effect.getTarget());
                }
            }
        }
        Assertions.assertEquals(0.4, plain, 1e-9, "the data row states 0.4");
    }

    @Test
    public void anAllyBenefitsFromBothHalves() {
        double[] with = readings(true);
        double[] without = readings(false);
        System.out.println("[1504] with owner: normal=" + with[0] + " followUp=" + with[1]
                + " ratio=" + (with[1] / with[0]));
        System.out.println("[1504] without owner: normal=" + without[0] + " followUp=" + without[1]
                + " ratio=" + (without[1] / without[0]));
        // MEASURED first, then pinned. The two factors are independent and both predictable from the crit multiplier:
        // the aura turns 1.5 into 1.9 (ratio 1.9/1.5) and the follow-up half adds another 0.8 (ratio 2./1.9).
        Assertions.assertEquals(1.0, without[1] / without[0], 1e-6, "without 1504 a follow-up is not special");
        Assertions.assertEquals(1.2666666667, with[0] / without[0], 1e-6,
                "the first half raises the ally's crit damage by exactly 40 points");
        Assertions.assertEquals(1.4210526316, with[1] / with[0], 1e-6,
                "the second half adds exactly 80 points more to a follow-up");
    }
}
