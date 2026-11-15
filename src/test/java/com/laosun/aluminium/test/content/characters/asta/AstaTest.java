package com.laosun.aluminium.test.content.characters.asta;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1009 艾丝妲 (Asta) - the whole character in one place.
 *
 * <p>Consolidated from six per-concern test classes; every test and every assertion of those files is kept. The concerns,
 * in file order, are:
 *
 * <ul>
 *   <li>her own file: the Ultimate's FLAT 50-point party SPEED boost, and the technique's 50%-ATK opening with its
 *       undeclared control;</li>
 *   <li>星魂 4: the 15% energy-regeneration THRESHOLD once two charges exist, which is not a per-layer scale;</li>
 *   <li>the talent: the +14.00%-per-layer attack aura over 「我方全体」 (capped at five layers), its decay (her first turn
 *       pays nothing, every later one spends three), its charge counted per TARGET, and the extra layer a Fire-weakness
 *       target pays.</li>
 * </ul>
 */
public class AstaTest {
    private static final int ASTA = 1009;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final String CHARGE = "蓄能";

    /**
     * Note: "使我方全体速度提高50点，持续2回合" -- fifty points, for the party.
     *
     * <p>1009 Asta, from her own file: the FLAT speed boost, whose base is 1documents. The assertion is absolute because the
     * document is: speed rises by exactly 50 POINTS, not by a share.
     */
    @Test
    public void herUltimateRaisesEveryAllysSpeedByFiftyPoints() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(asta, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = ally.getAttribute(AttributeType.SPEED).get();

        battle.castImmediate(asta.getSkills().get(SkillType.ULTRA), asta, List.of(ally));

        Assertions.assertEquals(50.0, ally.getAttribute(AttributeType.SPEED).get() - before, 1e-6,
                "「使我方全体速度提高50点」 (raises all allies' SPD by 50): before " + before + ", after " + ally.getAttribute(AttributeType.SPEED).get());
    }

    /**
     * Note: The technique's 50%-ATK opening, and its control.
     */
    @Test
    public void theTechniqueHitsOnlyWhenDeclared() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(asta), List.of(enemy), fixed());
        battle.markTechniqueUsed(asta);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体目标造成等同于艾丝妲50%攻击力的火属性伤害」 (Fire damage to all enemies equal to 50% of Asta (艾丝妲)'s ATK)");

        Character plain = CharacterFactory.create(ASTA, LEVEL);
        Enemy enemy2 = BattleFixture.monster();
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9, "「使用秘技后」 (after using the Technique) -- undeclared, so nothing");
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    /**
     * 星魂 4: 「天赋的蓄能层数 ≥2 时，能量恢复效率提高 15%」 — a THRESHOLD on a counter, not a per-layer scale.
     *
     * <p>{@code per_stack} scales continuously (each layer gives 15%); the sentence pays a flat 15% once two layers exist and
     * nothing at one. The pair {@code while_stacks} / {@code while_stacks_min} is the threshold spelling, and the gate is asked on
     * Asta's own counter at a turn boundary.
     *
     * <p>1009 艾丝妲 clause coverage. Every document section maps to a rule, except Eidolon 4, which is a REGISTERED GAP (see
     * {@code _gaps}): 「天赋的蓄能层数大于等于 2 时，艾丝妲的能量恢复效率提高 15%」 is a condition that must turn on and off as a
     * live counter crosses 2, and the axis has no "while" modifier.
     */
    @Test
    public void eidolon4PaysOnlyAtTwoOrMoreCharges() {
        Assertions.assertEquals(0.0, regenAt(1), 1e-9, "one charge is below the threshold");
        Assertions.assertEquals(0.15, regenAt(2), 1e-9, "「大于等于 2 时…提高 15%」");
        Assertions.assertEquals(0.15, regenAt(3), 1e-9, "a third charge does not add another 15%");
    }

    private static double regenAt(int charges) {
        Character asta = CharacterFactory.create(1009, 80, false, null, null, 4);
        com.laosun.aluminium.enemy.Enemy enemy =
                BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(asta), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        for (int i = 0; i < charges; i++) {
            asta.getBuffManager().addBuff(new StackBuff("蓄能", 99, true, 99));
        }
        // The gate is swept at a TURN BOUNDARY (BuffManager.processBuffTick), not by a TURN_START event - so the fixture gives her
        // a real turn: the move signal is taken from the queue the way ArgentiStacksTest does it.
        battle.setCurrentMove(battle.queue().snapshot().stream()
                .filter(signal -> signal.getCanHit() == asta)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no signal for Asta")));
        battle.beforeMove();
        battle.afterMove();
        return asta.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
    }

    /**
     * 1009 艾丝妲's talent: 「每拥有 1 层蓄能，会使我方全体攻击力提高 14.00%，该效果最多叠加 5 层」.
     *
     * <p>Two properties: the aura follows the LIVE count (adding a charge moves every ally's ATK without re-attaching anything),
     * and it stops at five layers even when the counter holds more.
     */
    @Test
    public void everyChargeRaisesEveryonesAttackAndStopsAtFive() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Character ally = CharacterFactory.create(1001, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();

        double astaBase = asta.getAttribute(AttributeType.ATTACK).baseValue();
        double allyBase = ally.getAttribute(AttributeType.ATTACK).baseValue();
        double astaStart = asta.getAttribute(AttributeType.ATTACK).get();
        double allyStart = ally.getAttribute(AttributeType.ATTACK).get();

        addCharges(asta, 3);
        Assertions.assertEquals(0.14 * 3 * astaBase, asta.getAttribute(AttributeType.ATTACK).get() - astaStart, 1e-6,
                "3 layers = +42% of the base, on her");
        Assertions.assertEquals(0.14 * 3 * allyBase, ally.getAttribute(AttributeType.ATTACK).get() - allyStart, 1e-6,
                "and on the ally (「我方全体」)");

        addCharges(asta, 4);   // 7 layers, the aura must stop at 5
        double astaAtSeven = asta.getAttribute(AttributeType.ATTACK).get() - astaStart;
        System.out.println("[1009] 7 layers -> " + astaAtSeven + " ; capped = " + (0.14 * 5 * astaBase));
        Assertions.assertEquals(0.14 * 5 * astaBase, astaAtSeven, 1e-6,
                "「该效果最多叠加 5 层」 - seven layers still pay five");
    }

    private static void addCharges(Character asta, int layers) {
        for (int i = 0; i < layers; i++) {
            asta.getBuffManager().addBuff(new StackBuff(CHARGE, 1, true, 99));
        }
    }

    /**
     * 1009 艾丝妲's talent, last sentence: 「从自身的第2回合开始，艾丝妲每回合开始时蓄能层数减少3层」.
     *
     * <p>The "from her second turn" gate is counted by a counter the rules raise on her own turn starts, so the FIRST turn pays
     * nothing and every later one spends three layers.
     */
    @Test
    public void theFirstTurnPaysNothingAndEveryLaterOneSpendsThree() {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        for (int i = 0; i < 6; i++) {
            asta.getBuffManager().addBuff(new com.laosun.aluminium.buff.StackBuff(CHARGE, 1, true, 99));
        }
        Assertions.assertEquals(6, asta.getBuffManager().stacksOf(CHARGE), "precondition: six layers");

        astaTurn(battle, asta);
        Assertions.assertEquals(6, asta.getBuffManager().stacksOf(CHARGE), "the first turn does not decay");

        astaTurn(battle, asta);
        Assertions.assertEquals(3, asta.getBuffManager().stacksOf(CHARGE), "from the second turn: -3");

        astaTurn(battle, asta);
        Assertions.assertEquals(0, asta.getBuffManager().stacksOf(CHARGE), "and every later one");
    }

    private static void astaTurn(Battle battle, Character asta) {
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, asta, asta, 0, 0));
    }

    /**
     * 1009 艾丝妲's talent: 「每击中 1 名**不同**的敌方目标获得 1 层蓄能」 - counted per TARGET, not per hit.
     */
    @Test
    public void threeTargetsPayThreeAndOneTargetPaysOne() {
        Assertions.assertEquals(3, chargeFor(3), "three distinct targets: three layers");
        Assertions.assertEquals(1, chargeFor(1), "one target hit three times is still ONE target");
    }

    /**
     * @param targets how many enemies the attack connected with
     */
    private static int chargeFor(int targets) {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        List<Enemy> enemies = new ArrayList<>();
        for (int i = 0; i < targets; i++) {
            enemies.add(BattleFixture.enemy(803201006, LEVEL));   // no Fire weakness, so only the base layers count
        }
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                enemies, BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = asta.getBuffManager().stacksOf(CHARGE);
        battle.fire(TriggerPayload.Cast.of(TriggerEvent.ALLY_ATTACK, asta, enemies.getFirst(),
                targets, 0, com.laosun.aluminium.enums.SkillCategory.NORMAL, 1, 0, new ArrayList<com.laosun.aluminium.entity.CanHit>(enemies)));
        return asta.getBuffManager().stacksOf(CHARGE) - before;
    }

    /**
     * 1009 艾丝妲's talent: 「若受到攻击的敌方目标弱点为火属性，则额外获得 1 层蓄能」.
     */
    @Test
    public void onlyAFireWeaknessEnemyPaysTheExtraLayer() {
        Assertions.assertEquals(0, chargeFrom(false), "no Fire weakness: nothing");
        Assertions.assertEquals(1, chargeFrom(true), "「额外获得 1 层」");
    }

    private static int chargeFrom(boolean fireWeakness) {
        Character asta = CharacterFactory.create(ASTA, LEVEL);
        // 803201006 weakness = [Thunder, Wind] (no Fire); the standard fixture already has Fire.
        Enemy enemy = fireWeakness ? BattleFixture.monster() : BattleFixture.enemy(803201006, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(asta, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        int before = asta.getBuffManager().stacksOf(CHARGE);
        battle.applyDamage(enemy, new Damage(asta, enemy, DamageElement.FIRE, DamageType.NORMAL, 100));
        return asta.getBuffManager().stacksOf(CHARGE) - before;
    }
}
