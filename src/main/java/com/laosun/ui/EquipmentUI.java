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
 * 装备菜单: 光锥与遗器套装管理.
 */
public final class EquipmentUI {

    private EquipmentUI() {
    }

    public static GameState.PartyMember changeWeapon(int index, GameState.PartyMember m) {
        String path = CharacterBuilders.pathOf(m.cid());
        IO.println();
        IO.println("===== 光锥列表 (命途: " + path + ") =====");
        List<Integer> matches = new ArrayList<>();
        int shown = 0;
        StringBuilder line = new StringBuilder("  ");
        for (var entry : Constant.WEAPONS.entrySet()) {
            WeaponData wd = entry.getValue();
            if (wd == null || wd.type() == null || !wd.type().equals(path)) {
                continue;
            }
            int wid = entry.getKey();
            matches.add(wid);
            line.append(String.format("[%d]%s ", matches.size(), wd.name().chinese()));
            shown++;
            if (shown % 4 == 0) {
                IO.println(line.toString());
                line = new StringBuilder("  ");
            }
        }
        if (line.toString().trim().length() > 2) {
            IO.println(line.toString());
        }
        IO.println("  [0] 取消");
        String pick = IO.readln("选择光锥: ");
        try {
            int idx = Integer.parseInt(pick == null ? "" : pick.trim()) - 1;
            if (idx >= 0 && idx < matches.size()) {
                int wid = matches.get(idx);
                GameState.PartyMember updated = new GameState.PartyMember(m.cid(), m.name(), m.eidolon(), wid, m.relicSetId());
                GameState.PARTY.set(index, updated);
                Character c = CharacterBuilders.buildCharacter(updated);
                IO.println("装备 " + CharacterBuilders.weaponName(wid) + ": HP " + String.format("%.0f", c.getMaxHp())
                        + "  ATK " + String.format("%.0f", CharacterBuilders.atk(c))
                        + "  DEF " + String.format("%.0f", CharacterBuilders.def(c)));
                return updated;
            }
        } catch (NumberFormatException ignored) {
        }
        return m;
    }

    public static GameState.PartyMember changeRelicSet(int index, GameState.PartyMember m) {
        IO.println();
        IO.println("===== 遗器套装列表 (2件 | 4件) =====");
        List<Integer> setIds = new ArrayList<>();
        for (var entry : Constant.RELIC_SETS.entrySet()) {
            setIds.add(entry.getKey());
            com.laosun.aluminium.beans.RelicSet set = entry.getValue();
            String two = EquipmentUI.clip(EquipmentUI.formatDesc(set != null ? set.two() : null), 42);
            String four = EquipmentUI.clip(EquipmentUI.formatDesc(set != null ? set.four() : null), 52);
            IO.println(String.format("  [%d] %s (%s): 2件: %s | 4件: %s",
                    setIds.size(), EquipmentUI.relicSetName(entry.getKey()), entry.getKey(), two, four));
        }
        String pick = IO.readln("选择套装编号 (0 取消): ");
        try {
            int idx = Integer.parseInt(pick == null ? "" : pick.trim()) - 1;
            if (idx >= 0 && idx < setIds.size()) {
                int setId = setIds.get(idx);
                com.laosun.aluminium.beans.RelicSet set = Constant.RELIC_SETS.get(setId);
                IO.println("  2件套: " + EquipmentUI.formatDesc(set != null ? set.two() : null));
                IO.println("  4件套: " + EquipmentUI.formatDesc(set != null ? set.four() : null));
                String ok = IO.readln("确认装备 (y/n): ");
                if (ok != null && ok.trim().equalsIgnoreCase("y")) {
                    GameState.PartyMember updated = new GameState.PartyMember(m.cid(), m.name(), m.eidolon(), m.weaponId(), setId);
                    GameState.PARTY.set(index, updated);
                    Character c = CharacterBuilders.buildCharacter(updated);
                    IO.println("装备 " + EquipmentUI.relicSetName(setId) + ": HP " + String.format("%.0f", c.getMaxHp())
                            + "  ATK " + String.format("%.0f", CharacterBuilders.atk(c))
                            + "  DEF " + String.format("%.0f", CharacterBuilders.def(c))
                            + "  SPD " + String.format("%.1f", CharacterBuilders.spd(c)));
                    return updated;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return m;
    }

    public static String relicSetSummary(int setId) {
        com.laosun.aluminium.beans.RelicSet set = Constant.RELIC_SETS.get(setId);
        if (set == null) {
            return "(无)";
        }
        return EquipmentUI.clip("2件: " + EquipmentUI.formatDesc(set.two()) + " | 4件: " + EquipmentUI.formatDesc(set.four()), 90);
    }

    public static String relicSetName(int setId) {
        com.laosun.aluminium.beans.RelicSet set = Constant.RELIC_SETS.get(setId);
        if (set == null || set.name() == null || set.name().chinese() == null
                || set.name().chinese().isEmpty()) {
            return setId + " 号套装";
        }
        return set.name().chinese();
    }

    public static String formatDesc(com.laosun.aluminium.beans.RelicSet.SetSkill set) {
        if (set == null || set.desc() == null || set.desc().chinese() == null) {
            return "(无)";
        }
        String desc = set.desc().chinese();
        List<Double> params = set.param() != null ? set.param() : List.of();
        desc = desc.replaceAll("<[^>]+>", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("#(\\d+)\\[(i|f1|f2)\\](%)?").matcher(desc);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int idx = Integer.parseInt(matcher.group(1)) - 1;
            double value = idx >= 0 && idx < params.size() ? params.get(idx) : 0;
            String formatted = switch (matcher.group(2)) {
                case "i" -> String.format("%.0f", value * 100);
                case "f1" -> trimZeros(String.format("%.1f", value * 100));
                default -> trimZeros(String.format("%.2f", value * 100));
            };
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    formatted + (matcher.group(3) != null ? "%" : "")));
        }
        matcher.appendTail(sb);
        return sb.toString().trim();
    }

    public static String trimZeros(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    public static String bonusDesc(com.laosun.aluminium.beans.RelicSet.SetSkill set) {
        return EquipmentUI.formatDesc(set);
    }

    public static void equipmentMenu() {
        if (GameState.PARTY.isEmpty()) {
            IO.println("队伍为空, 请先添加角色。");
            return;
        }
        String line = IO.readln("选择要装备的角色 (1-" + GameState.PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index < 0 || index >= GameState.PARTY.size()) {
                return;
            }
            equipMember(index);
        } catch (NumberFormatException ignored) {
        }
    }

    public static void equipMember(int index) {
        GameState.PartyMember m = GameState.PARTY.get(index);
        Character c = CharacterBuilders.buildCharacter(m);
        boolean editing = true;
        while (editing) {
            IO.println();
            IO.println("===== 装备管理: " + c.getName() + " =====");
            IO.println("  光锥: " + (m.weaponId() > 0 ? CharacterBuilders.weaponName(m.weaponId()) : "无")
                    + "  遗器: " + EquipmentUI.relicSetName(m.relicSetId()));
            IO.println("  套装效果: " + EquipmentUI.relicSetSummary(m.relicSetId()));
            IO.println("  HP " + String.format("%.0f", c.getMaxHp())
                    + "  ATK " + String.format("%.0f", CharacterBuilders.atk(c))
                    + "  DEF " + String.format("%.0f", CharacterBuilders.def(c))
                    + "  SPD " + String.format("%.1f", CharacterBuilders.spd(c))
                    + "  暴击率 " + String.format("%.1f%%", CharacterBuilders.crit(c) * 100)
                    + "  暴击伤害 " + String.format("%.1f%%", CharacterBuilders.cdmg(c) * 100));
            IO.println("  [1] 更换光锥   [2] 更换遗器套装   [3] 卸下光锥 (遗器恢复默认)   [d] 完成");
            String choice = IO.readln("选择: ");
            if (choice == null) {
                return;
            }
            switch (choice.trim().toLowerCase()) {
                case "1" -> m = changeWeapon(index, m);
                case "2" -> m = changeRelicSet(index, m);
                case "3" -> {
                    m = new GameState.PartyMember(m.cid(), m.name(), m.eidolon(), GameState.NO_WEAPON, GameState.DEFAULT_RELIC_SET);
                    GameState.PARTY.set(index, m);
                    IO.println("已卸下光锥, 遗器恢复默认 (" + EquipmentUI.relicSetName(GameState.DEFAULT_RELIC_SET) + ")。");
                }
                case "d" -> editing = false;
                default -> IO.println("无效选择。");
            }
        }
    }

}
