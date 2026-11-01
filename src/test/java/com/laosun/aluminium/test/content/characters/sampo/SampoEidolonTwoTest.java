package com.laosun.aluminium.test.content.characters.sampo;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1108 桑博's Eidolon 2: 「风化状态下的敌方目标被消灭时，使敌方全体有 100% 的基础概率叠加 1 层等同于天赋的风化状态」.
 *
 * <p>The dead unit still carries its buffs at {@code KILL}, the windshear lands on every LIVING opponent, and a clean corpse
 * pays nothing.
 */
public class SampoEidolonTwoTest {

    private static final int LEVEL = 80;
    private static final int SAMPO = 1108;

    @Test
    public void aWindshearedDeathSpreadsOneLayer() {
        Fixture f = new Fixture(2);
        f.windtorn(f.corpse, 1);
        int otherLayers = f.stacks(f.other);

        f.kill(f.corpse);

        System.out.println("[1108] corpse dead=" + f.corpse.isDeath() + " other=" + f.stacks(f.other));
        Assertions.assertEquals(otherLayers + 1, f.stacks(f.other),
                "「使敌方全体…叠加 1 层」 - the living enemy gains one layer");
    }

    @Test
    public void theTwoGatesMatter() {
        Fixture withoutEidolon = new Fixture(0);
        withoutEidolon.windtorn(withoutEidolon.corpse, 1);
        int before = withoutEidolon.stacks(withoutEidolon.other);
        withoutEidolon.kill(withoutEidolon.corpse);
        Assertions.assertEquals(before, withoutEidolon.stacks(withoutEidolon.other), "E0: no spread");

        Fixture noWindshear = new Fixture(2);
        int otherBefore = noWindshear.stacks(noWindshear.other);
        noWindshear.kill(noWindshear.corpse);
        Assertions.assertEquals(otherBefore, noWindshear.stacks(noWindshear.other),
                "「风化状态下的」 - a clean corpse spreads nothing");
    }

    private static final class Fixture {
        private final Character sampo;
        private final Enemy corpse = BattleFixture.monster();
        private final Enemy other = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int eidolon) {
            sampo = CharacterFactory.create(SAMPO, LEVEL, true, null, null, eidolon);
            battle = new Battle(new ArrayList<>(List.of(sampo, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(corpse, other)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
        }

        private void windtorn(Enemy enemy, int layers) {
            for (int i = 0; i < layers; i++) {
                enemy.getBuffManager().addBuff(new DotBuff(sampo, DamageElement.WIND, 100, 3, 5, "风化"));
            }
        }

        private int stacks(Enemy enemy) {
            return enemy.getBuffManager().stacksOf("风化");
        }

        private void kill(Enemy enemy) {
            // Killed by the OTHER ally: if Sampo dealt the fatal hit, his talent would land windshear first and the
            // "clean corpse" case could never exist.
            battle.applyTrueDamage(battle.characters().get(1), enemy, DamageElement.PHYSICAL,
                    enemy.getCurrentHp() * 10);
            battle.processRequests();
        }
    }
}

