package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 光锥 23004 以世界之名："当装备者施放战技时，装备者此次攻击的效果命中提高 #2%，攻击力提高 #3%" (2026-10-02).
 *
 * <p>THE SCOPE IS `until: cast_end`, and that is what these readings pin:
 * <ul>
 *   <li>the boost is <b>gone the moment the cast is over</b> -- if the lifetime were longer (a turn, or permanent) the
 *       attributes would stay lifted, which is the mutation this guards;</li>
 *   <li>the shipped file carries the rule on <b>every</b> superimposition rank with that rank's own numbers, read off the
 *       content itself (the same file-reading guard the cone census tests use).</li>
 * </ul>
 *
 * <p>Note: What this judge does NOT measure: the boost's effect <i>during</i> the cast. The spelling it rides on is not new --
 * light cone 20001 ships four `"until": "cast_end"` rules today -- so the during-the-cast half is inherited, and this test
 * pins the half that is this cone's own (the numbers and the expiry).
 */
public class Cone23004CastScopeTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final int CONE = 23004;
    private static final int RANK = 5;
    private static final String RULE = "cone23004_cast_scope_stats";

    /** No residue: both attributes are back to where they started once the cast is over. */
    @Test
    public void theBoostDoesNotOutliveTheCast() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, RANK));
        Battle battle = new Battle(List.of(unit),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double hitBefore = unit.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double atkBefore = unit.getAttribute(AttributeType.ATTACK).get();
        String printed = "[" + CONE + "] before: effectHit=" + hitBefore + " atk=" + atkBefore;

        battle.castImmediate(unit.getSkills().get(SkillType.SKILL), unit, List.of(battle.enemies.getFirst()));
        battle.processRequests();

        double hitAfter = unit.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double atkAfter = unit.getAttribute(AttributeType.ATTACK).get();
        System.out.println(printed + " ; after the cast: effectHit=" + hitAfter + " atk=" + atkAfter);

        Assertions.assertEquals(hitBefore, hitAfter, 1e-9,
                "「此次攻击」的效果命中不能活过这一次施放");
        Assertions.assertEquals(atkBefore, atkAfter, 1e-9,
                "攻击力也一样：`until: cast_end` 到此为止");
    }

    /** The content guard: every rank carries the rule, with that rank's own two numbers and the cast-end lifetime. */
    @Test
    public void everyRankCarriesTheCastScopedRule() throws java.io.IOException {
        String raw = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "resources", "light_cones", "23004.json"),
                java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertTrue(raw.contains(RULE), "the shipped file must carry " + RULE);
        Assertions.assertEquals(5, count(raw, "\"id\": \"" + RULE + "\""),
                "one rule per superimposition rank");
        // Note: TEN, not five: each rank carries TWO effects (effect hit and attack) and both state the lifetime.
        Assertions.assertEquals(10, count(raw, "\"until\": \"cast_end\""),
                "both effects of every rank are scoped to the cast");
        Assertions.assertEquals(5, count(raw, "\"attribute\": \"EFFECT_HIT_RATE\""), "the effect-hit half");
        Assertions.assertEquals(5, count(raw, "\"attribute\": \"ATTACK\""), "and the attack half");
        for (String percent : List.of("0.18", "0.21", "0.24", "0.27", "0.3", "0.28", "0.32", "0.36", "0.4")) {
            Assertions.assertTrue(raw.contains("\"percent\": " + percent),
                    "rank numbers must be the data's own: missing " + percent);
        }
    }

    // ==================================================================

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            found++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
    }
}
