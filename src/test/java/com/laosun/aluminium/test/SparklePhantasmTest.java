package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1306 Sparkle's talent: "每层[幻相]使敌方全体受到的伤害提高 4.00%，持续 2 回合，最多叠加 3 层".
 *
 * <p>Judged on the COMPILED rules, and on the counter they depend on: the stacking rule must be there and the scaled zone must read the wearer's own counter.
 */
public class SparklePhantasmTest {
    private static final int WEARER = 1306;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theCounterIsStackedAndTheZoneScalesOffIt() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, unit, enemy, 1, 0);
        int layers = unit.getBuffManager().stacksOf("幻相");
        System.out.println("[1306] layersAfterOneSpend=" + layers);
        var rules = unit.getTriggerTable().matching(TriggerEvent.SKILL_POINT_SPENT,
                new TriggerTable.TriggerContext(unit, unit, enemy, 1, 0));
        String scale = null;
        double percent = -1;
        double cap = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    scale = effect.getScale();
                    percent = effect.getPercent();
                    cap = effect.getCapAmount();
                    System.out.println("[1306] rule id=" + rule.id() + " scale=" + scale + " percent=" + percent
                            + " capAmount=" + cap + " turns=" + effect.getTurns());
                }
            }
        }
        Assertions.assertEquals(1, layers, "the spend stacked one layer");
        Assertions.assertEquals("self_stacks:幻相", scale, "the layers live on the wearer");
        Assertions.assertEquals(0.04, percent, 1e-9, "each layer adds 4%");
        Assertions.assertEquals(0.12, cap, 1e-9, "three layers cap it at 12%");
    }

    @Test
    public void moreLayersMeanMoreDamageTaken() {
        Character unit = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double none = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        battle.fireTriggers(TriggerEvent.SKILL_POINT_SPENT, unit, enemy, 1, 0);
        double one = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000));
        System.out.println("[1306] none=" + none + " oneLayer=" + one + " ratio=" + (one / none));
        // Note: The measured ratio is 1.04 x 1.06, and the second factor is NOT this clause: the same event also fires
        // `talent_party_damage_on_spend`, which raises every ally's ALL_DAMAGE_TYPE_BOOST by 6%. Stating the product keeps the
        // assertion honest about what the fixture can see (the spec-level judge above isolates this clause).
        Assertions.assertEquals(1.04 * 1.06, one / none, 1e-6,
                "the zone adds 4% on top of the sibling rule's 6% all-damage boost");
    }
}
