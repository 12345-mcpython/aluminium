package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
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
 * 相抗 (20014): 「使装备者消灭敌方目标后，速度提高 10%，持续 2 回合」.
 *
 * <p><b>Why the ordering matters here.</b> The buff is gated on {@code actor == self}, so the kill that grants it is always the
 * wearer's OWN kill - which happens inside her turn. Two of her turns are therefore the kill's turn and the one after it, and
 * that is exactly what this pins. (An earlier reading called the clause short because its fixture killed the enemy BEFORE her
 * first turn, an order that cannot arise once the gate is in place.)
 */
public class ConeTwoThousandFourteenTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;

    private Battle battle;
    private Character wearer;

    private void build() {
        wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(20014, LEVEL, false, 1));
        Enemy enemy = BattleFixture.monster();
        battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
    }

    private void takeTurnOf(Character unit) {
        battle.setCurrentMove(battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for that unit")));
        battle.beforeMove();
        battle.afterMove();
    }

    /**
     * The kill's turn and the next one carry the speed, and the third does not.
     */
    @Test
    public void twoTurnsIsTheKillsTurnAndTheOneAfterIt() {
        build();
        double base = wearer.getAttribute(AttributeType.SPEED).get();

        battle.setCurrentMove(battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == wearer).findFirst().orElseThrow());
        battle.beforeMove();
        battle.applyDamage(battle.enemyUnits().get(0), new Damage(wearer, battle.enemyUnits().get(0),
                DamageElement.WIND, DamageType.NORMAL, 1e9));
        double boosted = wearer.getAttribute(AttributeType.SPEED).get();
        Assertions.assertEquals(base * 1.10, boosted, 1e-9, "her own kill raises her speed by 10%");
        battle.afterMove();

        Assertions.assertEquals(boosted, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the kill's turn keeps it");
        takeTurnOf(wearer);
        Assertions.assertEquals(boosted, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "and so does the next one - that is the sentence's two turns");
        takeTurnOf(wearer);
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the third one does not");
    }
}
