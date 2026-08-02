package com.laosun.ui;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillAttackType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DataSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Trace;
import com.laosun.aluminium.models.Weapon;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 敌人设置与敌人波次构建.
 */
public final class EnemyUI {

    private EnemyUI() {
    }

    public static String presetName(int preset) {
        return switch (preset) {
            case 2 -> "精英战斗";
            case 3 -> "Boss 战";
            default -> "普通遭遇";
        };
    }

    public static List<Enemy> buildEnemyWave(int level, List<Character> party) {
        double hpScale = party.stream().anyMatch(c ->
                c.getSkills().containsKey(SkillType.ELATION)) ? 3.0 : 1.0;
        if (party.stream().anyMatch(c -> c.getCid() == 8005 || c.getCid() == 8006)) {
            hpScale = 1.5;
        }
        double toughnessScale = party.stream().anyMatch(c -> c.getCid() == 8005 || c.getCid() == 8006) ? 0.5 : 1.0;
        List<Enemy> wave = new ArrayList<>();
        switch (GameState.enemyPreset) {
            case 2 -> {
                wave.add(Enemy.fromTemplate("Trotter (扑满)", level,
                        80 * hpScale, 26, 240, 120, 3 * toughnessScale,
                        Element.PHYSICAL, EnumSet.of(Element.PHYSICAL), java.util.Map.of(),
                        List.of(new Enemy.EnemySkill("Headbutt", Element.PHYSICAL, 0.5, SkillAttackType.SINGLE))));
                wave.add(Enemy.fromTemplate("Voidranger Eliminator (虚卒·掠夺者)", level,
                        220 * hpScale, 40, 260, 100, 16 * toughnessScale,
                        Element.IMAGINARY, EnumSet.of(Element.IMAGINARY, Element.PHYSICAL),
                        java.util.Map.of(Element.IMAGINARY, 0.25),
                        List.of(new Enemy.EnemySkill("Rift Slash", Element.IMAGINARY, 0.9, SkillAttackType.SINGLE),
                                new Enemy.EnemySkill("Null Wave", Element.IMAGINARY, 0.5, SkillAttackType.ALL))));
                wave.add(Enemy.fromTemplate("Blaze Out of Space (炽燃徘徊者)", level,
                        110 * hpScale, 30, 240, 105, 8 * toughnessScale,
                        Element.FIRE, EnumSet.of(Element.FIRE, Element.ICE), java.util.Map.of(Element.FIRE, 0.2),
                        List.of(new Enemy.EnemySkill("Flame Lash", Element.FIRE, 0.8, SkillAttackType.SINGLE))));
            }
            case 3 -> {
                wave.add(Enemy.fromTemplate("Blaze Out of Space (炽燃徘徊者)", level,
                        110 * hpScale, 30, 240, 105, 8 * toughnessScale,
                        Element.FIRE, EnumSet.of(Element.FIRE, Element.ICE), java.util.Map.of(Element.FIRE, 0.2),
                        List.of(new Enemy.EnemySkill("Flame Lash", Element.FIRE, 0.8, SkillAttackType.SINGLE))));
                wave.add(Enemy.fromTemplate("Cocolia, Mother of Deception (可可利亚)", level,
                        260 * hpScale, 42, 280, 110, 20 * toughnessScale,
                        Element.ICE, EnumSet.of(Element.PHYSICAL, Element.FIRE, Element.QUANTUM),
                        java.util.Map.of(Element.ICE, 0.35),
                        List.of(new Enemy.EnemySkill("Icicle Barrage", Element.ICE, 1.2, SkillAttackType.SINGLE),
                                new Enemy.EnemySkill("Frozen Tempest", Element.ICE, 0.7, SkillAttackType.ALL),
                                new Enemy.EnemySkill("Frost Nova", Element.ICE, 0.9, SkillAttackType.ALL))));
            }
            default -> {
                wave.add(Enemy.fromTemplate("Trotter (扑满)", level,
                        80 * hpScale, 26, 240, 120, 3 * toughnessScale,
                        Element.PHYSICAL, EnumSet.of(Element.PHYSICAL), java.util.Map.of(),
                        List.of(new Enemy.EnemySkill("Headbutt", Element.PHYSICAL, 0.5, SkillAttackType.SINGLE))));
                wave.add(Enemy.fromTemplate("Blaze Out of Space (炽燃徘徊者)", level,
                        110 * hpScale, 30, 240, 105, 8 * toughnessScale,
                        Element.FIRE, EnumSet.of(Element.FIRE, Element.ICE), java.util.Map.of(Element.FIRE, 0.2),
                        List.of(new Enemy.EnemySkill("Flame Lash", Element.FIRE, 0.8, SkillAttackType.SINGLE),
                                new Enemy.EnemySkill("Searing Wave", Element.FIRE, 0.5, SkillAttackType.ALL))));
                wave.add(Enemy.fromTemplate("Cocolia, Mother of Deception (可可利亚)", level,
                        160 * hpScale, 36, 260, 110, 12 * toughnessScale,
                        Element.ICE, EnumSet.of(Element.PHYSICAL, Element.FIRE, Element.QUANTUM),
                        java.util.Map.of(Element.ICE, 0.3),
                        List.of(new Enemy.EnemySkill("Icicle Barrage", Element.ICE, 1.0, SkillAttackType.SINGLE),
                                new Enemy.EnemySkill("Frozen Tempest", Element.ICE, 0.6, SkillAttackType.ALL))));
            }
        }
        return wave;
    }

    public static void enemyMenu() {
        IO.println();
        IO.println("========== 敌人设置 ==========");
        IO.println("  [1] 普通遭遇 (3 小怪)");
        IO.println("  [2] 精英战斗 (精英 + 小怪)");
        IO.println("  [3] Boss 战 (可可利亚)");
        String line = IO.readln("选择 [1]: ");
        int preset = 1;
        if (line != null && line.trim().matches("[1-3]")) {
            preset = Integer.parseInt(line.trim());
        }
        GameState.enemyPreset = preset;
        IO.println("敌人已设置为 " + EnemyUI.presetName(preset));
    }

}
