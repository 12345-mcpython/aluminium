package com.laosun;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.nio.charset.Charset;
import java.util.List;

/**
 * Application entry point — a demonstration harness for the Aluminium game simulator.
 *
 * <p>This main method exercises the full pipeline:
 * <ol>
 *   <li>Creates individually configured relics via the {@link Relic.Builder}</li>
 *   <li>Assembles a {@link RelicSuit} and computes total relic values</li>
 *   <li>Loads weapon and character data from JSON resources</li>
 *   <li>Builds a fully-equipped {@link Character} with all stat calculations</li>
 *   <li>Prints the character's skill point tree</li>
 *   <li>Simulates a turn-based action {@link Queue} with multiple characters</li>
 * </ol>
 */
public class Main {
    /** Demonstration entry point. */
    static void main() {
        IO.println(Relic.createRandomLevelZero(RelicType.BODY, 5));
        Relic hyaBody = Relic.builder()
                .type(RelicType.BODY)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.OUTGOING_HEALING_BOOST)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 0)
                .subAttribute(AttributeType.SPEED, 1, 1)
                .subAttribute(AttributeType.CRIT_ATTACK, 2, 5)
                .build();

        Relic hyaLine = Relic.builder()
                .type(RelicType.LINE)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ENERGY_REGENERATION_RATE)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 3)
                .subAttribute(AttributeType.SPEED, 1, 4)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 1, 3)
                .subAttribute(AttributeType.BREAKING_EFFECT, 0, 2)
                .build();

        Relic hyaBall = Relic.builder()
                .type(RelicType.BALL)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH_PERCENT)
                .subAttribute(AttributeType.DEFENCE, 0, 2)
                .subAttribute(AttributeType.SPEED, 3, 5)
                .subAttribute(AttributeType.CRIT_CHANCE, 2, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 2)
                .build();

        Relic hyaBoot = Relic.builder()
                .type(RelicType.BOOT)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.SPEED)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 4)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 2)
                .subAttribute(AttributeType.CRIT_ATTACK, 1, 1)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHand = Relic.builder()
                .type(RelicType.HAND)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ATTACK)
                .subAttribute(AttributeType.DEFENCE, 0, 1)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.SPEED, 3, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHead = Relic.builder()
                .type(RelicType.HEAD)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 1)
                .subAttribute(AttributeType.SPEED, 3, 7)
                .subAttribute(AttributeType.CRIT_CHANCE, 0, 1)
                .subAttribute(AttributeType.EFFECT_HIT_RATE, 1, 2)
                .build();
        IO.println(hyaBody);
        IO.println(hyaLine);
        IO.println(hyaBall);
        IO.println(hyaBoot);
        IO.println(hyaHand);
        IO.println(hyaHead);
        RelicSuit hya = new RelicSuit();
        hya.addMore(hyaBody, hyaLine, hyaBall, hyaBoot, hyaHand, hyaHead);
        Object2DoubleOpenHashMap<AttributeType> relicValue = new Object2DoubleOpenHashMap<>();
        hya.calcTotalValue(relicValue);
        IO.println(relicValue);
        IO.println(Constant.WEAPONS.get(23042).name().english());
        IO.println(Constant.CHARACTERS.get(1409).name().english());

        IO.println(LevelPromotionCalc.calcCharacterRate(80));
        IO.println(LevelPromotionCalc.calcWeaponRate(80));
        IO.println(Constant.CHARACTERS.get(1409).health());
        IO.println(Constant.WEAPONS.get(23042).health());
        IO.println(Constant.WEAPONS.get(23042).weaponSkillData().getFirst().abilityProperties());
        Weapon wp = Weapon.build(23042, 80);
        Character character = Character.builder()
                .cid(1409)
                .level(80)
                .relicSuit(hya)
                .weapon(wp)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 0, 0, 0, 0, 0.12))
                .build();
        IO.println(character.getAttribute(AttributeType.HEALTH));
        IO.println(character.getAttribute(AttributeType.DEFENCE));
        IO.println(character.getAttribute(AttributeType.SPEED));
        DoubleValue dp = character.getAttribute(AttributeType.HEALTH).clone();
        dp.addModifier(DoubleValue.Modifier.addPercent(0.20));
        IO.println(dp);
        IO.println();
        for (DoubleValue db : character.getAttributes()) {
            IO.println(db);
        }
        SkillPoint.printTree(SkillPoint.init(1409));
        IO.println(SkillPoint.sumAttributes(SkillPoint.init(1409)));

        Character c1 = Character.builder().cid(1001).build();
        Character c2 = Character.builder().cid(1002).build();
        Character c3 = Character.builder().cid(1003).build();
        Character c4 = Character.builder().cid(1004).build();

        IO.println(c1);
        IO.println(c2);
        IO.println(c3);
        IO.println(c4);

        Queue q = new Queue(List.of(c1, c2, c3, c4));
        q.initialize();
        q.printActionQueue();
        q.move();
        q.printActionQueue();
        q.setTopZero();
        q.printActionQueue();
        q.move();
        q.printActionQueue();

        IO.println(Charset.defaultCharset().displayName());
    }
}
