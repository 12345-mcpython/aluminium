package com.laosun.aluminium.test;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.HardLevelGroup;
import com.laosun.aluminium.beans.MonsterConfig;
import com.laosun.aluminium.beans.MonsterTemplate;
import com.laosun.aluminium.enums.DamageElement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * P2-1 acceptance: the three monster data files load with the right shapes and values.
 *
 * <p>Anchor data (matching `E:\code\blog\hsr\HSR.md` §1.2 / the measurements taken in the session):
 * 冰锋 1002011 template `18 / 210 / 69.75 / 100 / 60`, weak to fire+lightning, resistance to
 * physical/ice/wind/quantum/imaginary 0.2;
 * level group 1·Lv90 = `36.821384 / 5.238095 / 236.53471 / 1.32 / 1 / 0.32 / 0.1`.
 */
public class MonsterDataTest {
    private static final double EPS = 1e-9;

    @Test
    public void iceEdgeTemplateLoads() {
        MonsterTemplate template = Constant.MONSTER_TEMPLATES.get(1002011);

        Assertions.assertNotNull(template);
        Assertions.assertEquals(18, template.attack(), EPS);
        Assertions.assertEquals(210, template.defence(), EPS);
        Assertions.assertEquals(69.75, template.health(), EPS);
        Assertions.assertEquals(100, template.speed(), EPS);
        Assertions.assertEquals(60, template.stance(), EPS);
        Assertions.assertEquals(1, template.stanceCount());
        Assertions.assertEquals(DamageElement.ICE, template.stanceType());
        Assertions.assertEquals(0.2, template.effectResistance(), EPS);
    }

    @Test
    public void iceEdgeInstanceLoads() {
        MonsterConfig config = Constant.MONSTER_CONFIGS.get(1002011);

        Assertions.assertNotNull(config);
        Assertions.assertEquals(1002011, config.templateId());
        Assertions.assertEquals(1, config.eliteGroup());
        Assertions.assertEquals(1, config.hardLevelGroup());
        Assertions.assertEquals(java.util.List.of(DamageElement.FIRE, DamageElement.THUNDER), config.stanceWeak());
        Assertions.assertEquals(Map.of(
                DamageElement.PHYSICAL, 0.2,
                DamageElement.ICE, 0.2,
                DamageElement.WIND, 0.2,
                DamageElement.QUANTUM, 0.2,
                DamageElement.IMAGINARY, 0.2), config.damageResistance());
    }

    @Test
    public void everyModifyRatioIsPresentAfterNormalisation() {
        Assertions.assertFalse(Constant.MONSTER_CONFIGS.isEmpty());
        Constant.MONSTER_CONFIGS.forEach((id, config) -> {
            Assertions.assertNotNull(config.hpRatio(), "hpRatio null @" + id);
            Assertions.assertNotNull(config.attackRatio(), "attackRatio null @" + id);
            Assertions.assertNotNull(config.defenceRatio(), "defenceRatio null @" + id);
            Assertions.assertNotNull(config.speedRatio(), "speedRatio null @" + id);
            Assertions.assertNotNull(config.stanceRatio(), "stanceRatio null @" + id);
            Assertions.assertNotNull(config.stanceWeak(), "stanceWeak null @" + id);
            Assertions.assertNotNull(config.damageResistance(), "damageResistance null @" + id);
        });
    }

    @Test
    public void hpRatioUsesHealthModifyRatioAndNotTheGhostField() {
        // 802501003 is a 绝境 instance measured in the session: the real HP ratio is 1.979167;
        // the hp_modify_ratio in the same entry is a ghost field (always 1), using it would double the HP.
        Assertions.assertEquals(1.979167, Constant.MONSTER_CONFIGS.get(802501003).hpRatio(), EPS);
        // 100201101 is even more extreme: the real value is 0.266667, the ghost field says 1
        Assertions.assertEquals(0.266667, Constant.MONSTER_CONFIGS.get(100201101).hpRatio(), EPS);
    }

    @Test
    public void attackRatioIsPatchedFromTbgd() {
        // this data set did not export tbgd's AttackModifyRatio → it is filled in by the patch file (100201506 = 0.33333302)
        Assertions.assertEquals(0.33333302, Constant.MONSTER_CONFIGS.get(100201506).attackRatio(), 1e-8);
        // monsters that were not patched take 1.0
        Assertions.assertEquals(1.0, Constant.MONSTER_CONFIGS.get(1002011).attackRatio(), EPS);
    }

    @Test
    public void levelGroupRatiosLoad() {
        HardLevelGroup level90 = Constant.HARD_LEVEL_GROUPS.get(1).get(90);

        Assertions.assertEquals(36.821384, level90.attack(), EPS);
        Assertions.assertEquals(5.238095, level90.defence(), EPS);
        Assertions.assertEquals(236.53471, level90.health(), EPS);
        Assertions.assertEquals(1.32, level90.speed(), EPS);
        Assertions.assertEquals(1, level90.stance(), EPS);
        Assertions.assertEquals(0.32, level90.effectHitRate(), EPS);
        Assertions.assertEquals(0.1, level90.effectResistance(), EPS);
    }

    @Test
    public void highLevelGroupRatiosLoad() {
        HardLevelGroup level120 = Constant.HARD_LEVEL_GROUPS.get(3).get(120);

        // a 绝境 level group verified in the session (group 3·Lv120): HP ratio 1938.7634
        Assertions.assertEquals(1938.7634, level120.health(), EPS);
        Assertions.assertEquals(49.879406, level120.attack(), EPS);
        Assertions.assertEquals(5.714286, level120.defence(), EPS);
        Assertions.assertEquals(1.5, level120.speed(), EPS);
        Assertions.assertEquals(0.5, level120.effectHitRate(), EPS);
        Assertions.assertEquals(0.2, level120.effectResistance(), EPS);
    }
}
