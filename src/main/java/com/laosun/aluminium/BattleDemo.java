package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Summon;

import java.util.Arrays;
import java.util.List;

public class BattleDemo {
    public static void main() {
        // Character
        Character warrior = new Character("WARRIOR", Camp.PLAYER, 1500, 300, 400, 180);

        Character mage = new Character("MAGICAL", Camp.PLAYER, 1000, 150, 500, 140);

        Summon wolf = new Summon("SUMMON WOLF", Camp.PLAYER, 800, 100, 250, 200);

        // Enemy
        Enemy orc = new Enemy("XG CHIEF", Camp.ENEMY, 2000, 400, 350, 120);

        Enemy goblin = new Enemy("GOBLIN", Camp.ENEMY, 800, 100, 200, 160);

        // Battle
        List<CanHit> allCombatants = Arrays.asList(warrior, mage, wolf, orc, goblin);
        Queue queue = new Queue(allCombatants);
        Battle battle = new Battle(queue);

        // Start
        battle.startBattle();

        // Waiting for move
        // STATE

        // Push front
        battle.pushTime();
    }
}