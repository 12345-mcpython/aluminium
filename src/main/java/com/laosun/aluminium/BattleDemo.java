package com.laosun.aluminium;

import com.laosun.aluminium.action.AttackAction;
import com.laosun.aluminium.action.SkillAction;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Summon;

import java.util.Arrays;
import java.util.List;

public class BattleDemo {
    public static void main(String[] args) {
        // 1. 创建玩家角色（设置行动逻辑）
        Character warrior = new Character("WARRIOR", Camp.PLAYER, 1500, 300, 400, 180);
        // 战士行动逻辑：优先攻击第一个存活的敌人
        warrior.setActionSelector(battle -> {
            System.out.println("player selected warrior");
            List<CanHit> enemies = battle.getAliveTargets(Camp.ENEMY);
            return enemies.isEmpty() ? null : new AttackAction(enemies.get(0));
        });

        Character mage = new Character("MAGICAL", Camp.PLAYER, 1000, 150, 500, 140);
        // 法师行动逻辑：使用技能攻击（倍率1.8，消耗800长度）
        mage.setActionSelector(battle -> {
            List<CanHit> enemies = battle.getAliveTargets(Camp.ENEMY);
            return enemies.isEmpty() ? null : new SkillAction(enemies.get(0), 1.8, 800);
        });

        // 2. 创建召唤物（玩家阵营）
        Summon wolf = new Summon("SUMMON WOLF", Camp.PLAYER, 800, 100, 250, 200);
        wolf.setActionSelector(battle -> {
            List<CanHit> enemies = battle.getAliveTargets(Camp.ENEMY);
            return enemies.isEmpty() ? null : new AttackAction(enemies.get(0));
        });

        // 3. 创建敌人角色
        Enemy orc = new Enemy("XG CHIEF", Camp.ENEMY, 2000, 400, 350, 120);
        orc.setActionSelector(battle -> {
            List<CanHit> players = battle.getAliveTargets(Camp.PLAYER);
            return players.isEmpty() ? null : new AttackAction(players.get(0));
        });

        Enemy goblin = new Enemy("GOBLIN", Camp.ENEMY, 800, 100, 200, 160);
        goblin.setActionSelector(battle -> {
            List<CanHit> players = battle.getAliveTargets(Camp.PLAYER);
            return players.isEmpty() ? null : new AttackAction(players.get(0));
        });

        // 4. 组装队列和战斗
        List<CanHit> allCombatants = Arrays.asList(warrior, mage, wolf, orc, goblin);
        Queue queue = new Queue(allCombatants);
        Battle battle = new Battle(queue);

        // 5. 开始战斗
        battle.startBattle();
    }
}