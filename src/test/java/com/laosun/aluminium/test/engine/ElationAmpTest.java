package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 增笑 ({@code ELATION_DAMAGE_AMP}) is Elation damage's own amplifier, MULTIPLIED with 欢愉度, not added to it.
 *
 * <p>The spec at {@code ROADMAP.md:1262} writes them as two separate factors --
 * {@code (1+欢愉度) × (1+增笑)} -- so +100% of each must quadruple the instance, not triple it. That is the
 * assertion this judge is built around: an implementation that added them would read 3x and fail.
 *
 * <p>Second side: a NORMAL instance must be untouched by either attribute.
 */
public class ElationAmpTest {
    private static final int PEARL = 1503;
    private static final int MONSTER = 1002011;

    @Test
    public void theAmpMultipliesWithElationBoostRatherThanAddingToIt() {
        double plain = damage(false, 0.0, 0.0);
        double amp = damage(false, 0.0, 1.0);
        double boost = damage(false, 1.0, 0.0);
        double both = damage(false, 1.0, 1.0);
        double normal = damage(true, 1.0, 1.0);
        System.out.println("[amp] plain=" + plain + " amp(+100%)=" + amp + " boost(+100%)=" + boost
                + " both=" + both + " (ratio " + (both / plain) + ") ; normal with both = " + normal);

        Assertions.assertEquals(2.0, amp / plain, 1e-6, "增笑 +100% doubles the instance");
        Assertions.assertEquals(2.0, boost / plain, 1e-6, "欢愉度 +100% doubles it too");
        Assertions.assertEquals(4.0, both / plain, 1e-6,
                "and together they QUADRUPLE it: (1+1) x (1+1), not 1+1+1");
        Assertions.assertEquals(plain, normal, 1e-9, "a NORMAL instance sees neither attribute");
    }

    /** Settles one rule-driven instance; `normal` picks the damage type, the two doubles grant the attributes. */
    private static double damage(boolean normal, double boost, double amp) {
        Character pearl = CharacterFactory.create(PEARL, 80);
        var enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
        Character mine = battle.characters.getFirst();

        EffectSpec instance = TriggerSpecs.damage(null, 0, null, "target");
        TriggerSpecs.set(instance, "scale", "elation_base");
        TriggerSpecs.set(instance, "percent", 1.0);
        TriggerSpecs.set(instance, "element", "Ice");
        if (!normal) {
            TriggerSpecs.set(instance, "damageType", "ELATION");
        }

        List<TriggerSpec> rules = new ArrayList<>();
        List<EffectSpec> grants = new ArrayList<>();
        if (boost > 0) {
            grants.add(TriggerSpecs.modifyAttr("ELATION_DAMAGE_BOOST", boost, 999));
        }
        if (amp > 0) {
            grants.add(TriggerSpecs.modifyAttr("ELATION_DAMAGE_AMP", amp, 999));
        }
        if (!grants.isEmpty()) {
            rules.add(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                    grants.toArray(new EffectSpec[0])));
        }
        rules.add(TriggerSpecs.rule("ULT_CAST", List.of("actor == self"), instance));
        mine.setTriggerTable(new TriggerTable(PEARL, rules));
        if (!grants.isEmpty()) {
            battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.TURN_START, mine, mine, 0, 0);
        }

        double before = enemy.getCurrentHp();
        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, mine, battle.enemies.getFirst(), 0, 0);
        return before - enemy.getCurrentHp();
    }
}
