package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1203 罗刹's kit, from his own file: the Abyss Flower counter, the low-HP retrigger, the Field (【结界】) and the two
 * ways it ends, the technique that opens the battle with the Field already up, and the ultimate's
 * 「解除敌方全体 1 个增益效果」.
 *
 * <p>Every clause is built from vocabulary that already existed: {@code ADD_STACK} for the named counter (1111's
 * Fighting Will (斗志)), the {@code HP_LOST} event with the {@code target_hp_percent} numeric variable for the
 * threshold crossing, {@code REMOVE_BUFF} for the ultimate's 「解除敌方全体1个增益效果」, and {@code RESIST_DEBUFF}
 * for 行过幽谷's control resistance -- the same sentence 1013, 1107 and 1109 carry.
 *
 * <p>The Field's two endings are asserted separately on purpose: {@code ticks_on: "self"} delivers both the 2-turn
 * duration and the death sweep from one field, so a case for the death alone would pass with an anchor and no
 * duration, and a case for the duration alone would leave the zone standing over his corpse. Precedent for the shape:
 * 1303 阮•梅's 终结技 opens a 结界 as {@code ticks_on: "self"} with a 2-turn duration.
 */
public class LuochaTest {

    private static final int LUOCHA = 1203;
    // The kit cases use ally 1002; the ultimate's dispel case uses 1001, so the two ids stay separate constants.
    private static final int ALLY = 1002;
    private static final int ALLY_FOR_DISPEL = 1001;
    private static final int LEVEL = 80;
    private static final String TEMPORARY = "敌方增益";
    private static final String PERMANENT = "永久增益";

    /**
     * Note: "并使罗刹获得1层[白花之刻]" (and gives Luocha 1 stack of [白花之刻], Abyss Flower): one stack per skill cast, on himself.
     */
    @Test
    public void hisSkillAddsOneAbyssFlowerStack() {
        SkillFixture f = new SkillFixture();
        Assertions.assertEquals(0, f.luocha.getBuffManager().stacksOf("白花之刻"), "precondition: no stacks");

        f.battle.castImmediate(f.luocha.getSkills().get(SkillType.SKILL), f.luocha, List.of(f.ally));

        Assertions.assertEquals(1, f.luocha.getBuffManager().stacksOf("白花之刻"),
                "「并使罗刹获得1层【白花之刻】」 (and Luocha (罗刹) gains 1 stack of Abyss Flower (【白花之刻】))");
    }

    private static final class SkillFixture {
        private final Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(luocha, ally), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });

        private SkillFixture() {
            battle.startBattle();
        }
    }

    // ==================================================================
    // The Field (【结界】): how it opens, and the two independent ways it ends.
    //
    // The document gives it two endings: 「结界效果持续2回合」 (a duration) and 「当罗刹陷入无法战斗状态时，结界也会被
    // 解除」 (removed when he is knocked down). They are easy to conflate because this engine delivers them from one
    // field: {@code ticks_on: "self"} makes the duration count down on his turn boundaries AND makes his death sweep
    // the buff ({@code Battle.releaseBuffsAnchoredToTheDead}). Each path gets its own case below.
    // ==================================================================

    /**
     * The field opens once 【白花之刻】 reaches 2, and opening it spends the stacks.
     *
     * <p>Without the spend the counter would sit at 2 and every later Skill would reopen the field, so this
     * precondition is asserted before the endings are.
     */
    @Test
    public void twoStacksOpenTheFieldAndAreSpent() {
        FieldFixture f = new FieldFixture();

        f.castSkill();
        Assertions.assertEquals(1, f.stacks(), "one Skill = one stack");
        Assertions.assertFalse(f.fieldUp(), "one stack is not enough to open the field");

        // The second Skill is what reaches two, and the opener consumes both in the same cast -- so the counter
        // reads 0 and the field is already up by the time this call returns.
        f.castSkill();
        Assertions.assertEquals(0, f.stacks(),
                "「达到2层时，罗刹会消耗全部【白花之刻】」 -- the two stacks are spent");
        Assertions.assertTrue(f.fieldUp(), "and that is what opens the field");
    }

    /**
     * The field disappears when 罗刹 is knocked down.
     *
     * <p>The document states this separately from the duration, and it is the clause that used to be impossible to
     * write. The reason is structural: {@code fireTriggers} iterates the party and <b>skips any unit that is dead</b>,
     * so the one unit that has to clean up after itself -- the one leaving -- could never run a rule. The engine now
     * has a {@code DEATH} event delivered to that unit's own table past the skip, and his Talent keys a rule on it.
     *
     * <p>⚠ <b>Why the fight is driven rather than poked.</b> The engine removes the dead at <b>turn end</b>
     * ({@code Battle.afterMove} → {@code removeDeadCombatants}), and {@code afterMove} returns immediately unless a
     * turn is actually in progress -- so a case that applies lethal damage and then looks would be testing nothing.
     * The loop runs real turns until he falls and that turn ends.
     *
     * <p>⚠ <b>Why it is not a broad sweep.</b> Removing a dead unit's own-clock buffs in
     * {@code releaseBuffsAnchoredToTheDead} was tried and reverted: it also strips buffs that a unit legitimately
     * receives as it dies -- 「all allies lethally hit this action」 is a real rule family, and its carrier is already
     * {@code isDeath()} when the buff lands ({@code LethalSetTest}, {@code RelicAbilityBatchTest}). The removal has
     * to be <b>asked for</b> by content, which is what the {@code DEATH} rule does.
     */
    @Test
    public void theFieldLeavesWhenHeIsKnockedDown() {
        FieldFixture f = new FieldFixture();
        f.openField();
        Assertions.assertTrue(f.fieldUp(), "precondition: the field is up");

        f.killHimOnARealTurn();

        Assertions.assertTrue(f.luocha.isDeath(), "precondition: he really fell");
        Assertions.assertFalse(f.fieldUp(),
                "「当罗刹陷入无法战斗状态时，结界也会被解除」 -- it must not outlive him");
    }

    /**
     * The field also runs out on its own after 2 of his turns.
     *
     * <p>Asserted separately from the death case: an anchor alone would keep it up forever, and a duration alone
     * would leave it standing over his corpse. Takes <b>three</b> of his turns, because the turn it was opened on
     * is the first one it is spent on.
     */
    @Test
    public void theFieldRunsOutAfterTwoOfHisTurns() {
        FieldFixture f = new FieldFixture();
        f.openField();
        Assertions.assertTrue(f.fieldUp(), "precondition: the field is up");

        f.takeTurnsUntilHisAreSpent();

        Assertions.assertFalse(f.fieldUp(),
                "「结界效果持续2回合」, counted on his own turns -- it is gone once they are spent");
    }

    /**
     * A battle with 罗刹, an ally and a monster, plus the shorthand the cases need.
     */
    private static final class FieldFixture {

        private final Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        private final Character ally = CharacterFactory.create(ALLY, LEVEL);
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle = new Battle(List.of(luocha, ally), List.of(enemy),
                BattleFixture.alwaysRoll(0.0));

        private FieldFixture() {
            battle.startBattle();
        }

        /** One Skill, which adds a stack. */
        void castSkill() {
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        }

        /** Enough Skills to reach the two stacks that open the field. */
        void openField() {
            castSkill();
            castSkill();
        }

        /**
         * Runs real turns until 罗刹 falls and the turn that killed him has ended.
         *
         * <p>Each iteration advances the bar and runs the turn's start and end, which is what the engine's own
         * cleanup hangs off. Lethal damage is applied at the start of his turn so the end of that same turn is the
         * one that clears the dead.
         */
        void killHimOnARealTurn() {
            for (int guard = 0; guard < 60 && !luocha.isDeath(); guard++) {
                battle.stepForward();
                battle.beforeMove();
                battle.applyDamage(luocha, new Damage(enemy, luocha, DamageElement.PHYSICAL,
                        DamageType.NORMAL, luocha.getMaxHp() * 10));
                battle.afterMove();
            }
        }

        /**
         * Runs real turns until his own turns have spent the field's duration.
         *
         * <p>Bounded, so a duration that never ticks fails the assertion rather than hanging the suite.
         */
        void takeTurnsUntilHisAreSpent() {
            for (int guard = 0; guard < 40 && fieldUp(); guard++) {
                battle.stepForward();
                battle.beforeMove();
                battle.afterMove();
            }
        }

        int stacks() {
            return luocha.getBuffManager().stacksOf("白花之刻");
        }

        /**
         * Whether the zone is open.
         *
         * <p>⚠ Read on the ENEMIES, not on Luocha. The zone marks the units that were in it when it opened -- 「处于结界中的
         * 任意敌方目标受到我方攻击后」 -- and the two healing rules test `target has_state 结界` against the enemy that was
         * attacked. It used to be applied to `self`, which meant no enemy ever carried it and the talent healed nothing;
         * measured, the zone was up for fourteen turns with allies attacking on six of them and not one zone heal.
         *
         * @return {@code true} when any enemy carries the zone
         */
        boolean fieldUp() {
            for (Enemy foe : battle.enemyUnits()) {
                if (foe.getBuffManager().hasState("char:1203/barrier")) {
                    return true;
                }
            }
            return false;
        }
    }

    // ==================================================================
    // The technique 「愚者的悲悯」: "the next battle opens with the talent already triggered".
    //
    // ⚠ What the technique must produce. The talent's own effect is that 【白花之刻】 reaching 2 stacks is spent to
    // EXPAND THE ZONE. So "trigger the talent" means the zone is already up when the battle opens -- and the zone has
    // to be where the talent puts it, on the ENEMIES, because both rules that read it ask `target has_state 结界` on
    // the unit our attack was aimed at:
    //   - 「处于结界中的任意敌方目标受到我方攻击后…」 (talent_zone_heals_the_attacker)
    //   - 「处于结界中的任意敌方目标受到我方攻击后，除攻击者外的我方目标也会回复…」 (trace_zone_heals_the_others)
    // ⚠ A zone on 罗刹 himself satisfies neither, which is the defect these cases exist for: it fired, and produced a
    // 结界, and healed nothing.
    // ==================================================================

    /**
     * ⭐ The declared technique is what puts the zone on the ENEMIES, and only there: with the technique every enemy
     * carries it while 罗刹 himself does not, and with no technique declared nothing opens it.
     *
     * <p>⚠ The negative observations are the point of the case. The defect it exists for was a zone that existed in
     * the wrong place, so a case that only asked "is there a 结界 anywhere" would have passed against the broken
     * version, and a case that only asked it with the technique would have passed against a zone that appeared for
     * some other reason.
     */
    @Test
    public void theDeclaredTechniqueExpandsTheZoneOnTheEnemiesOnly() {
        Battle withTechnique = battle(true);
        Character luocha = withTechnique.characters().get(0);
        Battle withoutTechnique = battle(false);

        Assertions.assertAll(
                () -> {
                    for (Enemy enemy : withTechnique.enemyUnits()) {
                        Assertions.assertTrue(enemy.getBuffManager().hasState("char:1203/barrier"),
                                "「立即触发天赋」 -- the talent expands the zone, and it lands on the ENEMIES, which is "
                                        + "where both «has_state 结界» rules read it; missing on " + enemy.getName());
                    }
                },
                () -> Assertions.assertFalse(luocha.getBuffManager().hasState("char:1203/barrier"),
                        "moving the zone onto Luocha is what made the technique heal nothing -- an ally never "
                                + "satisfies «target has_state 结界», because the target is the enemy our attack was "
                                + "aimed at"),
                () -> {
                    for (Enemy enemy : withoutTechnique.enemyUnits()) {
                        Assertions.assertFalse(enemy.getBuffManager().hasState("char:1203/barrier"),
                                "「使用秘技后」 -- with no technique declared nothing must open the zone");
                    }
                });
    }

    /**
     * One battle with Luocha alone, optionally with his technique declared.
     *
     * @param technique whether he used it
     * @return the started battle
     */
    private static Battle battle(boolean technique) {
        Character luocha = CharacterFactory.create(LUOCHA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(luocha), List.of(enemy), BattleFixture.alwaysRoll(1.0));
        if (technique) {
            battle.markTechniqueUsed(luocha);
        }
        battle.startBattle();
        return battle;
    }

    // ==================================================================
    // The ultimate: 「解除敌方全体 1 个增益效果」.
    //
    // ⚠ Direction matters: this strips a positive effect off the ENEMIES, so it is {@code REMOVE_BUFF}
    // ({@code removeBuffs}) and not {@code DISPEL} ({@code removeDebuffs}, which is the other direction and the one his
    // trace uses). Permanent loadout bonuses are skipped by the engine on purpose - nobody's ultimate dispels a relic.
    // ==================================================================

    /**
     * Every enemy loses exactly its one timed buff; the permanent one stays.
     */
    @Test
    public void theUltimateStripsOneBuffFromEveryEnemy() {
        DispelFixture f = new DispelFixture(2);
        for (Enemy enemy : f.enemies) {
            enemy.getBuffManager().addBuff(new StateBuff(TEMPORARY, 2));
            enemy.getBuffManager().addBuff(new StateBuff(PERMANENT, 100, true));
        }
        f.castUltimate();
        for (Enemy enemy : f.enemies) {
            Assertions.assertFalse(enemy.getBuffManager().hasState(TEMPORARY),
                    enemy.getName() + ": the timed buff is dispelled");
            Assertions.assertTrue(enemy.getBuffManager().hasState(PERMANENT),
                    enemy.getName() + ": a permanent loadout buff is not a 增益效果");
        }
    }

    /**
     * It is one per enemy, not one overall: with two timed buffs each, one remains.
     */
    @Test
    public void onlyOneIsRemovedPerEnemy() {
        DispelFixture f = new DispelFixture(1);
        Enemy enemy = f.enemies.getFirst();
        enemy.getBuffManager().addBuff(new StateBuff(TEMPORARY, 2));
        enemy.getBuffManager().addBuff(new StateBuff("第二个增益", 2));
        f.castUltimate();
        boolean first = enemy.getBuffManager().hasState(TEMPORARY);
        boolean second = enemy.getBuffManager().hasState("第二个增益");
        System.out.println("[1203] after the ultimate: [" + TEMPORARY + "]=" + first + " [第二个增益]=" + second);
        Assertions.assertNotEquals(first, second, "「1 个」 - exactly one of the two timed buffs is gone");
    }

    // ==================================================================

    private static final class DispelFixture {
        private final Character luocha;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private DispelFixture(int enemyCount) {
            luocha = CharacterFactory.create(LUOCHA, LEVEL);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.monster());
            }
            battle = new Battle(new ArrayList<>(List.of(luocha, CharacterFactory.create(ALLY_FOR_DISPEL, LEVEL))),
                    new ArrayList<>(enemies), BattleFixture.seeded());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(luocha.getSkills().get(SkillType.ULTRA), luocha, List.copyOf(enemies));
        }
    }
}
