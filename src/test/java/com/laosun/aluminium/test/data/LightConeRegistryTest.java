package com.laosun.aluminium.test.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * <b>The light-cone registry guard</b>. The mirror of the relic guard.
 *
 * <p>Every light cone whose ability is not authored must be REGISTERED in {@code light_cones/_unmodelled.json} with
 * the capability it is missing. Before this existed, "5 cones are unwritten" lived only in prose -- and the count
 * itself was wrong (10 vs the real 169 rows), because it came from a file that does not carry abilities at all
 * ({@code data/weapons.json} has zero rows with an ability id).
 *
 * <p><b>The teeth:</b> a registered cone must have NO rule file on the classpath. Authoring one therefore fails this
 * test until the registry entry is deleted in the same commit -- which is exactly how the six relic reclaims worked.
 */
public class LightConeRegistryTest {
    private static final String REGISTRY = "/light_cones/_unmodelled.json";

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> registry() {
        try (InputStream in = LightConeRegistryTest.class.getResourceAsStream(REGISTRY)) {
            Assertions.assertNotNull(in, REGISTRY + " is not on the classpath");
            return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, List<Map<String, Object>>>>() { }.getType());
        } catch (Exception e) {
            throw new AssertionError("cannot read " + REGISTRY, e);
        }
    }

    private static boolean hasRuleFile(String id) {
        return LightConeRegistryTest.class.getResourceAsStream("/light_cones/" + id + ".json") != null;
    }

    @Test
    public void everyRegisteredLightConeIsUnwrittenAndSaysWhy() {
        Map<String, List<Map<String, Object>>> reg = registry();
        Assertions.assertEquals(
                new TreeSet<>(java.util.List.of("20023", "21032", "21038")),
                new TreeSet<>(reg.keySet()),
                "the registered set is pinned: a new cone must be added here, a built one removed");
        for (Map.Entry<String, List<Map<String, Object>>> e : reg.entrySet()) {
            String id = e.getKey();
            Assertions.assertFalse(e.getValue().isEmpty(), id + " must carry at least one entry");
            for (Map<String, Object> entry : e.getValue()) {
                Object reason = entry.get("reason");
                Assertions.assertNotNull(reason, id + " must say WHY it is unwritten");
                Assertions.assertTrue(reason.toString().length() > 80,
                        id + " must name the missing capability, and a one-liner is not enough: " + reason);
                Assertions.assertNotNull(entry.get("ability"), id + " must name the ability");
                Assertions.assertTrue(reason.toString().contains("GREPPED"),
                        id + " must record WHICH engine sets were searched before calling a capability missing. "
                                + "This is not ceremony: 21029 was registered three times wrongly (its random-target "
                                + "selector and ADD_DAMAGE both already existed), because the reasons were written "
                                + "from the cone text alone. A reason that does not say what was searched cannot be "
                                + "trusted, and this registry is only worth having if it can be trusted.");
            }
            Assertions.assertFalse(hasRuleFile(id),
                    id + " IS written (a rule file exists), so it must be removed from the registry in the same "
                            + "commit -- that is how every relic reclaim worked");
        }
    }
}
