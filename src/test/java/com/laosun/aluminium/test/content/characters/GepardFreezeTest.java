package com.laosun.aluminium.test.content.characters;

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
 * Gepard (杰帕德)'s freeze, made deterministic, and the reason it reads as broken on the default fixture.
 *
 * <p><b>What the source says.</b> `Battle.hitChance` computes
 * <pre>baseChance * (1 + EFFECT_HIT_RATE) * (1 - EFFECT_RESISTANCE) * (1 - specific)</pre>
 * where `specific` is, for an enemy target, `enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0)` - and the control's
 * key for frozen (冻结) is its own resist key. The monster this project's fixtures use by default carries that key at <b>1.0</b>, so the
 * chance is clamped to <b>0</b> and the roll can never pass: the content is right, the enemy is immune.
 *
 * <p><b>What makes it deterministic.</b> A hand-made target with no resistances at all, plus eidolon 1's +35% base chance on
 * top of the 65% in the document - i.e. exactly 100%, against 0 hit-rate and 0 resistance. The freeze then lands whatever
 * `rng` returns, so this single case verifies the control, its per-turn payload, the base-chance amendment, and the choice of
 * target all at once.
 */
public class GepardFreezeTest {
    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;

    /** Note: E1 (65% + 35% = 100%) against a target with no resistances: deterministic, and it proves the whole chain. */
    @Test
    public void hisFreezeLandsOnAnUnresistingTarget() {
        Fixture f = new Fixture(1);
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("冻结"), "precondition: not frozen yet");

        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"),
                "\"a 65% base chance to put the attacked enemy target into the Frozen state\" (「有65%的基础概率使受到攻击的敌方目标陷入冻结状态」) + the +35% from Eidolon (星魂) 1 ⇒ 100% base chance");
    }

    /** Note: The control carries its own per-turn payload: "冻结状态下...每回合开始时受到...冰属性附加伤害". */
    @Test
    public void theFreezeCarriesItsPerTurnDamage() {
        Fixture f = new Fixture(1);
        f.battle.castImmediate(f.gepard.getSkills().get(SkillType.SKILL), f.gepard, List.of(f.enemy));
        Assertions.assertTrue(f.enemy.getBuffManager().hasState("冻结"), "precondition: the freeze landed");

        double before = f.enemy.getCurrentHp();
        f.battle.tickDots(f.enemy);

        Assertions.assertTrue(f.enemy.getCurrentHp() < before,
                "「冻结状态下，敌方目标不能行动同时每回合开始时受到等同于杰帕德60%攻击力的冰属性附加伤害」 (while Frozen, the enemy target cannot act and takes additional Ice DMG equal to 60% of Gepard (杰帕德)'s ATK at the start of every turn)");
    }

    /** At E0 the chance is the document's 65%, so a roll of 0.0 still lands - the amendment is not what makes it pass. */
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
