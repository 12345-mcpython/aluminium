package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 23052: where the wearer's MEMOSPRITE aims its skill decides what the party gets -- at an ally it marks \u3010\u7a7a\u767d\u3011 and
 * everything the enemies take goes up 10%; at an enemy it marks \u3010\u8bd7\u884c\u3011 and the whole party crits 16% harder.
 */
public class Cone23052Test {
    private static final int CONE = 23052;
    private static final int WEARER = 1402;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String BLANK = "\u7a7a\u767d";
    private static final String POEM = "\u8bd7\u884c";

    private Character wearer;
    private Character ally;
    private Enemy enemy;
    private Battle battle;

    private Battle build(boolean withCone) {
        wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        ally = CharacterFactory.create(ALLY, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, ally), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    private double wearableHit() {
        wearer.getAttribute(AttributeType.CRIT_CHANCE)
                .addModifier(DoubleValue.Modifier.pure(-1.0, DoubleValue.Modifier.ModifierSource.BUFF, 230521));
        return battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1000));
    }

    @Test
    public void aimingAtAnAllyMakesEnemiesTakeMore() {
        build(true);
        double before = wearableHit();
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, sprite, ally, 0, 0);
        int blank = wearer.getBuffManager().stacksOf(BLANK);
        double after = wearableHit();
        System.out.println("[23052] blank=" + blank + " ; damage on the enemy " + before + " -> " + after
                + " (x" + (after / before) + ")");
        Assertions.assertEquals(1, blank, "\u5bf9\u6211\u65b9\u5355\u4f53 gives \u7a7a\u767d");
        Assertions.assertEquals(1.1, after / before, 0.02, "and every enemy takes 10% more");
    }

    @Test
    public void aimingAtAnEnemyRaisesThePartysCritDamage() {
        build(true);
        double before = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, sprite, enemy, 0, 0);
        int poem = wearer.getBuffManager().stacksOf(POEM);
        double after = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        System.out.println("[23052] poem=" + poem + " ; party crit damage " + before + " -> " + after
                + " (allies: " + ally.getAttribute(AttributeType.CRIT_ATTACK).get() + ")");
        Assertions.assertEquals(1, poem, "\u5bf9\u654c\u65b9 gives \u8bd7\u884c");
        Assertions.assertEquals(before + 0.16, after, 1e-9, "and the party crits 16% harder");
        Assertions.assertEquals(after, ally.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9, "the ALLY too");
    }

    @Test
    public void theWearerCastingIsNotTheMemospriteCasting() {
        build(true);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, wearer, ally, 0, 0);
        System.out.println("[23052] after the WEARER casts: blank=" + wearer.getBuffManager().stacksOf(BLANK)
                + " poem=" + wearer.getBuffManager().stacksOf(POEM));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(BLANK), "actor == summon (false case)");
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(POEM), "actor == summon (false case)");
    }

    @Test
    public void withoutTheConeNothingHappens() {
        build(false);
        Summon sprite = battle.summonMemosprite(wearer);
        battle.fireTriggers(TriggerEvent.SKILL_CAST, sprite, ally, 0, 0);
        System.out.println("[23052] without the cone: blank=" + wearer.getBuffManager().stacksOf(BLANK));
        Assertions.assertEquals(0, wearer.getBuffManager().stacksOf(BLANK), "no cone, no mark (false case)");
    }
}
