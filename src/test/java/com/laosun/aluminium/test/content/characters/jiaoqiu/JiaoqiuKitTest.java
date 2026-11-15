package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1218 Jiaoqiu (椒丘): the kit's own numbers - [烬煨]'s capped stacks and the Burn equivalence, the technique, the per-layer
 * resist down, Eidolons 1/2/4/6, and the three traces - together with the clause-coverage readings that pin them.
 *
 * <p>These were six files in the same package ({@code JiaoqiuTest}, {@code JiaoqiuResistDownTest},
 * {@code JiaoqiuEidolonOneTest}, {@code JiaoqiuEidolonOneLayerTest}, {@code JiaoqiuEidolonTwoTest},
 * {@code JiaoqiuClauseCoverageTest}); one class keeps the kit's numbers together without changing one assertion. The
 * domain and the Ultimate are in {@link JiaoqiuDomainTest}.
 *
 * <p><b>What the clause scan found (2026-11-02)</b>: the ledger called the character 完全实现, and its own {@code _gaps} said
 * only 星魂 4/6 were missing — but <b>three whole trace sections</b> had no rule either. Added: 星魂 4 (the domain's
 * attack-down), 星魂 6's layer cap (5 → 9, the «two rules with opposite Eidolon gates» pattern), and 行迹 爟火. The rest is
 * registered in {@code _gaps}, one entry per missing mechanism.
 *
 * <p><b>L3:</b> the readings that already share one fixture and one observed object stay grouped as they were (the capped
 * stacks with the Burn state, the technique's three readings, the resist ladder, and the Eidolon ladders). The two
 * single-assertion cases left each own a fixture no other case shares - a plain ally's hit on a roasted enemy, and the
 * battle-start energy - so they keep their own tests rather than being merged across fixtures. The domain's consolidated
 * scenarios are in {@link JiaoqiuDomainTest}.
 */
public class JiaoqiuKitTest {
    private static final int JIAOQIU = 1218;
    private static final int ATTACKER = 1001;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";

    /**
     * 1218 Jiaoqiu, from his own file: [烬煨]'s capped stacks and the Burn equivalence, both of whose numbers the document
     * states.
     *
     * <p>Note: Seven applications, five layers (the document's cap), and the Burn state alongside it. The cap is tested by
     * EXCEEDING it (round 192's lesson): seven applications must still read five. And `hasState` resolves the engine's DOT
     * states, so the Burn the talent's second sentence creates is askable - which is why it is asserted rather than
     * assumed.
     */
    @Test
    public void theStacksStopAtFiveAndTheBurnIsReal() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();

        for (int i = 0; i < 7; i++) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        }

        Assertions.assertEquals(5, jiaoqiu.getBuffManager() == null ? enemy.getBuffManager().stacksOf("烬煨") : enemy.getBuffManager().stacksOf("烬煨"),
                "「【烬煨】最多叠加5层」 (Ashen Roast stacks at most 5 times) -- seven applications must still read five");
        Assertions.assertTrue(enemy.getBuffManager().hasState("dot:burn"),
                "\"is also considered to be in the Burn state at the same time\" (「也会被视为同时陷入了灼烧状态」) -- a Fire DotBuff IS Burn (dot:burn) by the engine's own translation");
        // and this rule applies a Fire DoT. Registered as an open question rather than asserted falsely or deleted silently.
    }

    /**
     * Note: The technique's opening: the AoE and a stack, only when the technique was declared.
     */
    @Test
    public void theTechniqueHitsAndStacksOnlyWhenDeclared() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.markTechniqueUsed(jiaoqiu);
        double before = enemy.getCurrentHp();

        battle.startBattle();

        Assertions.assertTrue(before - enemy.getCurrentHp() > 0,
                "「对敌方全体造成等同于椒丘100%攻击力的火属性伤害」 (Fire damage to all enemies equal to 100% of Jiaoqiu (椒丘)'s ATK)");
        Assertions.assertEquals(1, enemy.getBuffManager().stacksOf("烬煨"), "and applies 1 stack of Ashen Roast (【烬煨】)");

        Character plain = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy2 = BattleFixture.monster();
        Battle plainBattle = new Battle(List.of(plain), List.of(enemy2), fixed());
        double untouched = enemy2.getCurrentHp();
        plainBattle.startBattle();
        Assertions.assertEquals(untouched, enemy2.getCurrentHp(), 1e-9, "「使用秘技后」 (after using the Technique) -- undeclared, so nothing");
    }

    /**
     * 1218 椒丘's talent: 「每层【烬煨】会使目标的全属性抗性降低 3%」.
     *
     * <p>Two properties: the penalty follows the CURRENT layer count, and it goes back to zero when the layers do - the case
     * the old snapshot spelling got wrong (it stayed at whatever it was when the last attack landed).
     */
    @Test
    public void theResistPenaltyFollowsTheLayersAndReturnsToZero() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        double bare = enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();

        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        int layers = enemy.getBuffManager().stacksOf(ASHEN);
        Assertions.assertTrue(layers >= 1, "precondition: 「施加 1 层【烬煨】」");
        double withOne = enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
        System.out.println("[1218] layers=" + layers + " res " + bare + " -> " + withOne);
        Assertions.assertEquals(0.03 * layers, withOne - bare, 1e-9, "「每层 3%」");

        enemy.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        Assertions.assertEquals(0.03 * (layers + 1), enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare,
                1e-9, "one more layer: the panel follows without another attack");

        // Clear every layer: the penalty has to disappear with them.
        for (int i = 0; i < 10; i++) {
            enemy.getBuffManager().removeState(ASHEN);
        }
        System.out.println("[1218] after clearing: " + (enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare));
        Assertions.assertEquals(0.0, enemy.getAttribute(AttributeType.RESISTANCE_REDUCTION).get() - bare, 1e-9,
                "层数归零后抗性惩罚必须回到 0（旧写法会停在最后一次攻击时的值）");
    }

    /**
     * 1218 椒丘's Eidolon 1, first clause: 「我方目标对处于【烬煨】状态的敌方目标造成的伤害提高 40.00%」.
     *
     * <p>Note: the attacker is a plain ally, not Jiaoqiu - the sentence says 「我方目标」 (any of our units), and a plain ally's
     * boost zone starts at 1.0, so the +0.4 reads as exactly x1.4 instead of being diluted by Jiaoqiu's own buff.
     */
    @Test
    public void anyAllyHitsARoastedEnemyHarder() {
        double e0 = settled(0);
        double e1 = settled(1);
        System.out.println("[1218] e0=" + e0 + " e1=" + e1 + " ratio=" + (e1 / e0));
        Assertions.assertEquals(1.4, e1 / e0, 1e-6, "「对处于【烬煨】状态的敌方目标造成的伤害提高 40%」");
    }

    /**
     * 1218 椒丘's Eidolon 1, second clause: 「每当触发天赋效果使敌方目标陷入【烬煨】状态时，使本次叠加的【烬煨】层数额外提高 1 层」.
     *
     * <p>The Talent applies one layer per hit; with the Eidolon the same application carries two.
     */
    @Test
    public void theTalentAppliesTwoLayersWithTheEidolon() {
        Assertions.assertEquals(1, layersAfterOneHit(0), "without the Eidolon: 「施加 1 层」");
        Assertions.assertEquals(2, layersAfterOneHit(1), "with it: 「本次叠加的层数额外提高 1 层」");
    }

    /**
     * 1218 椒丘's Eidolon 2: 「【烬煨】状态下的敌方目标受到的灼烧伤害倍率提高 300%」 - the talent's Burn scales x4.
     *
     * <p>Note: the fixture is the narrow one the stack-cap case above uses (`alwaysRoll(0.0)`, the base constructor): an
     * earlier probe with a wider fixture and a 0.99 roll saw no Burn at all, which misread as an engine path problem rather
     * than a scaffolding difference.
     */
    @Test
    public void theBurnPerTickQuadruples() {
        double e1 = burnTick(1);
        double e2 = burnTick(2);
        System.out.println("[1218] e1=" + e1 + " e2=" + e2 + " ratio=" + (e2 / e1));
        Assertions.assertTrue(e1 > 0, "precondition: the talent's Burn is on the enemy and ticks");
        Assertions.assertEquals(4.0, e2 / e1, 1e-6, "「倍率提高 300%」 = x4");
    }

    /**
     * 星魂 4: 「结界存在时，敌方目标的攻击力降低 15%」.
     *
     * <p>Measured: the enemy's ATK goes <b>662.784912</b> → <b>563.3671752</b> (×0.85) once the domain is up at E4, and is
     * untouched at E3.
     */
    @Test
    public void eidolon4LowersEnemyAttackWhileTheDomainIsUp() {
        Fixture e4 = new Fixture(4);
        Assertions.assertEquals(662.784912, e4.enemy.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "precondition: the enemy's panel before the Ultimate");
        e4.openDomain();
        Assertions.assertTrue(e4.jiaoqiu.getBuffManager().hasState("char:1218/barrier"), "precondition: the domain is up");
        Assertions.assertEquals(563.3671752, e4.enemy.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "「结界存在时，敌方目标的攻击力降低 15%」 — exactly ×0.85");

        Fixture e3 = new Fixture(3);
        e3.openDomain();
        Assertions.assertEquals(662.784912, e3.enemy.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "control: it is Eidolon 4");
    }

    /**
     * 星魂 6's layer cap: 「【烬煨】的层数上限提升至 9」.
     *
     * <p>Measured by firing the talent's stacking ten times: <b>5</b> layers at E5, <b>9</b> at E6.
     */
    @Test
    public void eidolon6RaisesTheAshenRoastCap() {
        Fixture e5 = new Fixture(5);
        for (int hit = 0; hit < 10; hit++) {
            e5.stackOnce();
        }
        Assertions.assertEquals(5, e5.enemy.getBuffManager().stacksOf(ASHEN), "the base cap");

        Fixture e6 = new Fixture(6);
        for (int hit = 0; hit < 10; hit++) {
            e6.stackOnce();
        }
        Assertions.assertEquals(9, e6.enemy.getBuffManager().stacksOf(ASHEN), "「层数上限提升至 9」");
    }

    /**
     * 行迹 爟火: 「战斗开始时，立即恢复 15 点能量」.
     */
    @Test
    public void theKindlingTracePaysAtBattleStart() {
        Fixture f = new Fixture(0);
        Assertions.assertEquals(15.0, f.jiaoqiu.getCurrentEnergy(), 1e-9,
                "「战斗开始时，立即恢复 15 点能量」 (nothing else in her kit pays at battle start)");
    }

    /**
     * 星魂 6's kill transfer: 「其持有的【烬煨】会叠加给场上存活的【烬煨】层数最低的敌人」.
     *
     * <p>Measured with two enemies and Eidolon 1 also live (it adds one layer per application, so each attack marks twice):
     * the one holding <b>6</b> dies and the one holding <b>2</b> goes to <b>8</b>. At E5 nothing moves.
     */
    @Test
    public void eidolon6MovesTheLayersOfTheFallenToTheLeastRoasted() {
        Fixture e6 = new Fixture(6, 2);
        e6.stackOn(e6.enemies.get(0), 3);
        e6.stackOn(e6.enemies.get(1), 1);

        e6.kill(e6.enemies.get(0));

        Assertions.assertEquals(8, e6.enemies.get(1).getBuffManager().stacksOf(ASHEN),
                "the fallen enemy's 6 layers moved onto the enemy that had 2");

        Fixture e5 = new Fixture(5, 2);
        e5.stackOn(e5.enemies.get(0), 3);
        e5.stackOn(e5.enemies.get(1), 1);
        e5.kill(e5.enemies.get(0));
        Assertions.assertEquals(2, e5.enemies.get(1).getBuffManager().stacksOf(ASHEN),
                "control: it is Eidolon 6");
    }

    /**
     * 行迹 炙香: 「敌方目标进入战斗时，会被施加【烬煨】，层数与结界展开期间【烬煨】层数最高者相同，最低为 1 层」.
     *
     * <p>Measured: with the domain up and the two enemies at 4 and 1 layers, a newcomer arrives with <b>4</b>. (The 4, not 3, is
     * because the Ultimate that opens the domain is itself an attack and marks its target once.) With no domain it stays at 0,
     * and with nobody holding any layers it gets the stated minimum of <b>1</b>.
     */
    @Test
    public void theFragrantTraceCopiesTheHighestLayerCount() {
        Fixture with = new Fixture(0, 2);
        with.openDomain();
        with.stackOn(with.enemies.get(0), 3);
        with.stackOn(with.enemies.get(1), 1);
        Enemy newcomer = with.addEnemy();
        Assertions.assertEquals(4, newcomer.getBuffManager().stacksOf(ASHEN),
                "「层数与…层数最高者相同」 — the highest is 4");

        Fixture withoutDomain = new Fixture(0, 2);
        withoutDomain.stackOn(withoutDomain.enemies.get(0), 3);
        Enemy other = withoutDomain.addEnemy();
        Assertions.assertEquals(0, other.getBuffManager().stacksOf(ASHEN),
                "control: 「结界存在时」 — no domain, no layers");

        Fixture empty = new Fixture(0, 1);
        empty.openDomain();
        Enemy first = empty.addEnemy();
        Assertions.assertEquals(1, first.getBuffManager().stacksOf(ASHEN),
                "「最低为 1 层」 — the floor applies");
    }

    /**
     * 行迹 举炊: 「效果命中大于 80% 时，每超过 15%，则额外提高 60% 攻击力，最高不超过 240%」.
     *
     * <p>Measured over the whole ladder: 0.94 and below ⇒ <b>601.524</b> (no step yet), 0.95 ⇒ <b>962.4384</b> (one step),
     * 1.10 ⇒ <b>1323.3528</b> (two), 1.40 ⇒ <b>2045.1816</b> (four, the cap), and 1.60 ⇒ still 2045.1816.
     *
     * <p>Note: the step is a <b>whole</b> step, which is what {@code floor} buys — a continuous ratio would already pay 4% at
     * 81% EHR, and the ladder above would be a straight line instead of five plateaus.
     */
    @Test
    public void theStokingTraceStepsEveryFifteenPercent() {
        Assertions.assertEquals(601.524, attackAt(0.94), 1e-6, "below 95% ⇒ no step yet");
        Assertions.assertEquals(962.4384, attackAt(0.95), 1e-6, "the first whole step");
        Assertions.assertEquals(1323.3528, attackAt(1.10), 1e-6, "two steps");
        Assertions.assertEquals(2045.1816, attackAt(1.40), 1e-6, "four steps = the 240% cap");
        Assertions.assertEquals(2045.1816, attackAt(1.60), 1e-6, "「最高不超过 240%」 — 1.60 pays no more");
    }
    // ==================================================================
    // Helpers
    // ==================================================================

    /** The narrow fixture the stack cases use: a roll of 0.0, so every crit/proc question is settled the same way. */
    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    /**
     * The panel behind the ladder: the character is built, the effect-hit rate is set, and the battle start is what applies it.
     */
    private static double attackAt(double effectHitRate) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, 80);
        jiaoqiu.getAttribute(AttributeType.EFFECT_HIT_RATE)
                .addBase(effectHitRate - jiaoqiu.getAttribute(AttributeType.EFFECT_HIT_RATE).get());
        Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return jiaoqiu.getAttribute(AttributeType.ATTACK).get();
    }

    /** @return the damage one Burn tick settles on the enemy */
    private static double burnTick(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        double before = enemy.getCurrentHp();
        battle.tickDots(enemy);
        return before - enemy.getCurrentHp();
    }

    private static int layersAfterOneHit(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu), List.of(enemy), fixed());
        battle.startBattle();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
        return enemy.getBuffManager().stacksOf(ASHEN);
    }

    private static double settled(int eidolon) {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL, true, null, null, eidolon);
        Character attacker = CharacterFactory.create(ATTACKER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(jiaoqiu, attacker), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        enemy.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        return battle.applyDamage(enemy, new Damage(attacker, enemy, DamageElement.PHYSICAL, DamageType.NORMAL, 100));
    }

    private static JsonArray gaps() throws IOException {
        return character().getAsJsonArray("_gaps");
    }

    private static JsonObject character() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1218.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final class Fixture {
        private final Character jiaoqiu;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;
        private final Enemy enemy;

        private Fixture(int eidolon) {
            this(eidolon, 1);
        }

        private Fixture(int eidolon, int enemyCount) {
            jiaoqiu = CharacterFactory.create(JIAOQIU, 80, false, null, null, eidolon);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
            }
            enemy = enemies.getFirst();
            battle = new Battle(List.of(jiaoqiu), List.copyOf(enemies), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void openDomain() {
            battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu, List.of(enemy));
            battle.processRequests();
        }

        private void stackOnce() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, enemy, 0, 0));
            battle.processRequests();
        }

        /** Gives {@code target} {@code count} layers of [烬煨] directly, so a fixture can set up the counter it needs. */
        private void stackOn(Enemy target, int count) {
            for (int i = 0; i < count; i++) {
                battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, jiaoqiu, target, 0, 0));
                battle.processRequests();
            }
        }

        private void kill(Enemy victim) {
            battle.applyTrueDamage(jiaoqiu, victim, DamageElement.FIRE,
                    victim.getMaxHp() * 2.0);
            battle.processRequests();
        }

        private Enemy addEnemy() {
            Enemy newcomer = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
            battle.addEnemy(newcomer);
            battle.processRequests();
            return newcomer;
        }
    }
}
