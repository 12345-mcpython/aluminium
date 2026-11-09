package com.laosun.aluminium.test.content.characters.jingyuan;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1204 景元's Eidolon 1: 「【神君】施放攻击时，对指定敌方单体的相邻目标造成的伤害倍率额外提高，提高数值等同于对主目标伤害倍率的25%」.
 *
 * <p>The talent gives the splash at <b>25%</b> of the main target's share; the Eidolon adds <b>25 percentage points</b>, so 50%.
 * {@code blast} is a field of the RULE (one value per firing), and the engine has no "raise this field by N while an Eidolon is
 * on", so the sentence is written twice - the base one gated {@code eidolon < 1} and the variant {@code eidolon >= 1}, each
 * stating the COMPLETE number rather than a delta. This judge pins both halves: which rule is live at which rank (the field
 * itself), and that the live one really is the one that settles damage.
 */
public class JingyuanEidolonOneTest {
    private static final int JING_YUAN = 1204;
    private static final int LEVEL = 80;

    /** The rule that fires at this rank states the whole splash, and only ONE of the pair is ever live. */
    @Test
    public void theSplashIsStatedTwiceAndThePairIsComplementary() {
        assertSplashPair();
    }

    /** The raised share is the one that settles: the same turn start takes more HP off three identical enemies. */
    @Test
    public void theRaisedSplashIsTheOneThatSettles() {
        double atZero = totalOfTheLordsTurn(0);
        double atOne = totalOfTheLordsTurn(1);
        System.out.println("[1204] splash totals: e0=" + atZero + " e1=" + atOne);
        Assertions.assertTrue(atZero > 0, "precondition: the strike settled without the Eidolon");
        Assertions.assertTrue(atOne > atZero,
                "the Eidolon's splash must be visibly bigger on the same scene and seed ("
                        + atOne + " vs " + atZero + ")");
    }

    /**
     * The splash the pair states, read off the compiled table.
     *
     * <p>⚠ Read through {@code rulesFor} + the rule's own conditions, NOT through {@code matching(event, ctx)}: a hand-built
     * context does not carry the summon's stack counter, so {@code actor_stacks:攻击段数 >= 1} is false and nothing matches
     * (measured: 0 rules at every rank). This half pins the pair's SHAPE - two splash rules whose eidolon conditions are
     * complementary - while {@link #theRaisedSplashIsTheOneThatSettles} pins that the right one settles.
     */
    private static void assertSplashPair() {
        var table = com.laosun.aluminium.data.TriggerTables.of(JING_YUAN);
        List<String> splash = new ArrayList<>();
        for (var rule : table.rulesFor(TriggerEvent.TURN_START)) {
            for (var effect : rule.effects()) {
                if (!"DAMAGE".equals(effect.getOp()) || effect.getBlast() == null) {
                    continue;
                }
                String conditions = String.join(" ; ", rule.conditions().stream().map(c -> c.source()).toList());
                splash.add(rule.id() + " blast=" + effect.getBlast() + " when=" + conditions);
            }
        }
        System.out.println("[1204] splash rules: " + splash);
        Assertions.assertEquals(2, splash.size(), "the pair: the talent 25% and eidolon 1 50%");
        Assertions.assertTrue(splash.stream().anyMatch(s -> s.contains("blast=0.25") && s.contains("eidolon < 1")),
                "the talent splash is gated to ranks below 1: " + splash);
        Assertions.assertTrue(splash.stream().anyMatch(s -> s.contains("blast=0.5") && s.contains("eidolon >= 1")),
                "eidolon 1 splash takes over from rank 1: " + splash);
    }

    /** Total HP taken off three identical enemies by one Lord turn (three segments, each picking a random centre). */
    private static double totalOfTheLordsTurn(int eidolon) {
        SceneForSplash scene = new SceneForSplash(eidolon);
        double before = scene.hp();
        // The event TARGET is an enemy, not the summon: `random_enemy` resolves the party the event is about, and a
        // payload whose target is the summon itself has no enemy side to draw from (measured: "this event has no such party").
        scene.battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, scene.lord, scene.enemies.getFirst(), 1, 0));
        scene.battle.processRequests();
        return before - scene.hp();
    }

    /** A battle with 景元, his Lord, and three identical enemies with a bar too tough to break. */
    private static final class SceneForSplash {
        private final Character character;
        private final Summon lord;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private SceneForSplash(int eidolon) {
            character = CharacterFactory.create(JING_YUAN, LEVEL, true, null, null, eidolon);
            for (int i = 0; i < 3; i++) {
                Enemy enemy = BattleFixture.monster();
                enemy.setMaxStance(100_000);
                enemy.setStance(100_000);
                enemies.add(enemy);
            }
            battle = new Battle(List.of(character), enemies, BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            battle.processRequests();
            lord = battle.summonsOf(character).getFirst();
        }

        private double hp() {
            return enemies.stream().mapToDouble(Enemy::getCurrentHp).sum();
        }
    }
}