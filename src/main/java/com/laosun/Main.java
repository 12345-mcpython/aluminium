package com.laosun;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.battle.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.List;
import java.util.Scanner;

public class Main {
    static void main() {
        IO.println(Relic.createRandomLevelZero(RelicType.BODY, 5));
        Relic hyaBody = Relic.builder()
                .type(RelicType.BODY)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.OUTGOING_HEALING_BOOST)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 0)
                .subAttribute(AttributeType.SPEED, 1, 1)
                .subAttribute(AttributeType.CRIT_ATTACK, 2, 5)
                .build();

        Relic hyaLine = Relic.builder()
                .type(RelicType.LINE)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ENERGY_REGENERATION_RATE)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 3)
                .subAttribute(AttributeType.SPEED, 1, 4)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 1, 3)
                .subAttribute(AttributeType.BREAKING_EFFECT, 0, 2)
                .build();

        Relic hyaBall = Relic.builder()
                .type(RelicType.BALL)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH_PERCENT)
                .subAttribute(AttributeType.DEFENCE, 0, 2)
                .subAttribute(AttributeType.SPEED, 3, 5)
                .subAttribute(AttributeType.CRIT_CHANCE, 2, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 2)
                .build();

        Relic hyaBoot = Relic.builder()
                .type(RelicType.BOOT)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.SPEED)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 4)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 2)
                .subAttribute(AttributeType.CRIT_ATTACK, 1, 1)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHand = Relic.builder()
                .type(RelicType.HAND)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ATTACK)
                .subAttribute(AttributeType.DEFENCE, 0, 1)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.SPEED, 3, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHead = Relic.builder()
                .type(RelicType.HEAD)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 1)
                .subAttribute(AttributeType.SPEED, 3, 7)
                .subAttribute(AttributeType.CRIT_CHANCE, 0, 1)
                .subAttribute(AttributeType.EFFECT_HIT_RATE, 1, 2)
                .build();
        IO.println(hyaBody);
        IO.println(hyaLine);
        IO.println(hyaBall);
        IO.println(hyaBoot);
        IO.println(hyaHand);
        IO.println(hyaHead);
        RelicSuit hya = new RelicSuit();
        hya.addMore(hyaBody, hyaLine, hyaBall, hyaBoot, hyaHand, hyaHead);
        Object2DoubleOpenHashMap<AttributeType> relicValue = new Object2DoubleOpenHashMap<>();
        hya.calcTotalValue(relicValue);
        IO.println(relicValue);
        IO.println(Constant.WEAPONS.get(23042).name().english());
        IO.println(Constant.CHARACTERS.get(1409).name().english());

        IO.println(LevelPromotionCalc.calcCharacterRate(80));
        IO.println(LevelPromotionCalc.calcWeaponRate(80));
        IO.println(Constant.CHARACTERS.get(1409).health());
        IO.println(Constant.WEAPONS.get(23042).health());
        IO.println(Constant.WEAPONS.get(23042).weaponSkillData().getFirst().abilityProperties());
        Weapon wp = Weapon.build(23042, 80);
        Character character = Character.builder()
                .cid(1409)
                .level(80)
                .relicSuit(hya)
                .weapon(wp)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 0, 0, 0, 0, 0.12))
                .build();
        IO.println(character.getAttribute(AttributeType.HEALTH));
        IO.println(character.getAttribute(AttributeType.DEFENCE));
        IO.println(character.getAttribute(AttributeType.SPEED));
        DoubleValue dp = character.getAttribute(AttributeType.HEALTH).clone();
        dp.addModifier(DoubleValue.Modifier.addPercent(0.20));
        IO.println(dp);
        IO.println();
        for (DoubleValue db : character.getAttributes()) {
            IO.println(db);
        }
        SkillPoint.printTree(SkillPoint.init(1409));
        IO.println(SkillPoint.sumAttributes(SkillPoint.init(1409)));
//
//        Character c1 = Character.builder().cid(1001).extraValue(new ExtraBasicPromote(0, 0, 0, 9, 0, 0, 0, 0))
//                .build();
//        Character c2 = Character.builder().cid(1002).build();
//        Character c3 = Character.builder().cid(1003).build();
//        Character c4 = Character.builder().cid(1004).build();
//
//        Queue q = new Queue(List.of(c1, c2, c3, c4));
//        q.initialize();
//        IO.println("=== init ===");
//        q.printActionQueue();
//
//        IO.println("getNext() == null: " + (q.getNext() == null));           // true, no one at action point yet
//        IO.println("getCurrentActor() == null: " + (q.getCurrentActor() == null)); // true
//
//        for (int i = 0; i < 10; i++) {
//            q.move();
//            IO.println("=== move " + i + " -> " + q.getCurrentActor().getCanHit().getName() + " ===");
//            IO.println("getNext() != null: " + (q.getNext() != null));           // true
//            IO.println("getCurrentActor() != null: " + (q.getCurrentActor() != null)); // true
//            q.printActionQueue();
//
//            q.setTopZero();
//            IO.println("=== reset -> next is " + q.peekNext().getName() + " ===");
//            IO.println("getNext() == null: " + (q.getNext() == null));           // true, setTopZero clears
//            IO.println("getCurrentActor() == null: " + (q.getCurrentActor() == null)); // true
//            q.printActionQueue();
//        }
//        IO.println(Charset.defaultCharset().displayName());

        // ==================== Battle demo ====================

        Character c1 = Battle.createPlayer("C1", 3000, 500, 300, 200);
        Character c2 = Battle.createPlayer("C2", 3000, 500, 300, 180);
        Character c3 = Battle.createPlayer("C3", 3000, 500, 300, 160);
        Character c4 = Battle.createPlayer("C4", 3000, 500, 300, 120);
        Enemy e1 = Battle.createEnemy("E1", 4000, 400, 300, 150);
        Enemy e2 = Battle.createEnemy("E2", 4000, 400, 300, 100);
        Enemy e3 = Battle.createEnemy("E3", 4000, 400, 300, 80);
        Enemy e4 = Battle.createEnemy("E4", 4000, 400, 300, 80);

        Battle b = new Battle(
                new Queue(List.of(c1, c2, c3, c4)),
                new Queue(List.of(e1, e2, e3, e4))
        );

        IO.println("\n======== Battle Demo ========");

        Battle.Result r = b.start();
        printResult(r);

        // --- manual steps for the first round ---

        // c1 attacks e3 (index 2), kills
        r = act(b, r, 2);
        // c2 attacks e2 (index 1), kills
        r = act(b, r, 1);
        // c3 attacks e1 (index 0), doesn't kill
        r = act(b, r, 0);
        // e1 attacks random player
        r = act(b, r, -1);
        // c4 attacks e1 (index 0), kills
        r = act(b, r, 0);

        // --- auto battle the rest ---
        while (!r.over()) {
            r = act(b, r, -1);
        }

        IO.println("Winner: " + r.winner());

        // ==================== Manual battle ====================
        try {
            manualBattle();
        } catch (Exception e) {
            IO.println("\n[Manual battle skipped: " + e.getClass().getSimpleName() + "]. Try to run without gradle.");
        }
    }

    /** Act with the current actor from prev, then push to next. */
    static Battle.Result act(Battle b, Battle.Result prev, int targetIdx) {
        if (prev.over()) return prev;

        // 1. current actor acts
        Battle.Result r;
        if ("player".equals(prev.actorType())) {
            var alive = prev.enemyTeam().stream().filter(Battle.TeamSnapshot::alive).toList();
            int idx = targetIdx >= 0 ? targetIdx : prev.enemyTeam().indexOf(alive.getFirst());
            r = b.attack(idx);
        } else {
            r = b.attackRandom();
        }
        printResult(r);
        if (r.over()) return r;

        // 2. advance to next actor
        r = b.pushQueue();
        printResult(r);
        return r;
    }

    static void printResult(Battle.Result r) {
        String actorLine = r.currentActor() != null
                ? String.format("[%s] %-6s", r.actorType(), r.currentActor().getName())
                : "       done";

        String actionLine = r.targetName() != null
                ? String.format("  --> %s  dmg=%.0f%s",
                        r.targetName(), r.damage(), r.targetDead() ? "  KILL" : "")
                : "";

        IO.println(actorLine + actionLine);
        IO.println("  P: " + teamLine(r.playerTeam()));
        IO.println("  E: " + teamLine(r.enemyTeam()));
        if (r.over()) {
            IO.println("  === OVER, winner=" + r.winner() + " ===");
        }
    }

    static String teamLine(List<Battle.TeamSnapshot> team) {
        StringBuilder sb = new StringBuilder();
        for (var t : team) {
            sb.append(String.format("%s %8s", t.alive()
                    ? String.format("%-6s", t.name())
                    : String.format("%-6s", t.name() + "†"),
                    String.format("%.0f/%.0f", t.currentHp(), t.maxHp())));
            sb.append("  ");
        }
        return sb.toString();
    }

    // ─── Manual battle ──────────────────────────────────────────────────

    static void manualBattle() {
        Character p1 = Battle.createPlayer("Hero1", 2500, 800, 300, 150);
        Character p2 = Battle.createPlayer("Hero2", 2500, 800, 300, 140);

        Enemy m1 = Battle.createEnemy("Goblin", 3000, 350, 200, 120);
        Enemy m2 = Battle.createEnemy("Orc",    5000, 450, 350, 100);
        Enemy m3 = Battle.createEnemy("Slime",  1500, 200, 100, 80);

        Battle b = new Battle(
                new Queue(List.of(p1, p2)),
                new Queue(List.of(m1, m2, m3))
        );

        IO.println("\n======== Manual Battle ========");
        IO.println("Commands: 0 1 2 = attack enemy by index, q = quit\n");

        Battle.Result r = b.start();
        printResult(r);

        Scanner sc = new Scanner(System.in);
        while (!r.over()) {
            if ("player".equals(r.actorType())) {
                var enemies = r.enemyTeam().stream().filter(Battle.TeamSnapshot::alive).toList();
                if (enemies.isEmpty()) break;

                StringBuilder prompt = new StringBuilder("  Pick target (");
                for (int i = 0; i < r.enemyTeam().size(); i++) {
                    var t = r.enemyTeam().get(i);
                    if (t.alive()) prompt.append(i).append("=").append(t.name()).append(" ");
                }
                prompt.append("): ");
                IO.println(prompt.toString());

                String input = sc.nextLine().trim();
                if ("q".equalsIgnoreCase(input)) break;

                try {
                    int idx = Integer.parseInt(input);
                    r = b.attack(idx);
                } catch (Exception e) {
                    IO.println("  Invalid! " + e.getMessage());
                    continue;
                }
            } else {
                r = b.attackRandom();
            }
            printResult(r);
            if (r.over()) break;
            r = b.pushQueue();
            printResult(r);
        }
        IO.println("Winner: " + r.winner());
    }
}
