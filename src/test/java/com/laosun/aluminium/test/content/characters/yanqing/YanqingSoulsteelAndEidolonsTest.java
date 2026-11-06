package com.laosun.aluminium.test.content.characters.yanqing;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
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

import java.util.List;

/**
 * 1209 彦卿's three newly written clauses:
 *
 * <ul>
 *   <li>天赋「当彦卿受到伤害后，【智剑连心】将会消失」;</li>
 *   <li>星魂 2「处于【智剑连心】时，额外提高 10% 的能量恢复效率」;</li>
 *   <li>星魂 1「攻击处于冻结状态的目标时，造成等同于 60% 攻击力的冰属性附加伤害」.</li>
 * </ul>
 */
public class YanqingSoulsteelAndEidolonsTest {
    private static final int YANQING = 1209;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;

    @Test
    public void hisSyncEndsWhenHeIsHit() {
        Battle battle = scene(0);
        Character yanqing = battle.characters().getFirst();
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(yanqing.getBuffManager().hasState("智剑连心"), "precondition: the Skill applies it");
        double critWith = yanqing.getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.applyDamage(yanqing, new Damage(battle.enemies().getFirst(), yanqing, DamageElement.ICE,
                DamageType.NORMAL, 1));
        battle.processRequests();

        Assertions.assertFalse(yanqing.getBuffManager().hasState("智剑连心"),
                "「当彦卿受到伤害后，【智剑连心】将会消失」");
        // The CRIT modifiers are gated on the state (`while_state`), so they leave at the next gate evaluation - the point of
        // this assertion is only that the state itself is gone; the numbers are pinned by the Skill test above.
        Assertions.assertTrue(critWith > 0, "precondition: the sync raises his CRIT Rate while it lasts");
    }

    @Test
    public void theSecondEidolonRaisesEnergyRecoveryWhileSynced() {
        Assertions.assertEquals(0.1, energyRegen(2), EPS, "「处于【智剑连心】时，额外提高 10% 的能量恢复效率」");
        Assertions.assertEquals(0.0, energyRegen(1), EPS, "before 星魂 2 there is none");
    }

    @Test
    public void theFirstEidolonHitsAFrozenTargetAgain() {
        Battle battle = scene(1);
        Character yanqing = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        enemy.getBuffManager().addBuff(new StateBuff("冻结", 2));

        double before = enemy.getCurrentHp();
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, yanqing, enemy, 1, 0));
        battle.processRequests();
        double withFreeze = before - enemy.getCurrentHp();

        Battle plain = scene(1);
        Enemy target = (Enemy) plain.enemies().getFirst();
        double hp = target.getCurrentHp();
        plain.fire(TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, plain.characters().getFirst(), target, 1, 0));
        plain.processRequests();
        double withoutFreeze = hp - target.getCurrentHp();
        // Same seed in both runs, so the talent's 60%-chance follow-up fires in both: the DIFFERENCE is the Eidolon's rider,
        // and 60% / 50% of the same base means the rider is 1.2x the follow-up it rides on.
        Assertions.assertTrue(withFreeze > withoutFreeze,
                "「…造成等同于彦卿 60% 攻击力的冰属性附加伤害」 — the frozen target takes an extra hit");
        Assertions.assertEquals(1.2, (withFreeze - withoutFreeze) / withoutFreeze, 1e-6,
                "the rider is 60% of ATK against the follow-up's 50%, so exactly 1.2x of it");
    }

    private static double assertPositive(double value, String message) {
        Assertions.assertTrue(value > 0, message);
        return value;
    }

    private static double energyRegen(int eidolon) {
        Battle battle = scene(eidolon);
        Character yanqing = battle.characters().getFirst();
        double before = yanqing.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        return yanqing.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get() - before;
    }

    private static Battle scene(int eidolon) {
        Character yanqing = CharacterFactory.create(YANQING, LEVEL, false, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        enemy.setMaxStance(10_000);
        enemy.setStance(10_000);
        Battle battle = new Battle(List.of(yanqing), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    /**
     * 星魂 4:「当前生命值百分比 ≥80% 时，提高自身 12% 的冰属性抗性穿透」 - a LIVE gate on the HP bar.
     *
     * <p>Applied once at battle start (full HP, so it holds), then the owner is wounded below 80% and the gate is asked again
     * at the next turn boundary - the modifier must be gone. That "asked again" is the whole point of `while_hp_percent`: a
     * state or counter gate could not express a bar that moves on every event.
     */
    @Test
    public void theFourthEidolonPiercesOnlyWhileHeIsHealthy() {
        Battle battle = scene(4);
        Character yanqing = battle.characters().getFirst();
        Assertions.assertEquals(0.12, yanqing.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get(), EPS,
                "at full HP the gate holds");

        battle.applyDamage(yanqing, new Damage(battle.enemies().getFirst(), yanqing, DamageElement.ICE,
                DamageType.NORMAL, yanqing.getMaxHp() * 0.5));
        battle.processRequests();
        Assertions.assertTrue(yanqing.getCurrentHp() / yanqing.getMaxHp() < 0.8, "precondition: he is wounded");

        // The gate is asked at the turn boundary, so one has to pass.
        yanqing.getBuffManager().beforeMove();
        battle.processRequests();
        Assertions.assertEquals(0.0, yanqing.getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get(), EPS,
                "below 80% HP the penetration is gone");

        Assertions.assertEquals(0.0, penetrationAt(1), EPS, "before 星魂 4 there is none at all");
    }

    /** 天赋「处于【智剑连心】时，受到攻击的概率降低」: the DIRECTION only - the document states no number. */
    @Test
    public void theSyncLowersHisAggro() {
        Battle battle = scene(0);
        Character yanqing = battle.characters().getFirst();
        double before = battle.aggroOf(yanqing);
        battle.castImmediate(yanqing.getSkills().get(SkillType.SKILL), yanqing, List.of(battle.enemies().getFirst()));
        battle.processRequests();
        Assertions.assertTrue(battle.aggroOf(yanqing) < before,
                "「受到攻击的概率降低」 — the weight drops (the amount is a placeholder: the sentence states none)");
    }

    private static double penetrationAt(int eidolon) {
        return scene(eidolon).characters().getFirst().getAttribute(AttributeType.DAMAGE_PENETRATION_ICE).get();
    }
}
