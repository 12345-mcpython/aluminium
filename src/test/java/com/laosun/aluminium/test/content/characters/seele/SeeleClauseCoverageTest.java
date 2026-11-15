package com.laosun.aluminium.test.content.characters.seele;

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
 * 1102 希儿: the Skill's speed buff and the Eidolon that lets it stack.
 *
 * <p><b>What was missing.</b> 「使希儿的速度提高 25%，持续 2 回合」 had no rule at all — and the gap that mentioned it said
 * 「把现有的速度 buff 改成可叠」, presupposing a buff that did not exist. So the base clause was absent and the Eidolon had
 * nothing to raise; both are here now.
 *
 * <p><b>L3.</b> The Skill's speed buff and Eidolon 2's stacking read the same board shape and the same observed object (her
 * SPEED after the casts), so they are one scenario test with an {@code Assertions.assertAll}, the E0 board serving as the
 * rank-below control for the stacking sentence. Eidolon 6's mark and its rider keep their own test - a different observation.
 */
public class SeeleClauseCoverageTest {

    private static final int SEELE = 1102;

    /**
     * 战技: 「速度提高 25%，持续 2 回合」 — one layer, whether she casts once or twice — and 星魂 2: 「战技的加速效果可以叠加，
     * 最多叠加 2 层」, whose second cast adds a second layer. The E0 board is the control for the stacking clause.
     */
    @Test
    public void theSkillSpeedBuffStacksOnlyAtEidolonTwo() {
        Fixture e0 = new Fixture(0);
        double e0Base = e0.seele.getAttribute(AttributeType.SPEED).get();
        e0.castSkill();
        double e0AfterOne = e0.seele.getAttribute(AttributeType.SPEED).get();
        e0.castSkill();
        double e0AfterTwo = e0.seele.getAttribute(AttributeType.SPEED).get();

        Fixture e2 = new Fixture(2);
        double e2Base = e2.seele.getAttribute(AttributeType.SPEED).get();
        e2.castSkill();
        double e2AfterOne = e2.seele.getAttribute(AttributeType.SPEED).get();
        e2.castSkill();
        double e2AfterTwo = e2.seele.getAttribute(AttributeType.SPEED).get();

        Assertions.assertAll("战技「使希儿的速度提高 25%，持续 2 回合」: E0 one layer, no stack; 星魂 2 two layers",
                () -> Assertions.assertTrue(e0AfterOne > e0Base,
                        "「使希儿的速度提高 25%」 (" + e0Base + " -> " + e0AfterOne + ")"),
                () -> Assertions.assertEquals(e0AfterOne, e0AfterTwo, 1e-9, "at E0 the buff does not stack"),
                () -> Assertions.assertTrue(e2AfterTwo > e2AfterOne,
                        "「可以叠加，最多叠加 2 层」 (" + e2AfterOne + " -> " + e2AfterTwo + ")"),
                () -> Assertions.assertEquals(e2Base * 1.5, e2AfterTwo, 1e-6,
                        "two layers of 25% on the base speed"));
    }

    /**
     * 星魂 6: the Ultimate marks its target, and the mark turns every later ATTACK into one extra Quantum instance.
     *
     * <p>The marking half is measured through the state; the rider through a damage observer, because it is a settled instance rather
     * than a number on the sheet.
     */
    @Test
    public void eidolon6MarksTheTargetAndTheMarkAddsARider() {
        Assertions.assertTrue(markedByTheUltimate(), "「施放终结技攻击后…陷入【char:1102/chaos_butterfly】」");
        Assertions.assertTrue(riderFiresOnANormalAttack(), "「受到攻击后，额外受到 1 次…量子附加伤害」");
    }

    private static boolean markedByTheUltimate() {
        Fixture f = new Fixture(6);
        f.castUltimate();
        return f.enemy.getBuffManager().hasState("char:1102/chaos_butterfly");
    }

    private static boolean riderFiresOnANormalAttack() {
        Fixture withMark = new Fixture(6);
        withMark.castUltimate();
        int withCount = instancesOfOneBasic(withMark);

        Fixture plain = new Fixture(6);
        int plainCount = instancesOfOneBasic(plain);
        return withCount > plainCount;
    }

    private static int instancesOfOneBasic(Fixture f) {
        int[] n = {0};
        f.battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> n[0]++);
        f.castBasic();
        return n[0];
    }

    // ==================================================================

    private static final class Fixture {
        private final Character seele;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture(int eidolon) {
            seele = CharacterFactory.create(SEELE, 80, false, null, null, eidolon);
            battle = new Battle(List.of(seele), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castUltimate() {
            battle.castImmediate(seele.getSkills().get(SkillType.ULTRA), seele, List.of(enemy));
            battle.processRequests();
        }

        private void castBasic() {
            battle.castImmediate(seele.getSkills().get(SkillType.COMMON), seele, List.of(enemy));
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(seele.getSkills().get(SkillType.SKILL), seele, List.of(enemy));
            battle.processRequests();
        }
    }
}

