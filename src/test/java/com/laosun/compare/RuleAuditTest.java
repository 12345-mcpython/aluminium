package com.laosun.compare;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * The rule block a capture attaches to rule-delivered damage, and the one check that can be made from the payload alone.
 *
 * <p><b>Why.</b> Those instances state no {@code damage_percentage}, so a replay had nothing to compare and reported them
 * {@code n/a}. The richer payload says how each was built, in three shapes: {@code base = 累计治疗 x params[0]}
 * ({@code HEAL_TALLY}), {@code base = attacker_attack x damage_ratio} ({@code ATTACK}) and neither ({@code RULE}). For the
 * middle one the payload holds both numbers, so its own consistency is checkable - which is what this pins, together with the
 * other two shapes being recognised rather than mistaken for a failure.
 */
public class RuleAuditTest {

    private static JsonObject event(String shareOf, double base, double attack, Double ratio, Double rate) {
        String rule = "{\"share_of\":" + (shareOf == null ? "null" : "\"" + shareOf + "\"")
                + ",\"base\":" + base
                + (ratio == null ? "" : ",\"damage_ratio\":" + ratio)
                + (rate == null ? "" : ",\"params\":[" + rate + ",0.5]")
                + "}";
        return JsonParser.parseString("{\"cast_index\":7,\"type\":\"Additional\",\"rule\":" + rule
                + ",\"zones\":{\"damage_value\":" + base + ",\"attacker_attack\":" + attack + "}}").getAsJsonObject();
    }

    private static JsonObject capture(JsonObject... events) {
        StringBuilder body = new StringBuilder("{\"damage_events\":[");
        for (int i = 0; i < events.length; i++) {
            body.append(i == 0 ? "" : ",").append(events[i]);
        }
        return JsonParser.parseString(body.append("]}").toString()).getAsJsonObject();
    }

    /**
     * An ATTACK row's ratio is its own {@code base / attacker_attack} - so the payload can be checked against itself.
     */
    @Test
    public void anAttackRowsRatioIsItsOwnBaseOverTheAttackPanel() {
        RuleAudit.Report ok = RuleAudit.of(capture(event("ATTACK", 1416.0194, 1325.45, 1416.0194 / 1325.45, null)));
        Assertions.assertEquals(1, ok.checked(), "the row had enough numbers");
        Assertions.assertTrue(ok.problems().isEmpty(), "and they agree: " + ok.problems());

        RuleAudit.Report wrong = RuleAudit.of(capture(event("ATTACK", 1416.0194, 1325.45, 1.5, null)));
        Assertions.assertEquals(1, wrong.problems().size(),
                "a stated ratio that does not match base / attacker_attack is a disagreement");
    }

    /**
     * A HEAL_TALLY row implies its counter, which is the number a replay has to hold to reproduce the base.
     */
    @Test
    public void aHealTallyRowImpliesItsCounter() {
        RuleAudit.Report report = RuleAudit.of(capture(event("HEAL_TALLY", 10334.0262, 0, null, 0.22)));
        Assertions.assertTrue(report.problems().isEmpty(), "22% of a finite counter is a fine statement");
        Assertions.assertEquals(46972.85, report.impliedCounters().get(7), 0.01,
                "10334.0262 / 0.22 - the tally the cast read");
    }

    /**
     * A RULE row states neither shape, and that is a fact about the payload - not a failure.
     */
    @Test
    public void aRuleRowIsRecognisedAndNotFailed() {
        RuleAudit.Report report = RuleAudit.of(capture(event("RULE", 1416.0194, 1325.45, null, null)));
        Assertions.assertEquals(1, report.byShape().get("RULE"));
        Assertions.assertEquals(0, report.checked(), "nothing in it can be checked against itself");
        Assertions.assertTrue(report.problems().isEmpty());
    }

    /**
     * With {@code parent_event}, each derived instance's rate against its parent is measurable - and a rule's rate is a
     * constant, so two values for one rule means the parent or the state changed.
     */
    @Test
    public void aDerivedInstancesRateAgainstItsParentIsMeasurable() throws IOException {
        java.util.Map<String, java.util.TreeSet<Double>> rates =
                RuleAudit.parentRates(RuleAudit.fromResource("/captures/veritas-2026-10-30d.json"));

        Assertions.assertFalse(rates.isEmpty(), "the capture states parent_event, so the rates exist");
        Assertions.assertTrue(rates.containsKey("True|追忆，蹁跹，如雨"),
                "and are grouped by the rule that produced the rider: " + rates.keySet());
        Assertions.assertTrue(rates.get("True|看，希望的起始！").contains(0.24),
                "1415's zone pays 24% of the parent hit: " + rates.get("True|看，希望的起始！"));
        Assertions.assertTrue(rates.get("True|叽叽啾啾四重奏").contains(0.42),
                "and another state pays 42% - the two are distinct facts, not one drifting rate: "
                        + rates.get("True|叽叽啾啾四重奏"));
    }

    /**
     * The captures the project holds do not all state a rule block; the audit says so instead of failing.
     */
    @Test
    public void aCaptureWithNoRuleBlocksIsReportedAsSuch() throws IOException {
        RuleAudit.Report older = RuleAudit.of(RuleAudit.fromResource("/captures/veritas-2026-10-30.json"));
        Assertions.assertTrue(older.byShape().isEmpty(), "the older payload states no rule block");

        RuleAudit.Report richer = RuleAudit.of(RuleAudit.fromResource("/captures/veritas-2026-10-30b.json"));
        Assertions.assertFalse(richer.byShape().isEmpty(), "the richer one does");
        Assertions.assertTrue(richer.problems().isEmpty(),
                "and its own numbers agree: " + richer.problems());
    }
}
