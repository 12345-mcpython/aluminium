package com.laosun;

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
 * 崩坏：星穹铁道 — 交互式回合制游戏 (HSR.md).
 *
 * <p>像游戏一样操作:
 * <ul>
 *   <li>组队: 从全角色池自由挑选最多 4 人, 设置星魂等级</li>
 *   <li>装备: 为角色更换光锥 (按命途筛选) 与遗器套装 (2/4 件套效果生效)</li>
 *   <li>战斗: 选择普攻/战技/终结技/欢愉技, 敌方/友方目标均可指定</li>
 *   <li>终结技插队: 能量满的角色可以在任意时刻 (包括敌方回合) 插入施放终结技</li>
 *   <li>展示数据: 角色图鉴 (属性/技能/行迹/星魂), 战斗内 [i] 检查状态, [v] 查看行动条</li>
 * </ul>
 */
public class Main {

    /** 可用的全角色池 (编号 → 角色 ID). */
    private static final int[] ROSTER = {
            1001, 1002, 1003, 1004, 1005, 1006, 1008, 1009, 1013,       // 10**
            1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112, // 11**
            1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211, 1212,
            1213, 1214, 1215, 1217, 1218, 1220, 1221, 1222, 1223, 1224, 1225,      // 12**
            1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1312, 1313,
            1314, 1315, 1317, 1321,                                             // 13**
            1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1412,
            1413, 1414, 1415,                                                   // 14**
            1501, 1502, 1504, 1505, 1506, 1507, 1510,                            // 15**
            8005, 8007, 8009                                                     // 开拓者
    };

    private static final String[] ROSTER_NAMES = {
            "三月七", "丹恒", "姬子", "瓦尔特", "卡芙卡", "银狼", "阿兰", "艾丝妲", "黑塔",
            "布洛妮娅", "希儿", "希露瓦", "杰帕德", "娜塔莎", "佩拉", "克拉拉", "桑博", "虎克",
            "玲可", "卢卡", "托帕",
            "青雀", "停云", "罗刹", "景元", "刃", "素裳", "驭空", "符玄", "彦卿", "桂乃芬",
            "白露", "镜流", "丹恒·饮月", "雪衣", "寒鸦", "藿藿", "椒丘", "飞霄", "云璃",
            "灵砂", "貊泽", "三月七·巡猎", "忘归人",
            "加拉赫", "银枝", "阮·梅", "砂金", "真理医生", "花火", "黑天鹅", "黄泉", "知更鸟",
            "流萤", "米沙", "星期日", "翡翠", "波提欧", "乱破", "大丽花",
            "大黑塔", "阿格莱雅", "缇宝", "万敌", "那刻夏", "赛飞儿", "遐蝶", "白厄", "风堇",
            "海瑟音", "刻律德菈", "长夜月", "丹恒·腾荒", "昔涟",
            "火花", "爻光", "不死途", "绯英", "银狼LV.999", "千冶·刃", "姬子·启行",
            "开拓者·同谐", "开拓者·记忆", "开拓者·欢愉"
    };

    /** 队伍成员配置 (cid + 星魂 + 光锥 + 遗器套装). */
    private record PartyMember(int cid, String name, int eidolon, int weaponId, int relicSetId) {
    }

    private static final List<PartyMember> PARTY = new ArrayList<>();
    private static int enemyPreset = 1;

    /** 默认装备. */
    private static final int DEFAULT_WEAPON = 23042;
    private static final int DEFAULT_RELIC_SET = 102;

    public static void main(String[] args) {
        IO.println("========================================");
        IO.println("  崩坏：星穹铁道 - Interactive Battle");
        IO.println("========================================");

        boolean running = true;
        while (running) {
            IO.println();
            IO.println("========== 主菜单 ==========");
            IO.println("  [1] 组队 (Team Formation)");
            IO.println("  [2] 敌人设置 (Enemy Setup)");
            IO.println("  [3] 开始战斗 (Start Battle)");
            IO.println("  [4] 角色图鉴 (Character Database)");
            IO.println("  [6] 快速开始 (预设队伍直接开战)");
            IO.println("  [5] 退出 (Exit)");
            String choice = IO.readln("选择: ");
            if (choice == null) {
                break;
            }
            switch (choice.trim()) {
                case "1" -> teamMenu();
                case "2" -> enemyMenu();
                case "3" -> startBattle();
                case "4" -> databaseMenu();
                case "6" -> quickStart();
                case "5" -> {
                    running = false;
                    IO.println("再见！");
                }
                default -> IO.println("无效选择。");
            }
        }
    }

    // ─── 队伍预设 ──────────────────────────────────────────────────────

    private static final int[][] PRESET_TEAMS = {
            {1001, 1002, 1003, 1211},   // 星穹开局队
            {1005, 1204, 1003, 1217},   // 雷火爆发队
            {1310, 1303, 8005, 1301},   // 击破队
            {1220, 1223, 1309, 1304},   // 追猎队
            {1402, 1313, 1202, 1217},   // 记忆队
            {1501, 1502, 1504, 1505}    // 欢愉队
    };

    private static final String[] PRESET_NAMES = {
            "星穹开局队", "雷火爆发队", "击破队", "追猎队", "记忆队", "欢愉队"
    };

    private static void printPresetTeams() {
        for (int i = 0; i < PRESET_TEAMS.length; i++) {
            StringBuilder sb = new StringBuilder("  [" + (i + 1) + "] " + PRESET_NAMES[i] + ": ");
            for (int cid : PRESET_TEAMS[i]) {
                sb.append(rosterName(cid)).append(" ");
            }
            IO.println(sb.toString());
        }
    }

    private static String rosterName(int cid) {
        for (int i = 0; i < ROSTER.length; i++) {
            if (ROSTER[i] == cid) {
                return ROSTER_NAMES[i];
            }
        }
        return "角色#" + cid;
    }

    private static int rosterIndex(int cid) {
        for (int i = 0; i < ROSTER.length; i++) {
            if (ROSTER[i] == cid) {
                return i;
            }
        }
        return -1;
    }

    private static void applyPreset(int preset) {
        PARTY.clear();
        for (int cid : PRESET_TEAMS[preset]) {
            int idx = rosterIndex(cid);
            PARTY.add(new PartyMember(cid, ROSTER_NAMES[idx], 0, DEFAULT_WEAPON, DEFAULT_RELIC_SET));
        }
        IO.println("已应用预设队伍: " + PRESET_NAMES[preset]);
    }

    /** 快速开始: 选择预设队伍 (也可回车用当前队伍) 直接进入战斗. */
    private static void quickStart() {
        IO.println();
        IO.println("========== 快速开始 ==========");
        printPresetTeams();
        IO.println("  当前队伍 (" + PARTY.size() + "人)" + (PARTY.isEmpty() ? " (空)" : "") + " — 回车直接开战");
        String line = IO.readln("选择预设队伍 (1-6, 0/回车 = 使用当前队伍): ");
        if (line == null) {
            return;
        }
        try {
            int preset = Integer.parseInt(line.trim()) - 1;
            if (preset >= 0 && preset < PRESET_TEAMS.length) {
                applyPreset(preset);
            }
        } catch (NumberFormatException ignored) {
        }
        if (PARTY.isEmpty()) {
            IO.println("队伍为空, 请先组队。");
            return;
        }
        startBattle();
    }

    // ─── 组队 ──────────────────────────────────────────────────────────

    /** 组队菜单: 选择/移除角色, 设置星魂, 更换装备. */
    private static void teamMenu() {
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
                case "q" -> equipmentMenu();
                case "p" -> {
                    printPresetTeams();
                    String line = IO.readln("选择预设队伍 (1-6, 0 取消): ");
                    try {
                        int preset = Integer.parseInt(line == null ? "" : line.trim()) - 1;
                        if (preset >= 0 && preset < PRESET_TEAMS.length) {
                            applyPreset(preset);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                case "c" -> {
                    PARTY.clear();
                    IO.println("队伍已清空。");
                }
                case "d" -> editing = false;
                default -> IO.println("无效选择。");
            }
        }
    }

    private static void printTeam() {
        IO.println("当前队伍 (" + PARTY.size() + "/4):");
        if (PARTY.isEmpty()) {
            IO.println("  (空)");
            return;
        }
        for (int i = 0; i < PARTY.size(); i++) {
            PartyMember m = PARTY.get(i);
            Character c = buildCharacter(m);
            String weapon = weaponName(m.weaponId());
            IO.println("  [" + (i + 1) + "] " + c.getName() + " (cid " + c.getCid()
                    + ", 星魂 " + m.eidolon() + ")");
            IO.println("      HP " + String.format("%.0f", c.getMaxHp())
                    + "  ATK " + String.format("%.0f", atk(c))
                    + "  DEF " + String.format("%.0f", def(c))
                    + "  SPD " + String.format("%.1f", spd(c))
                    + "  元素 " + c.getElement().string);
            IO.println("      光锥: " + weapon + "  遗器: " + m.relicSetId() + " 号套装"
                    + (m.weaponId() == DEFAULT_WEAPON ? " (默认)" : "")
                    + (m.relicSetId() == DEFAULT_RELIC_SET ? " (默认)" : ""));
        }
    }

    /** 列出全角色池 (分系列). */
    private static void printRoster() {
        int[][] series = {{0, 9}, {9, 21}, {21, 44}, {44, 60}, {60, 74}, {74, 81}, {81, 84}};
        String[] seriesNames = {"10** (星穹)", "11** (星穹)", "12** (星穹)", "13** (星穹)",
                "14** (星穹)", "15** (欢愉)", "开拓者"};
        for (int s = 0; s < series.length; s++) {
            IO.println("  --- " + seriesNames[s] + " ---");
            int start = series[s][0], end = series[s][1];
            StringBuilder line = new StringBuilder("    ");
            for (int i = start; i < end; i++) {
                line.append(String.format("[%d]%s ", i + 1, ROSTER_NAMES[i]));
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

    private static void addCharacter() {
        if (PARTY.size() >= 4) {
            IO.println("队伍已满 (最多4人)。");
            return;
        }
        printRoster();
        String line = IO.readln("输入角色编号添加 (或输入名字搜索, 如 \"希儿\", 0 取消): ");
        if (line == null) {
            return;
        }
        Integer index = findRosterIndex(line);
        if (index == null) {
            IO.println("未找到该角色。");
            return;
        }
        int cid = ROSTER[index];
        if (PARTY.stream().anyMatch(m -> m.cid() == cid)) {
            IO.println("该角色已在队伍中。");
            return;
        }
        PARTY.add(new PartyMember(cid, ROSTER_NAMES[index], 0, DEFAULT_WEAPON, DEFAULT_RELIC_SET));
        IO.println("添加 " + ROSTER_NAMES[index] + " (cid " + cid + ")...");
    }

    /**
     * 解析输入为角色池编号: 先按数字, 再按名字 (子串) 搜索.
     *
     * @return 角色池下标, 未找到返回 {@code null}
     */
    private static Integer findRosterIndex(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        try {
            int index = Integer.parseInt(input.trim()) - 1;
            if (index >= 0 && index < ROSTER.length) {
                return index;
            }
            return null;
        } catch (NumberFormatException ignored) {
        }
        String name = input.trim();
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < ROSTER_NAMES.length; i++) {
            if (ROSTER_NAMES[i].contains(name)) {
                matches.add(i);
            }
        }
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        if (!matches.isEmpty()) {
            StringBuilder sb = new StringBuilder("匹配到多个: ");
            for (int i : matches) {
                sb.append("[").append(i + 1).append("]").append(ROSTER_NAMES[i]).append(" ");
            }
            IO.println(sb.toString().trim());
            return null;
        }
        return null;
    }

    private static void removeCharacter() {
        if (PARTY.isEmpty()) {
            IO.println("队伍为空。");
            return;
        }
        String line = IO.readln("输入要移除的角色序号 (1-" + PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index >= 0 && index < PARTY.size()) {
                IO.println("移除 " + PARTY.remove(index).name());
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static void setEidolon() {
        if (PARTY.isEmpty()) {
            IO.println("队伍为空。");
            return;
        }
        String line = IO.readln("输入角色序号 (1-" + PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index < 0 || index >= PARTY.size()) {
                return;
            }
            String lv = IO.readln("星魂等级 (0-6): ");
            int eidolon = Math.max(0, Math.min(6, Integer.parseInt(lv == null ? "" : lv.trim())));
            PartyMember old = PARTY.get(index);
            PARTY.set(index, new PartyMember(old.cid(), old.name(), eidolon, old.weaponId(), old.relicSetId()));
            IO.println(old.name() + " 星魂 → " + eidolon);
        } catch (NumberFormatException ignored) {
        }
    }

    // ─── 装备系统 (光锥 / 遗器) ────────────────────────────────────────

    /** 装备菜单: 为队伍成员更换光锥与遗器套装. */
    private static void equipmentMenu() {
        if (PARTY.isEmpty()) {
            IO.println("队伍为空, 请先添加角色。");
            return;
        }
        String line = IO.readln("选择要装备的角色 (1-" + PARTY.size() + ", 0 取消): ");
        if (line == null) {
            return;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index < 0 || index >= PARTY.size()) {
                return;
            }
            equipMember(index);
        } catch (NumberFormatException ignored) {
        }
    }

    private static void equipMember(int index) {
        PartyMember m = PARTY.get(index);
        Character c = buildCharacter(m);
        boolean editing = true;
        while (editing) {
            IO.println();
            IO.println("===== 装备管理: " + c.getName() + " =====");
            IO.println("  光锥: " + weaponName(m.weaponId())
                    + "  遗器: " + m.relicSetId() + " 号套装");
            IO.println("  HP " + String.format("%.0f", c.getMaxHp())
                    + "  ATK " + String.format("%.0f", atk(c))
                    + "  DEF " + String.format("%.0f", def(c))
                    + "  SPD " + String.format("%.1f", spd(c))
                    + "  暴击率 " + String.format("%.1f%%", crit(c) * 100)
                    + "  暴击伤害 " + String.format("%.1f%%", cdmg(c) * 100));
            IO.println("  [1] 更换光锥   [2] 更换遗器套装   [3] 卸下装备   [d] 完成");
            String choice = IO.readln("选择: ");
            if (choice == null) {
                return;
            }
            switch (choice.trim().toLowerCase()) {
                case "1" -> m = changeWeapon(index, m);
                case "2" -> m = changeRelicSet(index, m);
                case "3" -> {
                    m = new PartyMember(m.cid(), m.name(), m.eidolon(), DEFAULT_WEAPON, DEFAULT_RELIC_SET);
                    PARTY.set(index, m);
                    IO.println("已恢复默认装备。");
                }
                case "d" -> editing = false;
                default -> IO.println("无效选择。");
            }
        }
    }

    /** 更换光锥: 列出与该角色命途匹配的光锥. */
    private static PartyMember changeWeapon(int index, PartyMember m) {
        String path = pathOf(m.cid());
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
                PartyMember updated = new PartyMember(m.cid(), m.name(), m.eidolon(), wid, m.relicSetId());
                PARTY.set(index, updated);
                Character c = buildCharacter(updated);
                IO.println("装备 " + weaponName(wid) + ": HP " + String.format("%.0f", c.getMaxHp())
                        + "  ATK " + String.format("%.0f", atk(c))
                        + "  DEF " + String.format("%.0f", def(c)));
                return updated;
            }
        } catch (NumberFormatException ignored) {
        }
        return m;
    }

    /** 更换遗器套装: 列出套装 (2/4 件套效果). */
    private static PartyMember changeRelicSet(int index, PartyMember m) {
        IO.println();
        IO.println("===== 遗器套装列表 =====");
        List<Integer> setIds = new ArrayList<>();
        int shown = 0;
        StringBuilder line = new StringBuilder("  ");
        for (var entry : Constant.RELIC_SETS.entrySet()) {
            setIds.add(entry.getKey());
            com.laosun.aluminium.beans.RelicSet set = entry.getValue();
            String two = set != null && set.two() != null && set.two().desc() != null
                    ? set.two().desc().chinese() : "";
            line.append(String.format("[%d]%s ", setIds.size(), entry.getKey()));
            shown++;
            if (shown % 6 == 0) {
                IO.println(line.toString());
                line = new StringBuilder("  ");
            }
        }
        if (line.toString().trim().length() > 2) {
            IO.println(line.toString());
        }
        String pick = IO.readln("选择套装编号 (0 取消): ");
        try {
            int idx = Integer.parseInt(pick == null ? "" : pick.trim()) - 1;
            if (idx >= 0 && idx < setIds.size()) {
                int setId = setIds.get(idx);
                com.laosun.aluminium.beans.RelicSet set = Constant.RELIC_SETS.get(setId);
                IO.println("  2件套: " + bonusDesc(set != null ? set.two() : null));
                IO.println("  4件套: " + bonusDesc(set != null ? set.four() : null));
                String ok = IO.readln("确认装备 (y/n): ");
                if (ok != null && ok.trim().equalsIgnoreCase("y")) {
                    PartyMember updated = new PartyMember(m.cid(), m.name(), m.eidolon(), m.weaponId(), setId);
                    PARTY.set(index, updated);
                    Character c = buildCharacter(updated);
                    IO.println("装备 " + setId + " 号套装: HP " + String.format("%.0f", c.getMaxHp())
                            + "  ATK " + String.format("%.0f", atk(c))
                            + "  DEF " + String.format("%.0f", def(c))
                            + "  SPD " + String.format("%.1f", spd(c)));
                    return updated;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return m;
    }

    private static String bonusDesc(com.laosun.aluminium.beans.RelicSet.SetSkill set) {
        if (set == null || set.desc() == null || set.desc().chinese() == null) {
            return "(无)";
        }
        String desc = set.desc().chinese();
        return desc.substring(0, Math.min(60, desc.length())) + (desc.length() > 60 ? "..." : "");
    }

    // ─── 角色/装备构建 ─────────────────────────────────────────────────

    /** 根据配置构建角色 (含光锥与遗器套装). */
    private static Character buildCharacter(PartyMember m) {
        return Character.builder().cid(m.cid()).level(80).isPromote().eidolon(m.eidolon())
                .relicSuit(buildRelicSuit(m.relicSetId(), m.cid()))
                .weapon(Weapon.build(m.weaponId(), 80)).build();
    }

    /** 按套装 ID 构建 6 件遗器 (主词条按部位选择, 副词条通用双爆/攻击/速度). */
    private static RelicSuit buildRelicSuit(int setId, int cid) {
        Element element = elementOf(cid);
        AttributeType ballMain = element != null ? element.boostAttribute : AttributeType.ATTACK_PERCENT;
        Relic body = relic(RelicType.BODY, AttributeType.ATTACK_PERCENT, setId);
        Relic line = relic(RelicType.LINE, AttributeType.ATTACK_PERCENT, setId);
        Relic ball = relic(RelicType.BALL, ballMain, setId);
        Relic boot = relic(RelicType.BOOT, AttributeType.SPEED, setId);
        Relic hand = relic(RelicType.HAND, AttributeType.ATTACK, setId);
        Relic head = relic(RelicType.HEAD, AttributeType.HEALTH, setId);
        RelicSuit suit = new RelicSuit();
        suit.addMore(body, line, ball, boot, hand, head);
        return suit;
    }

    private static Relic relic(RelicType type, AttributeType main, int setId) {
        return Relic.builder().type(type).star(5).level(15)
                .mainAttribute(main).set(setId)
                .subAttribute(AttributeType.CRIT_CHANCE, 2, 4)
                .subAttribute(AttributeType.CRIT_ATTACK, 3, 5)
                .subAttribute(AttributeType.ATTACK_PERCENT, 2, 4)
                .subAttribute(AttributeType.SPEED, 1, 3)
                .build();
    }

    /** 角色元素 (来自角色数据). */
    private static Element elementOf(int cid) {
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

    /** 角色命途 (用于光锥筛选). */
    private static String pathOf(int cid) {
        var data = Constant.CHARACTERS.get(cid);
        return data != null ? data.mt() : "all";
    }

    private static String weaponName(int wid) {
        var wd = Constant.WEAPONS.get(wid);
        return wd != null && wd.name() != null && wd.name().chinese() != null
                ? wd.name().chinese() : "光锥#" + wid;
    }

    // ─── 敌人设置 ──────────────────────────────────────────────────────

    private static void enemyMenu() {
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
        enemyPreset = preset;
        IO.println("敌人已设置为 " + presetName(preset));
    }

    private static String presetName(int preset) {
        return switch (preset) {
            case 2 -> "精英战斗";
            case 3 -> "Boss 战";
            default -> "普通遭遇";
        };
    }

    // ─── 开始战斗 ──────────────────────────────────────────────────────

    private static void startBattle() {
        if (PARTY.isEmpty()) {
            IO.println("请先组队 (至少 1 人)。");
            return;
        }
        List<Character> party = new ArrayList<>();
        for (PartyMember m : PARTY) {
            party.add(buildCharacter(m));
        }
        List<Enemy> wave = buildEnemyWave(80, party);

        Battle battle = new Battle(party, wave);
        IO.println("battle init finished");
        battle.startBattle();
        IO.println("Battle started!");

        boolean auto = false;
        boolean quit = false;
        int rounds = 0;
        while (!battle.isOver()) {
            // 终结技插队: 任意时刻 (含敌方回合) 均可插入施放能量已满的终结技.
            if (!auto) {
                auto = offerUltInsertion(battle);
            }
            battle.stepForward();
            if (battle.isOver()) {
                break;
            }
            var current = battle.currentMove;
            if (current == null) {
                break;
            }
            var actor = current.getCanHit();
            printBattleStatus(battle);

            boolean skipped = battle.beforeMove();
            if (actor.isDeath()) {
                battle.afterMove();
                continue;
            }
            if (actor instanceof Character character) {
                if (skipped) {
                    IO.println(character.getName() + " 处于控制状态, 跳过回合!");
                } else if (auto) {
                    autoAct(battle, character);
                } else {
                    int act = playerAct(battle, character);
                    if (act == 1) {
                        auto = true;
                    } else if (act == 2) {
                        quit = true;
                        break;
                    }
                }
            } else if (actor instanceof Summon summon) {
                battle.summonAction(summon);
            } else if (actor instanceof com.laosun.aluminium.models.Aha aha) {
                battle.ahaMoment();
            } else if (actor instanceof Enemy enemy) {
                battle.enemyTurn(enemy);
            }
            battle.afterMove();
            rounds++;
        }

        printBattleStatus(battle);
        IO.println();
        if (quit) {
            IO.println("=== 战斗中止 (撤退) ===");
            return;
        }
        IO.println("战斗结束: 共 " + rounds + " 次行动。");
        if (battle.isPlayerWon()) {
            IO.println("=== 胜利! 敌方被全部击败! ===");
            IO.println("剩余我方: " + partySummary(battle));
        } else {
            IO.println("=== 败北... 我方队伍全灭。 ===");
        }
    }

    /** 紧凑的剩余我方状态摘要. */
    private static String partySummary(Battle battle) {
        StringBuilder sb = new StringBuilder();
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(ally.getName()).append(" ")
                    .append(String.format("%.0f%%", ally.getHpPercent() * 100));
        }
        return sb.length() == 0 ? "(全灭)" : sb.toString();
    }

    /**
     * 终结技插队 (HSR.md §3.3): 能量满的我方角色可以在任意时刻插入施放终结技.
     *
     * @return 是否切换为自动战斗
     */
    private static boolean offerUltInsertion(Battle battle) {
        List<Character> ready = battle.getAliveCharacters().stream()
                .filter(c -> c.getMaxEnergy() > 0 && c.getEnergy() >= c.getMaxEnergy())
                .toList();
        if (ready.isEmpty()) {
            return false;
        }
        CanHit next = battle.currentMove != null ? battle.currentMove.getCanHit() : null;
        // 当前行动者自己回合内已有 [3] 终结技 选项, 无需重复提示.
        if (next instanceof Character nextChar && ready.contains(nextChar)) {
            return false;
        }
        IO.println();
        IO.println(">>> 终结技插队! 能量已满的队友: [a] 全部自动 / [n] 跳过");
        for (int i = 0; i < ready.size(); i++) {
            Character c = ready.get(i);
            IO.println("  [" + (i + 1) + "] " + c.getName() + " 立即施放终结技");
        }
        String choice = IO.readln("选择 (0/回车 跳过): ");
        if (choice == null) {
            return false;
        }
        choice = choice.trim().toLowerCase();
        if (choice.equals("n") || choice.isEmpty() || choice.equals("0")) {
            return false;
        }
        if (choice.equals("a")) {
            IO.println("自动战斗已开启 (后续终结技自动插入)!");
            return true;
        }
        try {
            int idx = Integer.parseInt(choice) - 1;
            if (idx < 0 || idx >= ready.size()) {
                return false;
            }
            Character c = ready.get(idx);
            List<Enemy> alive = battle.getAliveEnemies();
            if (alive.isEmpty()) {
                return false;
            }
            IO.println("  敌方目标:");
            for (int i = 0; i < alive.size(); i++) {
                Enemy e = alive.get(i);
                IO.println("    [" + (i + 1) + "] " + e.getName() + " (HP "
                        + String.format("%.0f", e.getCurrentHp()) + ")");
            }
            int pick = pickIndex(alive.size());
            battle.castUltra(c, List.of(alive.get(pick)));
            IO.println(">>> " + c.getName() + " 插入施放了终结技!");
            return false;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // ─── 角色图鉴 ──────────────────────────────────────────────────────

    private static void databaseMenu() {
        printRoster();
        String line = IO.readln("输入角色编号查看详细数据 (或输入名字搜索, 0 返回): ");
        if (line == null) {
            return;
        }
        Integer index = findRosterIndex(line);
        if (index == null) {
            return;
        }
        int cid = ROSTER[index];
        showCharacterData(buildCharacter(new PartyMember(cid, ROSTER_NAMES[index], 6,
                DEFAULT_WEAPON, DEFAULT_RELIC_SET)));
    }

    /** 展示角色完整数据: 属性/技能/行迹/星魂/装备. */
    private static void showCharacterData(Character c) {
        IO.println();
        IO.println("========== " + c.getName() + " (cid " + c.getCid()
                + ") ==========");
        IO.println("  元素: " + c.getElement().string + "  等级: 80  星魂: "
                + c.getEidolonLevel());
        IO.println("  生命: " + String.format("%.0f", c.getMaxHp())
                + "  攻击: " + String.format("%.0f", atk(c))
                + "  防御: " + String.format("%.0f", def(c))
                + "  速度: " + String.format("%.1f", spd(c)));
        IO.println("  暴击率: " + String.format("%.1f%%", crit(c) * 100)
                + "  暴击伤害: " + String.format("%.1f%%", cdmg(c) * 100)
                + "  能量上限: " + String.format("%.0f", c.getMaxEnergy())
                + "  击破特攻: " + String.format("%.1f%%", be(c) * 100));
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

    private static String typeLabel(SkillType type) {
        return switch (type) {
            case COMMON -> "普攻";
            case SKILL -> "战技";
            case ULTRA -> "终结技";
            case TALENT -> "天赋";
            default -> type.name();
        };
    }

    // ─── 敌人构建 ──────────────────────────────────────────────────────

    private static List<Enemy> buildEnemyWave(int level, List<Character> party) {
        double hpScale = party.stream().anyMatch(c ->
                c.getSkills().containsKey(SkillType.ELATION)) ? 3.0 : 1.0;
        if (party.stream().anyMatch(c -> c.getCid() == 8005 || c.getCid() == 8006)) {
            hpScale = 1.5;
        }
        double toughnessScale = party.stream().anyMatch(c -> c.getCid() == 8005 || c.getCid() == 8006) ? 0.5 : 1.0;
        List<Enemy> wave = new ArrayList<>();
        switch (enemyPreset) {
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

    // ─── 玩家操作 ──────────────────────────────────────────────────────

    /**
     * 玩家选择行动: 普攻/战技/终结技/欢愉技, 治疗与辅助技能自动切换为友方目标选择.
     *
     * @return 0 = 继续, 1 = 切换自动战斗, 2 = 撤退 (退出战斗)
     */
    private static int playerAct(Battle battle, Character character) {
        List<Enemy> alive = battle.getAliveEnemies();
        IO.println();
        IO.println(">>> " + character.getName() + " 的回合   (战技点 " + battle.getSkillPoints()
                + "  |  敌方: " + enemySummary(alive) + ")");
        StringBuilder line = new StringBuilder("  [1] 普攻  [2] 战技");
        if (battle.getSkillPoints() <= 0) {
            line.append(" (无SP)");
        }
        line.append("  [3] 终结技");
        if (character.getEnergy() >= character.getMaxEnergy()) {
            line.append(" [就绪!]");
        }
        if (character.getSkills().containsKey(SkillType.ELATION)) {
            line.append("  [4] 欢愉技 (笑点 ").append(String.format("%.0f", battle.getLaughPoints())).append(")");
        }
        IO.println(line.toString());
        IO.println("  [i] 检查  [v] 行动条  [t] 敌方详情  [b] 队友状态  [a] 自动  [q] 撤退"
                + "   (回车 = 普攻)");
        String choice = IO.readln("选择: ");
        if (choice == null || choice.trim().isEmpty()) {
            if (!alive.isEmpty()) {
                battle.useSkill(character.getSkills().get(SkillType.COMMON),
                        List.of(pickByWeakness(battle, character, alive)));
            }
            return 0;
        }
        choice = choice.trim().toLowerCase();
        switch (choice) {
            case "a" -> {
                IO.println("自动战斗已开启!");
                autoAct(battle, character);
                return 1;
            }
            case "i" -> {
                inspect(battle, character);
                return playerAct(battle, character);
            }
            case "v" -> {
                showActionBar(battle);
                return playerAct(battle, character);
            }
            case "t" -> {
                showEnemyDetail(battle);
                return playerAct(battle, character);
            }
            case "b" -> {
                showAllyStatus(battle);
                return playerAct(battle, character);
            }
            case "q" -> {
                IO.println("撤退! 返回主菜单。");
                return 2;
            }
        }
        if (alive.isEmpty()) {
            return 0;
        }
        Skill skill = switch (choice) {
            case "2" -> character.getSkills().get(SkillType.SKILL);
            case "3" -> character.getSkills().get(SkillType.ULTRA);
            case "4" -> character.getSkills().get(SkillType.ELATION);
            default -> character.getSkills().get(SkillType.COMMON);
        };
        if (skill == null) {
            IO.println("该技能不可用。");
            return 0;
        }
        // 治疗/辅助/护盾类技能 → 选择友方目标.
        if (isFriendlySkill(skill)) {
            List<CanHit> allies = battle.getAlivePlayerUnits();
            if (allies.isEmpty()) {
                return 0;
            }
            IO.println("  友方目标:");
            for (int i = 0; i < allies.size(); i++) {
                CanHit ally = allies.get(i);
                IO.println("    [" + (i + 1) + "] " + ally.getName() + " (HP "
                        + String.format("%.0f", ally.getCurrentHp()) + "/"
                        + String.format("%.0f", ally.getMaxHp()) + ")"
                        + (ally == character ? " [自身]" : ""));
            }
            int pick = pickIndex(allies.size());
            battle.useSkill(skill, List.of(allies.get(pick)));
            return 0;
        }
        // 攻击类技能 → 选择敌方目标 (回车 = 按弱点自动选).
        IO.println("  敌方目标:");
        for (int i = 0; i < alive.size(); i++) {
            Enemy e = alive.get(i);
            IO.println("    [" + (i + 1) + "] " + e.getName() + " (HP "
                    + String.format("%.0f", e.getCurrentHp()) + ", 韧性 "
                    + String.format("%.0f", e.getCurrentToughness()) + "/"
                    + String.format("%.0f", e.getMaxToughness())
                    + (e.isBroken() ? ", 已击破" : "")
                    + (e.isWeakTo(character.getElement()) ? ", 弱点" : "") + ")");
        }
        int pick = pickIndex(alive.size());
        battle.useSkill(skill, List.of(alive.get(pick)));
        return 0;
    }

    private static int pickIndex(int size) {
        String line = IO.readln("  选择目标 (1-" + size + ", 回车 = 1): ");
        try {
            int pick = Integer.parseInt(line == null ? "" : line.trim());
            if (pick >= 1 && pick <= size) {
                return pick - 1;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    /** 紧凑的敌方血量摘要 (用于回合提示行). */
    private static String enemySummary(List<Enemy> alive) {
        if (alive.isEmpty()) {
            return "(无)";
        }
        StringBuilder sb = new StringBuilder();
        for (Enemy e : alive) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            String name = e.getName();
            String shortName = name.length() > 6 ? name.substring(0, 6) : name;
            sb.append(shortName).append(" ")
                    .append(String.format("%.0f%%", e.getHpPercent() * 100));
        }
        return sb.toString();
    }

    /** 敌方详情: 血量/韧性/弱点/状态. */
    private static void showEnemyDetail(Battle battle) {
        IO.println();
        IO.println("=== 敌方详情 ===");
        for (Enemy e : battle.getAliveEnemies()) {
            IO.println("  " + e.getName() + " HP "
                    + String.format("%.0f", e.getCurrentHp()) + "/"
                    + String.format("%.0f", e.getMaxHp()) + " 韧性 "
                    + String.format("%.0f", e.getCurrentToughness()) + "/"
                    + String.format("%.0f", e.getMaxToughness())
                    + (e.isBroken() ? " [已击破]" : ""));
            StringBuilder weak = new StringBuilder("    弱点: ");
            for (Element el : e.getWeaknesses()) {
                weak.append(el.string).append(" ");
            }
            IO.println(weak.toString());
            showBuffs(e);
        }
    }

    /** 队友状态: 血量/能量/护盾/状态. */
    private static void showAllyStatus(Battle battle) {
        IO.println();
        IO.println("=== 我方状态 ===");
        for (CanHit ally : battle.getAlivePlayerUnits()) {
            IO.println("  " + ally.getName() + " HP "
                    + String.format("%.0f", ally.getCurrentHp()) + "/"
                    + String.format("%.0f", ally.getMaxHp()) + " 能量 "
                    + String.format("%.0f", ally.getEnergy()) + "/"
                    + String.format("%.0f", ally.getMaxEnergy())
                    + " 护盾 " + String.format("%.0f", ally.getShield())
                    + (ally.getControlState() != null ? " [控制: " + ally.getControlState().name() + "]" : ""));
            showBuffs(ally);
        }
    }

    /** 是否为治疗/辅助/护盾类 (作用于友方) 技能. */
    private static boolean isFriendlySkill(Skill skill) {
        if (!(skill instanceof DataSkill dataSkill)) {
            return false;
        }
        SkillData data = dataSkill.getData();
        if (data == null) {
            return false;
        }
        String effect = data.getSkillEffect();
        return "Restore".equals(effect) || "Support".equals(effect) || "Defence".equals(effect);
    }

    private static void autoAct(Battle battle, Character character) {
        List<Enemy> alive = battle.getAliveEnemies();
        if (alive.isEmpty()) {
            return;
        }
        List<Enemy> targets = List.of(pickAutoTarget(battle, character));
        Skill skill = character.getSkills().get(SkillType.SKILL);
        Skill elationSkill = character.getSkills().get(SkillType.ELATION);
        boolean isHealer = skill instanceof DataSkill ds
                && ds.getData() != null && "Restore".equals(ds.getData().getSkillEffect());
        boolean allyHurt = battle.getAlivePlayerUnits().stream()
                .anyMatch(c -> c.getHpPercent() < 0.85);

        if (character.isEnhanced()) {
            battle.useSkill(character.getSkills().get(SkillType.COMMON), targets);
        } else if (character.getEnergy() >= character.getMaxEnergy()
                && character.getSkills().containsKey(SkillType.ULTRA)
                && (!isHealer || !allyHurt || character.getHpPercent() < 0.4)) {
            battle.castUltra(character, targets);
        } else if (elationSkill != null) {
            battle.useSkill(elationSkill, targets);
        } else if (battle.getSkillPoints() > 0 && skill != null
                && ((isHealer && allyHurt) || !isHealer)) {
            battle.useSkill(skill, targets);
        } else {
            battle.useSkill(character.getSkills().get(SkillType.COMMON), targets);
        }
    }

    private static Enemy pickAutoTarget(Battle battle, Character character) {
        List<Enemy> alive = battle.getAliveEnemies();
        if (character.hasBuffNamed("舞梦")) {
            return alive.stream().filter(Enemy::isBroken).findFirst()
                    .orElseGet(() -> pickByWeakness(battle, character, alive));
        }
        return pickByWeakness(battle, character, alive);
    }

    private static Enemy pickByWeakness(Battle battle, Character character, List<Enemy> alive) {
        List<Enemy> matching = alive.stream()
                .filter(e -> e.getWeaknesses().contains(character.getElement()))
                .toList();
        if (matching.isEmpty()) {
            return alive.getFirst();
        }
        return matching.stream()
                .min(java.util.Comparator.comparingDouble(Enemy::getCurrentToughness))
                .orElse(alive.getFirst());
    }

    // ─── 数据展示 ──────────────────────────────────────────────────────

    private static void printBattleStatus(Battle battle) {
        IO.println();
        battle.printBattle();
        IO.println();
    }

    /** 展示单个角色的详细战斗数据 (含增益/减益). */
    private static void inspect(Battle battle, Character character) {
        IO.println();
        IO.println("=== " + character.getName() + " 状态 ===");
        IO.println("  生命: " + String.format("%.0f", character.getCurrentHp()) + "/"
                + String.format("%.0f", character.getMaxHp())
                + "  攻击: " + String.format("%.0f", atk(character))
                + "  防御: " + String.format("%.0f", def(character))
                + "  速度: " + String.format("%.1f", spd(character)));
        IO.println("  暴击率: " + String.format("%.1f%%", crit(character) * 100)
                + "  暴击伤害: " + String.format("%.1f%%", cdmg(character) * 100)
                + "  效果命中: " + String.format("%.1f%%", ehr(character) * 100)
                + "  效果抵抗: " + String.format("%.1f%%", er(character) * 100));
        IO.println("  能量: " + String.format("%.0f", character.getEnergy()) + "/"
                + String.format("%.0f", character.getMaxEnergy())
                + "  击破特攻: " + String.format("%.1f%%", be(character) * 100)
                + "  护盾: " + String.format("%.0f", character.getShield()));
        IO.println("  当前状态: " + (character.getControlState() != null
                ? character.getControlState().name() : "正常")
                + (character.isEnhanced() ? " [强化状态]" : ""));
        showBuffs(character);
    }

    /** 展示当前行动条顺序与战局信息. */
    private static void showActionBar(Battle battle) {
        IO.println();
        IO.println("=== 行动条 (按速度排序) ===");
        for (Signal signal : battle.getQueueSnapshot()) {
            CanHit unit = signal.getCanHit();
            String marker = unit.isDeath() ? " [已阵亡]" : "";
            IO.println("  " + unit.getName() + " — 下次行动 "
                    + String.format("%.1f", signal.nextActionTime) + " AV" + marker);
        }
        IO.println("  战技点: " + battle.getSkillPoints()
                + "  笑点: " + String.format("%.0f", battle.getLaughPoints()));
        IO.println("  敌方:");
        for (Enemy enemy : battle.getAliveEnemies()) {
            IO.println("    " + enemy.getName() + " HP "
                    + String.format("%.0f", enemy.getCurrentHp()) + "/"
                    + String.format("%.0f", enemy.getMaxHp()) + " 韧性 "
                    + String.format("%.0f", enemy.getCurrentToughness()) + "/"
                    + String.format("%.0f", enemy.getMaxToughness())
                    + (enemy.isBroken() ? " [已击破]" : ""));
        }
    }

    private static void showBuffs(CanHit unit) {
        if (unit.getBuffs().isEmpty()) {
            return;
        }
        IO.println("  增益/减益:");
        for (Buff buff : unit.getBuffs()) {
            IO.println("    · " + buff.getName() + " ("
                    + (buff.getDuration() >= 0 ? buff.getDuration() + " 回合" : "永久")
                    + (buff.getControl() != null ? ", 控制: " + buff.getControl() : "")
                    + (buff.getDotDamage() > 0 ? ", DOT " + String.format("%.0f", buff.getDotDamage()) : "")
                    + (buff.getHealPerTurn() > 0 ? ", 回复 " + String.format("%.0f", buff.getHealPerTurn()) : "")
                    + ")");
        }
        if (!unit.getDots().isEmpty()) {
            IO.println("  持续伤害:");
            for (Buff.Dot dot : unit.getDots()) {
                IO.println("    · " + dot.getName() + " " + String.format("%.0f", dot.getDamage())
                        + " (" + dot.getElement().string + ", " + dot.getDuration() + " 回合)");
            }
        }
    }

    // ─── 属性辅助 ──────────────────────────────────────────────────────

    private static double attr(Character c, AttributeType type) {
        var value = c.getAttribute(type);
        return value != null ? value.get() : 0;
    }

    private static double atk(Character c) {
        return attr(c, AttributeType.ATTACK);
    }

    private static double def(Character c) {
        return attr(c, AttributeType.DEFENCE);
    }

    private static double spd(Character c) {
        return attr(c, AttributeType.SPEED);
    }

    private static double crit(Character c) {
        return attr(c, AttributeType.CRIT_CHANCE);
    }

    private static double cdmg(Character c) {
        return attr(c, AttributeType.CRIT_ATTACK);
    }

    private static double ehr(Character c) {
        return attr(c, AttributeType.EFFECT_HIT_RATE);
    }

    private static double er(Character c) {
        return attr(c, AttributeType.EFFECT_RESISTANCE);
    }

    private static double be(Character c) {
        return attr(c, AttributeType.BREAKING_EFFECT);
    }
}
