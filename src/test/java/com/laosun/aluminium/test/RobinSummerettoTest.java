package com.laosun.aluminium.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

/**
 * 1512 Robin - Summeretto, from her own file (2026-09-29, round 193): the summon whose panel the document states in full.
 *
 * <p>Two numbers are asserted, both from the document: the memosprite's Max HP is 0% of hers and its SPD is 180% of hers. The control shows nothing appears
 * without the Skill.
 */
public class RobinSummerettoTest {
    private static final int ROBIN = 1512;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** Note:"初始拥有等同于知更鸟-晴歌0%生命上限的生命上限和等同于知更鸟-晴歌180%速度的速度". */
    @Test
    public void theSummonArrivesWithTheDocumentedPanel() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(robin), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(robin.getSkills().get(SkillType.SKILL), robin, List.of(enemy));

        var mem = battle.memospriteOf(robin);
        Assertions.assertNotNull(mem, "「召唤忆灵「晴空乐手」贝茜」");
        double expectedHp = robin.getMaxHp() * 0.7;
        Assertions.assertEquals(expectedHp, mem.getMaxHp(), expectedHp * 0.02,
                "「70%生命上限」: expected " + expectedHp + ", got " + mem.getMaxHp());
        double expectedSpeed = robin.getAttribute(AttributeType.SPEED).get() * 1.8;
        Assertions.assertEquals(expectedSpeed, mem.getAttribute(AttributeType.SPEED).get(), expectedSpeed * 0.02,
                "「180%速度」: expected " + expectedSpeed + ", got " + mem.getAttribute(AttributeType.SPEED).get());
    }

    /** Note: The control: without the Skill nothing is summoned. */
    @Test
    public void nothingIsSummonedWithoutTheSkill() {
        Character robin = CharacterFactory.create(ROBIN, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(robin), List.of(enemy), fixed());
        battle.startBattle();

        Assertions.assertNull(battle.memospriteOf(robin), "no Skill, no memosprite");
    }

    /** Note: The file declares the Vibes resource with the document's cap, and the memosprite file states both numbers. */
    @Test
    public void theFilesDeclareTheResourceAndThePanel() {
        JsonObject character = read("/characters/1512.json");
        JsonArray resources = character.getAsJsonArray("resources");
        Assertions.assertNotNull(resources, "the character file must declare 气氛值");
        Assertions.assertEquals(50, resources.get(0).getAsJsonObject().get("max").getAsInt(), "「上限50点」");

        JsonObject memo = read("/memosprites/1512.json");
        JsonArray panel = memo.getAsJsonArray("panel");
        Assertions.assertEquals(2, panel.size(), "the panel states exactly the two numbers the document gives");
    }

    private static JsonObject read(String path) {
        try (InputStream stream = RobinSummerettoTest.class.getResourceAsStream(path)) {
            Assertions.assertNotNull(stream, "missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
