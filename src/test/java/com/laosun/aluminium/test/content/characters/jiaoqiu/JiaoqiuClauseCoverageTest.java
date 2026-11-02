package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1218 椒丘 clause coverage: every section of {@code 1218_椒丘.md}, plus the readings that pin what this round added.
 *
 * <p><b>What the scan found (2026-11-02)</b>: the ledger called the character 完全实现, and its own {@code _gaps} said only
 * 星魂 4/6 were missing — but <b>three whole trace sections</b> had no rule either. Added: 星魂 4 (the domain's attack-down),
 * 星魂 6's layer cap (5 → 9, the «two rules with opposite Eidolon gates» pattern), and 行迹 爟火. The rest is registered in
 * {@code _gaps}, one entry per missing mechanism.
 */
public class JiaoqiuClauseCoverageTest {

    private static final int JIAOQIU = 1218;
    private static final String ASHEN = "烬煨";

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：火烈弹", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：仁者见仁", "talent_ashen_roast_and_burn");
        SECTION_TO_RULE.put("终结技：随时随地，突发奇想", "ult_opens_the_domain");
        SECTION_TO_RULE.put("天赋：四示八权，纤滋精味", "talent_ashen_roast_and_burn");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：旺火却乱", "technique_opening_aoe_and_stack");
        SECTION_TO_RULE.put("行迹：爟火", "trace_kindling_energy_at_battle_start");
        SECTION_TO_RULE.put("行迹：举炊", "trace_stoking_attack_per_fifteen_percent");
        SECTION_TO_RULE.put("行迹：炙香", "trace_fragrant_guide_copies_the_highest_layers");
        SECTION_TO_RULE.put("行迹：效果命中 / 火伤 / 速度（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：五味五走，生熟有定", "eidolon1_more_damage_to_roasted");
        SECTION_TO_RULE.put("星魂 2：执领烟火", "eidolon2_burn_multiplier");
        SECTION_TO_RULE.put("星魂 3：五味五走", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：藏腑和平，血气资荣", "eidolon4_weaken_enemies_while_the_domain_is_up");
        SECTION_TO_RULE.put("星魂 5：九沸九变", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：九沸九变，火为之纪", "talent_ashen_roast_and_burn_at_eidolon_six");
        SECTION_TO_RULE.put("星魂 6（击杀转移）", "eidolon6_the_roast_moves_to_the_least_roasted_enemy");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        int checked = 0;
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            if (entry.getValue().startsWith("(")) {
                continue;
            }
            checked++;
            Assertions.assertTrue(has(rules, entry.getValue()),
                    entry.getKey() + " has no rule (expected " + entry.getValue() + ")");
        }
        System.out.println("[1218] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
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
        Assertions.assertTrue(e4.jiaoqiu.getBuffManager().hasState("结界"), "precondition: the domain is up");
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
    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean has(JsonArray rules, String id) {
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray rules() throws IOException {
        return character().getAsJsonArray("rules");
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
            battle.applyTrueDamage(jiaoqiu, victim, com.laosun.aluminium.enums.DamageElement.FIRE,
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







