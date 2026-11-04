package com.laosun.aluminium.test.content.characters.aglaea;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1402 阿格莱雅 clause coverage: every section of {@code 1402_阿格莱雅.md} is a rule, an engine/data fact, or a
 * <b>registered gap</b> — and every rule in the file answers for a section.
 *
 * <p><b>Why this file exists now.</b> The {@code _gaps} list held a single line about <i>another</i> character's
 * memosprite skill (「1415 忆灵技 12 其余四部分」), and that one line was both misfiled (the 浪漫 poem's five halves are all
 * written here, in {@code memosprite_ode_of_romance_*}) and misleading (秘技, 强化普攻, the four memosprite skills, the
 * three traces and four of the six Eidolons had <b>no rule at all</b>). A gap list that short cannot be trusted until
 * every section is named, so every section is named below; the map is the audit.
 *
 * <p>The two directions matter: a section with neither a rule nor a gap is a silent hole (this test fails), and a rule
 * none of whose sections is named is a rule nobody can check against the document (also fails).
 */
public class AglaeaClauseCoverageTest {

    private static final Path FILE = Path.of("src", "main", "resources", "game", "characters", "1402.json");
    private static final String GAP = "gap:";
    private static final String RULE = "rule:";
    private static final String DATA = "data:";

    /** Section title (as the document writes it) → what answers for it. */
    private static final Map<String, List<String>> SECTION = new LinkedHashMap<>();

    /**
     * The one rule that is not a clause: it states which row of the skill tables this file's numbers come from (M-32),
     * so it answers for the whole file rather than for a sentence.
     */
    private static final Set<String> NOT_A_CLAUSE = Set.of("level_convention");

    static {
        SECTION.put("普攻：刺纹之蜜", List.of(DATA + " the skill row 140201 deals it; no rule needed"));
        SECTION.put("战技：高举吧，升华的名讳", List.of(RULE + "skill_effect", RULE + "skill_effect_2"));
        SECTION.put("终结技：共舞吧，命定的衣匠", List.of(RULE + "ult_effect", GAP + "【至高之姿】的「状态里做什么」"));
        SECTION.put("天赋：金玫之指", List.of(RULE + "talent_fissure_thread_applies_state",
                RULE + "talent_fissure_thread_bonus", DATA + " the memosprite panel is MemospriteSpec 11402"));
        SECTION.put("地图攻击：攻击", List.of(DATA + " the map attack happens outside battle"));
        SECTION.put("秘技：披星百裂", List.of(RULE + "technique_meteoric_sunder"));
        SECTION.put("强化普攻：孤锋千吻", List.of(GAP + "强化普攻 孤锋千吻 未接上"));
        SECTION.put("强化战技：高举吧，升华的名讳", List.of(RULE + "skill_effect"));
        SECTION.put("忆灵：衣匠（面板）", List.of(DATA + " MemospriteSpec 11402"));
        SECTION.put("忆灵技能 1：刺纹之陷", List.of(GAP + "四个忆灵技能全部未写"));
        SECTION.put("忆灵技能 2：泪水锻造的匠躯", List.of(GAP + "四个忆灵技能全部未写"));
        SECTION.put("忆灵技能 3：飞驰之夏", List.of(GAP + "四个忆灵技能全部未写"));
        SECTION.put("忆灵技能 4：枯草之盈", List.of(GAP + "四个忆灵技能全部未写"));
        SECTION.put("行迹：短视之惩", List.of(GAP + "三条行迹未写"));
        SECTION.put("行迹：织运之竭", List.of(GAP + "三条行迹未写"));
        SECTION.put("行迹：飞驰之阳", List.of(GAP + "三条行迹未写"));
        SECTION.put("星魂 1：飘曳金星的行列", List.of(RULE + "eidolon1_threaded_target_takes_more",
                RULE + "eidolon1_threaded_attack_pays_energy",
                RULE + "eidolon1_threaded_attack_pays_energy_for_the_garmentmaker"));
        SECTION.put("星魂 2：行舟命运的眼睑", List.of(GAP + "星魂 2 / 4 / 6 未写"));
        SECTION.put("星魂 3：华珠凝滞的礼物", List.of(RULE + "eidolon3_levels", GAP + "另：星魂 3/5 的**忆灵**等级加成"));
        SECTION.put("星魂 4：大理石内的闪烁", List.of(GAP + "星魂 2 / 4 / 6 未写"));
        SECTION.put("星魂 5：漆黑苦难的织者", List.of(RULE + "eidolon5_levels", GAP + "另：星魂 3/5 的**忆灵**等级加成"));
        SECTION.put("星魂 6：盈虚无常的金线", List.of(GAP + "星魂 2 / 4 / 6 未写"));
        // Not a section of HER document, but five rules of this file answer for it: 1415's poem is written on the
        // beneficiary's table, because the buff lands on Aglaea and the Garmentmaker.
        SECTION.put("1415 忆灵技 12（写在本文件的跨角色规则）", List.of(
                RULE + "memosprite_ode_of_romance_marks_aglaea",
                RULE + "memosprite_ode_of_romance_spends_itself_for_energy",
                RULE + "memosprite_ode_of_romance_raises_damage_and_pierces_defence",
                RULE + "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
                RULE + "memosprite_ode_of_romance_ends_with_her_stance",
                RULE + "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker"));
    }

    @Test
    public void everySectionIsARuleOrARegisteredGap() throws IOException {
        Set<String> ruleIds = ruleIds();
        String gaps = gapsJoined();
        for (Map.Entry<String, List<String>> entry : SECTION.entrySet()) {
            for (String answer : entry.getValue()) {
                if (answer.startsWith(RULE)) {
                    String id = answer.substring(RULE.length());
                    Assertions.assertTrue(ruleIds.contains(id), entry.getKey() + " names a rule the file does not have: " + id);
                } else if (answer.startsWith(GAP)) {
                    String marker = answer.substring(GAP.length());
                    Assertions.assertTrue(gaps.contains(marker),
                            entry.getKey() + " is claimed as a registered gap, but no _gaps entry says: " + marker);
                }
            }
        }
    }

    @Test
    public void everyRuleAnswersForASection() throws IOException {
        Set<String> named = new LinkedHashSet<>();
        for (List<String> answers : SECTION.values()) {
            for (String answer : answers) {
                if (answer.startsWith(RULE)) {
                    named.add(answer.substring(RULE.length()));
                }
            }
        }
        List<String> orphans = new ArrayList<>();
        for (String id : ruleIds()) {
            if (!named.contains(id) && !NOT_A_CLAUSE.contains(id)) {
                orphans.add(id);
            }
        }
        Assertions.assertTrue(orphans.isEmpty(), "rules that answer for no named section: " + orphans);
    }

    /**
     * The gap list must stay an honest list of <b>open clauses</b>: the file is partial, every entry says what is
     * missing (a ⛔) or records what landed (a ⭐), and none of them points at another file instead of naming the
     * mechanism.
     */
    @Test
    public void theGapListNamesItsMechanismsInsteadOfPointingElsewhere() throws IOException {
        JsonObject root = root();
        Assertions.assertEquals("partial", root.get("_status").getAsString(),
                "two thirds of the document have no rule, so this file is not complete");
        JsonArray gaps = root.getAsJsonArray("_gaps");
        Assertions.assertTrue(gaps.size() > 0, "a partial file states at least one gap");
        for (var element : gaps) {
            String gap = element.getAsString();
            Assertions.assertTrue(gap.startsWith("⛔") || gap.startsWith("⭐"),
                    "a gap entry starts with ⛔ (open) or ⭐ (landed): " + gap);
            Assertions.assertFalse(gap.contains("见 CONTENT-STATUS"),
                    "a gap names the mechanism itself rather than pointing at the ledger: " + gap);
        }
    }

    private static JsonObject root() throws IOException {
        return JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> ruleIds() throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (var element : root().getAsJsonArray("rules")) {
            ids.add(element.getAsJsonObject().get("id").getAsString());
        }
        return ids;
    }

    private static String gapsJoined() throws IOException {
        StringBuilder joined = new StringBuilder();
        for (var element : root().getAsJsonArray("_gaps")) {
            joined.append(element.getAsString()).append('\n');
        }
        return joined.toString();
    }
}
