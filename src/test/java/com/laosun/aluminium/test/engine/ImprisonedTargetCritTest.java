package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.equipment.Relic;

/**
 * Relic 112, 4 pieces: crit chance against a debuffed target, crit damage against an imprisoned one -- both INSTANCE-scoped.
 *
 * <p>Fixtures follow existing tests: `new StateBuff(name, turns)` for the state (BailuRegenTest) and `new DotBuff(source, element, baseDamage, turns)` for the
 * debuff (the DoT the buff manager counts).
 */
public class ImprisonedTargetCritTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int RELIC_LEVEL = 15;
    private static final String IMPRISONED = "禁锢";

    @Test
    public void theFourthPieceAddsCritDamageAgainstAnImprisonedTarget() {
        Damage damage = settle(true, false);
        Assertions.assertEquals(0.2, damage.getExtraCritDamage(), 1e-9, "the data's crit-damage param");
    }

    @Test
    public void theFourthPieceAddsCritChanceAgainstADebuffedTarget() {
        Damage damage = settle(false, true);
        Assertions.assertEquals(0.1, damage.getExtraCritChance(), 1e-9, "the data's crit-chance param");
    }

    private static Damage settle(boolean imprisoned, boolean debuffed) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(112, 4, RELIC_LEVEL));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        if (imprisoned) {
            enemy.getBuffManager().addBuff(new StateBuff(IMPRISONED, 3));
        }
        if (debuffed) {
            enemy.getBuffManager().addBuff(new DotBuff(unit, DamageElement.FIRE, 100, 3));
        }
        Damage damage = new Damage(unit, enemy, DamageElement.QUANTUM, DamageType.NORMAL, 1000);
        battle.applyDamage(enemy, damage);
        System.out.println("[112] debuffCount=" + enemy.getBuffManager().debuffCount()
                + " critChance=" + damage.getExtraCritChance() + " critDamage=" + damage.getExtraCritDamage());
        return damage;
    }
}
