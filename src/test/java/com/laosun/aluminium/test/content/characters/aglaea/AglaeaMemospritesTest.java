package com.laosun.aluminium.test.content.characters.aglaea;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 1402 阿格莱雅's two memosprite passives that the engine CAN answer for, because it emits the events:
 * 「衣匠被召唤时，使自身行动提前 100%」 (忆灵技能 3) and 「衣匠消失时，使阿格莱雅恢复 20 点能量」 (忆灵技能 4).
 *
 * <p>They live in HER file because a trigger table belongs to a character; the executor is the Garmentmaker (衣匠), which
 * is the same split {@code memosprite_ode_of_romance_*} already uses on this file.
 */
public class AglaeaMemospritesTest {
    private static final int AGLAEA = 1402;
    private static final int LEVEL = 80;

    /**
     * The memosprite's <b>ATTACK</b> is hers: tbgd's servant config says {@code "SyncPropertyExceptList"} carries only the
     * Speed entries, so every other property - attack included - is synced from the summoner. Measured 2026-11-04, and it
     * is what 「衣匠造成相当于其自身 200% 攻击力的伤害」 needs in order to read a number at all.
     */
    @Test
    public void theMemoSpriteFightsWithHerAttack() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();

        double hers = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        Assertions.assertEquals(hers, garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get(), 1e-9,
                "the servant syncs every property except speed, so its ATTACK is the summoner's");
        Assertions.assertEquals(hers,
                garmentmaker.damageBase(com.laosun.aluminium.enums.AttributeType.ATTACK), 1e-9,
                "and `damageBase` reads the same number the damage path would use");
    }

    /**
     * 「行动提前 100%」 is observable as a position: right after it is summoned, the Garmentmaker (衣匠) is what the
     * action order would play next. Without the advance a 35%-of-her-SPD memosprite would be behind her.
     */
    @Test
    public void theSpeedingSummerPutsItAtTheFront() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();

        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();

        Assertions.assertSame(garmentmaker, battle.queue().peekNext(),
                "「衣匠被召唤时，使自身行动提前 100%」 — it is next in the action order");
        Assertions.assertNotSame(aglaea, battle.queue().peekNext(), "and it is the memosprite, not her");
    }

    /**
     * 忆灵技能 1「刺纹之陷」: 「对敌方单体造成等同于「衣匠」110% 攻击力的雷属性伤害，对其相邻目标造成 66% 攻击力的雷属性伤害」.
     *
     * <p>The two shares are the whole clause - one multiplier would over-hit every neighbour - so the test compares the
     * aimed enemy's loss against a neighbour's on identical monsters, where the ratio is exactly 110/66 whatever the
     * defence and crit zones do to both sides. The spec states the Lv10 row (1.54 / 0.924), which is the same ratio.
     */
    @Test
    public void theMemospriteBlastHitsTheNeighboursForLess() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy left = BattleFixture.monster();
        Enemy aimed = BattleFixture.monster();
        Enemy right = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(left, aimed, right), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();

        double leftBefore = left.getCurrentHp();
        double aimedBefore = aimed.getCurrentHp();
        double rightBefore = right.getCurrentHp();
        var attack = garmentmaker.getSkills().get(com.laosun.aluminium.enums.SkillType.COMMON);
        Assertions.assertNotNull(attack, "the spec states an attack (忆灵技能 1)");
        attack.execute(battle, garmentmaker, List.of(aimed));
        battle.processRequests();

        double aimedLost = aimedBefore - aimed.getCurrentHp();
        double leftLost = leftBefore - left.getCurrentHp();
        double rightLost = rightBefore - right.getCurrentHp();
        Assertions.assertTrue(aimedLost > 0, "the aimed enemy was hit: " + aimedLost);
        Assertions.assertEquals(1.1 / 0.66, aimedLost / leftLost, 1e-6,
                "「单体 110%、相邻 66%」 — the aimed enemy takes exactly the stated ratio more: "
                        + aimedLost + " vs " + leftLost);
        Assertions.assertEquals(leftLost, rightLost, 1e-9, "both neighbours took the same share");
    }

    /**
     * 「衣匠消失时，使阿格莱雅恢复 20 点能量」: the energy is paid by the <b>SUMMON_LEFT</b> event, i.e. when the
     * memosprite is gone - which is why the rule cannot look the memosprite up at that moment.
     */
    @Test
    public void theBloomPaysTwentyWhenItLeaves() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        // A teammate delivers the killing blow: a kill credits KILL energy to the ATTACKER, so letting HER do it would
        // mix 5 unexplained points into the reading. Measured 2026-11-04: hers read 25, a teammate's leaves exactly 20.
        Character ally = CharacterFactory.create(1001, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea, ally), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double before = aglaea.getCurrentEnergy();

        battle.applyDamage(garmentmaker,
                new Damage(ally, garmentmaker, DamageElement.THUNDER, DamageType.NORMAL, 1_000_000));
        battle.processRequests();

        Assertions.assertTrue(garmentmaker.isDeath(), "precondition: the memosprite is gone");
        Assertions.assertEquals(20.0, aglaea.getCurrentEnergy() - before, 1e-9,
                "「衣匠消失时，使阿格莱雅恢复 20 点能量」");
    }

    /**
     * 忆灵技能 2's stacking half:「攻击处于【间隙织线】状态的敌人后，使自身的速度提高 55 点，最多叠加 6 层」.
     *
     * <p>The clause is one modifier on the MEMOSPRITE reading its own skill row's Lv10 cell (63.8; the prose quotes the
     * Lv6 55). Two controls: a hit on an enemy that is not threaded adds nothing (the condition is the whole trigger),
     * and a seventh attack at the cap adds nothing.
     */
    @Test
    public void theMemospriteStacksSpeedOnlyOnAThreadedHit() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        Summon garmentmaker = battle.summonMemosprite(aglaea);
        battle.processRequests();
        double base = garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        fireSummonAttack(battle, garmentmaker, enemy);
        Assertions.assertEquals(base, garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-9,
                "「攻击处于【间隙织线】状态的敌人后」 — an unthreaded target adds no stack");

        for (int hit = 0; hit < 6; hit++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("间隙织线", 5));
            fireSummonAttack(battle, garmentmaker, enemy);
        }
        double capped = garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();
        Assertions.assertEquals(base + 6 * 63.8, capped, 1e-6,
                "six stacked layers of the Lv10 cell (63.8): " + base + " -> " + capped);

        fireSummonAttack(battle, garmentmaker, enemy);
        Assertions.assertEquals(capped, garmentmaker.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-9,
                "「该效果最多叠加 6 层」");
    }

    private static void fireSummonAttack(Battle battle, Summon summon, Enemy victim) {
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.SUMMON_ATTACK, summon, victim, 1, 0));
        battle.processRequests();
    }

    /**
     * 【至高之姿】:「获得衣匠忆灵天赋的速度提高层数，每层使自身速度提高 15%」 - and the layers keep arriving while the stance
     * holds, so the share must be read **live**: the same buff, no re-cast, and her SPD moves when the memosprite stacks.
     */
    @Test
    public void theStanceSpeedFollowsTheLayersLive() {
        Character aglaea = CharacterFactory.create(AGLAEA, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(aglaea), List.of(enemy), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double base = aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get();

        battle.castImmediate(aglaea.getSkills().get(com.laosun.aluminium.enums.SkillType.ULTRA),
                aglaea, List.of(enemy));
        battle.processRequests();
        Summon garmentmaker = battle.summonOf(aglaea);
        Assertions.assertNotNull(garmentmaker, "precondition: the stance brought the memosprite out");
        Assertions.assertEquals(base, aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-9,
                "no 【匠躯】 layers yet, so no speed");

        for (int layer = 1; layer <= 3; layer++) {
            enemy.getBuffManager().addBuff(new com.laosun.aluminium.buff.StateBuff("间隙织线", 5));
            fireSummonAttack(battle, garmentmaker, enemy);
            Assertions.assertEquals(base * (1 + 0.15 * layer),
                    aglaea.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get(), 1e-6,
                    "layer " + layer + " raises her speed without re-casting the Ultimate");
        }
    }
}
