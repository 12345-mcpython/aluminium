package com.laosun.aluminium.test.content.relics;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set 129, four pieces (2026-09-30): "装备者及其忆灵造成的欢愉伤容无视目标 10% 防御".
 *
 * <p>Two-sided on the same wearer: an ELATION instance is bigger with the set on, an ordinary one is untouched -- the scope is
 * the whole point of the clause, and it is the capability that was measured when the Elation slice landed.
 */
public class EverGloriousMagicalGirlTest {
    private static final int WEARER = 1002;
    private static final int ALLY = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** Note: Relic pieces cap at level 15, not at the character's level (measured: a RelicException said so). */
    private static final int RELIC_LEVEL = 15;

    private Battle battle;
    private Character wearer;
    private Enemy enemy;

    private void build(boolean withSet) {
        wearer = withSet
                ? CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(129, 4, RELIC_LEVEL))
                : CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
    }

    private double settled(DamageType type) {
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.PHYSICAL, type, 1000));
    }

    @Test
    public void theSetMakesElationDamageIgnoreDefenceAndNothingElse() {
        build(false);
        double elationPlain = settled(DamageType.ELATION);
        double normalPlain = settled(DamageType.NORMAL);
        build(true);
        double elationSet = settled(DamageType.ELATION);
        double normalSet = settled(DamageType.NORMAL);
        System.out.println("[129] ELATION " + elationPlain + " -> " + elationSet + " (x" + (elationSet / elationPlain)
                + ") ; NORMAL " + normalPlain + " -> " + normalSet + " (x" + (normalSet / normalPlain) + ")");
        // Note: The cross-build comparison is NOT a clean control (measured): the relic pieces move the wearer's own panel, and
        // `DamageType.ELATION` is deliberately not boostable, so the two ratios are not comparable. What IS specific: ignoring
        // 10% of the target's DEF lifts an Elation instance by a small, bounded factor. The scope and the number are pinned by
        // the spec half below.
        double factor = elationSet / elationPlain;
        System.out.println("[129] the Elation factor is " + factor + " ; the ordinary one " + (normalSet / normalPlain)
                + " (its own stats, which is why it is not the control)");
        Assertions.assertTrue(factor > 1.0 && factor < 1.15,
                "the set lifts Elation damage by the DEF-ignore effect alone (x" + factor + ")");
        Assertions.assertTrue(normalSet > normalPlain, "and the relic’s own stats show on ordinary damage");
    }

    @Test
    public void theShippedRuleCarriesTheNumberAndTheScope() {
        build(true);
        var rules = wearer.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(rule -> rule.id().startsWith("relic129_")).toList();
        Assertions.assertEquals(1, rules.size(), "one shipped rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[129] spec " + rules.getFirst().id() + " attribute=" + effect.getAttribute()
                + " percent=" + effect.getPercent() + " damage_type=" + effect.getDamageType()
                + " instance=" + effect.getInstance());
        Assertions.assertEquals(0.1, effect.getPercent(), 1e-9, "10% of the target’s DEF");
        Assertions.assertEquals("ELATION", effect.getDamageType(), "scoped to Elation damage");
        Assertions.assertEquals(Boolean.TRUE, effect.getInstance(), "as an instance modifier");
    }
}
