package com.laosun;

import com.laosun.ui.BattleUI;
import com.laosun.ui.DatabaseUI;
import com.laosun.ui.EnemyUI;
import com.laosun.ui.GameState;
import com.laosun.ui.TeamUI;

/**
 * 崩坏：星穹铁道 — 交互式回合制游戏 (HSR.md).
 *
 * <p>入口类: 主菜单循环. 各功能模块见 {@code com.laosun.ui} 包
 * (TeamUI/EquipmentUI/DatabaseUI/EnemyUI/BattleUI).
 */
public class Main {

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
                case "1" -> TeamUI.teamMenu();
                case "2" -> EnemyUI.enemyMenu();
                case "3" -> BattleUI.startBattle();
                case "4" -> DatabaseUI.databaseMenu();
                case "6" -> quickStart();
                case "5" -> {
                    running = false;
                    IO.println("再见！");
                }
                default -> IO.println("无效选择。");
            }
        }
    }

    public static void quickStart() {
        IO.println();
        IO.println("========== 快速开始 ==========");
        TeamUI.printPresetTeams();
        IO.println("  当前队伍 (" + GameState.PARTY.size() + "人)" + (GameState.PARTY.isEmpty() ? " (空)" : "") + " — 回车直接开战");
        String line = IO.readln("选择预设队伍 (1-6, 0/回车 = 使用当前队伍): ");
        if (line == null) {
            return;
        }
        try {
            int preset = Integer.parseInt(line.trim()) - 1;
            if (preset >= 0 && preset < GameState.PRESET_TEAMS.length) {
                TeamUI.applyPreset(preset);
            }
        } catch (NumberFormatException ignored) {
        }
        if (GameState.PARTY.isEmpty()) {
            IO.println("队伍为空, 请先组队。");
            return;
        }
        BattleUI.startBattle();
    }

}
