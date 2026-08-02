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
 * 组队菜单: 角色选择/星魂/预设队伍.
 */
public final class TeamUI {

    private TeamUI() {
    }

    public static Integer findRosterIndex(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            int index = Integer.parseInt(input.trim()) - 1;
            if (index >= 0 && index < GameState.ROSTER.length) {
                return index;
            }
            return null;
        } catch (NumberFormatException ignored) {
        }
        String name = input.trim();
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < GameState.ROSTER_NAMES.length; i++) {
            if (GameState.ROSTER_NAMES[i].contains(name)) {
                matches.add(i);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (!matches.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多个: ");
            for (int i : matches) {
                sb.append("[").append(i + 1).append("]").append(GameState.ROSTER_NAMES[i]).append(" ");
            }
            IO.println(sb.toString().trim());
            return null;
        }
        return null;
    }

    public static String rosterName(int cid) {
        for (int i = 0; i < GameState.ROSTER.length; i++) {
            if (GameState.ROSTER[i] == cid) {
                return GameState.ROSTER_NAMES[i];
            }
        }
        return "角色#" + cid;
    }

    public static int rosterIndex(int cid) {
        for (int i = 0; i < GameState.ROSTER.length; i++) {
            if (GameState.ROSTER[i] == cid) {
                return i;
            }
        }
        return -1;
    }

    public static void teamMenu() {
        boolean editing = true;
        while (editing) {
            IO.println();
            IO.println("========== 组队 ==========");
            printTeam();
            IO.println("  [a] 添加角色   [r] 移除角色   [e] 设置星魂   [q] 装备 (光锥/遗器)"
                    + "   [p] 预设队伍   [c] 清空队伍   [d] 完成");
            String choice = IO.readln("选择: ");
            if (choice == null) {
                return;
            }
            switch (choice.trim().toLowerCase()) {
                case "a" -> addCharacter();
                case "r" -> removeCharacter();
                case "e" -> setEidolon();
                case "q" -> EquipmentUI.equipmentMenu();
                case "p" -> {
                    TeamUI.printPresetTeams();
                    String line = IO.readln("选择预设队伍 (1-6, 0 取消): ");
                    try {
                        int preset = Integer.parseInt(line == null ? "" : line.trim()) - 1;
                        if (preset >= 0 && preset < GameState.PRESET_TEAMS.length) {
                            TeamUI.applyPreset(preset);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                case "c" -> {
                    GameState.PARTY.clear();
                    IO.println("队伍已清空。");
                }
                case "d" -> editing = false;
                default -> IO.println("无效选择。");
            }
        }
    }

    public static void printTeam() {
        IO.println("当前队伍 (" + GameState.PARTY.size() + "/4):");
        if (GameState.PARTY.isEmpty()) {
            IO.println("  (空)");
            return;
        }
        for (int i = 0; i < GameState.PARTY.size(); i++) {
            GameState.PartyMember m = GameState.PARTY.get(i);
            Character c = CharacterBuilders.buildCharacter(m);
            String weapon = m.weaponId() > 0 ? CharacterBuilders.weaponName(m.weaponId()) : "无";
            IO.println("  [" + (i + 1) + "] " + c.getName() + " (cid " + c.getCid()
                    + ", 星魂 " + m.eidolon() + ")");
            IO.println("      HP " + String.format("%.0f", c.getMaxHp())
                    + "  ATK " + String.format("%.0f", CharacterBuilders.atk(c))
                    + "  DEF " + String.format("%.0f", CharacterBuilders.def(c))
                    + "  SPD " + String.format("%.1f", CharacterBuilders.spd(c))
                    + "  元素 " + c.getElement().string);
            IO.println("      光锥: " + weapon + "  遗器: " + EquipmentUI.relicSetName(m.relicSetId())
                    + (m.relicSetId() == GameState.DEFAULT_RELIC_SET ? " (默认)" : ""));
            IO.println("      套装效果: " + EquipmentUI.relicSetSummary(m.relicSetId()));
        }
    }

    public static void printRoster() {
        int[][] series = {{0, 9}, {9, 21}, {21, 44}, {44, 60}, {60, 74}, {74, 81}, {81, 84}};
        String[] seriesNames = {"10** (星穹)", "11** (星穹)", "12** (星穹)", "13** (星穹)",
                "14** (星穹)", "15** (欢愉)", "开拓者"};
        for (int s = 0; s < series.length; s++) {
            IO.println("  --- " + seriesNames[s] + " ---");
            int start = series[s][0], end = series[s][1];
            StringBuilder line = new StringBuilder("    ");
            for (int i = start; i < end; i++) {
                line.append(String.format("[%d]%s ", i + 1, GameState.ROSTER_NAMES[i]));
                if ((i - start + 1) % 5 == 0) {
                    IO.println(line.toString());
                    line = new StringBuilder("    ");
                }
            }
            if (line.toString().trim().length() > 4) {
                IO.println(line.toString());
            }
        }
    }

    public static void addCharacter() {
        if (GameState.PARTY.size() >= 4) {
            IO.println("队伍已满 (最多4人)。");
            return;
        }
        TeamUI.printRoster();
        String line = IO.readln("输入角色编号添加 (或输入名字搜索, 如 \"希儿\", 0 取消): ");
        if (line == null) {
            return;
        }
        Integer index = TeamUI.findRosterIndex(line);
        if (index == null) {
            IO.println("未找到该角色。");
            return;
        }
        int cid = GameState.ROSTER[index];
        if (GameState.PARTY.stream().anyMatch(m -> m.cid() == cid)) {
            IO.println("该角色已在队伍中。");
            return;
        }
        GameState.PARTY.add(new GameState.PartyMember(cid, GameState.ROSTER_NAMES[index], 0, GameState.NO_WEAPON, GameState.DEFAULT_RELIC_SET));
        IO.println("添加 " + GameState.ROSTER_NAMES[index] + " (cid " + cid + ")...");
    }

    public static void removeCharacter() {
        if (GameState.PARTY.isEmpty()) {
            IO.println("队伍为空。");
            return;
        }
        String line = IO.readln("输入要移除的角色序号 (1-" + GameState.PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index >= 0 && index < GameState.PARTY.size()) {
                IO.println("移除 " + GameState.PARTY.remove(index).name());
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static void setEidolon() {
        if (GameState.PARTY.isEmpty()) {
            IO.println("队伍为空。");
            return;
        }
        String line = IO.readln("输入角色序号 (1-" + GameState.PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index < 0 || index >= GameState.PARTY.size()) {
                return;
            }
            String lv = IO.readln("星魂等级 (0-6): ");
            int eidolon = Math.max(0, Math.min(6, Integer.parseInt(lv == null ? "" : lv.trim())));
            GameState.PartyMember old = GameState.PARTY.get(index);
            GameState.PARTY.set(index, new GameState.PartyMember(old.cid(), old.name(), eidolon, old.weaponId(), old.relicSetId()));
            IO.println(old.name() + " 星魂 → " + eidolon);
        } catch (NumberFormatException ignored) {
        }
    }

    public static void printPresetTeams() {
        for (int i = 0; i < GameState.PRESET_TEAMS.length; i++) {
            StringBuilder sb = new StringBuilder("  [" + (i + 1) + "] " + GameState.PRESET_NAMES[i] + ": ");
            for (int cid : GameState.PRESET_TEAMS[i]) {
                sb.append(TeamUI.rosterName(cid)).append(" ");
            }
            IO.println(sb.toString());
        }
    }

    public static void applyPreset(int preset) {
        GameState.PARTY.clear();
        for (int cid : GameState.PRESET_TEAMS[preset]) {
            int idx = TeamUI.rosterIndex(cid);
            GameState.PARTY.add(new GameState.PartyMember(cid, GameState.ROSTER_NAMES[idx], 0, GameState.NO_WEAPON, GameState.DEFAULT_RELIC_SET));
        }
        IO.println("已应用预设队伍: " + GameState.PRESET_NAMES[preset]);
    }

}
