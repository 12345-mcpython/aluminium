package com.laosun.aluminium.test.content.characters.kafka;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1005 卡芙卡's Eidolon 4:「当敌方目标承受的由卡芙卡造成的触电状态产生伤害时，额外为卡芙卡恢复 2 点能量」.
 *
 * <p>The load-bearing part is the guard: {@code DAMAGE_SETTLED} fires for every settled instance, so without
 * {@code damage_is_dot} her ordinary attacks would pay the same 2 energy - which is what the second case checks.
 */
public class KafkaEidolonFourTest {

    @Test
    public void aShockTickPaysTwoEnergy() {
        double[] measured = run();
        System.out.println("[1005] energy before tick " + measured[0] + " ; after " + measured[1]);
        Assertions.assertEquals(2.0, measured[1] - measured[0], 1e-9, "「恢复 2 点能量」");
    }

    @Test
    public void anOrdinaryAttackPaysNothing() {
        Character her = CharacterFactory.create(1005, 80, true, null, null, 4);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        double before = her.getCurrentEnergy();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(enemy));
        battle.processRequests();
        System.out.println("[1005] energy after an ordinary attack " + (her.getCurrentEnergy() - before));
        // control relaxed: a basic attack pays its own 20 energy; the point is the DoT adds nothing extra there.
        Assertions.assertEquals(20.0, her.getCurrentEnergy() - before, 1e-9, "an ordinary attack pays only its own energy");
    }

    /** {energy before the tick, energy after it}. */
    private static double[] run() {
        Character her = CharacterFactory.create(1005, 80, true, null, null, 4);
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        var enemy = battle.enemyUnits().getFirst();
        // Her SKILL only ticks existing DoTs (「立即产生 75%」); the SHOCK comes from the ULTIMATE.
        battle.castImmediate(her.getSkills().get(SkillType.ULTRA), her, List.of(enemy));
        battle.processRequests();
        System.out.println("[1005] shocked=" + enemy.getBuffManager().hasState("触电"));
        double before = her.getCurrentEnergy();
        battle.tickDots(enemy);
        battle.processRequests();
        return new double[]{before, her.getCurrentEnergy()};
    }
}