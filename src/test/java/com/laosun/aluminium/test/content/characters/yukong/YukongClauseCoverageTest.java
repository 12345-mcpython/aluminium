package com.laosun.aluminium.test.content.characters.yukong;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1207 驭空 clause coverage: every section of {@code 1207_驭空.md}, plus the layer-decay reading this round made exact.
 *
 * <p><b>What changed on 2026-11-02</b>: 「驭空施放战技获得【鸣弦号令】的回合，不会移除【鸣弦号令】」 used to be read as
 * {@code target != self} — <b>her own turn never removed a layer</b>, which is one turn too many. The sentence only exempts the
 * turn she <i>casts</i>, so a one-turn marker now carries that fact and her later turns remove a layer like everyone else's.
 */
public class YukongClauseCoverageTest {

    private static final int YUKONG = 1207;
    private static final int ALLY = 1002;
    private static final String STACKS = "鸣弦号令";

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：流镝", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("战技：天阙鸣弦", "skill_command");
        SECTION_TO_RULE.put("终结技：贯云饮羽", "ult_effect");
        SECTION_TO_RULE.put("天赋：箭彻七札", "basic_effect");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：云鸢逐风", "technique_two_stacks_on_entry");
        SECTION_TO_RULE.put("星魂 1：天舶飞将，枕弧待战", "eidolon1_battle_start");
        SECTION_TO_RULE.put("星魂 2：青霄驰骋，驱驭苍穹", "eidolon2_energy_on_ally_full");
        SECTION_TO_RULE.put("星魂 3：危弓未辍，执射不宁", "eidolon3_levels");
        SECTION_TO_RULE.put("星魂 4：百里闻风，九曲响镝", "eidolon4_on_damage");
        SECTION_TO_RULE.put("星魂 5：井仪贯侯，疾羽参连", "eidolon5_levels");
        SECTION_TO_RULE.put("星魂 6：弦栝如雷，铣珧激荡", "eidolon6_ult");
        SECTION_TO_RULE.put("行迹：气壮", "turn_end_effect");
        SECTION_TO_RULE.put("行迹：迟彝", "battle_start");
        SECTION_TO_RULE.put("行迹：archerion", "trace_archerion_one_debuff_resist");
    }

    @Test
    public void everyDocumentSectionHasItsRule() throws IOException {
        JsonArray rules = rules();
        int checked = 0;
        for (Map.Entry<String, String> entry : SECTION_TO_RULE.entrySet()) {
            if (entry.getValue().startsWith("(")) {
                continue;
            }
            checked++;
            Assertions.assertTrue(has(rules, entry.getValue()),
                    entry.getKey() + " has no rule (expected " + entry.getValue() + ")");
        }
        System.out.println("[1207] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 「驭空施放战技获得【鸣弦号令】的回合，不会移除【鸣弦号令】」 — and every later turn end <b>does</b> take a layer.
     *
     * <p>Measured: cast ⇒ <b>2</b> layers; her own turn end right after the cast ⇒ still <b>2</b> (the marker is consumed);
     * an ally's turn end ⇒ <b>1</b>; her own turn end again ⇒ <b>0</b>. The old spelling ({@code target != self}) left that last
     * step out — her own turns never removed anything, so two layers would survive two full rounds.
     */
    @Test
    public void theCastingTurnKeepsItsLayersAndEveryLaterTurnEndDoesNot() {
        Fixture f = new Fixture();
        f.castSkill();
        Assertions.assertEquals(2, f.stacks(), "「获得 2 层【鸣弦号令】」");

        f.endTurn(f.yukong);
        Assertions.assertEquals(2, f.stacks(), "the casting turn keeps them");

        f.endTurn(f.ally);
        Assertions.assertEquals(1, f.stacks(), "an ally's turn end takes one");

        f.endTurn(f.yukong);
        Assertions.assertEquals(0, f.stacks(), "and so does her own, once it is no longer the casting turn");
        Assertions.assertFalse(f.ally.getBuffManager().hasState("鸣弦号令增益"),
                "the last layer takes the party's attack boost with it");
    }

    /**
     * The boost the layers carry: 「当驭空持有【鸣弦号令】时，我方全体攻击力提高 80%」.
     */
    @Test
    public void theLayersCarryThePartyAttackBoost() {
        Fixture f = new Fixture();
        double before = f.ally.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        f.castSkill();
        double after = f.ally.getAttribute(com.laosun.aluminium.enums.AttributeType.ATTACK).get();
        Assertions.assertTrue(after > before, "「我方全体攻击力提高 80%」 (" + before + " -> " + after + ")");
        Assertions.assertEquals(1082.7432000000001, after, 1e-6, "the 80% joins the same ATK-percent pool as her traces: 1082.7432, not before×1.8");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean has(JsonArray rules, String id) {
        for (var element : rules) {
            JsonObject candidate = element.getAsJsonObject();
            if (candidate.has("id") && id.equals(candidate.get("id").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static JsonArray rules() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1207.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("rules");
    }

    private static final class Fixture {
        private final Character yukong;
        private final Character ally = CharacterFactory.create(ALLY, 80);
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture() {
            yukong = CharacterFactory.create(YUKONG, 80);
            battle = new Battle(List.of(yukong, ally), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        private void castSkill() {
            battle.castImmediate(yukong.getSkills().get(SkillType.SKILL), yukong, List.of(yukong));
            battle.processRequests();
        }

        private void endTurn(Character who) {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_END, who, who, 0, 0));
            battle.processRequests();
        }

        private int stacks() {
            return yukong.getBuffManager().stacksOf(STACKS);
        }
    }
}


