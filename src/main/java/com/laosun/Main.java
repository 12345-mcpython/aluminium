package com.laosun;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.battle.Battle;
import com.laosun.aluminium.battle.Skill;
import com.laosun.aluminium.battle.Skill.*;
import com.laosun.aluminium.beans.Translate;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Character c4 = new Character(new Translate("C4", "C4"),
                Battle.makeAttrs(3000, 500, 300, 120)) {
            @Override
            public void onBattleStart(Battle b, CanHit self) {
                IO.println("  [C4] I'm the slowest, but I'll finish this!");
            }

            @Override
            public void afterMove(Battle b, CanHit self) {
                IO.println("  [C4] My turn done.");
            }

            @Override
            public boolean onEnemyKilled(Battle b, CanHit killer, CanHit enemy) {
                IO.println("  [C4] " + enemy.getName() + " slain!");
                return true;
            }
        };
        Enemy e1 = Battle.createEnemy("E1", 4000, 400, 300, 150);
        Enemy e2 = Battle.createEnemy("E2", 4000, 400, 300, 100);
        Enemy e3 = Battle.createEnemy("E3", 4000, 400, 300, 80);
        Enemy e4 = Battle.createEnemy("E4", 4000, 400, 300, 80);

        Map<CanHit, List<Skill>> skillMap = new LinkedHashMap<>();
        skillMap.put(c1, List.of(
                new Skill("Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.0, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("Triple Slash", SkillType.SKILL, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 2.0, TargetScope.THREE_ENEMIES, 0)))
        ));
        skillMap.put(c2, List.of(
                new Skill("HP Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.HP, 0.5, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("Heal", SkillType.SKILL, List.of(new SkillEffect(EffectType.HEAL, StatScale.HP, 0.4, TargetScope.SINGLE_ALLY, 1)))
        ));
        skillMap.put(c3, List.of(
                new Skill("Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.0, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("AoE Slash", SkillType.SKILL, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 2.0, TargetScope.ALL_ENEMIES, 0)))
        ));
        skillMap.put(c4, List.of(
                new Skill("Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.0, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("Advance", SkillType.SKILL, List.of(new SkillEffect(EffectType.ADVANCE, StatScale.ATK, 0.5, TargetScope.SINGLE_ALLY, 1)))
        ));
        skillMap.put(e1, List.of(new Skill("Claw", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1)))));
        skillMap.put(e2, List.of(new Skill("Double Claw", SkillType.COMMON, List.of(
                new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1),
                new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.6, TargetScope.ALL_ENEMIES, 0)
        ))));
        skillMap.put(e3, List.of(new Skill("Claw", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1)))));
        skillMap.put(e4, List.of(new Skill("Claw", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1)))));

        Battle b = new Battle(
                new Queue(List.of(c1, c2, c3, c4)),
                new Queue(List.of(e1, e2, e3, e4)),
                skillMap
        );

        IO.println("\n======== Battle Demo ========");

        Battle.Result r = b.start();
        printResult(b, r);
        // c1 skill (3-target blast) on e3 center
        r = act(b, r, 1, List.of(2));
        // c2 skill to heal himself
        r = act(b, r, 1, List.of(1));
        // c3 common on e1
        r = act(b, r, 0, List.of(0));
        // e1 (enemy auto)
        r = act(b, r);
        // c4 skill advance c2
        r = act(b, r, 1, List.of(1));

        while (!r.over()) {
            r = act(b, r);
        }
        IO.println("Winner: " + r.winner());

        // ==================== Manual battle ====================
        try {
            manualBattle();
        } catch (Exception e) {
            IO.println("\n[Manual battle skipped: " + e.getClass().getSimpleName() + "]");
        }
    }

    /**
     * Auto act: player uses skill on first alive enemy, enemy uses random skill.
     */
    static Battle.Result act(Battle b, Battle.Result prev) {
        if (prev.over()) return prev;
        Battle.Result r;
        if ("player".equals(prev.actorType())) {
            int idx = 1; // use 'skill' by default
            var alive = prev.enemyTeam().stream().filter(Battle.TeamSnapshot::alive).toList();
            if (alive.isEmpty()) return prev;
            int tidx = prev.enemyTeam().indexOf(alive.getFirst());
            r = b.useSkill(idx, List.of(tidx));
        } else {
            r = b.useRandomSkill();
        }
        printResult(b, r);
        if (r.over()) return r;
        r = b.pushQueue();
        printResult(b, r);
        return r;
    }

    /**
     * Player uses skill with explicit index and targets, then pushes.
     */
    static Battle.Result act(Battle b, Battle.Result prev, int skillIndex, List<Integer> targets) {
        if (prev.over()) return prev;
        Battle.Result r = b.useSkill(skillIndex, targets);
        printResult(b, r);
        if (r.over()) return r;
        r = b.pushQueue();
        printResult(b, r);
        return r;
    }

    static void printResult(Battle b, Battle.Result r) {
        String actorLine = r.currentActor() != null
                ? String.format("[%s] %-6s", r.actorType(), r.currentActor().getName())
                : "       done";

        String skills = r.skills() != null && !r.skills().isEmpty()
                ? " skills=" + r.skills().stream().map(Skill::name).toList() : "";

        IO.println(actorLine + skills);
        if (r.log() != null) {
            IO.println("  " + r.log());
        }
        IO.println("  P: " + teamLine(r.playerTeam()));
        IO.println("  E: " + teamLine(r.enemyTeam()));
        b.printQueue();
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
        Enemy m2 = Battle.createEnemy("Orc", 5000, 450, 350, 100);
        Enemy m3 = Battle.createEnemy("Slime", 1500, 200, 100, 80);

        Map<CanHit, List<Skill>> skillMap = new LinkedHashMap<>();
        skillMap.put(p1, List.of(
                new Skill("Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.0, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("Slash", SkillType.SKILL, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 2.0, TargetScope.SINGLE_ENEMY, 1)))
        ));
        skillMap.put(p2, List.of(
                new Skill("Strike", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.0, TargetScope.SINGLE_ENEMY, 1))),
                new Skill("Heal", SkillType.SKILL, List.of(new SkillEffect(EffectType.HEAL, StatScale.HP, 0.4, TargetScope.SINGLE_ALLY, 1)))
        ));
        skillMap.put(m1, List.of(new Skill("Claw", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1)))));
        skillMap.put(m2, List.of(new Skill("Smash", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 1.2, TargetScope.ALL_ENEMIES, 0)))));
        skillMap.put(m3, List.of(new Skill("Bite", SkillType.COMMON, List.of(new SkillEffect(EffectType.DAMAGE, StatScale.ATK, 0.8, TargetScope.SINGLE_ENEMY, 1)))));

        Battle b = new Battle(
                new Queue(List.of(p1, p2)),
                new Queue(List.of(m1, m2, m3)),
                skillMap
        );

        IO.println("\n======== Manual Battle ========");
        IO.println("Input: <skill> <target>  (e.g. '0 1' = skill[0] on enemy[1])");
        IO.println("       'q' to quit\n");

        Battle.Result r = b.start();
        printResult(b, r);

        Scanner sc = new Scanner(System.in);
        while (!r.over()) {
            if ("player".equals(r.actorType())) {
                IO.println("  Targets: E=" + targetsStr(r.enemyTeam()) + " P=" + targetsStr(r.playerTeam()));

                String input = sc.nextLine().trim();
                if ("q".equalsIgnoreCase(input)) break;

                try {
                    String[] parts = input.split("\\s+");
                    int skillIdx = Integer.parseInt(parts[0]);
                    int targetIdx = Integer.parseInt(parts[1]);
                    r = b.useSkill(skillIdx, List.of(targetIdx));
                } catch (Exception e) {
                    IO.println("  Invalid! " + e.getMessage());
                    continue;
                }
            } else {
                r = b.useRandomSkill();
            }
            printResult(b, r);
            if (r.over()) break;
            r = b.pushQueue();
            printResult(b, r);
        }
        IO.println("Winner: " + r.winner());
    }

    static String targetsStr(List<Battle.TeamSnapshot> team) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < team.size(); i++) {
            var t = team.get(i);
            if (t.alive()) sb.append(i).append("=").append(t.name()).append(" ");
        }
        return sb.toString();
    }
}
