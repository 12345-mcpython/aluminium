package com.laosun.aluminium.test.content.characters.tingyun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1202 Tingyun, from her own file: Benediction with its cap, the ultimate's grant, the talent, and the three traces.
 *
 * <p>Against her own listener-first logic: none of these needed a new engine ability. What is registered instead is the pair of
 * sentences where the BLESSED ALLY, not Tingyun, is the one dealing the extra damage.
 */
public class TingyunTest {
    private static final int TINGYUN = 1202;
    private static final int ALLY = 1002;
    private static final int OTHER_ALLY = 1003;
    private static final int LEVEL = 80;
    private static final String BLESSING = "赐福";

    /**
     * Note: "使其攻击力提高50%，最高不超过停云当前攻击力的25%": the gain must respect the ceiling.
     */
    @Test
    public void herSkillBlessesWithACappedGain() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(AttributeType.ATTACK).get();

        f.battle.castImmediate(f.tingyun.getSkills().get(SkillType.SKILL), f.tingyun, List.of(f.ally));

        double after = f.ally.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(f.ally.getBuffManager().hasState("赐福"), "「为指定我方单体提供【赐福】」 (provides a designated single ally with [赐福])");
        Assertions.assertTrue(after > before, "\"raises its ATK by 50%\" (「使其攻击力提高50%」): " + before + " -> " + after);
        double ceiling = f.tingyun.getAttribute(AttributeType.ATTACK).get() * 0.25;
        Assertions.assertTrue(after - before <= ceiling + 1e-6,
                "「最高不超过停云当前攻击力的25%」 (at most 25% of Tingyun's current ATK) -- gain " + (after - before) + " vs ceiling " + ceiling);
    }

    /**
     * Note: The ultimate grants energy AND a two-turn damage boost to the chosen ally.
     */
    @Test
    public void herUltimateGrantsEnergyAndABoost() {
        Fixture f = new Fixture();
        double energyBefore = f.ally.getCurrentEnergy();
        double boostBefore = f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        f.battle.castImmediate(f.tingyun.getSkills().get(SkillType.ULTRA), f.tingyun, List.of(f.ally));

        Assertions.assertTrue(f.ally.getCurrentEnergy() > energyBefore,
                "\"restores 50 Energy to a designated single ally\" (「为指定我方单体恢复50点能量」): " + energyBefore + " -> " + f.ally.getCurrentEnergy());
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() > boostBefore,
                "「同时使目标造成的伤害提高50%，持续2回合」 (and raises the target's DMG dealt by 50% for 2 turns)");
    }


    /**
     * 天赋 紫电扶摇:「敌方目标受到停云攻击后，获得【赐福】的目标立即对其造成等同于自身 60% 攻击力的雷属性附加伤害」.
     *
     * <p>Who settles the instance is the whole clause: the attacker is the BLESSED ALLY, so 「其自身 60%」 is read off that
     * ally ({@code attacker: "holder_of:赐福"}), not off Tingyun - which is why the assertion is on the instance's attacker
     * rather than on a total.
     */
    @Test
    public void herTalentStrikesThroughTheBlessedAlly() {
        Fixture f = new Fixture();
        f.blessAndAttack();
        Assertions.assertEquals(2, f.instances.size(), "her basic attack, then the talent's one extra instance");
        Damage talent = f.instances.get(1);
        Assertions.assertSame(f.ally, talent.getAttacker(),
                "「获得【赐福】的目标立即对其造成…」 — the blessed ally strikes, not Tingyun");
        Assertions.assertEquals(DamageType.ADDITIONAL, talent.getType(), "「附加伤害」");

        Fixture plain = new Fixture();
        plain.attack();
        Assertions.assertEquals(1, plain.instances.size(), "nobody is blessed ⇒ nothing extra");
    }

    /**
     * 战技:「【赐福】持续 3 回合且仅对停云战技最新的施放目标生效」.
     *
     * <p>The attack gain is a SEPARATE, unnamed effect, so this also pins the note-on-the-modifier half: the modifier carries
     * the state's name, and the removal that moves the state therefore takes the number with it.
     */
    @Test
    public void theBlessingMovesToTheNewestTargetOnly() {
        Fixture f = new Fixture();
        double allyBase = f.ally.getAttribute(AttributeType.ATTACK).get();
        f.bless(f.ally);
        Assertions.assertTrue(f.ally.getAttribute(AttributeType.ATTACK).get() > allyBase,
                "precondition: the first blessing landed");
        f.bless(f.otherAlly);

        Assertions.assertFalse(f.ally.getBuffManager().hasState(BLESSING), "「仅对…最新的施放目标生效」");
        Assertions.assertEquals(allyBase, f.ally.getAttribute(AttributeType.ATTACK).get(), 1e-9,
                "and the attack gain leaves with the state (its modifier is named 【赐福】)");
        Assertions.assertTrue(f.otherAlly.getBuffManager().hasState(BLESSING), "the newest target holds it");
    }

    /** 行迹 驻晴:「施放战技时，停云自身速度提高 20%，持续 1 回合」. */
    @Test
    public void herSpeedTraceLastsOneOfHerTurns() {
        Fixture f = new Fixture();
        double base = f.tingyun.getAttribute(AttributeType.SPEED).get();
        f.bless(f.ally);
        Assertions.assertEquals(base * 1.2, f.tingyun.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「施放战技时，停云自身速度提高 20%」");
        TestTurns.take(f.battle, f.tingyun);
        Assertions.assertEquals(base, f.tingyun.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「持续 1 回合」 — one of her own turns is the whole duration");
    }

    /** 行迹 亨通:「停云的回合开始时，自身立即恢复 5 点能量」. */
    @Test
    public void herEnergyTracePaysFiveWhenHerTurnStarts() {
        Fixture f = new Fixture();
        double before = f.tingyun.getCurrentEnergy();
        f.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, f.tingyun, f.tingyun, 0, 0));
        f.battle.processRequests();
        Assertions.assertEquals(before + 5, f.tingyun.getCurrentEnergy(), 1e-9,
                "「回合开始时…立即恢复 5 点能量」");
    }

    private static final class Fixture {
        private final Character tingyun;
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Character otherAlly = CharacterFactory.create(OTHER_ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private final List<Damage> instances = new ArrayList<>();

        private Fixture() {
            this(0);
        }

        private Fixture(int eidolon) {
            tingyun = CharacterFactory.create(TINGYUN, LEVEL, false, null, null, eidolon);
            battle = new Battle(List.of(tingyun, ally, otherAlly), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> instances.add(damage));
        }

        private void bless(Character target) {
            battle.castImmediate(tingyun.getSkills().get(SkillType.SKILL), tingyun, List.of(target));
            battle.processRequests();
        }

        private void blessAndAttack() {
            bless(ally);
            attack();
        }

        /** Tingyun's own basic attack - the event the talent hangs on. */
        private void attack() {
            instances.clear();
            battle.castImmediate(tingyun.getSkills().get(SkillType.COMMON), tingyun, List.of(enemy));
            battle.processRequests();
        }
    }
}