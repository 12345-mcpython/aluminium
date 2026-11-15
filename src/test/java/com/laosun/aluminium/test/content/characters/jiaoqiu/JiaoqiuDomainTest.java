package com.laosun.aluminium.test.content.characters.jiaoqiu;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StackBuff;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
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
 * 1218 椒丘's Ultimate: the 【结界】 domain - its lifetime, its per-enemy-turn trigger budget, the layer unification it
 * performs, and the damage-taken amplification it grants.
 *
 * <p>These were four files in the same package ({@code JiaoqiuDomainTest}, {@code JiaoqiuDomainTriggerTest},
 * {@code JiaoqiuUnifyLayersTest}, {@code JiaoqiuUltTakenTest}); one class keeps every clause of the one skill together
 * without changing an assertion. The rest of the kit is in {@link JiaoqiuKitTest}.
 *
 * <p><b>L3:</b> the single-clause cases that share one fixture and one clause are folded into the scenario tests below,
 * each assertion inside {@link Assertions#assertAll} keeping the message that names the clause: the domain's three-turn
 * lifetime with the recast that re-arms it ({@code battle()}), its per-enemy-turn layer together with the six-trigger
 * budget it spends and the recast that clears it (one {@code Fixture}), and the 15% ultimate-damage clause judged twice,
 * at the compiled rule and on the measured ratio. The layer unification and the tiered vulnerability keep their own
 * fixtures and their own tests. Nothing was dropped: every expected value, tolerance and message the separate cases
 * carried is still asserted.
 */
public class JiaoqiuDomainTest {

    private static final int JIAOQIU = 1218;
    private static final int LEVEL = 80;
    private static final String ASHEN = "烬煨";
    private static final String DOMAIN = "char:1218/barrier";

    /**
     * 1218 椒丘's Ultimate, the zone's lifetime: 「结界持续 3 回合，自身每回合开始时持续回合数减 1」,
     * 「当椒丘陷入无法战斗状态时，结界也会被解除」, and 「每次施放终结技时重置」 - a recast puts it back to three.
     */
    @Test
    public void theDomainLastsThreeOfHerTurnsAndACastRearmsIt() {
        Battle battle = battle();
        Character jiaoqiu = battle.characters().getFirst();
        battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu,
                List.of(battle.enemies().getFirst()));
        boolean opened = jiaoqiu.getBuffManager().hasState(DOMAIN);
        int afterFirstCast = jiaoqiu.getBuffManager().allBuffsOf(StateBuff.class).stream()
                .filter(b -> DOMAIN.equals(b.getState())).findFirst().orElseThrow().remainingTurns();

        battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu,
                List.of(battle.enemies().getFirst()));
        int afterRecast = jiaoqiu.getBuffManager().allBuffsOf(StateBuff.class).stream()
                .filter(b -> DOMAIN.equals(b.getState())).findFirst().orElseThrow().remainingTurns();

        for (int i = 0; i < 3; i++) {
            jiaoqiu.getBuffManager().beforeMove();
            jiaoqiu.getBuffManager().afterMove();
        }
        boolean spent = jiaoqiu.getBuffManager().hasState(DOMAIN);

        Assertions.assertAll("the domain's three-turn lifetime and the recast that re-arms it",
                () -> Assertions.assertTrue(opened, "「随后开启结界」"),
                () -> Assertions.assertEquals(3, afterFirstCast, "「持续 3 回合」"),
                () -> Assertions.assertEquals(3, afterRecast, "「每次施放终结技时重置」 - a recast puts it back to three"),
                () -> Assertions.assertFalse(spent, "spent on HER turn boundaries (ticks_on: self)"));
    }

    /**
     * 1218 椒丘's Ultimate: 「敌方目标行动时有 60% 的基础概率被施加 1 层【烬煨】，结界存在期间最多触发 6 次，且每个敌方目标
     * 每回合只能触发 1 次…每次施放终结技时重置」.
     *
     * <p>One sentence, one {@code Fixture} shape: the per-turn limit is read on the layer the trigger pays, and the
     * budget it spends is read on the counter that spends it - the two facets of the same trigger.
     */
    @Test
    public void theDomainPaysOneLayerPerEnemyTurnAndStopsAtSixTriggers() {
        Fixture perTurn = new Fixture();
        int before = perTurn.enemy.getBuffManager().stacksOf(ASHEN);

        perTurn.enemyTurn();
        int afterOneTurn = perTurn.enemy.getBuffManager().stacksOf(ASHEN);

        perTurn.enemyTurn();
        int afterSecondTurn = perTurn.enemy.getBuffManager().stacksOf(ASHEN);

        Fixture budget = new Fixture();
        for (int i = 0; i < 4; i++) {
            budget.newEnemyTurn();
        }
        int afterFourTriggers = budget.count();

        budget.ultimate();
        int afterRecast = budget.count();

        for (int i = 0; i < 10; i++) {
            budget.newEnemyTurn();
        }
        int afterTenTurns = budget.count();
        System.out.println("[1218] triggers=" + afterTenTurns);

        Assertions.assertAll("the domain's per-enemy-turn layer and its six-trigger budget",
                () -> Assertions.assertEquals(before + 1, afterOneTurn,
                        "「60% 的基础概率被施加 1 层」 - the roll is forced to succeed by the fixture"),
                () -> Assertions.assertEquals(before + 1, afterSecondTurn,
                        "「每个敌方目标每回合只能触发 1 次」 - a second turn of the same enemy pays once"),
                () -> Assertions.assertEquals(4, afterFourTriggers, "precondition: four triggers so far"),
                () -> Assertions.assertEquals(0, afterRecast, "「每次施放终结技时重置触发次数」"),
                () -> Assertions.assertEquals(6, afterTenTurns, "「结界存在期间该效果最多触发 6 次」"));
    }

    /**
     * 1218 椒丘's Ultimate: 「将敌方目标具有的【烬煨】层数统一设置为场上【烬煨】层数的最高值」.
     *
     * <p>The op is {@code SET_STACKS} with the new {@code max_enemy_stacks:烬煨} scale: every enemy ends on the highest count
     * anyone had - the one below gains layers, the one above is untouched because it IS the maximum.
     */
    @Test
    public void theUltimateLevelsEveryEnemyToTheHighestCount() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy low = BattleFixture.monster();
        Enemy high = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(low, high)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        low.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        for (int i = 0; i < 3; i++) {
            high.getBuffManager().addBuff(new StackBuff(ASHEN, 1, true, 5));
        }
        Assertions.assertEquals(1, low.getBuffManager().stacksOf(ASHEN), "precondition: 1 vs 3");

        // Fire the event itself (not the whole cast): the cast also fires the talent, which adds a layer to the primary target
        // afterwards and would blur what this clause does.
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, jiaoqiu, low, 0, 0));
        System.out.println("[1218] low=" + low.getBuffManager().stacksOf(ASHEN)
                + " high=" + high.getBuffManager().stacksOf(ASHEN));
        Assertions.assertEquals(3, low.getBuffManager().stacksOf(ASHEN), "「统一设置为最高值」");
        Assertions.assertEquals(3, high.getBuffManager().stacksOf(ASHEN), "the maximum itself does not move");
    }

    /**
     * 1218 Jiaoqiu's ultimate: "处于结界中时，敌方目标受到的终结技伤害提高 15.00%…结界持续 3 回合".
     *
     * <p>Judged twice and both bind the number: the compiled rule (scope, share, turns) and the measured ratio between ultimate and normal damage.
     */
    @Test
    public void theUltimateDamageBoostIsStatedAtFifteenPercentAndMeasured() {
        Character unit = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        var rules = unit.getTriggerTable().matching(TriggerEvent.ULT_CAST,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double share = -1;
        String scope = null;
        int turns = -1;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp())) {
                    // The share may be READ from the Talent row: this rule scopes a per-layer vulnerability, and reading a row
                    // is the spelling the corpus is moving to, so the shape check follows whichever spelling the file uses.
                    share = com.laosun.aluminium.test.support.RowLookup.shareOf(1218, effect);
                    scope = effect.getDamageType();
                    turns = effect.getTurns();
                    System.out.println("[1218] rule id=" + rule.id() + " damageType=" + scope + " percent=" + share
                            + " turns=" + turns);
                }
            }
        }
        String ruleScope = scope;
        double ruleShare = share;
        int ruleTurns = turns;

        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ULT_CAST, unit, enemy, 0, 0));
        double normal = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
        double ultra = battle.applyDamage(enemy, new Damage(unit, enemy, DamageElement.FIRE, DamageType.ULTRA, 1000));
        System.out.println("[1218] normal=" + normal + " ultra=" + ultra + " ratio=" + (ultra / normal));

        Assertions.assertAll("the domain's 15% ultimate-damage share: the compiled rule and the measured ratio",
                () -> Assertions.assertEquals("ULTRA", ruleScope, "scoped to ultimate damage"),
                () -> Assertions.assertEquals(0.15, ruleShare, 1e-9, "the clause states 15%"),
                () -> Assertions.assertEquals(3, ruleTurns, "and the domain lasts three turns"),
                () -> Assertions.assertEquals(1.15, ultra / normal, 1e-6, "ultimate damage rises by exactly the authored 15%"));
    }

    @Test
    public void theTieredRuleStatesItsBaseSharePerLayerAndCeiling() {
        Character unit = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(unit), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        // Note: `matching` EVALUATES the rule's conditions, so the counter must really be stacked: the character's own ADD_STACK rule
        // (on ALLY_ATTACK) is what creates it -- a hand-made state does not register (round 219/240).
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, unit, enemy, 0, 0));
        System.out.println("[1218] layers=" + enemy.getBuffManager().stacksOf("烬煨"));
        var rules = unit.getTriggerTable().matching(TriggerEvent.DEALING_DAMAGE,
                new TriggerTable.TriggerContext(unit, unit, enemy, 0, 0));
        double amount = -1;
        double percent = -1;
        double cap = -1;
        String scale = null;
        for (var rule : rules) {
            for (var effect : rule.effects()) {
                if ("MODIFY_DAMAGE_TAKEN".equals(effect.getOp()) && effect.getScale() != null) {
                    amount = effect.getAmount();
                    percent = com.laosun.aluminium.test.support.RowLookup.shareOf(1218, effect);
                    cap = effect.getCapAmount();
                    scale = effect.getScale();
                    System.out.println("[1218] tiered rule id=" + rule.id() + " amount=" + amount + " scale=" + scale
                            + " percent=" + percent + " capAmount=" + cap + " turns=" + effect.getTurns());
                }
            }
        }
        Assertions.assertEquals("target_stacks:烬煨", scale, "the layers live on the event target");
        Assertions.assertEquals(0.10, amount, 1e-9, "the base is 10% so that ONE layer reads 15%");
        Assertions.assertEquals(0.05, percent, 1e-9, "each further layer adds 5%");
        Assertions.assertEquals(0.35, cap, 1e-9, "five layers cap it at 35%");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Battle battle() {
        Character jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    private static final class Fixture {
        private final Character jiaoqiu;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture() {
            jiaoqiu = CharacterFactory.create(JIAOQIU, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(jiaoqiu, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
            battle.startBattle();
            ultimate();
        }

        private void ultimate() {
            battle.castImmediate(jiaoqiu.getSkills().get(SkillType.ULTRA), jiaoqiu, List.of(enemy));
        }

        private void enemyTurn() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, enemy, enemy, 0, 0));
        }

        /**
         * A fresh enemy takes its turn, so 「每敌每回合 1 次」 does not block the next trigger.
         */
        private void newEnemyTurn() {
            Enemy fresh = BattleFixture.monster();
            battle.addEnemy(fresh);
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, fresh, fresh, 0, 0));
        }

        private int count() {
            return jiaoqiu.getBuffManager().stacksOf("结界计数");
        }
    }
}
