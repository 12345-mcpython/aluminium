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
 * 角色图鉴: 完整角色数据展示.
 */
public final class DatabaseUI {

    private DatabaseUI() {
    }

    public static String typeLabel(SkillType type) {
        return switch (type) {
            case COMMON -> "普攻";
            case SKILL -> "战技";
            case ULTRA -> "终结技";
            case TALENT -> "天赋";
            default -> type.name();
        };
    }

    public static void databaseMenu() {
        TeamUI.printRoster();
        String line = IO.readln("输入角色编号查看详细数据 (或输入名字搜索, 0 返回): ");
        if (line == null) {
            return;
        }
        Integer index = TeamUI.findRosterIndex(line);
        if (index == null) {
            return;
        }
        int cid = GameState.ROSTER[index];
        showCharacterData(CharacterBuilders.buildCharacter(new GameState.PartyMember(cid, GameState.ROSTER_NAMES[index], 6,
                GameState.NO_WEAPON, GameState.DEFAULT_RELIC_SET)));
    }

    public static void showCharacterData(Character c) {
        IO.println();
        IO.println("========== " + c.getName() + " (cid " + c.getCid()
                + ") ==========");
        IO.println("  元素: " + c.getElement().string + "  等级: 80  星魂: "
                + c.getEidolonLevel());
        IO.println("  生命: " + String.format("%.0f", c.getMaxHp())
                + "  攻击: " + String.format("%.0f", CharacterBuilders.atk(c))
                + "  防御: " + String.format("%.0f", CharacterBuilders.def(c))
                + "  速度: " + String.format("%.1f", CharacterBuilders.spd(c)));
        IO.println("  暴击率: " + String.format("%.1f%%", CharacterBuilders.crit(c) * 100)
                + "  暴击伤害: " + String.format("%.1f%%", CharacterBuilders.cdmg(c) * 100)
                + "  能量上限: " + String.format("%.0f", c.getMaxEnergy())
                + "  击破特攻: " + String.format("%.1f%%", CharacterBuilders.be(c) * 100));
        IO.println("  --- 技能 ---");
        for (SkillType type : new SkillType[]{SkillType.COMMON, SkillType.SKILL, SkillType.ULTRA, SkillType.TALENT}) {
            Skill skill = c.getSkills().get(type);
            if (skill == null) {
                continue;
            }
            SkillData data = skill.getData();
            IO.println("  [" + typeLabel(type) + "] Lv." + skill.getLevel()
                    + (data != null && data.getSkillType() != null ? " (" + data.getSkillType() + ")" : ""));
            if (data != null && data.getSkillIntroduction() != null
                    && data.getSkillIntroduction().chinese() != null) {
                String desc = data.getSkillIntroduction().chinese();
                IO.println("      " + desc.substring(0, Math.min(90, desc.length()))
                        + (desc.length() > 90 ? "..." : ""));
            }
        }
        if (c.getSkills().containsKey(SkillType.ELATION)) {
            IO.println("  [欢愉技] 拥有欢愉技 (Skill 20)");
        }
        IO.println("  --- 行迹 & 星魂 ---");
        for (Trace trace : c.getTraces()) {
            IO.println("  · " + trace.getName());
        }
    }

}
