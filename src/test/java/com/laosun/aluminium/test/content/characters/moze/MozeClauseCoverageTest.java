package com.laosun.aluminium.test.content.characters.moze;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1223 貊泽: the three Eidolons this round wrote.
 *
 * <p>Its {@code _gaps} said 「其余星魂未读」 — nothing pinned them, and Eidolon 2 is the interesting one: the sentence says
 * 「<b>我方全体目标</b>对成为【猎物】的敌方目标造成伤害时」, so the critical-damage bonus belongs to whoever <b>attacks</b>, not to 貊泽. The
 * rule therefore targets {@code actor}, not {@code self} — the trap that makes a cross-unit sentence look like a self-buff.
 */
public class MozeClauseCoverageTest {

    private static final int MOZE = 1223;
    private static final int ALLY = 1002;

    /**
     * 星魂 2: a teammate attacking the marked prey gets +40% crit damage for that instance.
     */
    @Test
    public void eidolon2GivesTheAttackerCritDamageAgainstThePrey() {
        Fixture e2 = new Fixture(2);
        e2.markPrey();
        Assertions.assertEquals(0.4, e2.extraCritDamageOnTheAllysHit(), 1e-9,
                "「我方全体目标对成为【猎物】的…造成伤害时，暴击伤害提高 40%」 — the bonus rides the ATTACKER's instance");

        Fixture e1 = new Fixture(1);
        e1.markPrey();
        Assertions.assertEquals(0.0, e1.extraCritDamageOnTheAllysHit(), 1e-9, "control: it is Eidolon 2");
    }

    /**
     * 星魂 4: 「施放终结技时，貊泽造成的伤害提高 30%，持续 2 回合」.
     */
    @Test
    public void eidolon4BoostsHerDamageAfterTheUltimate() {
        Fixture e4 = new Fixture(4);
        double before = e4.moze.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        e4.castUltimate();
        Assertions.assertEquals(before + 0.3, e4.moze.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "「貊泽造成的伤害提高 30%」");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character moze;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            moze = CharacterFactory.create(MOZE, 80, false, null, null, eidolon);
            battle = new Battle(List.of(moze, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** The Skill puts 【猎物】 on its target; casting it is how the state gets there. */
        private void markPrey() {
            battle.castImmediate(moze.getSkills().get(SkillType.SKILL), moze, List.of(enemy));
            battle.processRequests();
        }

        private void allyAttacks() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
        }

        /**
         * The ally attacks the marked prey, and this reads the instance's own crit-damage bonus.
         *
         * <p>It has to be read off {@code Damage.getExtraCritDamage()}: an {@code instance: true} modifier never lands on the sheet,
         * so a panel reading would show 0.5 before and after and "prove" nothing.
         */
        private double extraCritDamageOnTheAllysHit() {
            double[] seen = {-1};
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> {
                if (seen[0] < 0) {
                    seen[0] = damage.getExtraCritDamage();
                }
            });
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
            return seen[0];
        }

        private void castUltimate() {
            battle.castImmediate(moze.getSkills().get(SkillType.ULTRA), moze, List.of(enemy));
            battle.processRequests();
        }
    }
}

