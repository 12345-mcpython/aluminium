package com.laosun.aluminium.test.content.characters.rappa;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 131Rappa, from her own file: the Charge that Weakness Break feeds, and the document's cap of 10.
 *
 * <p>The cap is tested by EXCEEDING it (round 192's lesson): twelve breaks must still read ten.
 */
public class RappaTest {
    private static final int RAPPA = 1317;
    private static final int LEVEL = 80;

    /**
     * Note: One Charge per Weakness Break, stopped at the document's ten.
     */
    @Test
    public void everyBreakAddsOneChargeUpToTen() {
        Character rappa = CharacterFactory.create(RAPPA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(rappa), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertEquals(0, chargeOf(rappa), "the document states no initial value, so it starts at 0");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, rappa, enemy, 0, 0));
        Assertions.assertEquals(1, chargeOf(rappa),
                "「每当敌方目标的弱点被击破时，乱破获得1点充能」 (every time an enemy target's Weakness is Broken, Rappa gains 1 point of Charge)");

        for (int i = 0; i < 11; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, rappa, enemy, 0, 0));
        }
        Assertions.assertEquals(10, chargeOf(rappa),
                "「最多拥有10点充能」 (holds at most 10 points of Charge) -- twelve breaks must still read ten");
    }

    /**
     * 131Rappa, trace 131103: "敌方目标的弱点被击破时，受到的击破伤害提高2%；若当前攻击力高于2400，每超过100点额外提高1%，最多额外提高8%".
     *
     * <p>Two assertions that a magnitude mutation cannot survive: the base branch's own share (read from the compiled rule) and the measured ratio between break and normal
     * damage. The branch is selected by its condition, because the tiers are mutually exclusive step functions of ATTACK.
     */
    @Test
    public void theBaseBranchStatesTwoPercentScopedToBreak() {
        Character unit = CharacterFactory.create(RAPPA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        double attack = unit.getAttribute(AttributeType.ATTACK).get();
        var rules = unit.getTriggerTable().matching(TriggerEvent.BREAK,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        Assertions.assertTrue(attack < 2500, "precondition: this wearer sits in the base tier, ATTACK=" + attack);
        double baseShare = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp()) && "BREAK".equals(effect.getDamageType())
                        && rule.conditions().stream().anyMatch(c -> c.source().contains("attack < 2500"))) {
                    baseShare = effect.getPercent();
                    System.out.println("[1317] base rule=" + rule.id() + " percent=" + baseShare);
                }
            }
        }
        Assertions.assertEquals(0.02, baseShare, 1e-9, "the base tier is 2%");
    }

    @Test
    public void breakDamageIsAmplifiedByExactlyTheAuthoredShare() {
        Character unit = CharacterFactory.create(RAPPA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, unit, enemy, 0, 0));
        double breaking = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.BREAK, 1000));
        System.out.println("[1317] normal=" + normal + " break=" + breaking + " ratio=" + (breaking / normal));
        // The talent (+50%) and the trace (+2%) are two sentences and ADD (canon: 1 + Σ 易伤). Before the identity
        // fix the trace's 2% evicted the talent's 50%, so this used to read 1.02 - a wrong number that nothing reported.
        Assertions.assertEquals(1.52, breaking / normal, 1e-6,
                "break damage carries the talent's 50% and the trace's 2%");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int chargeOf(Character rappa) {
        return rappa.getResources().get("充能").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
