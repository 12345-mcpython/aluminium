package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1002 Dan Heng (丹恒): the base kit - the documented panel and row values, plus the talent that answers "an ally
 * aimed a skill at me".
 *
 * <h2>Panel and rows</h2>
 * The other Dan Heng cases assert <b>behaviour</b> - a slow lands, the talent fires again after one turn, the ultimate
 * hits harder at a slowed target. None of them would notice a regenerated data set moving his ATK from 546.84 to 550:
 * the behaviour is identical. This case pins the numbers themselves, so a data change has to be a decision.
 *
 * <p>What the document says (and where): 「面板成长」last row: Lv80 生命 <b>882.0</b> / 攻击 <b>546.84</b> /
 * 防御 <b>396.9</b> / 速度 <b>110</b>; 「基础面板」: 暴击率 <b>0.05</b> / 暴击伤害 <b>0.5</b>; 「行迹」stat traces:
 * 风属性伤害提高 <b>3.2 / 3.2 / 4.8 / 4.8 / 6.4</b> (= 22.4%), 攻击力 <b>4 / 6 / 8</b> (= 18%), 防御力
 * <b>5 / 7.5</b> (= 12.5%); skills: 最高等级 <b>10 / 15 / 15 / 15</b>, 破韧值 <b>30 / 60 / 90</b>, 终结技能量
 * <b>100</b>, 最大能量 <b>100</b>; Lv10 rows: 普攻 <b>1.4</b>, 战技 <b>[2.6, 0.12, 2, 1]</b>, 终结技
 * <b>[4.0, 1.2]</b>, 天赋 <b>[0.36, 2]</b>.
 *
 * <p>⚠ The document's 普攻 prose says 100%: that is the <b>Lv6</b> row of a ten-row table, and this file follows the
 * repository-wide Lv10 convention (`RAISE_SKILL_LEVEL +9`, recorded in the file's `level_convention` note). The
 * <b>140%</b> asserted below is therefore the documented row, not a disagreement with the prose.
 *
 * <h2>天赋: who may trigger it</h2>
 * The talent that answers "an ally aimed a skill at me". Everything it needs already existed: `SKILL_CAST` carries the
 * aim (so `target == self` means "I was the one aimed at"), `actor is_ally` says who cast it,
 * `DAMAGE_PENETRATION` is the engine's name for RES PEN (抗性穿透), `until: next_attack` is the duration the sentence
 * asks for, and `cooldown` is the rule-level field for "2回合后可再次触发".
 *
 * <p>Merged from the former {@code DanHengPanelAgreementTest} and {@code DanHengTest}; every assertion and Javadoc of
 * measurement is preserved, the single-clause cases consolidated into scenario tests.
 */
public class DanHengBaseKitTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    // ==================================================================
    // From DanHengPanelAgreementTest: the panel and every row value
    // ==================================================================

    /**
     * The level-80 panel, and the three stat-trace totals folded into it.
     */
    @Test
    public void thePanelIsTheDocuments() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        new Battle(new ArrayList<>(List.of(danHeng)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded()).startBattle();

        double health = danHeng.getAttribute(AttributeType.HEALTH).get();
        double attack = danHeng.getAttribute(AttributeType.ATTACK).get();
        double defence = danHeng.getAttribute(AttributeType.DEFENCE).get();
        double speed = danHeng.getAttribute(AttributeType.SPEED).get();

        // 「面板成长」's last row is the promotion base, which is what `baseValue()` carries: the traces are modifiers.
        Assertions.assertEquals(882.0, danHeng.getAttribute(AttributeType.HEALTH).baseValue(), 1e-6, "生命上限");
        Assertions.assertEquals(546.84, danHeng.getAttribute(AttributeType.ATTACK).baseValue(), 1e-6, "攻击力");
        Assertions.assertEquals(396.9, danHeng.getAttribute(AttributeType.DEFENCE).baseValue(), 1e-6, "防御力");
        Assertions.assertEquals(110.0, speed, 1e-6, "速度");

        // 行迹: 攻击力 +4+6+8, 防御力 +5+7.5, 风属性伤害提高 3.2+3.2+4.8+4.8+6.4
        Assertions.assertEquals(546.84 * 1.18, attack, 1e-6, "攻击力 +18%（行迹 4 / 6 / 8%）");
        Assertions.assertEquals(396.9 * 1.125, defence, 1e-6, "防御力 +12.5%（行迹 5 / 7.5%）");
        Assertions.assertEquals(0.224, danHeng.getAttribute(AttributeType.WIND_DAMAGE_BOOST).get(), 1e-9,
                "风属性伤害提高 22.4%（行迹 3.2 / 3.2 / 4.8 / 4.8 / 6.4%）");
        Assertions.assertEquals(health, danHeng.getAttribute(AttributeType.HEALTH).get(), EPS, "no health traces");

        // 「基础面板」
        Assertions.assertEquals(0.05, danHeng.getAttribute(AttributeType.CRIT_CHANCE).get(), EPS, "暴击率 5%");
        Assertions.assertEquals(0.5, danHeng.getAttribute(AttributeType.CRIT_ATTACK).get(), EPS, "暴击伤害 50%");
        Assertions.assertEquals(100.0, danHeng.getMaxEnergy(), EPS, "最大能量 100");
    }

    /**
     * The skill table's own numbers: level counts, toughness, the ultimate's cost, and the Lv10 rows the content reads.
     */
    @Test
    public void theRowsAreTheDocuments() {
        Assertions.assertEquals(10, SkillData.init(DANHENG, 1).getSkills().size(), "普攻 最高等级 10");
        Assertions.assertEquals(15, SkillData.init(DANHENG, 2).getSkills().size(), "战技 最高等级 15");
        Assertions.assertEquals(15, SkillData.init(DANHENG, 3).getSkills().size(), "终结技 最高等级 15");
        Assertions.assertEquals(15, SkillData.init(DANHENG, 4).getSkills().size(), "天赋 最高等级 15");

        Assertions.assertEquals(30.0, SkillData.init(DANHENG, 1).getStanceList().single(), EPS, "普攻 破韧 30");
        Assertions.assertEquals(60.0, SkillData.init(DANHENG, 2).getStanceList().single(), EPS, "战技 破韧 60");
        Assertions.assertEquals(90.0, SkillData.init(DANHENG, 3).getStanceList().single(), EPS, "终结技 破韧 90");
        Assertions.assertEquals(30.0, SkillData.init(DANHENG, 6).getStanceList().single(), EPS, "地图攻击 破韧 30");
        Assertions.assertEquals(100.0, SkillData.init(DANHENG, 3).getSpNeed(), EPS, "终结技 释放所需能量 100");

        // The rows the content reads (all at Lv10, the file's convention).
        Assertions.assertEquals(List.of(1.4), SkillData.init(DANHENG, 1).getSkills().get(9), "普攻 Lv10");
        Assertions.assertEquals(List.of(2.6, 0.12, 2.0, 1.0), SkillData.init(DANHENG, 2).getSkills().get(9),
                "战技 Lv10 [倍率, 减速, 回合, 1]");
        Assertions.assertEquals(List.of(4.0, 1.2), SkillData.init(DANHENG, 3).getSkills().get(9),
                "终结技 Lv10 [倍率, 减速追加倍率]");
        Assertions.assertEquals(List.of(0.36, 2.0), SkillData.init(DANHENG, 4).getSkills().get(9),
                "天赋 Lv10 [穿透, 冷却]");
        Assertions.assertEquals(List.of(0.4, 3.0), SkillData.init(DANHENG, 7).getSkills().getFirst(),
                "秘技 [攻击力, 回合]");
    }

    // ==================================================================
    // From DanHengTest: the talent that answers an ally's Skill
    // ==================================================================

    /**
     * Note: An ally's skill aimed at him raises his penetration, and the control - a skill aimed elsewhere does not.
     * Both halves read the same observed object (DAMAGE_PENETRATION) off the same fixture, so they are one scenario;
     * only the aim differs.
     */
    @Test
    public void theTalentAnswersAnAllySkillAimedAtHimAndNothingElse() {
        Fixture aimedAtHim = new Fixture();
        double before = aimedAtHim.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        aimedAtHim.battle.castImmediate(aimedAtHim.ally.getSkills().get(SkillType.SKILL), aimedAtHim.ally,
                List.of(aimedAtHim.danheng));
        double after = aimedAtHim.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Fixture aimedElsewhere = new Fixture();
        double elsewhereBefore = aimedElsewhere.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        aimedElsewhere.battle.castImmediate(aimedElsewhere.ally.getSkills().get(SkillType.SKILL),
                aimedElsewhere.ally, List.of(aimedElsewhere.ally));
        double elsewhereAfter = aimedElsewhere.danheng.getAttribute(AttributeType.DAMAGE_PENETRATION).get();

        Assertions.assertAll("「成为我方技能的施放目标」",
                () -> Assertions.assertTrue(after > before,
                        "「当丹恒成为我方技能的施放目标时，下一次攻击的风属性抗性穿透提高36%」 (when Dan Heng becomes the target of one of our Skills, his next attack has 36% more Wind RES PEN): "
                                + before + " -> " + after),
                () -> Assertions.assertEquals(elsewhereBefore, elsewhereAfter, 1e-9,
                        "「成为我方技能的施放目标」 (becomes the target of one of our Skills) -- being on the field is not enough"));
    }

    private static final class Fixture {
        private final Character danheng = CharacterFactory.create(DANHENG, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(danheng, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private Fixture() {
            battle.startBattle();
        }
    }
}
