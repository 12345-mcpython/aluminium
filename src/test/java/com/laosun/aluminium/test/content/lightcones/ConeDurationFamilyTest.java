package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * The two cones whose 「持续 N 回合」 clock was still open.
 *
 * <p><b>20019 调和</b> - 「战斗开始时，我方全体速度提高 12 点，持续 1 回合」. Granted before anybody's first turn, so the data says
 * {@code turns: 2} for the same reason 锋镝 does: the boundary that opens the carrier's first turn arrives before the buff has
 * been live for a turn.
 *
 * <p><b>20020 睿见</b> - 「施放终结技后，攻击力提高 24%，持续 2 回合」. Granted INSIDE her own turn, so the two turns are the
 * ultimate's turn and the one after it.
 */
public class ConeDurationFamilyTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;

    private Battle battle;

    private Character build(int cone) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(cone, LEVEL, false, 1));
        Enemy enemy = BattleFixture.monster();
        battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        return wearer;
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
     * 调和 (20019): the speed is there for her first turn and gone on the second.
     */
    @Test
    public void theBattleStartSpeedLastsItsOneTurn() {
        Character wearer = build(20019);
        double base = CharacterFactory.create(WEARER, LEVEL).getAttribute(AttributeType.SPEED).get();
        Assertions.assertEquals(base + 12, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "「战斗开始时，我方全体速度提高 12 点」 - points, not a percentage");

        takeTurnOf(wearer);
        Assertions.assertEquals(base + 12, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the first turn still has it: 「持续 1 回合」 counts this one");
        takeTurnOf(wearer);
        Assertions.assertEquals(base, wearer.getAttribute(AttributeType.SPEED).get(), 1e-9,
                "the second does not");
    }

    /**
     * 睿见 (20020): the cone's +24% attack is spent by two of her own turns, whatever her own kit is doing.
     *
     * <p>Read as the MODIFIER's remaining duration rather than as an attack value: her kit also moves attack on an ultimate,
     * and a value comparison credits the cone with that too (measured while writing this: the gap between with-cone and
     * without-cone changed when HER buff expired, not when the cone's did).
     */
    @Test
    public void theUltimateAttackLastsTwoTurns() {
        Character wearer = build(20020);
        ultWith(battle, wearer);
        var cone = coneBuff(wearer);
        Assertions.assertNotNull(cone, "the cone hung its modifier on her when she used her ultimate");
        Assertions.assertEquals(2, cone.duration(), "「持续 2 回合」");

        battle.afterMove();
        Assertions.assertEquals(2, coneBuff(wearer).duration(),
                "an early-clock modifier is spent at the START of her turn, not at the end of the one it was granted in");

        takeTurnOf(battle, wearer);
        Assertions.assertEquals(1, coneBuff(wearer).duration(), "one of her turns spent it");
        takeTurnOf(battle, wearer);
        Assertions.assertNull(coneBuff(wearer),
                "two of her turns spend it - the sentence's two turns are the ultimate's turn and the one after it");
    }

    private static com.laosun.aluminium.buff.StatModifierBuff coneBuff(Character unit) {
        return unit.getBuffManager().allBuffsOf(com.laosun.aluminium.buff.StatModifierBuff.class).stream()
                .filter(buff -> buff.getAttribute() == AttributeType.ATTACK
                        && Math.abs(buff.getValue() - 0.24) < 1e-9)
                .findFirst()
                .orElse(null);
    }

    private static void ultWith(Battle target, Character unit) {
        target.setCurrentMove(target.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == unit).findFirst().orElseThrow());
        target.beforeMove();
        target.castImmediate(unit.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA), unit,
                List.of(target.enemyUnits().get(0)));
    }

    private static void takeTurnOf(Battle target, Character unit) {
        target.setCurrentMove(target.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == unit)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for that unit")));
        target.beforeMove();
        target.afterMove();
    }
}
