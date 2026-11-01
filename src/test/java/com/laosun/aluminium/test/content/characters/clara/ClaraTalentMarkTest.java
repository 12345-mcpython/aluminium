package com.laosun.aluminium.test.content.characters.clara;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1107 克拉拉's talent: 「克拉拉受到敌方目标攻击的伤害降低 10%。攻击克拉拉的敌方目标会被史瓦罗标上【反击标记】，并遭到
 * 史瓦罗的反击」 - plus the Skill's 「战技施放后所有【反击标记】失效」.
 *
 * <p>The reduction is measured against a rule-less dummy with her DEF and base HP so the only difference is the zone;
 * the marker and its clearing are state facts.
 */
public class ClaraTalentMarkTest {

    private static final int LEVEL = 80;
    private static final int CLARA = 1107;
    private static final String MARK = "反击标记";

    /**
     * The 10% protection: the same probe hit is 10% smaller on her than on a bare dummy with her DEF.
     */
    @Test
    public void theTalentProtectsHer() {
        Fixture f = new Fixture();
        double claraLoss = f.probeClara();
        double dummyLoss = f.probeDummy();
        System.out.println("[1107] clara=" + claraLoss + " dummy=" + dummyLoss + " ratio=" + (claraLoss / dummyLoss));
        Assertions.assertEquals(0.9, claraLoss / dummyLoss, 1e-6,
                "「受到敌方目标攻击的伤害降低 10%」");
    }

    /**
     * The attacker carries 【反击标记】, and the Skill clears every mark.
     */
    @Test
    public void theAttackerIsMarkedAndTheSkillClearsIt() {
        Fixture f = new Fixture();
        f.claraLosesHp();
        Assertions.assertTrue(f.enemy.getBuffManager().hasState(MARK),
                "「攻击克拉拉的敌方目标会被史瓦罗标上【反击标记】」");

        f.castSkill();
        Assertions.assertFalse(f.enemy.getBuffManager().hasState(MARK),
                "「战技施放后所有【反击标记】失效」");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character clara;
        private final Character dummy;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        private final Battle dummyBattle;

        private Fixture() {
            clara = CharacterFactory.create(CLARA, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(clara, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
            battle.startBattle();

            double defence = clara.getAttribute(AttributeType.DEFENCE).get();
            double health = clara.getMaxHp();
            dummy = Character.fromAttributes("Bare Dummy", health, defence, 100, 100);
            dummyBattle = new Battle(new ArrayList<>(List.of(dummy)), new ArrayList<>(List.of(BattleFixture.monster())),
                    BattleFixture.alwaysRoll(0.99));
            dummyBattle.startBattle();
        }

        private double probeClara() {
            double before = clara.getCurrentHp();
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - clara.getCurrentHp();
        }

        private double probeDummy() {
            double before = dummy.getCurrentHp();
            dummyBattle.applyDamage(dummy, new Damage(dummyBattle.enemies().getFirst(), dummy,
                    DamageElement.PHYSICAL, DamageType.NORMAL, 200.0));
            return before - dummy.getCurrentHp();
        }

        private void claraLosesHp() {
            battle.applyDamage(clara, new Damage(enemy, clara, DamageElement.PHYSICAL, DamageType.NORMAL, 100.0));
        }

        private void castSkill() {
            battle.castImmediate(clara.getSkills().get(SkillType.SKILL), clara, List.of(enemy));
        }
    }
}

