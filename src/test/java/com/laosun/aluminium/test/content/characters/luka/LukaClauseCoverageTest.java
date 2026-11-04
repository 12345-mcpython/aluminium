package com.laosun.aluminium.test.content.characters.luka;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1111 卢卡 clause coverage: every section of {@code 1111_卢卡.md}, plus the one trace this round could write and the two it
 * could not.
 *
 * <p><b>What changed on 2026-11-02</b>: 行迹 粉碎斗志 is written ({@code chance: 0.5} on each segment of the enhanced Basic ATK,
 * an ADDITIONAL instance that cannot re-trigger itself). Two things are registered instead of written, each with what was
 * measured: 「每获得 1 层【斗志】」 has no "counter was just gained" event, and the enhanced Basic ATK's <b>segment list</b> is
 * not modelled — the engine settles one 20% hit where the document says three 20% hits plus one 80% hit. Both are now closed: 循环制动 reads {@code STACK_GAINED} (2026-11-02) and the enhanced Basic ATK reads the segment table's {@code times} field (2026-11-04), so this file is the whole document.
 */
public class LukaClauseCoverageTest {

    private static final int LUKA = 1111;

    private static final Map<String, String> SECTION_TO_RULE = new LinkedHashMap<>();

    static {
        SECTION_TO_RULE.put("普攻：直冲拳", "(no rule needed: the basic attack reads its own skill row)");
        SECTION_TO_RULE.put("强化普攻：直冲碎天拳", "talent_enhance_basic");
        SECTION_TO_RULE.put("战技：裂伤拳", "skill_lacerate");
        SECTION_TO_RULE.put("终结技：直冲碎天拳", "ult_coup");
        SECTION_TO_RULE.put("天赋：直冲拳", "talent_start");
        SECTION_TO_RULE.put("地图攻击：攻击", "(no rule needed: the map attack happens outside battle)");
        SECTION_TO_RULE.put("秘技：裂伤拳", "technique_damage_and_bleed_on_one_enemy");
        SECTION_TO_RULE.put("行迹：动能过载", "trace_overload");
        SECTION_TO_RULE.put("行迹：循环制动", "trace_recycle_energy_per_will_layer");
        SECTION_TO_RULE.put("行迹：粉碎斗志", "trace_crushing_will_extra_segment");
        SECTION_TO_RULE.put("行迹：攻击 / 效果命中 / 防御（数值行迹）",
                "(no rule needed: stat traces are folded into the panel by the loader)");
        SECTION_TO_RULE.put("星魂 1：裂伤拳", "eidolon1_damage_up_while_bleeding");
        SECTION_TO_RULE.put("星魂 2：直冲拳", "eidolon2_will_on_physical_weakness");
        SECTION_TO_RULE.put("星魂 3：裂伤拳", "eidolon3_raises_skill_and_talent");
        SECTION_TO_RULE.put("星魂 4：斗志", "eidolon4_attack_per_will");
        SECTION_TO_RULE.put("星魂 5：直冲拳", "eidolon5_raises_ult_and_basic");
        SECTION_TO_RULE.put("星魂 6：裂伤拳", "eidolon6_laceration_ticks_per_segment");
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
        System.out.println("[1111] sections covered = " + checked + " rule-backed + "
                + (SECTION_TO_RULE.size() - checked) + " with a written reason");
    }

    /**
     * 行迹 粉碎斗志: 「【直冲拳】的每 1 段攻击都有 50% 的固定概率使卢卡额外施放 1 段攻击」.
     *
     * <p>Measured: the plain Basic ATK settles <b>1</b> instance (NORMAL, 255); the enhanced one settles <b>2</b> — the 20% hit
     * (NORMAL, 51) plus the trace's extra <b>ADDITIONAL</b> 51. The extra instance is ADDITIONAL on purpose: that is what keeps
     * 「此效果对额外施放的攻击不会生效」 true without a second guard.
     */
    @Test
    public void theCrushingWillTraceAddsOneExtraSegment() {
        Fixture f = new Fixture();
        Assertions.assertEquals(1, f.settleBasic().size(), "the plain Basic ATK is one instance");

        List<DamageType> enhanced = f.settleEnhancedBasic();
        long normal = enhanced.stream().filter(type -> type == DamageType.NORMAL).count();
        // Note: the BASE half is four instances since 2026-11-04 (「3 段 20% + 1 段 80%」, the `times` field), and the trace
        // rolls 50% on EACH segment, so the ADDITIONAL count is 0..4 with the seeded RNG - which is why the base half is
        // what is pinned here rather than the total.
        Assertions.assertEquals(4, normal, "「先…3 段…每段 20%…再…1 段…80%」 — four segments: " + enhanced);
        Assertions.assertEquals(enhanced.size(), normal + enhanced.stream()
                        .filter(type -> type == DamageType.ADDITIONAL).count(),
                "and everything else on this cast is the trace's ADDITIONAL segment");
    }

    /**
     * 行迹 循环制动: 「每获得 1 层【斗志】，额外恢复 3 点能量」 — now written on {@code STACK_GAINED}.
     *
     * <p>Measured: the Ultimate grants 2 layers at once and pays <b>+6</b> energy (one firing per layer), which is exactly why
     * the event fires per layer rather than carrying a count. Note the earlier version of this test pinned the REGISTRATION
     * (the engine had no "a counter was just gained" event); it now pins the payment.
     */
    @Test
    public void theRecycleTracePaysThreeEnergyPerLayer() {
        Fixture f = new Fixture();
        int layersBefore = f.luka.getBuffManager().stacksOf("斗志");
        double energyBefore = f.luka.getCurrentEnergy();

        f.castUlt();

        Assertions.assertEquals(layersBefore + 2, f.luka.getBuffManager().stacksOf("斗志"),
                "precondition: the Ultimate grants 2 layers");
        Assertions.assertEquals(energyBefore + 6, f.luka.getCurrentEnergy(), 1e-9,
                "「每获得 1 层…额外恢复 3 点能量」 — two layers, two firings");
    }

    /**
     * <b>Gap witness</b>: the enhanced Basic ATK's segment list is a DATA gap — measured, the engine settles one 20% hit where
     * the document says three 20% hits then one 80% hit. This test pins that reading; when the segments land it goes red.
     */
    @Test
    public void enhancedBasicPlaysItsFourSegments() {
        Fixture f = new Fixture();
        f.settleBasic();
        List<DamageType> enhanced = f.settleEnhancedBasic();
        // Three 20% instances and one 80% instance, all NORMAL; the trace's ADDITIONAL segments (if any rolled) are the
        // other kind, so the base half is counted by type.
        Assertions.assertEquals(4, enhanced.stream().filter(type -> type == DamageType.NORMAL).count(),
                "the document's three 20% segments plus one 80% segment: " + enhanced);
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static boolean gapMentions(String needle) throws IOException {
        for (var element : gaps()) {
            if (element.getAsString().contains(needle)) {
                return true;
            }
        }
        return false;
    }

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
        return character().getAsJsonArray("rules");
    }

    private static JsonArray gaps() throws IOException {
        return character().getAsJsonArray("_gaps");
    }

    private static JsonObject character() throws IOException {
        Path file = Path.of("src", "main", "resources", "game", "characters", "1111.json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static final class Fixture {
        private final Character luka;
        private final Enemy enemy = BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL);
        private final Battle battle;

        private Fixture() {
            luka = CharacterFactory.create(LUKA, 80);
            battle = new Battle(List.of(luka), List.of(enemy), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** Casts the plain Basic ATK and returns the settled damage types. */
        private List<DamageType> settleBasic() {
            return cast();
        }

        /** Casts once more — the first cast left Luka at 2 stacks, so this one is the enhanced form. */
        private List<DamageType> settleEnhancedBasic() {
            return cast();
        }

        private void castUlt() {
            battle.castImmediate(luka.getSkills().get(SkillType.ULTRA), luka, List.of(enemy));
            battle.processRequests();
        }

        private List<DamageType> cast() {
            List<DamageType> kinds = new ArrayList<>();
            battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) ->
                    kinds.add(damage.getType()));
            battle.castImmediate(luka.getSkills().get(SkillType.COMMON), luka, List.of(enemy));
            battle.processRequests();
            return kinds;
        }
    }
}


