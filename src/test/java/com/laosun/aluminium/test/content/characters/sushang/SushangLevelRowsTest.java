package com.laosun.aluminium.test.content.characters.sushang;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1206 (Sushang 素裳): the three numbers the file used to write as literals now follow the skill row - and each is
 * pinned at BOTH ends of its level, which is the only way to tell a row read from a literal.
 *
 * <p>Her file's {@code level_convention} is 「语料页引用 Lv10」 (+9), and her Eidolons raise the slots on top of it: E3 adds
 * +2 to ULTRA and TALENT, E5 adds +2 to SKILL. At E0 the literal and the row agree - measured below - so a case that only
 * looked at E0 would pass with either spelling.
 */
public class SushangLevelRowsTest {
    private static final int SUSHANG = 1206;
    private static final int ALLY = 1204;

    /**
     * 「最后一击后有 33% 概率发动【剑势】，对目标造成等同于素裳 100% 攻击力的物理附加伤害」 - the share is the SKILL row's #2.
     *
     * <p>⚠ The observable is a RATIO between two Eidolon ranks, so no zone is pinned: one Skill cast settles the skill's own
     * damage (row #1) plus one Sword Stance (row #2), and the two rows scale together - at E0 by 1.0, at E5 (SKILL +2) by
     * (2.31 + 1.1) / (2.1 + 1.0) = <b>1.1</b> exactly. With the Lv10 literal in the stance it would read (2.31 + 1.0) / 3.1 =
     * 1.0677 instead, which is the red this case owes.
     */
    @Test
    public void theSwordStanceShareFollowsTheSkillRowAtBothEnds() {
        double atZero = oneSkillCastAgainstOneEnemy(0);
        double atFive = oneSkillCastAgainstOneEnemy(5);
        System.out.println("[1206] one Skill cast (skill row #1 + one 剑势 row #2) deals " + atZero + " at E0 and "
                + atFive + " at E5 (x" + (atFive / atZero) + "; with the Lv10 literal it would be "
                + ((2.31 + 1.0) / (2.1 + 1.0)) + ")");
        Assertions.assertEquals(1.1, atFive / atZero, 1e-9,
                "战技行的 #2 随等级走（E0 1.0、E5 1.1），所以这一发「本体 + 剑势」的比正好是 1.1");
    }

    /**
     * 「同时使其攻击力提高 30%…持续 2 回合」 - the boost is the ULTRA row's #4, which E3 raises.
     *
     * <p>⚠ The multiple is not 1.3, and the reason is worth keeping: the boost is a SHARE of her base ATK, and her stat traces
     * already add <b>28%</b> (4 + 4 + 6 + 6 + 8 across her attribute traces, from the data). So the total is
     * {@code (1 + 0.28 + boost) / 1.28} - measured 1.234375 at E0 and 1.253125 at E3, i.e. exactly the two rows. A Lv10 literal
     * would read 1.234375 at BOTH ends, which is the red the E3 half owes.
     */
    @Test
    public void theUltimatesAttackBoostFollowsTheUltraRowAtBothEnds() {
        double atZero = attackGainFromTheUltimate(0);
        double atThree = attackGainFromTheUltimate(3);
        System.out.println("[1206] the Ultimate's ATK boost: E0 x" + atZero + " ; E3 x" + atThree
                + " (traces +28% ⇒ the rows read as shares of the base; the Lv10 literal would read x1.234375 at both)");
        Assertions.assertEquals(1.234375, atZero, 1e-9, "(1 + 0.28 + 0.30) / 1.28 - the Lv10 row's 30%");
        Assertions.assertEquals(1.253125, atThree, 1e-9, "(1 + 0.28 + 0.324) / 1.28 - E3 raises ULTRA to level 12");
    }

    /**
     * 「当场上有敌方目标的弱点被击破，素裳的速度提高 20%，持续 2 回合」 - E3 raises the TALENT that states this number.
     */
    @Test
    public void theTalentSpeedFollowsTheTalentRowAtBothEnds() {
        double atZero = speedGainAfterABreak(0);
        double atThree = speedGainAfterABreak(3);
        System.out.println("[1206] the talent's SPD boost after a break: E0 x" + atZero + " ; E3 x" + atThree
                + " (the Lv10 literal would read x1.2 at both)");
        Assertions.assertEquals(1.2, atZero, 1e-9, "Lv10 行 = 20% ⇒ E0 读到的就是它");
        Assertions.assertEquals(1.21, atThree, 1e-9, "E3 给天赋 +2 级 ⇒ 第 12 行的 #1 = 21%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * The enemy HP one Skill cast takes: the skill's own hit plus the Sword Stance that the 33% roll (made certain by the
     * fixture's source) triggers.
     */
    private static double oneSkillCastAgainstOneEnemy(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, RNG_ALWAYS_PASSES);
        Character sushang = battle.characters().getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        double before = victim.getCurrentHp();
        battle.requestSkill(sushang.getSkills().get(SkillType.SKILL), sushang, List.of(victim));
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.ATTACK_FINISHED, sushang, victim, 1, 0);
        battle.processRequests();
        double dealt = before - victim.getCurrentHp();
        Assertions.assertTrue(dealt > 0, "premise: the cast really settles damage");
        return dealt;
    }

    /**
     * How much her ATK moves, as a multiple, when the Ultimate lands.
     */
    private static double attackGainFromTheUltimate(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, BattleFixture.alwaysRoll(0.9999));
        Character sushang = battle.characters().getFirst();
        double before = sushang.getAttribute(AttributeType.ATTACK).get();
        castUltimate(battle, sushang);
        double after = sushang.getAttribute(AttributeType.ATTACK).get();
        return after / before;
    }

    /**
     * How much her SPD moves, as a multiple, when an enemy's weakness breaks.
     */
    private static double speedGainAfterABreak(int eidolonRank) {
        Battle battle = newBattle(eidolonRank, BattleFixture.alwaysRoll(0.9999));
        Character sushang = battle.characters().getFirst();
        Enemy victim = battle.enemyUnits().getFirst();
        double before = sushang.getAttribute(AttributeType.SPEED).get();
        // Note: the BREAK event's actor is the attacker and its target is the enemy whose toughness broke (Battle's own
        // shape), which is exactly what the talent's rule reads.
        battle.fireTriggers(TriggerEvent.BREAK, sushang, victim, 0, 0);
        battle.processRequests();
        double after = sushang.getAttribute(AttributeType.SPEED).get();
        Assertions.assertTrue(after > before, "premise: the break really raises her SPD");
        return after / before;
    }

    private static void castUltimate(Battle battle, Character sushang) {
        sushang.setCurrentEnergy(sushang.getMaxEnergy());
        battle.castUltra(sushang, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
    }

    /**
     * A source whose double always answers {@code 0.0}: the 33% Sword Stance chance passes (measured - it also makes every
     * crit land, which is a CONSTANT factor and therefore cancels in the ratio this case asserts).
     */
    private static final java.util.Random RNG_ALWAYS_PASSES = BattleFixture.alwaysRoll(0.0);

    private static Battle newBattle(int eidolonRank, java.util.Random rng) {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(SUSHANG, 80, false, null, null, eidolonRank),
                        CharacterFactory.create(ALLY, 80)),
                List.of(BattleFixture.monster()), rng);
        battle.startBattle();
        battle.processRequests();
        return battle;
    }
}
