package com.laosun.aluminium.test.content.characters.castorice;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.MemospriteSpec;
import com.laosun.aluminium.data.Memosprites;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.SummonFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1407 Castorice: the talent's damage boost on HP loss, the dragon's arrival (six hits and a party heal), and the
 * dragon's panel/skill shaped by 【新蕊】.
 *
 * <p>These were three small files in the same package; one class keeps the character together without changing any assertion.
 */
public class CastoriceTest {
    private static final int CASTORICE = 1407;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    /**
     * Note: The boost needs an HP loss, and its three-stack cap is enforced by exceeding it.
     */
    @Test
    public void theBoostNeedsAnHpLossAndStopsAtThreeStacks() {
        Character castorice = CharacterFactory.create(CASTORICE, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(castorice, ally), List.of(enemy), fixed());
        battle.startBattle();
        double before = castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();

        // Only HP_LOST moves it: an unrelated event must not.
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        Assertions.assertEquals(0.0, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-9,
                "「我方损失生命值时」 (when our side loses HP) -- an attack that costs no HP does nothing");

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, ally, ally, 0, 100));
        Assertions.assertEquals(0.2, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-6,
                "「造成的伤害提高20%」 (DMG dealt is raised by 20%)");

        for (int i = 0; i < 4; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.HP_LOST, ally, ally, 0, 100));
        }
        Assertions.assertEquals(0.6, castorice.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before, 1e-6,
                "「最多叠加3层」 (stacks at most 3 times) -- five firings must still read three stacks of 20%");
    }

    /**
     * The dragon's talent [灼掠幽墟的晦翼]: "造成 #2 次伤害，每次伤害对敌方随机单体造成等同于遐蝶 #1% 生命上限的量子属性伤害…".
     *
     * <p>Two-sided: summoning the dragon deals its six hits and heals our side; and the rule that carries the damage ratio
     * is there BY ID, because 114151 raises it with {@code MODIFY_RULE}.
     */
    @Test
    public void theDragonTalentHitsAndHeals() {
        double[] r = run();
        System.out.println("[dragon] the enemy lost " + r[0] + " ; our side gained " + r[1]
                + " HP (the dragon pays " + r[2] + " of its own master's max HP)");
        // COUNTABLE: six hits of #1 of her max HP, each settled on the enemy -- and `r[3]` is the mitigation the judge measured itself with one plain hit.
        // PLUS the small-pack extra: this battle has ONE enemy, so 114151's second half raises the ratio by `#5` as well.
        double expected = 6 * (0.56 + 0.0024) * r[4] * r[3];
        System.out.println("[dragon]   expected 6 x (56% + 0.24%) x her max HP " + r[4] + " x mitigation " + r[3]
                + " = " + expected);
        Assertions.assertEquals(expected, r[0], expected * 1e-6, "six hits of #1 of her max HP");
        Assertions.assertTrue(r[1] > 0, "and the party must be healed");
    }

    /**
     * The dead dragon: its panel states speed 165 flat and health = 100% of the [新蕊] cap, its ATTACK slot
     * carries the SUMMONER's Max HP, and memosprite skill 1 deals 40% of that.
     */
    @Test
    public void theDragonPanelFollowsNewbud() {
        MemospriteSpec spec = Memosprites.of(CASTORICE);
        Assertions.assertNotNull(spec, "the dragon's spec must load from resources/game/memosprites/1407.json");
        Character master = CharacterFactory.create(CASTORICE, LEVEL, false, null, null, 0);

        Summon small = SummonFactory.memosprite(master, spec, name -> 1000);
        Assertions.assertEquals(1000, small.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "100% of a 1000-point 【char:1407/rosebud】 (Newbud)");

        Summon large = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(34000, large.getAttribute(AttributeType.HEALTH).get(), 1e-6,
                "and 100% of a 34,000-point one -- the panel follows the resource");
        Assertions.assertEquals(165, large.getAttribute(AttributeType.SPEED).get(), 1e-6,
                "the document states 165 speed, flat");
    }

    /**
     * memosprite skill 1 as the document states it, and the panel slot it scales off.
     */
    @Test
    public void theDragonSkillIsWhatTheDocumentSays() {
        MemospriteSpec spec = Memosprites.of(CASTORICE);
        Assertions.assertNotNull(spec.attack(), "the skill must be stated (忆灵技能 1)");
        Assertions.assertEquals("Quantum", spec.attack().element(), "document: Quantum DMG (量子属性伤害)");
        Assertions.assertEquals("ATTACK", spec.attack().base(), "scales off this memosprite's ATTACK slot");
        Assertions.assertEquals(0.4, spec.attack().percent(), 1e-9, "40.00%, the level the prose quotes");
        Assertions.assertEquals("AoEAttack", spec.attack().shape(), "document: AoE attack (全体攻击)");
        Assertions.assertEquals(30, spec.attack().stance(), 1e-9, "document: AoE (全体) 30");

        Character master = CharacterFactory.create(CASTORICE, LEVEL, false, null, null, 0);
        Summon dragon = SummonFactory.memosprite(master, spec, name -> 34000);
        Assertions.assertEquals(master.getAttribute(AttributeType.HEALTH).get(),
                dragon.getAttribute(AttributeType.ATTACK).get(), 1e-6,
                "and that slot carries the summoner's Max HP, so the hit is 40% of it");
    }

    /**
     * [what the enemy lost, what our side gained, the HP cost the talent paid, the mitigation, her max HP].
     */
    private static double[] run() {
        Character her = CharacterFactory.create(CASTORICE, LEVEL);
        Character other = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(List.of(her, other),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        her = battle.characters().getFirst();
        double enemyBefore = battle.enemies().getFirst().getCurrentHp();
        // BOTH of us must be wounded: a heal on a full unit gains exactly nothing, which is what the first reading measured.
        her.takeDamage(her.getMaxHp() * 0.5);
        battle.characters().get(1).takeDamage(battle.characters().get(1).getMaxHp() * 0.5);
        battle.processRequests();
        double partyBefore = battle.characters().get(1).getCurrentHp();
        double herBefore = battle.characters().getFirst().getCurrentHp();
        // measure the enemy's mitigation with one plain 1000-damage hit of the same element, before the talent fires
        double probeBefore = battle.enemies().getFirst().getCurrentHp();
        battle.applyDamage(battle.enemies().getFirst(), new Damage(
                battle.characters().getFirst(), battle.enemies().getFirst(),
                com.laosun.aluminium.enums.DamageElement.QUANTUM,
                com.laosun.aluminium.enums.DamageType.NORMAL, 1000));
        battle.processRequests();
        double mitigation = (probeBefore - battle.enemies().getFirst().getCurrentHp()) / 1000.0;
        enemyBefore = battle.enemies().getFirst().getCurrentHp();
        double herMax = battle.characters().getFirst().getMaxHp();
        // The talent fires when the dragon ARRIVES; `summonMemosprite` uses the spec in memosprites/140.json (servant 1140).
        battle.summonMemosprite(her);
        battle.processRequests();
        return new double[]{enemyBefore - battle.enemies().getFirst().getCurrentHp(),
                battle.characters().get(1).getCurrentHp() - partyBefore,
                herBefore - battle.characters().getFirst().getCurrentHp(), mitigation, herMax};
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
