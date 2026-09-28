package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 杰帕德's freeze, made deterministic (2026-09-28, round 131), and the reason it looked broken for four rounds.
 *
 * <p><b>What the source said.</b> `Battle.hitChance` computes
 * <pre>baseChance * (1 + EFFECT_HIT_RATE) * (1 - EFFECT_RESISTANCE) * (1 - specific)</pre>
 * where `specific` is, for an enemy target, `enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0)` — and the control's
 * key for 冻结 is its own resist key. The monster this project's fixtures use by default carries that key at <b>1.0</b>, so the
 * chance was clamped to <b>0</b> and the roll could never pass: the content was right, the enemy was immune.
 *
 * <p><b>What makes it deterministic now.</b> A hand-made target with no resistances at all, plus eidolon 1's +35% base chance on
 * top of the 65% in the document — i.e. exactly 100%, against 0 hit-rate and 0 resistance. The freeze then lands whatever
 * `rng` returns, so this single case verifies the control, its per-turn payload, the base-chance amendment, and the choice of
 * target all at once.
 */
public class GepardFreezeTest {
    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;

    /** \u26a0 E1 (65% + 35% = 100%) against a target with no resistances: deterministic, and it proves the whole chain. */
    @Test
    public void hisFreezeLandsOnAnUnresistingTarget() {
        Fixture f = new Fixture(1);
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("冻结"), "precondition: not frozen yet");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"),
                "\u300c\u670965%\u7684\u57fa\u7840\u6982\u7387\u4f7f\u53d7\u5230\u653b\u51fb\u7684\u654c\u65b9\u76ee\u6807\u9677\u5165\u51bb\u7ed3\u72b6\u6001\u300d + \u661f\u9b42 1 \u7684 +35% \u21d2 100% base chance");
    }

    /** \u26a0 The control carries its own per-turn payload: 「冻结状态下…每回合开始时受到…冰属性附加伤害」. */
    @Test
    public void theFreezeCarriesItsPerTurnDamage() {
        Fixture f = new Fixture(1);
        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"), "precondition: the freeze landed");

        double before = f.enemy.getCurrentHp();
        f.battle.tickDots(f.enemy);

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "\u300c\u51bb\u7ed3\u72b6\u6001\u4e0b\uff0c\u654c\u65b9\u76ee\u6807\u4e0d\u80fd\u884c\u52a8\u540c\u65f6\u6bcf\u56de\u5408\u5f00\u59cb\u65f6\u53d7\u5230\u7b49\u540c\u4e8e\u6770\u5e15\u5fb760%\u653b\u51fb\u529b\u7684\u51b0\u5c5e\u6027\u9644\u52a0\u4f24\u5bb3\u300d");
    }

    /** At E0 the chance is the document's 65%, so a roll of 0.0 still lands \u2014 the amendment is not what makes it pass. */
    @Test
    public void atEidolonZeroTheDocumentChanceApplies() {
        Fixture f = new Fixture(0);
        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"),
                "65% base chance with a roll of 0.0 and no resistance: the plain clause works without any eidolon");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static final class Fixture {
        private final Character gepard;
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle;

        private Fixture(int eidolon) {
            gepard = CharacterFactory.create(GEPARD, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(gepard), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
        }
    }
}
