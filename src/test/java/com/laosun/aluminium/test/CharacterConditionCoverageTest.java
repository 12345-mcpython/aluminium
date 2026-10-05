package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every rule of the characters shipped in this arc states the conditions its file states (2026-09-30).
 *
 * <p>A condition sweep (negating {@code actor == self}, swapping {@code from_category}, raising a
 * {@code self_resource} threshold) stayed green for eleven of these, so the character judges assert effects but not
 * {@code when}. This one walks the FILE and checks what the TABLE ended up with -- the two must agree.
 */
public class CharacterConditionCoverageTest {
    private static final int[] CHARACTERS = {1505, 1502, 1506};
    private static final int LEVEL = 80;

    /**
     * Note: The parser normalises two things we measured: attribute names are LOWERED
     * ({@code self_attr:breaking_effect}) and integer literals gain a decimal point ({@code >= 1.0}). So the file's
     * text is normalised the same way before the comparison -- a different threshold still fails.
     */
    private static String normalise(String text) {
        Matcher matcher = Pattern.compile("([<>]=?\\s*)(\\d+)$").matcher(text);
        return matcher.find() ? matcher.replaceFirst("$1$2.0") : text;
    }

    @Test
    public void everyShippedRuleKeepsTheConditionsItsFileStates() {
        for (int cid : CHARACTERS) {
            Character c = CharacterFactory.create(cid, LEVEL);
            JsonObject file = new Gson().fromJson(new InputStreamReader(
                    CharacterConditionCoverageTest.class.getResourceAsStream("/characters/" + cid + ".json"),
                    StandardCharsets.UTF_8), JsonObject.class);
            var rules = file.getAsJsonArray("rules");
            int checked = 0;
            for (var element : rules) {
                JsonObject rule = element.getAsJsonObject();
                List<String> expected = new ArrayList<>();
                if (rule.has("when")) {
                    for (var condition : rule.getAsJsonArray("when")) {
                        expected.add(normalise(condition.getAsString()));
                    }
                }
                String id = rule.get("id").getAsString();
                String event = rule.get("on").getAsString();
                var compiled = c.getTriggerTable().rulesFor(TriggerEvent.valueOf(event)).stream()
                        .filter(r -> r.id().equals(id)).toList();
                Assertions.assertEquals(1, compiled.size(), cid + " " + id + " on " + event);
                var actual = compiled.getFirst().conditions().stream().map(x -> x.source()).toList();
                System.out.println("[char-conditions] " + cid + " " + id + " -> " + actual);
                Assertions.assertEquals(expected, actual, cid + " " + id);
                checked++;
            }
            Assertions.assertTrue(checked > 0, "the file has rules");
            System.out.println("[char-conditions] " + cid + " checked " + checked + " rules");
        }
    }
}
