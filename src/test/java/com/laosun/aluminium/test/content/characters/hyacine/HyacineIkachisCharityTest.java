package com.laosun.aluminium.test.content.characters.hyacine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1409 风堇 忆灵技2, first clause: the memosprite pays 4% of its OWN Max HP and heals whoever lost HP for 2% of 风堇
 * Max HP + 20.
 *
 * <p>The heal is measured as the DIFFERENCE between two battles (with and without 小伊卡), never by predicting the HP
 * after the hit: a {@code Damage} goes through the defence and resistance zones, so "20% of Max HP" is not what the
 * victim loses. Assuming it was made this rule look like it paid twice.
 */
public class HyacineIkachisCharityTest {

    private static final int HYACINE = 1409;
    private static final int WOUNDED = 1001;
    private static final int HEALTHY = 1002;
    private static final double EPS = 1e-6;

    @Test
    public void ikachiPaysAndHealsWhoeverLostHp() {
        Run withoutIka = new Run(false);
        Run withIka = new Run(true);
        double itsHpBefore = withIka.ika.getCurrentHp();

        withIka.hitOnce();
        withoutIka.hitOnce();

        double paid = itsHpBefore - withIka.ika.getCurrentHp();
        Assertions.assertEquals(withIka.ika.getMaxHp() * 0.04, paid, Math.max(EPS, itsHpBefore * 1e-9),
                "paid 4% of 小伊卡 OWN Max HP (its=" + withIka.ika.getMaxHp() + ", paid=" + paid + ")");

        double heal = withIka.woundedAfter - withoutIka.woundedAfter;
        double expected = withIka.hyacine.getAttribute(AttributeType.HEALTH).get() * 0.02 + 20;
        Assertions.assertEquals(expected, heal, Math.max(EPS, expected * 1e-6),
                "2% of 风堇 Max HP + 20 to the ally that lost HP (with=" + withIka.woundedAfter + ", without="
                        + withoutIka.woundedAfter + ")");

        Assertions.assertEquals(withoutIka.healthyAfter, withIka.healthyAfter, EPS,
                "an ally that took no damage is not healed");
    }

    private static final class Run {
        final Battle battle;
        final Character hyacine;
        final Character wounded;
        final Character healthy;
        final Summon ika;
        double woundedAfter;
        double healthyAfter;

        Run(boolean summon) {
            this.hyacine = CharacterFactory.create(HYACINE, 80);
            this.wounded = CharacterFactory.create(WOUNDED, 80);
            this.healthy = CharacterFactory.create(HEALTHY, 80);
            this.battle = new Battle(List.of(hyacine, wounded, healthy), List.of(BattleFixture.dummy()),
                    BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
            if (summon) {
                SkillExecutor.execute(battle, hyacine.getSkills().get(SkillType.SKILL), hyacine,
                        List.of(battle.enemies().getFirst()));
                battle.processRequests();
            }
            this.ika = (Summon) battle.memospriteOf(hyacine);
            if (summon) {
                Assertions.assertNotNull(ika, "precondition: 小伊卡 is out");
            }
        }

        void hitOnce() {
            battle.applyDamage(wounded, new Damage(battle.enemies().getFirst(), wounded, DamageElement.PHYSICAL,
                    DamageType.NORMAL, wounded.getMaxHp() * 0.2));
            battle.processRequests();
            this.woundedAfter = wounded.getCurrentHp();
            this.healthyAfter = healthy.getCurrentHp();
        }
    }
}
