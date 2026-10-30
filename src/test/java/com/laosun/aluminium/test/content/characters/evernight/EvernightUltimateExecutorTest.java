package com.laosun.aluminium.test.content.characters.evernight;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1413 长夜月's ultimate deals its damage <b>through her memosprite</b> 「长夜」.
 *
 * <p><b>Why this is a case and not a detail.</b> Her ultimate's text says 「使忆灵「长夜」对敌方全体造成等同于「长夜」200%生命上限的
 * 冰属性伤害」, and the game's own ability agrees: {@code Avatar_Evernight_00_Skill03_Servant_Damage} states
 * {@code AttackProperty.AttackType = "Ultra"} (so the ROW is the ultimate's) with
 * {@code OnStart[0].TargetType = Caster.GetSummoner} (so the HIT belongs to the servant). The recorded fight stamps those
 * instances with the servant as attacker — measured: {@code attacker_kind = Servant} while {@code type = Ultimate}.
 *
 * <p>Attribution is not cosmetic: 「长夜造成的伤害提高 60%」 (【至暗之谜】) and the 24% zone rider both key off the attacker.
 */
public class EvernightUltimateExecutorTest {

    private static final int EVERNIGHT = 1413;

    private static Enemy dummy() {
        Enemy enemy = Enemy.fromAttributes("target", 900000, 200, 100, 100);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        enemy.heal(900000);
        return enemy;
    }

    private static Random noCrit() {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return 0.99;
            }
        };
    }

    /**
     * The ultimate's instances are attributed to the memosprite, and the row is still the ultimate's.
     */
    @Test
    public void theUltimatesDamageIsDealtByTheMemosprite() {
        Character evernight = CharacterFactory.create(EVERNIGHT, 80);
        Battle battle = new Battle(List.of(evernight), new ArrayList<>(List.of(dummy())), noCrit());
        battle.startBattle();
        Summon evey = battle.summonMemosprite(evernight);
        Assertions.assertNotNull(evey, "precondition: 「长夜」 is on the field");

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        SkillExecutor.execute(battle, evernight.getSkills().get(SkillType.ULTRA), evernight,
                List.of(battle.enemyUnits().get(0)));

        List<Damage> hits = settled.stream().filter(d -> d.getType() == com.laosun.aluminium.enums.DamageType.NORMAL)
                .toList();
        Assertions.assertFalse(hits.isEmpty(), "the ultimate settled something");
        Assertions.assertTrue(hits.stream().allMatch(d -> d.getAttacker() == evey),
                "every instance is credited to 「长夜」, not to 长夜月 herself");
        Assertions.assertFalse(hits.stream().anyMatch(d -> d.getAttacker() == evernight),
                "and none of them to her");
    }
}
