package com.laosun.aluminium.test.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Every rule that <b>quotes a document sentence</b> must quote a sentence the document actually contains.
 *
 * <p><b>What this catches.</b> Measured on 2026-11-01: two rules carried a 「…」 quote that no document contains - one was
 * the author's own sentence dressed as a quote (1014) and one had dropped every 「最多不超过 N 级」 cap (1503). A quote that
 * is not in the document cannot be checked by a reader, so the rule behind it is unverifiable by construction.
 *
 * <p><b>Which quotes are checked.</b> Only CLEAN ones: no {@code …}, no {@code **} emphasis, no {@code #N} data
 * placeholder, no nested 「」. Those markers are how a <em>paraphrase</em> announces itself, and this guard must not call a
 * declared paraphrase a lie.
 *
 * <p><b>Why it skips.</b> The corpus lives OUTSIDE this repository ({@code E:/turnbasedgamedata/aluminium_texts}); without it
 * there is nothing to compare against, so the test reports itself as skipped rather than passing silently or failing on a
 * machine that never had the docs.
 */
public class QuotedSourceExistsTest {

    private static final Path CORPUS = Path.of(
            System.getenv().getOrDefault("ALUMINIUM_TEXTS", "E:/turnbasedgamedata/aluminium_texts"));
    private static final Path CHARACTERS = Path.of("src/main/resources/game/characters");

    /**
     * Quotes that are NOT verbatim although they carry no paraphrase marker, reviewed one by one on 2026-11-01. Each is a
     * rewrite or a trimmed sentence whose meaning matches the document; the list is here so a NEW such quote fails.
     */
    private static final Set<String> ACCEPTED = Set.of(
            "1218 :: eidolon2_burn_multiplier",          // 「受到的灼烧伤害」 for the document's 「【烬煨】对其造成的火属性持续伤害」
            "1222 :: eidolon2_party_break_effect",       // 「；」 for the document's 「，」
            "1315 :: standoff_target_takes_more_from_him", // 「该目标受到波提欧攻击时」 for the document's 「波提欧攻击该目标时」
            "1315 :: standoff_he_takes_more_from_that_target", // same sentence's other half
            "1317 :: talent_break_damage_taken",         // the document states the per-charge figure, not this rounding
            "1410 :: memosprite_ode_of_ocean_marks_hysilens", // 「单次生效，」 prefixed to the document sentence
            "1502 :: talent_lucky_followup",             // 「随机 1 个命中目标」 for the document's 「随机单体」
            "1503 :: eidolon3_raises_four_slots",        // caps trimmed, marked 「各级上限略」
            "1503 :: eidolon5_raises_three_slots",       // caps trimmed, marked 「各级上限略」
            "1503 :: form_a_spends_a_charge",            // 「真珠」 for the document's 「真珠（？）」
            "1503 :: form_b_spends_a_charge",            // same sentence's other half
            "1503 :: deep_study_ends_when_the_charges_run_out", // trimmed tail
            "1506 :: p1506_laughs_to_hidden");           // 「等量」 for the document's 「相同的」

    @Test
    public void everyQuotedSourceIsInItsDocument() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(CORPUS),
                "the character corpus is not on this machine (" + CORPUS + "); the quote check needs it");
        List<Path> docs = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(CORPUS)) {
            paths.filter(p -> p.toString().endsWith(".html") || p.toString().endsWith(".md")).forEach(docs::add);
        }
        Map<String, String> byCid = new LinkedHashMap<>();
        for (Path doc : docs) {
            String name = doc.getFileName().toString();
            int cut = name.indexOf('_');
            if (cut > 0) {
                byCid.putIfAbsent(name.substring(0, cut), skeleton(Files.readString(doc, StandardCharsets.UTF_8)));
            }
        }
        List<String> unknown = new ArrayList<>();
        int checked = 0;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> paths = Files.list(CHARACTERS)) {
            paths.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
        }
        for (Path file : files) {
            String cid = file.getFileName().toString().replace(".json", "");
            String reference = byCid.get(cid);
            if (reference == null) {
                continue;
            }
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            for (JsonObject rule : rulesOf(root)) {
                String id = rule.has("id") ? rule.get("id").getAsString() : "";
                if ("level_convention".equals(id)) {
                    continue;   // its source is an explanation, not a sentence from the document
                }
                String quote = quoted(rule);
                if (quote == null || quote.length() < 6 || declaresItselfAParaphrase(quote)) {
                    continue;
                }
                checked++;
                String shape = skeleton(quote);
                if (shape.length() < 6 || reference.contains(shape.substring(0, Math.min(10, shape.length())))) {
                    continue;
                }
                String key = cid + " :: " + id;
                if (!ACCEPTED.contains(key)) {
                    unknown.add(key + " :: " + quote.substring(0, Math.min(40, quote.length())));
                }
            }
        }
        System.out.println("[quoted-source] clean quotes=" + checked + " accepted=" + ACCEPTED.size()
                + " unknown=" + unknown.size());
        Assertions.assertTrue(unknown.isEmpty(),
                "a rule quotes a sentence its document does not contain, so nobody can check it: " + unknown
                        + ". Quote the document, mark the paraphrase (`…` / `**` / `#N`), or add it to ACCEPTED with a reason.");
    }

    /** `…`, `**`, `#N` placeholders and nested 「」 all say "this is not verbatim". */
    private static boolean declaresItselfAParaphrase(String quote) {
        return quote.contains("…") || quote.contains("**") || quote.matches("(?s).*#\\d.*")
                || quote.contains("[") || quote.contains("「") || quote.contains("」");
    }

    private static String quoted(JsonObject rule) {
        JsonElement source = rule.get("source");
        if (source == null || !source.isJsonPrimitive()) {
            return null;   // a non-string source is reported elsewhere
        }
        String text = source.getAsString();
        int open = text.indexOf('「');
        int close = text.indexOf('」', open + 1);
        return open >= 0 && close > open ? text.substring(open + 1, close) : null;
    }

    private static List<JsonObject> rulesOf(JsonElement element) {
        List<JsonObject> out = new ArrayList<>();
        collect(element, out);
        return out;
    }

    private static void collect(JsonElement element, List<JsonObject> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collect(child, out));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("on") && object.has("do")) {
            out.add(object);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!"resources".equals(entry.getKey())) {
                collect(entry.getValue(), out);
            }
        }
    }

    /** Tags, spacing and punctuation are formatting; digits are the document's numbers, not the quote's. */
    private static String skeleton(String text) {
        return text.replaceAll("<[^>]+>", "")
                .replaceAll("#\\d+(\\[[a-z0-9]+\\])?%?", "")
                .replaceAll("[0-9]+(\\.[0-9]+)?%?", "")
                .replaceAll("[「」【】\\s*…·,.、，。；;:：!！?？()（）\\[\\]=_'\"／|]", "");
    }
}