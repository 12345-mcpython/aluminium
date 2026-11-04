package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's two memosprite passives that the engine CAN answer for, because it emits the events:
 * 「衣匠被召唤时，使自身行动提前 100%」 (忆灵技能 3) and 「衣匠消失时，使阿格莱雅恢复 20 点能量」 (忆灵技能 4).
 *
 * <p>They live in HER file because a trigger table belongs to a character; the executor is the Garmentmaker (衣匠), which
 * is the same split {@code memosprite_ode_of_romance_*} already uses on this file.
 */
public class AglaeaMemospritesTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;

    /**
     * 「行动提前 100%」 is observable as a position: right after it is summoned, the Garmentmaker (衣匠) is what the
     * action order would play next. Without the advance a 35%-of-her-SPD memosprite would be behind her.
     */
    @Test
    public void theSpeedingSummerPutsItAtTheFront() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();

        Assertions.assertSame(garmentmaker, battle.queue().peekNext(),
                "「衣匠被召唤时，使自身行动提前 100%」 — it is next in the action order");
        Assertions.assertNotSame(aglaea, battle.queue().peekNext(), "and it is the memosprite, not her");
    }

    /**
     * 「衣匠消失时，使阿格莱雅恢复 20 点能量」: the energy is paid by the <b>SUMMON_LEFT</b> event, i.e. when the
     * memosprite is gone - which is why the rule cannot look the memosprite up at that moment.
     */
    @Test
    public void theBloomPaysTwentyWhenItLeaves() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        // A teammate delivers the killing blow: a kill credits KILL energy to the ATTACKER, so letting HER do it would
        // mix 5 unexplained points into the reading. Measured 2026-11-04: hers read 25, a teammate's leaves exactly 20.
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double before = aglaea.getCurrentEnergy();

        battle.applyDamage(garmentmaker,
                new Damage(ally, garmentmaker, DamageElement.THUNDER, DamageType.NORMAL, 1_000_000));
        battle.processRequests();

        Assertions.assertTrue(garmentmaker.isDeath(), "precondition: the memosprite is gone");
        Assertions.assertEquals(20.0, aglaea.getCurrentEnergy() - before, 1e-9,
                "「衣匠消失时，使阿格莱雅恢复 20 点能量」");
    }
}
