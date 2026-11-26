package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 当第一声「阿哈」响起 (light cone 23065), 叠影 1: 「使装备者的基础速度提高 12 点。对我方其他单体施放战技时，获得 10 个笑点，该效果
 * 每间隔 1 回合最多触发 1 次，随后使我方全体在本场战斗中造成伤害时无视敌方目标 16% 的防御力，该效果无法叠加。」
 *
 * <p><b>What the file carries, and what the data carries.</b> The base-speed clause is the data's own
 * {@code ability_property} ({@code speed: 12}) - writing it again would double it - so the rule file holds only the
 * Skill-target clause: {@code SKILL_CAST} + {@code target is_ally} + {@code target != self} → {@code cooldown: 1} →
 * gain {@code elation:punchline} 10 and {@code DEFENCE_IGNORE 0.16} on every ally for the battle.
 *
 * <p>「其他」 is the {@code target != self} term, and 「每间隔 1 回合最多触发 1 次」 is the rule-level {@code cooldown} -
 * both are pinned below by a second cast that changes nothing.
 */
public class Cone23065FirstEchoTest {

    private static final int LEVEL = 80;
    private static final int CONE = 23065;
    /** 1101 布洛妮娅: her Skill is aimed at ONE OTHER ally, which is the sentence's trigger. */
    private static final int WEARER = 1101;
    private static final int ALLY = 1001;
    private static final String PUNCHLINE = "elation:punchline";

    /** 「使装备者的基础速度提高 12 点」 is the cone's data-side ability property, not a rule. */
    @Test
    public void theBaseSpeedIsTheConesOwnProperty() {
        double bare = CharacterFactory.create(WEARER, LEVEL)
                .getAttribute(AttributeType.SPEED).get();
        double wearing = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                .getAttribute(AttributeType.SPEED).get();

        Assertions.assertEquals(12.0, wearing - bare, 1e-6,
                "ability_property speed=12: " + bare + " -> " + wearing);
    }

    /** 「对我方其他单体施放战技时，获得 10 个笑点…随后使我方全体…无视 16% 防御力」. */
    @Test
    public void castingTheSkillOnAnotherAllyGrantsPunchlineAndPartyDefenceIgnore() {
        Run run = new Run();
        Assertions.assertEquals(0.0, run.punchline(), 1e-9, "precondition: no punchline yet");
        Assertions.assertEquals(0.0, run.wearerDefenceIgnore(), 1e-9, "precondition: nothing ignored yet");

        run.castSkillOnAlly();

        Assertions.assertEquals(10.0, run.punchline(), 1e-9, "「获得 10 个笑点」");
        Assertions.assertEquals(0.16, run.wearerDefenceIgnore(), 1e-9, "「我方全体…无视 16% 防御力」 - the wearer");
        Assertions.assertEquals(0.16, run.allyDefenceIgnore(), 1e-9, "and the other ally");
    }

    /** 「每间隔 1 回合最多触发 1 次」: a second cast before a turn passes changes nothing. */
    @Test
    public void theCooldownSwallowsASecondCastInTheSameTurn() {
        Run run = new Run();
        run.castSkillOnAlly();
        run.castSkillOnAlly();

        Assertions.assertEquals(10.0, run.punchline(), 1e-9, "still one grant, not 20");
        Assertions.assertEquals(0.16, run.wearerDefenceIgnore(), 1e-9, "and still one 16%, not 32%");
    }

    /** 「对我方**其他**单体」: a Skill aimed at an ENEMY is not the trigger. */
    @Test
    public void aSkillAimedAtAnEnemyDoesNotTrigger() {
        Run run = new Run();
        run.castSkillOnEnemy();

        Assertions.assertEquals(0.0, run.punchline(), 1e-9, "「对我方其他单体」 - an enemy is not one");
        Assertions.assertEquals(0.0, run.wearerDefenceIgnore(), 1e-9, "and nothing is ignored");
    }

    // ==================================================================

    private static final class Run {
        private final Character wearer;
        private final Character ally;
        private final Enemy enemy;
        private final Battle battle;

        private Run() {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkillOnAlly() {
            battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(ally));
            battle.processRequests();
        }

        private void castSkillOnEnemy() {
            battle.castImmediate(wearer.getSkills().get(SkillType.SKILL), wearer, List.of(enemy));
            battle.processRequests();
        }

        private double punchline() {
            return battle.partyResource(PUNCHLINE).value();
        }

        private double wearerDefenceIgnore() {
            return wearer.getAttribute(AttributeType.DEFENCE_IGNORE).get();
        }

        private double allyDefenceIgnore() {
            return ally.getAttribute(AttributeType.DEFENCE_IGNORE).get();
        }
    }
}
