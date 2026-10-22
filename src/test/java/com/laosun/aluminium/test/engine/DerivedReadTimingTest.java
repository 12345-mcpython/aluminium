package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * When a rule reads {@code scale: self_attr:<X>}, does it read the CURRENT resolved value of {@code X}?
 *
 * <p>This exists because Pearl's healing link does not appear to. Measured there: the clause sits LAST among her
 * {@code BATTLE_START} rules, the panel ends at Elation 0.42, and yet the bonus it grants is 0.064 = 20% of 0.32 -
 * the gated tier's own contribution. Raising the stat-trace row from 0.10 to 0.50 moves the panel to 0.82 and leaves
 * the bonus at exactly 0.064. That is either (a) the documented "read once when the rule fires, then freeze", with the
 * read happening before the traces land, or (b) the reader resolving something narrower than the panel.
 *
 * <p>This case answers it on a fixture with no ordering ambiguity at all: one rule grants {@code ELATION_DAMAGE_BOOST}
 * +100%, a SECOND rule fires afterwards and derives a value from it. If the derived read follows the granted
 * attribute, the reader is current-value based and (a) is the explanation for Pearl; if it does not, the reader is
 * looking somewhere else and the engine has a bug worth chasing.
 */
public class DerivedReadTimingTest {
    private static final int PEARL = 1503;
    private static final int HUNTER = 1204;
    private static final double EPS = 1e-9;

    /**
     * Two rules on one event, in order: grant Elation, then derive from it.
     *
     * <p>Both are on the SAME event so the only thing that can differ is the reader, and the derived rule comes second
     * so "read at fire time" has every chance to see the grant.
     */
    @Test
    public void aDerivedReadSeesAnAttributeGrantedEarlierInTheSameEvent() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(HUNTER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        Character her = battle.characters().getFirst();

        double elationBefore = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        her.setTriggerTable(new TriggerTable(PEARL, List.of(
                grant("ELATION_DAMAGE_BOOST", 0.5),
                derive("OUTGOING_HEALING_BOOST", "ELATION_DAMAGE_BOOST", 0.2))));
        battle.fire(com.laosun.aluminium.models.trigger.TriggerPayload.Simple.of(TriggerEvent.BATTLE_START, her, her, 0, 0));
        battle.processRequests();

        double elation = her.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        double healing = her.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        System.out.println("[derived-read] Elation before=" + elationBefore + " -> after the grant=" + elation
                + " ; the value derived from it = " + healing
                + " ; 20% of the CURRENT Elation = " + (0.2 * elation)
                + " ; 20% of the PRE-GRANT Elation = " + (0.2 * elationBefore));

        Assertions.assertTrue(elation > elationBefore, "precondition: the grant landed (" + elationBefore + " -> "
                + elation + ")");
        Assertions.assertEquals(0.2 * elation, healing, EPS,
                "the derived read follows the attribute as it stands when the rule fires (this is the engine's "
                        + "documented convention: the value is read at fire time and then frozen)");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static com.laosun.aluminium.beans.TriggerSpec grant(String attribute, double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", attribute);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("BATTLE_START", List.of(), effect);
    }

    private static com.laosun.aluminium.beans.TriggerSpec derive(String attribute, String scale, double percent) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", attribute);
        TriggerSpecs.set(effect, "scale", "self_attr:" + scale);
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "permanent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        return TriggerSpecs.rule("BATTLE_START", List.of(), effect);
    }
}
