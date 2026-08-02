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
 * 战斗循环, 玩家操作, 终结技插队与状态展示.
 */
public final class BattleUI {

    private BattleUI() {
    }

    public static String partySummary(Battle battle) {
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

    public static boolean offerUltInsertion(Battle battle) {
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

    public static boolean insertAlliedUlt(Battle battle, Character current) {
        List<Character> ready = battle.getAliveCharacters().stream()
                .filter(c -> c != current && c.getMaxEnergy() > 0
                        && c.getEnergy() >= c.getMaxEnergy())
                .toList();
        if (ready.isEmpty()) {
            IO.println("没有能量已满的队友。");
            return false;
        }
        IO.println();
        IO.println(">>> 终结技插队 (能量已满的队友):");
        for (int i = 0; i < ready.size(); i++) {
            IO.println("  [" + (i + 1) + "] " + ready.get(i).getName() + " 立即施放终结技");
        }
        String choice = IO.readln("选择 (0/回车 取消): ");
        if (choice == null) {
            return false;
        }
        try {
            int idx = Integer.parseInt(choice.trim()) - 1;
            if (idx < 0 || idx >= ready.size()) {
                return false;
            }
            Character ally = ready.get(idx);
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
            battle.castUltra(ally, List.of(alive.get(pick)));
            IO.println(">>> " + ally.getName() + " 插队施放了终结技!");
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static int playerAct(Battle battle, Character character) {
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
        } else {
            line.append(" (").append(String.format("%.0f", character.getEnergy())).append("/")
                    .append(String.format("%.0f", character.getMaxEnergy())).append(")");
        }
        if (character.getSkills().containsKey(SkillType.ELATION)) {
            line.append("  [4] 欢愉技 (笑点 ").append(String.format("%.0f", battle.getLaughPoints())).append(")");
        }
        List<Character> readyAllies = battle.getAliveCharacters().stream()
                .filter(c -> c != character && c.getMaxEnergy() > 0
                        && c.getEnergy() >= c.getMaxEnergy())
                .toList();
        if (!readyAllies.isEmpty()) {
            line.append("  [5] 队友插队终结技 (").append(readyAllies.size()).append("人)");
        }
        IO.println(line.toString());
        IO.println("  [i] 检查  [v] 行动条  [s] 全队信息  [t] 敌方详情  [b] 队友状态  [a] 自动  [q] 撤退"
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
            case "s" -> {
                showAllCombatInfo(battle);
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
            case "5" -> {
                // 队友插队终结技: 不消耗当前角色行动, 重新选择.
                insertAlliedUlt(battle, character);
                return playerAct(battle, character);
            }
        }
        if (alive.isEmpty()) {
            return 0;
        }
        if (choice.equals("3") && character.getEnergy() < character.getMaxEnergy()) {
            IO.println("能量不足 (" + String.format("%.0f", character.getEnergy()) + "/"
                    + String.format("%.0f", character.getMaxEnergy())
                    + "), 终结技未施放 — 请重新选择行动。");
            return playerAct(battle, character);
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

    public static int pickIndex(int size) {
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

    public static String enemySummary(List<Enemy> alive) {
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

    public static double actionAv(Battle battle, CanHit unit) {
        for (Signal signal : battle.getQueueSnapshot()) {
            if (signal.getCanHit() == unit) {
                return signal.nextActionTime;
            }
        }
        return -1;
    }

    public static double attrOf(CanHit unit, AttributeType type) {
        var value = unit.getAttribute(type);
        return value != null ? value.get() : 0;
    }

    public static boolean isFriendlySkill(Skill skill) {
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

    public static Enemy pickAutoTarget(Battle battle, Character character) {
        List<Enemy> alive = battle.getAliveEnemies();
        if (character.hasBuffNamed("舞梦")) {
            return alive.stream().filter(Enemy::isBroken).findFirst()
                    .orElseGet(() -> pickByWeakness(battle, character, alive));
        }
        return pickByWeakness(battle, character, alive);
    }

    public static Enemy pickByWeakness(Battle battle, Character character, List<Enemy> alive) {
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

    public static int skillLevel(Character c, SkillType type) {
        Skill skill = c.getSkills().get(type);
        return skill != null ? skill.getLevel() : 0;
    }

    public static String buffModifiersSummary(Buff buff) {
        if (buff.getModifiers() == null || buff.getModifiers().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Buff.ModifierEntry entry : buff.getModifiers()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            String label = CharacterBuilders.attributeLabel(entry.attribute());
            double value = entry.modifier().getValue();
            boolean percent = entry.modifier().getModifierType()
                    != com.laosun.aluminium.models.DoubleValue.Modifier.ModifierType.PURE_VALUE;
            if (percent) {
                sb.append(label).append(value >= 0 ? "+" : "").append(String.format("%.1f", value * 100)).append("%");
            } else {
                sb.append(label).append(value >= 0 ? "+" : "").append(String.format("%.1f", value));
            }
        }
        return sb.toString();
    }

    public static void startBattle() {
        if (GameState.PARTY.isEmpty()) {
            IO.println("请先组队 (至少 1 人)。");
            return;
        }
        List<Character> party = new ArrayList<>();
        for (GameState.PartyMember m : GameState.PARTY) {
            party.add(CharacterBuilders.buildCharacter(m));
        }
        List<Enemy> wave = EnemyUI.buildEnemyWave(80, party);

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

    public static void showEnemyDetail(Battle battle) {
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

    public static void showAllyStatus(Battle battle) {
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

    public static void showAllCombatInfo(Battle battle) {
        IO.println();
        IO.println("══════════ 我方战斗信息 (" + battle.characters.size() + "人) ══════════");
        for (Character c : battle.characters) {
            showCombatUnit(battle, c);
            for (Summon summon : c.getSummons()) {
                if (!summon.isDeath()) {
                    showCombatUnit(battle, summon);
                }
            }
        }
        IO.println("══════════ 敌方战斗信息 ══════════");
        List<Enemy> enemies = battle.getAliveEnemies();
        if (enemies.isEmpty()) {
            IO.println("  (无存活敌人)");
        }
        for (Enemy e : enemies) {
            IO.println("  ◆ " + e.getName() + "  " + CharacterBuilders.elementLabel(e.getElement()));
            IO.println("      HP " + String.format("%.0f", e.getCurrentHp()) + "/"
                    + String.format("%.0f", e.getMaxHp()) + " ("
                    + String.format("%.0f%%", e.getHpPercent() * 100) + ")  韧性 "
                    + String.format("%.0f", e.getCurrentToughness()) + "/"
                    + String.format("%.0f", e.getMaxToughness())
                    + (e.isBroken() ? " [已击破]" : "")
                    + (e.getControlState() != null ? " [控制: " + e.getControlState().name() + "]" : ""));
            StringBuilder weak = new StringBuilder("      弱点: ");
            for (Element el : e.getWeaknesses()) {
                weak.append(CharacterBuilders.elementLabel(el)).append(" ");
            }
            IO.println(weak.toString().trim());
            showBuffs(e);
        }
        IO.println("  ── 战技点: " + battle.getSkillPoints()
                + "  笑点: " + String.format("%.0f", battle.getLaughPoints()) + " ──");
    }

    public static void showCombatUnit(Battle battle, CanHit unit) {
        String tag = unit.isDeath() ? " [阵亡]" : "";
        StringBuilder header = new StringBuilder("  ◆ " + unit.getName() + tag);
        if (unit instanceof Character c) {
            header.append("  (cid ").append(c.getCid()).append(")  ")
                    .append(CharacterBuilders.elementLabel(c.getElement())).append("  ")
                    .append(CharacterBuilders.pathLabel(CharacterBuilders.pathOf(c.getCid())));
            if (c.getSummons() != null && !c.getSummons().isEmpty()) {
                header.append("  [忆灵]");
            }
        } else if (unit instanceof Summon) {
            header.append("  (忆灵)  ").append(CharacterBuilders.elementLabel(unit.getElement()));
        } else {
            header.append("  ").append(CharacterBuilders.elementLabel(unit.getElement()));
        }
        IO.println(header.toString());
        IO.println("      HP " + String.format("%.0f", unit.getCurrentHp()) + "/"
                + String.format("%.0f", unit.getMaxHp()) + " ("
                + String.format("%.0f%%", unit.getHpPercent() * 100) + ")"
                + "  能量 " + String.format("%.0f", unit.getEnergy()) + "/"
                + String.format("%.0f", unit.getMaxEnergy())
                + "  护盾 " + String.format("%.0f", unit.getShield()));
        if (unit instanceof Character c) {
            IO.println("      ATK " + String.format("%.0f", CharacterBuilders.atk(c))
                    + "  DEF " + String.format("%.0f", CharacterBuilders.def(c))
                    + "  SPD " + String.format("%.1f", CharacterBuilders.spd(c))
                    + "  暴击率 " + String.format("%.1f%%", CharacterBuilders.crit(c) * 100)
                    + "  暴击伤害 " + String.format("%.1f%%", CharacterBuilders.cdmg(c) * 100));
            IO.println("      效果命中 " + String.format("%.1f%%", CharacterBuilders.ehr(c) * 100)
                    + "  效果抵抗 " + String.format("%.1f%%", CharacterBuilders.er(c) * 100)
                    + "  击破特攻 " + String.format("%.1f%%", CharacterBuilders.be(c) * 100)
                    + "  行动 " + String.format("%.1f", actionAv(battle, c)) + " AV");
        } else {
            IO.println("      ATK " + String.format("%.0f", attrOf(unit, AttributeType.ATTACK))
                    + "  DEF " + String.format("%.0f", attrOf(unit, AttributeType.DEFENCE))
                    + "  SPD " + String.format("%.1f", attrOf(unit, AttributeType.SPEED))
                    + "  行动 " + String.format("%.1f", actionAv(battle, unit)) + " AV");
        }
        StringBuilder state = new StringBuilder("      状态: 正常");
        if (unit.getControlState() != null) {
            state = new StringBuilder("      状态: 控制 [" + unit.getControlState().name() + "]");
        }
        if (unit instanceof Character c && c.isEnhanced()) {
            state.append("  [强化状态]");
        }
        IO.println(state.toString());
        showBuffs(unit);
    }

    public static void autoAct(Battle battle, Character character) {
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

    public static void printBattleStatus(Battle battle) {
        IO.println();
        battle.printBattle();
        IO.println();
    }

    public static void inspect(Battle battle, Character character) {
        IO.println();
        IO.println("=== " + character.getName() + " 状态 ===");
        showCombatUnit(battle, character);
        IO.println("  技能等级: 普攻 Lv." + skillLevel(character, SkillType.COMMON)
                + " 战技 Lv." + skillLevel(character, SkillType.SKILL)
                + " 终结技 Lv." + skillLevel(character, SkillType.ULTRA)
                + " 天赋 Lv." + skillLevel(character, SkillType.TALENT)
                + (character.getSkills().containsKey(SkillType.ELATION) ? " 欢愉技 Lv.1" : ""));
    }

    public static void showActionBar(Battle battle) {
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

    public static void showBuffs(CanHit unit) {
        if (unit.getBuffs().isEmpty()) {
            return;
        }
        IO.println("  增益/减益:");
        for (Buff buff : unit.getBuffs()) {
            StringBuilder line = new StringBuilder("    · " + buff.getName() + " ("
                    + (buff.getDuration() >= 0 ? buff.getDuration() + " 回合" : "永久")
                    + (buff.getControl() != null ? ", 控制: " + buff.getControl() : "")
                    + (buff.getDotDamage() > 0 ? ", DOT " + String.format("%.0f", buff.getDotDamage()) : "")
                    + (buff.getHealPerTurn() > 0 ? ", 回复 " + String.format("%.0f", buff.getHealPerTurn()) : "")
                    + ")");
            IO.println(line.toString());
            String mods = buffModifiersSummary(buff);
            if (!mods.isEmpty()) {
                IO.println("        [" + mods + "]");
            }
        }
        if (!unit.getDots().isEmpty()) {
            IO.println("  持续伤害:");
            for (Buff.Dot dot : unit.getDots()) {
                IO.println("    · " + dot.getName() + " " + String.format("%.0f", dot.getDamage())
                        + " (" + dot.getElement().string + ", " + dot.getDuration() + " 回合)");
            }
        }
    }

}
