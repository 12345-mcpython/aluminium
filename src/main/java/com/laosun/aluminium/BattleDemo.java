package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;

import java.util.Arrays;
import java.util.List;

public class BattleDemo {
    public static void main() {
        // Character
        Character warrior = new Character("WARRIOR", Camp.PLAYER, 1500, 300, 400, 180);

        Character mage = new Character("MAGICAL", Camp.PLAYER, 1000, 150, 500, 140);

        Summon wolf = new Summon("SUMMON WOLF", Camp.PLAYER, 800, 100, 250, 200);

        Character c1 = new Character("test 7", Camp.PLAYER, 100, 100, 100, 135);

        Character c2 = new Character("test 8", Camp.PLAYER, 200, 200, 100, 135);

        Character c3 = new Character("test 9", Camp.PLAYER, 100, 100, 100, 132);

        Character c4 = new Character("test 10", Camp.PLAYER, 200, 200, 100, 143) {
            @Override
            public void onBattleStart(Battle battle, CanHit canHit) {
                Signal signal = new Signal(this, this.getSpeed());
                signal.setLength(1000);
                signal.setId(1);
                battle.getActionQueue().addSignal(signal);
                super.onBattleStart(battle, canHit);
            }
        };

        // Enemy
        Enemy orc = new Enemy("XG CHIEF", Camp.ENEMY, 2000, 400, 350, 120);

        Enemy goblin = new Enemy("GOBLIN", Camp.ENEMY, 800, 100, 200, 160);

        int[] pList = new int[]{100, 200, 300, 400, 500, 600};
        Skill p = new Skill(4, "Test", "Test") {
            @Override
            protected boolean performSkill(List<CanHit> performer) {
                return true;
            }
        };

        // Battle
        List<CanHit> allCombatants = Arrays.asList(warrior, mage, wolf, orc, goblin, c1, c2, c3, c4);
        Queue queue = new Queue(allCombatants);
        Battle battle = new Battle(queue);

        // Start
        battle.startBattle();
        // MAZA SKILL RELEASED IN THIS
        // Waiting for move
        // STATE

        // Push front
        battle.pushTime();

        IO.println("1");
        battle.getActionQueue().printStatus();

        IO.println("2");
        battle.getActionQueue().resetCombatantLength(battle.getActionQueue().getNextCombatant());
        battle.getActionQueue().printStatus();

        IO.println("3");
        battle.pushTime();

        battle.getActionQueue().printStatus();
    }
}