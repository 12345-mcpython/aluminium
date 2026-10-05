package com.laosun.aluminium.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
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
 * 1404 Mydei, from his own file (2026-09-29, round 191): the technique's 80%-Max-HP opening, gated on the technique state.
 *
 * <p>His charge uses the resource vocabulary 1003 established, so the file declares it; the census reads that declaration back.
 */
public class MydeiTest {
    private static final int MYDEI = 1404;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ With the technique declared the enemies are hit; without it, nothing happens at all. */
    @Test
    public void theTechniqueHitsOnlyWhenDeclared() {
        double declared = openingLoss(true);
        double undeclared = openingLoss(false);

        Assertions.assertTrue(declared > 0, "「使用秘技后…对敌方全体造成等同于万敌80%生命上限的虚数属性伤害」: " + declared);
        Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 -- undeclared, so nothing");
    }

    /** ⚠ The number: 80% of his Max HP must be 1.6 of a hand-built 50% in the same pipeline. */
    @Test
    public void theTechniqueDealsEightyPercentOfHisMaxHp() {
        double content = openingLoss(true);
        double reference = referenceLoss();

        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.8 / 0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.8 / 0.5) + ")");
    }

    /** ⚠ The file declares the charge resource with the document's cap. */
    @Test
    public void theFileDeclaresTheChargeResource() {
        try (InputStream stream = MydeiTest.class.getResourceAsStream("/characters/1404.json")) {
            Assertions.assertNotNull(stream, "1404 must have a file");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray resources = root.getAsJsonArray("resources");
            Assertions.assertNotNull(resources, "the file must declare its resource");
            Assertions.assertEquals("天赋充能", resources.get(0).getAsJsonObject().get("id").getAsString());
            Assertions.assertEquals(200, resources.get(0).getAsJsonObject().get("max").getAsInt(),
                    "「最多积攒200点」");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Runs the opening with or without the technique marker. */
    private static double openingLoss(boolean declared) {
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(mydei, ally), List.of(enemy), fixed());
        if (declared) {
            battle.markTechniqueUsed(mydei);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    /** A hand-built 50%-Max-HP instance in the same pipeline, as the ratio's denominator. */
    private static double referenceLoss() {
        Character mydei = CharacterFactory.create(MYDEI, LEVEL);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "owner_max_hp");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "element", "Imaginary");
        TriggerSpecs.set(effect, "target", "all_enemies");
        mydei.setTriggerTable(new TriggerTable(MYDEI, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of("self has_state 秘技"), effect))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(mydei), List.of(enemy), fixed());
        battle.markTechniqueUsed(mydei);
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
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
