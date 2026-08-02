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
 * 角色与装备构建, 属性读取与中文标签辅助.
 */
public final class CharacterBuilders {

    private CharacterBuilders() {
    }

    public static Character buildCharacter(GameState.PartyMember m) {
        Character.Builder builder = Character.builder().cid(m.cid()).level(80).isPromote()
                .eidolon(m.eidolon())
                .relicSuit(CharacterBuilders.buildRelicSuit(m.relicSetId(), m.cid()));
        if (m.weaponId() > 0) {
            builder.weapon(Weapon.build(m.weaponId(), 80));
        }
        return builder.build();
    }

    public static RelicSuit buildRelicSuit(int setId, int cid) {
        Element element = CharacterBuilders.elementOf(cid);
        AttributeType ballMain = element != null ? element.boostAttribute : AttributeType.ATTACK_PERCENT;
        Relic body = CharacterBuilders.relic(RelicType.BODY, AttributeType.ATTACK_PERCENT, setId);
        Relic line = CharacterBuilders.relic(RelicType.LINE, AttributeType.ATTACK_PERCENT, setId);
        Relic ball = CharacterBuilders.relic(RelicType.BALL, ballMain, setId);
        Relic boot = CharacterBuilders.relic(RelicType.BOOT, AttributeType.SPEED, setId);
        Relic hand = CharacterBuilders.relic(RelicType.HAND, AttributeType.ATTACK, setId);
        Relic head = CharacterBuilders.relic(RelicType.HEAD, AttributeType.HEALTH, setId);
        RelicSuit suit = new RelicSuit();
        suit.addMore(body, line, ball, boot, hand, head);
        return suit;
    }

    public static Relic relic(RelicType type, AttributeType main, int setId) {
        return Relic.builder().type(type).star(5).level(15)
                .mainAttribute(main).set(setId)
                .subAttribute(AttributeType.CRIT_CHANCE, 2, 4)
                .subAttribute(AttributeType.CRIT_ATTACK, 3, 5)
                .subAttribute(AttributeType.ATTACK_PERCENT, 2, 4)
                .subAttribute(AttributeType.SPEED, 1, 3)
                .build();
    }

    public static Element elementOf(int cid) {
        var data = Constant.CHARACTERS.get(cid);
        if (data == null || data.attribute() == null) {
            return null;
        }
        for (Element element : Element.values()) {
            if (element.string.equalsIgnoreCase(data.attribute())) {
                return element;
            }
        }
        return null;
    }

    public static String pathOf(int cid) {
        var data = Constant.CHARACTERS.get(cid);
        return data != null ? data.mt() : "all";
    }

    public static String weaponName(int wid) {
        var wd = Constant.WEAPONS.get(wid);
        return wd != null && wd.name() != null && wd.name().chinese() != null
                ? wd.name().chinese() : "光锥#" + wid;
    }

    public static double attr(Character c, AttributeType type) {
        var value = c.getAttribute(type);
        return value != null ? value.get() : 0;
    }

    public static double atk(Character c) {
        return CharacterBuilders.attr(c, AttributeType.ATTACK);
    }

    public static double def(Character c) {
        return CharacterBuilders.attr(c, AttributeType.DEFENCE);
    }

    public static double spd(Character c) {
        return CharacterBuilders.attr(c, AttributeType.SPEED);
    }

    public static double crit(Character c) {
        return CharacterBuilders.attr(c, AttributeType.CRIT_CHANCE);
    }

    public static double cdmg(Character c) {
        return CharacterBuilders.attr(c, AttributeType.CRIT_ATTACK);
    }

    public static double ehr(Character c) {
        return CharacterBuilders.attr(c, AttributeType.EFFECT_HIT_RATE);
    }

    public static double er(Character c) {
        return CharacterBuilders.attr(c, AttributeType.EFFECT_RESISTANCE);
    }

    public static double be(Character c) {
        return CharacterBuilders.attr(c, AttributeType.BREAKING_EFFECT);
    }

    public static String elementLabel(Element element) {
        if (element == null) {
            return "无";
        }
        return switch (element) {
            case FIRE -> "火";
            case ICE -> "冰";
            case WIND -> "风";
            case THUNDER -> "雷";
            case QUANTUM -> "量子";
            case IMAGINARY -> "虚数";
            case PHYSICAL -> "物理";
        };
    }

    public static String pathLabel(String mt) {
        if (mt == null) {
            return "未知";
        }
        return switch (mt) {
            case "destruction" -> "毁灭";
            case "healing" -> "丰饶";
            case "single" -> "巡猎";
            case "all" -> "智识";
            case "help" -> "同谐";
            case "protection" -> "存护";
            case "debuff" -> "虚无";
            case "memory" -> "记忆";
            case "elation" -> "欢愉";
            default -> mt;
        };
    }

    public static String attributeLabel(AttributeType type) {
        return switch (type) {
            case ATTACK -> "攻击";
            case ATTACK_PERCENT -> "攻击%";
            case DEFENCE -> "防御";
            case DEFENCE_PERCENT -> "防御%";
            case HEALTH -> "生命";
            case HEALTH_PERCENT -> "生命%";
            case SPEED -> "速度";
            case SPEED_PERCENT -> "速度%";
            case CRIT_CHANCE -> "暴击率";
            case CRIT_ATTACK -> "暴击伤害";
            case EFFECT_HIT_RATE -> "效果命中";
            case EFFECT_RESISTANCE -> "效果抵抗";
            case BREAKING_EFFECT -> "击破特攻";
            case ENERGY_REGENERATION_RATE -> "能量恢复效率";
            case OUTGOING_HEALING_BOOST -> "治疗量";
            case HEAL_TAKEN_RATIO -> "受治疗量";
            case VULNERABILITY -> "受到伤害";
            case ALL_DAMAGE_TYPE_BOOST -> "全伤害";
            case NORMAL_DAMAGE_BOOST -> "普攻伤害";
            case SKILL_DAMAGE_BOOST -> "战技伤害";
            case ULTRA_DAMAGE_BOOST -> "终结技伤害";
            case DOT_DAMAGE_BOOST -> "持续伤害";
            case ELATION_DAMAGE_BOOST -> "欢愉伤害";
            case SUPER_BREAK_DAMAGE_BOOST -> "超击破伤害";
            case DAMAGE_REDUCTION -> "减伤";
            case DEFENCE_IGNORE -> "无视防御";
            case RESISTANCE_PENETRATION -> "穿透";
            case FIRE_DAMAGE_BOOST -> "火伤";
            case ICE_DAMAGE_BOOST -> "冰伤";
            case WIND_DAMAGE_BOOST -> "风伤";
            case THUNDER_DAMAGE_BOOST -> "雷伤";
            case QUANTUM_DAMAGE_BOOST -> "量子伤";
            case IMAGINARY_DAMAGE_BOOST -> "虚数伤";
            case PHYSICAL_DAMAGE_BOOST -> "物伤";
            default -> type.name();
        };
    }

}
