package com.laosun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.ExtraBasicPromote;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buff.CounterMechanic;
import com.laosun.aluminium.models.buff.DotBuff;
import com.laosun.aluminium.models.buff.SuperBreakBuff;
import com.laosun.aluminium.models.enemy.EnemySkill;
import com.laosun.aluminium.utils.CharacterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * aluminium engine demo: one complete battle.
 *
 * <p>This `main` only walks through what the engine actually supports right now:
 * <ul>
 *   <li>Character stats: real data (character_data to level scaling to light cone to relics to traces to extra bonuses)</li>
 *   <li>Enemy stats: real data ({@link EnemyFactory#create} = template  x  level group  x  instance coefficient)</li>
 *   <li>Action bar: {@code 10000 / speed}, weakness break push-forward 25%</li>
 *   <li>Turn flow: {@code stepForward to beforeMove to cast to afterMove}</li>
 *   <li>Damage: the full damage zones (DMG boost / crit / defence / resistance / vulnerability) + event hooks</li>
 *   <li>Toughness: toughness reduction to break damage to push-forward to apply DOT to break energy gain</li>
 *   <li>Super break: with a {@link SuperBreakBuff}, toughness reduction past the toughness bar turns into one extra hit of damage</li>
 *   <li>DOT: settled at the start of the enemy's turn, "applied first, settled first"</li>
 *   <li>Energy: skill energy gain / energy gain when hit / energy gain on kill / the ultimate zeroes energy and then returns 5 / only a full energy bar allows the ultimate</li>
 *   <li>Life and death: HP at zero to removed from the action bar; either side wiped out to battle over (the state machine)</li>
 * </ul>
 *
 * <p><b>Things this demo deliberately does not use</b>:
 * <ul>
 *   <li>Stages/waves (see {@code StageFactory}) - here 3 fixed enemies are hand-built,
 *       because the point is to display the "weakness / resistance / toughness / skill multiplier" data item by item,
 *       and walking wave by wave would only make that harder to read;</li>
 *   <li>Memosprites are shown in the <b>mechanics</b> demo (<code>--args="mechanics"</code>, scene 4),
 *       not in this whole-fight one: this battle is three fixed hand-built enemies with a fixed roster, and
 *       the point is to display the data item by item. The Elation system is still missing.</li>
 * </ul>
 *
 * <p>The random numbers all come from the injected {@link Random}: a fixed seed to the whole battle is reproducible.
 */
public class Main {

    /**
     * Skill point (战技点, SP) reserve of the demo AI: it only casts the skill when the points are above this
     * value, otherwise it uses the basic attack to get a point back.
     *
     * <p>This is a demo strategy, not an engine rule - the engine only provides "is there enough", and how to spend
     * it is up to the caller.
     */
    private static final int SKILL_POINT_RESERVE = 1;

    /**
     * Entry point.
     *
     * <p><b>Why an argument.</b> There are two demos and they answer different questions: the battle demo
     * ("what does a whole fight look like") and the mechanics demo ("do the pieces this project just built
     * actually work when you drive them"). Running both by default would bury the second in the first's
     * ~280 lines. With no argument the battle demo runs, so {@code gradlew run} keeps producing exactly
     * what it always did.
     *
     * <p>Usage: {@code gradlew run --args="mechanics"} (or {@code battle}, or {@code all}).
     */
    public static void main(String[] args) {
        String which = args.length == 0 ? "battle" : args[0].toLowerCase();
        switch (which) {
            case "battle" -> battleDemo();
            case "mechanics" -> mechanicsDemo();
            case "all" -> {
                battleDemo();
                mechanicsDemo();
            }
            default -> System.out.println("unknown demo '" + args[0]
                    + "'; use 'battle' (default), 'mechanics' or 'all'");
        }
    }

    private static void battleDemo() {
        System.out.println("=".repeat(78));
        System.out.println(" aluminium battle demo: Himeko (姬子) / March 7th (三月七) / Luocha (罗刹)  vs  Ice Edge (冰锋) + Junior Staff·Field Agent (基层员工·外勤) + Warp Trotter (次元扑满)");
        System.out.println("=".repeat(78));
        System.out.println();

        List<Character> team = List.of(himeko(), march7th(), luocha());

        // -- Enemies: real data. Three simple mooks, each with its own weakness/resistance, and all of them
        //    carry the basic attack from enemy_skills.json --
        //  Ice Edge (冰锋) 1002011  weak to fire/lightning, ice resistance 0.2, toughness 60
        // Junior Staff - Field Agent (基层员工·外勤) 8032010  physical
        //  Warp Trotter (次元扑满) 8002040  a low-multiplier (0.6) trash mob
        List<Enemy> enemies = new ArrayList<>(List.of(
                EnemyFactory.create(1002011, 90, 1),
                EnemyFactory.create(8032010, 90, 1),
                EnemyFactory.create(8002040, 90, 1)));
        for (Enemy enemy : enemies) {
            // Note: This value tracks how real the skill multipliers are:
            //   - if all six slots resolved to the basic attack, a single-target basic
            //     attack would only do a few hundred damage, so it is set to 12000;
            //   - with the slots real, skills/ultimates use the real multipliers (Himeko's (姬子) skill
            //     hits 3 targets and does tens of thousands on a single target), so 12000 would be one-shot, and it is
            //     raised to 30000, which puts the battle back at the scale of "a few dozen actions".
            // The HP cap lives in the HEALTH slot of the attribute array; currentHp has no setter, so after raising
            // the cap we top it up with heal.
            enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(30_000));
            enemy.heal(30_000);
            enemy.setMaxEnergy(0);               // monsters have no energy bar: maxEnergy == 0 to every energy gain is a no-op
            printEnemy(enemy);
        }
        System.out.println();

        // -- An enemy that hits back -------------------------------------------
        // Ice Edge (冰锋) wears a counter, so the demo exercises the mechanic instead of only describing it: hit it
        // and it answers with 50% of its ATK. That answer is ADDITIONAL damage, so it does not count as an
        // attack -- the character it lands on gains no energy from it (worth watching in the log below).
        // Duration 99 because this is a demo, not a balance pass.
        enemies.getFirst().getBuffManager().addBuff(
                new CounterMechanic(99, com.laosun.aluminium.enums.DamageElement.ICE, 0.5));
        System.out.println("[Setup] " + enemies.getFirst().getName()
                + " wears CounterMechanic: counters 50% of its ATK as additional damage");
        System.out.println();

        // -- Battle start -----------------------------------------------------
        Battle battle = new Battle(team, enemies, new Random(20260919));
        battle.startBattle();

        // A simplified version of Trailblazer-Harmony [伴舞]: hang a super break marker on a teammate
        team.getFirst().getBuffManager().addBuff(new SuperBreakBuff(99));
        System.out.println("[Opening] Himeko gains SuperBreakBuff (super break marker)");
        printQueue(battle);
        System.out.println();

        int actions = 0;
        while (!battle.isOver() && actions < 60) {
            actions++;
            // Note: the round is derived from the action bar's accumulated action value (150 for the first round,
            // 100 for every round after), not counted by hand
            System.out.println("────────── Round " + battle.getRound() + " (action " + actions
                    + ", total action value " + fmt(battle.queue.getElapsed()) + ")──────────");
            step(battle);
            System.out.println();
        }

        System.out.println("=".repeat(78));
        // Note: win/lose comes from the Battle state machine; the demo does not count the living itself
        System.out.println(switch (battle.getStatus()) {
            case WIN -> " Battle over: victory (" + battle.getRound() + " rounds / " + actions + " actions)";
            case LOSE ->
                    " Battle over: our team wiped out (" + battle.getRound() + " rounds / " + actions + " actions)";
            default -> " Action limit reached, battle not over (enemies left "
                    + battle.targetableEnemies().size() + ")";
        });
        System.out.println("=".repeat(78));
        battle.printHp();
    }

    // ==================================================================
    // Mechanics demo: the pieces built most recently, driven for real
    // ==================================================================

    /**
     * A short demo whose job is to <b>show the mechanics this project just built actually running</b>,
     * rather than to look like a fair fight. Four scenes:
     *
     * <ol>
     *   <li><b>Break control states</b> - ice = the victim cannot act, quantum/imaginary = it acts but
     *       slower and later. Prints the speed and action-value change for each, because those are the
     *       observables the mechanics are made of.</li>
     *   <li><b>A DOT on our own character</b> - the engine settles it at the start of that
     *       character's turn and expires it after N turns, exactly as it does for a monster: {@code tickDots} takes
     *       the carrier rather than an {@code Enemy}, which is what makes a DOT on our own character possible.</li>
     *   <li><b>An enemy-side summon</b> - the camp holds a non-monster, our side can hit it, and the
     *       battle is not won while it stands.</li>
     *   <li><b>A memosprite of our own</b> (忆灵) - summoned by its character's own rule, panelled from the
     *       summoner's resolved sheet, and then taking a turn whose damage is a share of <em>its</em> Max HP.</li>
     * </ol>
     *
     * <p>Note: Deliberate demo affordances, each labelled in the output: the enemy's weakness set is rewritten
     * so that all three control elements can break one target, {@code recoverFromBroken()} is called between
     * elements so each can be shown from a clean state, and the control states are cleared between them.
     * A real fight has none of that; the battle demo is the one that plays fair.
     */
    private static void mechanicsDemo() {
        System.out.println("=".repeat(78));
        System.out.println(" aluminium mechanics demo: the pieces built most recently, driven for real");
        System.out.println("          run with --args=\"battle\" for the whole-fight demo instead");
        System.out.println("=".repeat(78));
        System.out.println();

        breakControlScene();
        dotOnOurCharacterScene();
        enemyCampSummonScene();
        memospriteScene();

        System.out.println("=".repeat(78));
        System.out.println(" All four scenes ran without the engine refusing anything.");
        System.out.println(" What pins each one is a test, not this printout:");
        System.out.println("   control states      ControlTest + BreakEffectTableTest");
        System.out.println("   DOT as a buff       DotTest (incl. a DOT on a character)");
        System.out.println("   summon in the camp  EnemyCampSummonTest");
        System.out.println("   memosprite attack   MemospriteAttackTest");
        System.out.println("   delay survives slow QueueActionManipulationTest.aDelaySurvivesASpeedChange");
        System.out.println("=".repeat(78));
    }

    /**
     * Scene 1: one enemy broken three ways, printing what each element actually does.
     */
    private static void breakControlScene() {
        System.out.println("[1] Break control states — 冰 锁行动 / 量子·虚数 减速 + 推条");
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Enemy enemy = EnemyFactory.create(8002040, 90, 1);          // Warp Trotter (次元扑满)
        // Demo affordance #1: make one target weak to all three control elements so each can be shown.
        enemy.setStanceWeak(java.util.Set.of(DamageElement.ICE, DamageElement.QUANTUM,
                DamageElement.IMAGINARY));
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(7));
        System.out.println("    target " + enemy.getName() + "  speed " + fmt(speedOf(enemy))
                + "  weakness rewritten to " + enemy.getStanceWeak() + " (demo affordance)");

        for (DamageElement element : List.of(DamageElement.ICE, DamageElement.QUANTUM,
                DamageElement.IMAGINARY)) {
            // Demo affordance #2/#3: clear the previous state and re-fill the toughness bar, so this element
            // starts from the same place the last one did.
            enemy.recoverFromBroken();
            enemy.getBuffManager().clearAll();

            double speedBefore = speedOf(enemy);
            double avBefore = timeRemaining(battle, enemy);
            Constant.BreakEffect effect = Constant.BREAK_EFFECTS.get(element);

            battle.reduceToughness(hero, enemy, element, 9_999);

            System.out.println("    " + element + " break → control=" + effect.control()
                    + "  canAct=" + enemy.getBuffManager().canAct()
                    + "  speed " + fmt(speedBefore) + " → " + fmt(speedOf(enemy))
                    + "  action value +" + fmt(timeRemaining(battle, enemy) - avBefore)
                    + "  (fixed " + pct(Constant.BREAK_DELAY_RATIO)
                    + " + element's own " + pct(effect.delayPercent()) + ")"
                    + "  attached: " + describeControl(enemy));
        }
        System.out.println("    → only 冰 stopped the victim acting; all three pushed the bar further than the"
                + " fixed quarter alone, which is the element's own delay from Constant.BREAK_EFFECTS");
        System.out.println();
    }

    /**
     * Scene 2: a DOT on our own character, settled by the engine at the start of its turn.
     */
    private static void dotOnOurCharacterScene() {
        System.out.println("[2] A DOT on OUR character — the burn a boss puts on us");
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 200);   // fast: acts first
        Enemy boss = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(boss), new Random(7));

        hero.getBuffManager().addBuff(new DotBuff(boss, DamageElement.FIRE, 400, 2));
        System.out.println("    " + boss.getName() + " applies burn to " + hero.getName()
                + ": DOT×" + hero.getBuffManager().countBuffs(DotBuff.class)
                + ", 2 settlements, base 400");

        int heroTurns = 0;
        while (!battle.isOver() && heroTurns < 4) {
            battle.stepForward();
            Signal current = battle.queue.getCurrentActor();
            if (current == null) {
                break;
            }
            boolean isHero = current.getCanHit() == hero;
            double before = hero.getCurrentHp();
            battle.beforeMove();                    //  from the engine settles DOTs here, before the buff tick
            if (isHero) {
                heroTurns++;
                System.out.println("    turn " + heroTurns + ": HP " + fmt(before) + " → "
                        + fmt(hero.getCurrentHp()) + "  (burn settled; DOT×"
                        + hero.getBuffManager().countBuffs(DotBuff.class) + " left)");
            }
            battle.afterMove();
            if (isHero && hero.getBuffManager().allBuffsOf(DotBuff.class).isEmpty()) {
                System.out.println("    → gone after its 2 settlements; the next turn is clean, HP "
                        + fmt(hero.getCurrentHp()));
                break;
            }
        }
        System.out.println();
    }

    /**
     * Scene 3: the enemy camp holds a summon - targetable, and counted for the outcome.
     */
    private static void enemyCampSummonScene() {
        System.out.println("[3] A summon on the ENEMY side — the camp no longer only accepts monsters");
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Enemy monster = EnemyFactory.create(1002011, 90, 1);
        Summon minion = new Summon("冰锋的随从", com.laosun.aluminium.enums.Camp.ENEMY,
                new com.laosun.aluminium.utils.AttributeBuilder()
                        .setBase(AttributeType.HEALTH, 2_000)
                        .setBase(AttributeType.DEFENCE, 100)
                        .setBase(AttributeType.ATTACK, 100)
                        .setBase(AttributeType.SPEED, 100)
                        .build());
        Battle battle = new Battle(List.of(hero), List.of(monster, minion), new Random(7));
        battle.startBattle();

        System.out.println("    camp holds " + battle.enemies.size() + " unit(s): "
                + battle.enemies.stream().map(CanHit::getName).toList());
        System.out.println("    of which monsters: " + battle.enemyUnits().size()
                + "  (enemyUnits() — 'on that side' and 'is a monster' are two questions)");
        System.out.println("    our target list: " + battle.targetableEnemies().size()
                + " unit(s), so an AOE reaches the summon without anyone naming it");

        double minionBefore = minion.getCurrentHp();
        battle.castImmediate(new DefaultSkill(1001, 3, 1), hero, List.of(monster));   // AOE
        System.out.println("    hero's AOE (aimed at the monster) → summon HP " + fmt(minionBefore)
                + " → " + fmt(minion.getCurrentHp()));

        monster.takeDamage(9_999_999);
        battle.processRequests();
        System.out.println("    every monster down → status " + battle.getStatus()
                + " (the summon still stands, so this is NOT a win)");
        minion.takeDamage(9_999_999);
        battle.processRequests();
        System.out.println("    summon down too → status " + battle.getStatus());
        System.out.println();
    }

    /**
     * Remaining action value before the target acts - the observable every action-bar mechanic moves.
     */
    private static double timeRemaining(Battle battle, CanHit target) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == target) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        return 0;
    }

    /**
     * Which control buffs the target is wearing, as text (the demo prints rather than asserts).
     *
     * <p>The state itself is one {@code ControlBuff} that knows its own name (freeze / entanglement / imprisonment),
     * so the printout reads the name instead of inferring "a StunBuff means frozen" - which is also what lets a
     * rule ask "冻结状态" about a unit a <i>skill</i> froze rather than only a break.
     */
    private static String describeControl(Enemy enemy) {
        List<String> parts = new ArrayList<>();
        for (com.laosun.aluminium.models.buff.ControlBuff control
                : enemy.getBuffManager().allBuffsOf(com.laosun.aluminium.models.buff.ControlBuff.class)) {
            parts.add("ControlBuff " + control.getName()
                    + (control.canAct() ? " (acts, slower)" : " (cannot act)"));
        }
        // The slow a non-blocking control attaches is part of that state now, so counting every
        // StatModifierBuff would print the same fact twice; only a slow with no control beside it is worth naming.
        if (parts.isEmpty()) {
            int slows = enemy.getBuffManager().countBuffs(com.laosun.aluminium.models.buff.StatModifierBuff.class);
            if (slows > 0) {
                parts.add("StatModifierBuff ×" + slows + " (slow)");
            }
        }
        return parts.isEmpty() ? "no control state" : String.join(" + ", parts);
    }

    /**
     * Speed as the engine reads it (there is no {@code getSpeed()} - it is an attribute slot).
     */
    private static double speedOf(CanHit unit) {
        return unit.getAttribute(AttributeType.SPEED).get();
    }

    /**
     * "25%" - keeps the output readable for the ratio constants.
     */
    private static String pct(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    /**
     * Scene 4: one of OUR summons - a memosprite, whose panel is <b>derived from its summoner</b> and which
     * then takes its own turn (忆灵).
     *
     * <p>Nothing is fabricated here: Cyrene's own rule file summons "长夜" at battle start
     * ({@code BATTLE_START to SUMMON}), the panel comes from {@code memosprites/1413.json} and the attack is
     * the memosprite's own memosprite skill 1. The scene prints the derivation itself (share  x  the summoner's resolved
     * value) because "the panel is a function of the summoner" is the whole mechanic.
     */
    private static void memospriteScene() {
        System.out.println("[4] A memosprite of OUR side — its panel is a function of its summoner");
        Character summoner = CharacterFactory.create(1413, 80);
        Enemy monster = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(summoner), List.of(monster), new Random(7));
        battle.startBattle();                        // the character's own rule summons it, not this demo

        Summon evey = battle.memospriteOf(summoner);
        if (evey == null) {
            System.out.println("    → no memosprite: " + summoner.getName() + "'s rule did not summon one");
            return;
        }
        System.out.println("    " + summoner.getName() + " summoned " + evey.getName()
                + " at battle start (its own BATTLE_START rule); it stands in our camp ("
                + battle.allies.size() + " unit(s), " + battle.enemyUnits().size() + " monster(s) across the way)");

        MemospriteSpec spec = Memosprites.of(summoner.getCid());
        for (MemospriteSpec.Panel entry : spec.panel()) {
            AttributeType attribute = AttributeType.fromString(entry.attribute());
            StringBuilder formula = new StringBuilder();
            if (entry.percent() != null) {
                formula.append(pct(entry.percent())).append(" × ")
                        .append(fmt(summoner.getAttribute(attribute).get())).append(" (summoner)");
            }
            if (entry.flat() != null) {
                formula.append(formula.isEmpty() ? "" : " + ").append(fmt(entry.flat())).append(" flat");
            }
            System.out.println("    " + attribute.name() + " = " + formula
                    + "  → " + fmt(evey.getAttribute(attribute).get()));
        }
        MemospriteSpec.Attack attack = spec.attack();
        System.out.println("    attack: " + attack.shape() + " " + attack.element() + " = "
                + pct(attack.percent()) + " of its own " + attack.base()
                + " — no ATK anywhere, because the text gives 「长夜」 no ATK to use");

        // Who acts first is the action bar's answer, not an assumption: step until the next actor is the
        // memosprite, then let it take that turn. (Bounded, so a scene can never hang the demo.)
        for (int skipped = 0; skipped < 10 && !battle.isOver(); skipped++) {
            List<Signal> upcoming = battle.getQueueSnapshot();
            if (!upcoming.isEmpty() && upcoming.getFirst().getCanHit() == evey) {
                break;
            }
            step(battle);
        }
        if (!battle.isOver()) {
            step(battle);                            // the memosprite's own turn
        }
        System.out.println();
    }

    // ==================================================================
    // One turn
    // ==================================================================

    private static void step(Battle battle) {
        battle.stepForward();
        Signal current = battle.queue.getCurrentActor();
        if (current == null) {
            System.out.println("[Action bar] no unit can act");
            return;
        }
        CanHit actor = current.getCanHit();

        // 1) Turn start: enemies settle DOT first, then the buff and entity beforeMove hooks run
        battle.beforeMove();
        if (actor.isDeath()) {
            System.out.println("[Death] " + actor.getName() + " was settled by DOT before their turn began");
            battle.afterMove();
            return;
        }

        // 2) Cast
        // Note: Three actor kinds, not two (忆灵): a Summon can be the current actor -- an enemy's minion or
        // one of our memosprites. The `else` branch casts to Character, so a summon reaching its own turn
        // must be caught before it, or it would die with a ClassCastException rather than doing something
        // visible.
        if (actor instanceof Enemy enemy) {
            enemyTurn(battle, enemy);
        } else if (actor instanceof Summon summon) {
            summonTurn(battle, summon);
        } else {
            characterTurn(battle, (Character) actor);
        }

        // 3) Turn end: action value reset / the dead are removed from the action bar / buffs settle
        battle.afterMove();
        printQueue(battle);
    }

    /**
     * Our turn: cast the ultimate when energy is full, otherwise use the skill (not a weakness / no SP to fall back to the basic attack).
     */
    private static void characterTurn(Battle battle, Character hero) {
        System.out.println("[Ally] " + hero.getName()
                + "  HP " + fmt(hero.getCurrentHp()) + "/" + fmt(hero.getMaxHp())
                + "  Energy " + fmt(hero.getCurrentEnergy()) + "/" + fmt(hero.getMaxEnergy())
                + "  SP " + battle.getSkillPoints() + "/" + Constant.SKILL_POINT_MAX);

        Enemy target = firstAliveEnemy(battle);
        if (target == null) {
            return;
        }

        // Enough for the ultimate threshold to ultimate (the engine zeroes the energy first, settles the ultimate
        // itself, then gives the caster 5 points back)
        // Note: the test is battle.isUltraReady (it reads sp_need from the skill data), the bar does not have to be full
        if (battle.isUltraReady(hero) && hero.getSkills().containsKey(SkillType.ULTRA)) {
            System.out.println("        → energy reached the ultimate threshold, casting [Ultimate]");
            double hpBefore = target.getCurrentHp();
            if (!battle.castUltra(hero, List.of(target))) {
                System.out.println("        → ultimate cast failed");
                return;
            }
            report(hero, target, hpBefore);
            return;
        }

        // No skill points to basic attack - but a healing/shielding character's panic button must not be
        // thrown away just because 1 point is missing, so we first ask once "is there enough"; if not, we go straight
        // to the basic attack and stop dispatching the heal/shield branches.
        //
        // Note: 1 point of reserve is kept here: without it all three characters cast a skill on every action, the
        //    opening 3 points are gone after two actions, and then they can only basic-attack to get points back - 
        //    over 20 rounds the main DPS would only get one skill off, and the demo would not show what a battle
        //    looks like. Keeping 1 point makes "getting points back" and "spending points" alternate, which is the
        //    rhythm skill points (SP) are supposed to have.
        Skill skill = hero.getSkills().get(SkillType.SKILL);
        boolean canUseSkill = battle.getSkillPoints() > SKILL_POINT_RESERVE;
        if (canUseSkill && skill != null && skill.getData() != null) {
            switch (skill.getData().getEffect()) {
                case RESTORE -> {
                    battle.applySkillPointCost(skill, hero);     // a healing skill is not free either
                    healTurn(battle, hero, skill);
                    return;
                }
                case DEFENCE -> {
                    battle.applySkillPointCost(skill, hero);     // same for the shield skill
                    shieldTurn(battle, hero, skill);
                    return;
                }
                default -> {
                }
            }
        }

        boolean castSkill = canUseSkill && skill != null && skill.getData() != null
                && target.isWeakTo(skill.getData().getElement());
        if (!castSkill) {
            skill = hero.getSkills().get(SkillType.COMMON);      // not a weakness / short of SP to basic attack
        }
        if (skill == null) {
            return;
        }
        System.out.println("        → using " + (castSkill ? "[Skill]" : "[Basic ATK]"));
        double hpBefore = target.getCurrentHp();
        if (!battle.performAction(skill, List.of(target))) {
            System.out.println("        → action failed (dead / controlled / wrong action-bar state)");
            return;
        }
        // Note: performAction only queues; the actual settlement happens in afterMove()'s processRequests().
        //    We settle explicitly once here so the battle report below gets the real numbers (in the real battle
        //    loop afterMove takes care of it).
        battle.processRequests();
        report(hero, target, hpBefore);
    }

    /**
     * Healing: pick who to heal, then let the <b>engine</b> compute and apply it.
     *
     * <p>Note: Doing the arithmetic here - {@code ATK  x  param_list[0][0]} - would simply be wrong for the
     * character it is used on: Natasha's heal scales off her <b>Max HP</b>, and
     * the flat {@code +0} term would be dropped entirely. Reading the parameter slot by hand is
     * exactly the "engine depending on its caller" shape this avoids.
     *
     * <p>What is left is the one thing that genuinely belongs to the caller: <b>who</b> to heal. The
     * amount, the scaling attribute and the target of the effect all come from
     * {@code data/skill_effects.json} via {@code SkillExecutor}.
     */
    private static void healTurn(Battle battle, Character hero, Skill skill) {
        Character patient = lowestHpRateCharacter(battle);
        if (patient == null) {
            return;
        }
        double before = patient.getCurrentHp();
        System.out.println("        → using [Skill: Heal], target " + patient.getName());
        skill.execute(battle, hero, List.of(patient));      // engine computes + applies + grants energy
        System.out.println("        → " + patient.getName() + " HP " + fmt(before) + " → "
                + fmt(patient.getCurrentHp()) + "/" + fmt(patient.getMaxHp()));
    }

    /**
     * Shield: pick who to shield, then let the engine compute and apply it.
     *
     * <p>The same rule as {@link #healTurn}: the {@code DEF  x  param} arithmetic and the manual energy
     * grant do not live here. The scaling attribute is data ({@code scale: "def"} for the shields that
     * are unambiguous), not a convention this method should assume.
     */
    private static void shieldTurn(Battle battle, Character hero, Skill skill) {
        Character ally = lowestHpRateCharacter(battle);
        if (ally == null) {
            return;
        }
        System.out.println("        → using [Skill: Shield], target " + ally.getName());
        skill.execute(battle, hero, List.of(ally));
        System.out.println("        → " + ally.getName() + " shield " + fmt(ally.getShield()));
    }

    /**
     * Enemy turn: the engine's own AI rather than hitting people by hand.
     *
     * <p>Flow: broken to skip; otherwise use {@link TargetSelector} to pick a living target on our side weighted by
     * aggro, then act with the enemy's own {@link EnemySkill}
     * (the skills come from {@code enemy_skills.json}, and the multipliers are guessed, see the note in that file).
     */
    private static void enemyTurn(Battle battle, Enemy enemy) {
        System.out.println("[Enemy] " + enemy.getName()
                + "  HP " + fmt(enemy.getCurrentHp()) + "/" + fmt(enemy.getMaxHp())
                + (enemy.isBroken() ? "  [Broken " + enemy.getBrokenElement() + "]" : "")
                + "  Toughness " + fmt(enemy.getStance()) + "/" + fmt(enemy.getMaxStance()));

        // Broken: the caller has to ask on its own; returning true means "skip this turn"
        if (battle.handleBrokenTurn(enemy)) {
            System.out.println("        → broken, skips this turn ("
                    + enemy.getBrokenRemainTurns() + " turns to recover)");
            return;
        }

        // Candidate set = our living members (the caller filters "who can be targeted", do not pick a corpse)
        List<CanHit> candidates = new ArrayList<>();
        for (Character c : battle.characters) {
            if (!c.isDeath()) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        // pick the target randomly weighted by aggro (Preservation 150 is easier to hit than the regular 100)
        CanHit target = TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, battle.getRng());
        // a boss may swap its skill at an HP threshold; null means it has no phase behaviour and the
        // ordinary skill applies (which is every enemy in this demo).
        Skill attack = enemy.activeSkill();
        if (attack == null) {
            attack = enemy.getSkills().get(SkillType.COMMON);
        }
        if (target == null || attack == null) {
            System.out.println("        → no targetable target or skill");
            return;
        }

        System.out.println("        → selected " + target.getName()
                + " (aggro " + fmt(battle.aggroOf(target)) + ", team total "
                + fmt(candidates.stream().mapToDouble(battle::aggroOf).sum()) + ")");
        double hpBefore = target.getCurrentHp();
        if (!battle.performAction(attack, List.of(target))) {
            System.out.println("        → action failed");
            return;
        }
        battle.processRequests();                    // as above: settle explicitly after queueing so the battle report gets the real numbers
        System.out.println("        → " + enemy.getName() + " deals " + fmt(hpBefore - target.getCurrentHp())
                + " to " + target.getName() + " HP " + fmt(target.getCurrentHp())
                + "/" + fmt(target.getMaxHp())
                + (target.getShield() > 0 ? " (shield " + fmt(target.getShield()) + ")" : "")
                + ", energy on hit → " + fmt(target.getCurrentEnergy()));
        if (target.isDeath()) {
            System.out.println("        → " + target.getName() + " defeated, removed from the action bar");
        }
    }

    /**
     * A summon's turn - an enemy's minion, or one of our memosprites (忆灵).
     *
     * <p><b>What this method is, and is not.</b> The engine has no opinion about who drives a summon: the
     * action bar schedules it like any other unit and {@code Battle.performAction} casts whatever skill it
     * was given. This is the demo's policy, written down in one place - attack the opposing camp with the
     * {@code COMMON} skill, main target picked by the same aggro-weighted selector the enemies use. A real
     * client would pick differently (memosprite skill 1 says "优先攻击长夜月上次攻击的敌方目标" - see {@code ROADMAP.md});
     * the point here is that a summon <b>acts</b>, and that its damage is not a character's.
     *
     * <p>Which side it hits comes from {@code battle.getOpponents(summon)}, never from a hard-coded camp: an
     * enemy minion hits our team, a memosprite hits the monsters, and the same method serves both.
     */
    private static void summonTurn(Battle battle, Summon summon) {
        CanHit master = summon.getMaster();
        System.out.println("[Summon] " + summon.getName()
                + "  HP " + fmt(summon.getCurrentHp()) + "/" + fmt(summon.getMaxHp())
                + "  (summoned by " + (master == null ? "nobody" : master.getName()) + ")");

        Skill attack = summon.getSkills().get(SkillType.COMMON);
        if (attack == null) {
            System.out.println("        → no attack of its own, nothing to do");
            return;
        }
        // The damage base is worth printing for a memosprite: its damage is written as a share of an
        // attribute of *its own* ("等同于'长夜'50%生命上限"), which is the whole reason this class takes
        // the attribute as a parameter instead of assuming ATK.
        if (attack instanceof EnemySkill direct) {
            System.out.println("        → damage base " + pct(direct.getMultiplier()) + " of its own "
                    + direct.getBaseAttribute().name() + " ("
                    + fmt(summon.getAttribute(direct.getBaseAttribute()).get()) + ") × "
                    + direct.getHits() + " hit(s), " + direct.getElement());
        }

        List<CanHit> candidates = new ArrayList<>();
        for (CanHit unit : battle.getOpponents(summon)) {
            if (!unit.isDeath()) {
                candidates.add(unit);
            }
        }
        if (candidates.isEmpty()) {
            System.out.println("        → no targetable target");
            return;
        }
        CanHit target = TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, battle.getRng());
        if (target == null) {
            System.out.println("        → no targetable target");
            return;
        }
        // Summed over the whole opposing camp rather than read off the main target: a shape may reach more
        // than one unit, and reporting only the primary would understate an AOE to the point of being wrong.
        double before = candidates.stream().mapToDouble(CanHit::getCurrentHp).sum();
        if (!battle.performAction(attack, List.of(target))) {
            System.out.println("        → action failed");
            return;
        }
        battle.processRequests();
        double after = candidates.stream().mapToDouble(CanHit::getCurrentHp).sum();
        System.out.println("        → " + summon.getName() + " deals " + fmt(before - after) + " across "
                + candidates.size() + " target(s), main target " + target.getName()
                + " HP " + fmt(target.getCurrentHp()) + "/" + fmt(target.getMaxHp())
                + (target.isDeath() ? " — defeated" : ""));
    }

    // ==================================================================
    // Output
    // ==================================================================

    private static void report(Character hero, Enemy target, double hpBefore) {
        System.out.println("        → " + hero.getName() + " deals " + fmt(hpBefore - target.getCurrentHp())
                + ", energy " + fmt(hero.getCurrentEnergy()) + "/" + fmt(hero.getMaxEnergy())
                + "; " + target.getName()
                + " HP " + fmt(target.getCurrentHp()) + "/" + fmt(target.getMaxHp())
                + ", toughness " + fmt(target.getStance()) + "/" + fmt(target.getMaxStance())
                + (target.isBroken() ? " [Break " + target.getBrokenElement() + "]" : "")
                + (target.getBuffManager().countBuffs(DotBuff.class) == 0
                ? "" : "  DOT×" + target.getBuffManager().countBuffs(DotBuff.class)));
        if (target.isDeath()) {
            System.out.println("        → " + target.getName() + " defeated");
        }
    }

    private static void printEnemy(Enemy enemy) {
        System.out.println("[Enemy data] " + enemy.getName()
                + "  Lv" + enemy.getLevel()
                + "  HP " + fmt(enemy.getMaxHp())
                + "  ATK " + fmt(enemy.getAttribute(AttributeType.ATTACK).get())
                + "  DEF " + fmt(enemy.getAttribute(AttributeType.DEFENCE).get())
                + "  SPD " + fmt(enemy.getAttribute(AttributeType.SPEED).get()));
        System.out.println("        Weakness " + enemy.getStanceWeak()
                + "  Toughness " + fmt(enemy.getMaxStance())
                + "  Resist " + enemy.getDamageResist());
    }

    private static void printQueue(Battle battle) {
        List<String> names = new ArrayList<>();
        for (Signal signal : battle.getQueueSnapshot()) {
            names.add(signal.getCanHit().getName()
                    + "(" + fmt(battle.queue.getTimeRemaining(signal)) + ")");
        }
        System.out.println("[Action bar] " + String.join(" → ", names));
    }

    // ==================================================================
    // Team building (real stats)
    // ==================================================================

    private static Character himeko() {
        // Himeko (姬子) 1003: fire / speed 96 / max energy 120
        RelicSuit relics = new RelicSuit();
        relics.addMore(
                relic(RelicType.HEAD, AttributeType.HEALTH, 705.6, AttributeType.CRIT_CHANCE, 0.12),
                relic(RelicType.HAND, AttributeType.ATTACK, 352.8, AttributeType.ATTACK_PERCENT, 0.18),
                relic(RelicType.BODY, AttributeType.ATTACK_PERCENT, 0.5, AttributeType.CRIT_ATTACK, 0.24),
                relic(RelicType.BOOT, AttributeType.SPEED, 25, AttributeType.BREAKING_EFFECT, 0.3),
                relic(RelicType.BALL, AttributeType.FIRE_DAMAGE_BOOST, 0.4, AttributeType.CRIT_CHANCE, 0.1),
                relic(RelicType.LINE, AttributeType.ATTACK_PERCENT, 0.6, AttributeType.CRIT_ATTACK, 0.2));

        return Character.builder()
                .cid(1003)
                .level(80)
                .weapon(Weapon.build(23001, 80))
                .relicSuit(relics)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 0, 0, 0, 0.12, 0))
                .build();
    }

    private static Character march7th() {
        return Character.builder().cid(1001).level(80).build();
    }

    private static Character luocha() {
        return Character.builder().cid(1203).level(80).build();
    }

    /**
     * Hand-build a relic: one main affix + one sub affix (real random generation is in {@code Relic.createRandomLevelZero}).
     */
    private static Relic relic(RelicType type, AttributeType main, double mainValue,
                               AttributeType sub, double subValue) {
        return Relic.create(15, 5, type,
                new Relic.Attribute(main, mainValue),
                List.of(new Relic.Attribute(sub, subValue)));
    }

    // ==================================================================
    // Utilities
    // ==================================================================

    /**
     * The first living <b>monster</b> on the enemy side.
     *
     * <p>Note: The enemy camp can also hold a summon, and this picks around it on purpose: the demo's
     * skill heuristic asks {@code isWeakTo(...)}, which is a monster-only question, so it may only run
     * against a monster. Filtering here is the explicit form of "this rule needs an Enemy" - the
     * alternative (assuming every entry is one) is exactly what the roster widening removed.
     */
    private static Enemy firstAliveEnemy(Battle battle) {
        for (CanHit unit : battle.targetableEnemies()) {
            if (unit instanceof Enemy enemy) {
                return enemy;
            }
        }
        return null;
    }

    /**
     * The living character with the lowest HP ratio (the simplified target-picking strategy for healing/shielding).
     */
    private static Character lowestHpRateCharacter(Battle battle) {
        Character worst = null;
        double worstRate = Double.MAX_VALUE;
        for (Character c : battle.characters) {
            if (c.isDeath()) {
                continue;
            }
            double rate = c.getCurrentHp() / c.getMaxHp();
            if (rate < worstRate) {
                worstRate = rate;
                worst = c;
            }
        }
        return worst;
    }

    private static String fmt(double value) {
        return String.format("%.0f", value);
    }
}
