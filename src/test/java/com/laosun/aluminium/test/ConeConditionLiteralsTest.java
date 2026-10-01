package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Every light cone\u2019s loaded-tier rule conditions, written out LITERALLY (2026-09-30).
 *
 * <p>\u2b50 Tier semantics, read off the loader: the axis is the SUPERIMPOSITION RANK, the exact rank is used, a missing
 * rank falls back to the lowest existing row, and only numeric keys are ranks -- so these expectations come from each
 * file\u2019s smallest numeric key (what {@code Weapon.build(id, 9)} lands on).
 *
 * <p>\u26a0 Value-carrying comparisons are SKIPPED and registered instead: {@code source()} returns a parse-time SNAPSHOT
 * for them (measured: {@code target_hp_percent >= hp_percent} comes back as {@code target_hp_percent >= 0.0}), so a
 * literal cannot describe them. Pure-text conditions below are literal, in the parser\u2019s spelling.
 */
public class ConeConditionLiteralsTest {
    private static final int WEARER = 1210;
    private static final int RANK = 9;

    private void assertRule(int cone, String event, String id, List<String> expected) {
        Character c = CharacterFactory.create(WEARER, 80, true, Weapon.build(cone, RANK));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.valueOf(event)).stream()
                .filter(r -> r.id().equals(id)).toList();
        Assertions.assertEquals(1, rules.size(), cone + " " + id + " on " + event);
        var actual = rules.getFirst().conditions().stream().map(x -> x.source()).toList();
        Assertions.assertEquals(expected, actual, cone + " " + id);
    }

    @Test
    public void everyRuleWaitsForExactlyWhatItSays() {
        assertRule(20000, "BATTLE_START", "cone20000_crit_chance", List.of());
        assertRule(20002, "BATTLE_START", "boost_basic_attack_damage_boost_1", List.of());
        assertRule(20002, "BATTLE_START", "boost_skill_damage_boost_1", List.of());
        assertRule(20003, "TURN_START", "cone20003_defence_base", List.of("hp_percent >= 0.5"));
        assertRule(20003, "TURN_START", "cone20003_defence_below_threshold", List.of("hp_percent < 0.5"));
        assertRule(20004, "BATTLE_START", "cone20004_ehr", List.of());
        assertRule(20005, "BATTLE_START", "cone20005_party_attack", List.of());
        assertRule(20006, "BATTLE_START", "boost_ultimate_damage_boost_1", List.of());
        assertRule(20007, "KILL", "cone20007_attack_on_kill", List.of());
        assertRule(20008, "BATTLE_START", "cone20008_party_energy", List.of());
        assertRule(20010, "ULT_CAST", "cone20010_heal_on_ult", List.of());
        assertRule(20012, "BASIC_ATTACK", "cone20012_energy", List.of());
        assertRule(20013, "SKILL_CAST", "cone20013_energy_on_skill", List.of());
        assertRule(20014, "KILL", "cone20014_speed", List.of());
        assertRule(20015, "BASIC_ATTACK", "cone20015_advance_after_basic", List.of());
        assertRule(20016, "TURN_START", "cone20016_crit_below_threshold", List.of("hp_percent < 0.8"));
        assertRule(20017, "BREAK", "cone20017_heal_on_break", List.of());
        assertRule(20019, "BATTLE_START", "cone20019_party_speed", List.of());
        assertRule(20020, "ULT_CAST", "cone20020_attack_on_ult", List.of());
        assertRule(21002, "BATTLE_START", "cone21002_party_all_type_resistance", List.of());
        assertRule(21003, "BATTLE_START", "cone21003_attack", List.of());
        assertRule(21003, "BATTLE_START", "cone21003_crit_few_enemies", List.of("enemy_count <= 2.0"));
        assertRule(21004, "BATTLE_START", "cone21004_break_effect", List.of());
        assertRule(21004, "BASIC_ATTACK", "cone21004_energy", List.of());
        assertRule(21006, "BATTLE_START", "cone21006_followup_boost", List.of());
        assertRule(21007, "BATTLE_START", "cone21007_healing", List.of());
        assertRule(21007, "SKILL_CAST", "cone21007_party_energy", List.of());
        assertRule(21008, "BATTLE_START", "cone21008_ehr_and_dot", List.of());
        assertRule(21009, "BATTLE_START", "landau_aggro_up", List.of());
        assertRule(21009, "BATTLE_START", "landau_damage_taken_down", List.of());
        assertRule(21011, "DEALING_DAMAGE", "cone21011_same_element", List.of("actor is_ally", "damage_element_is_self"));
        assertRule(21013, "BATTLE_START", "cone21013_energy_on_entry", List.of());
        assertRule(21013, "BATTLE_START", "cone21013_ult_damage", List.of());
        assertRule(21014, "BATTLE_START", "cone21014_effect_res", List.of());
        assertRule(21014, "BATTLE_START", "cone21014_healing_off_effect_res", List.of());
        assertRule(21017, "BATTLE_START", "cone21017_basic_boost", List.of());
        assertRule(21017, "BATTLE_START", "cone21017_skill_boost", List.of());
        assertRule(21019, "BATTLE_START", "cone21019_attack", List.of());
        assertRule(21019, "KILL", "cone21019_crit_on_kill", List.of());
        assertRule(21020, "BATTLE_START", "cone21020_attack", List.of());
        assertRule(21020, "KILL", "cone21020_crit_damage_on_kill", List.of());
        assertRule(21022, "BATTLE_START", "cone21022_break_effect", List.of());
        assertRule(21023, "BATTLE_START", "cone21023_party_damage_taken_down", List.of());
        assertRule(21023, "BATTLE_START", "cone21023_heal_lost_hp", List.of());
        assertRule(21024, "BATTLE_START", "cone21024_battle_start", List.of());
        assertRule(21026, "BATTLE_START", "cone21026_attack", List.of());
        assertRule(21028, "BATTLE_START", "cone21028_health", List.of());
        assertRule(21028, "BASIC_ATTACK", "cone21028_party_heal", List.of());
        assertRule(21033, "BATTLE_START", "cone21033_attack", List.of());
        assertRule(21033, "KILL", "cone21033_heal_on_kill", List.of());
        assertRule(21034, "BATTLE_START", "cone21034_damage_per_energy", List.of());
        assertRule(21035, "BATTLE_START", "cone21035_break_effect", List.of());
        assertRule(21035, "BASIC_ATTACK", "cone21035_heal", List.of());
        assertRule(21039, "BATTLE_START", "cone21039_effect_res", List.of());
        assertRule(21039, "BATTLE_START", "cone21039_damage_per_defence", List.of());
        assertRule(21041, "BATTLE_START", "cone21041_attack_at_high_ehr", List.of("self_attr:effect_hit_rate >= 0.8"));
        assertRule(21042, "BATTLE_START", "cone21042_permanent", List.of());
        assertRule(21045, "BATTLE_START", "cone21045_break_effect", List.of());
        assertRule(21045, "ULT_CAST", "cone21045_speed", List.of());
        assertRule(21046, "BATTLE_START", "cone21046_path_twins", List.of());
        assertRule(21047, "BATTLE_START", "cone21047_break_effect", List.of());
        assertRule(21047, "BATTLE_START", "cone21047_speed", List.of());
        assertRule(21048, "BATTLE_START", "cone21048_speed", List.of());
        assertRule(21048, "BASIC_ATTACK", "cone21048_energy", List.of("target has_state 弱点击破"));
        assertRule(21053, "BATTLE_START", "cone21053_shield_boost", List.of());
        assertRule(21053, "DEALING_DAMAGE", "cone21053_damage_up_while_shielded", List.of("actor is_ally", "actor has_shield"));
        assertRule(21055, "DEALING_DAMAGE", "cone21055_healthy_attacker", List.of("actor is_ally", "actor_hp_percent >= 0.5"));
        assertRule(21056, "BATTLE_START", "cone21056_party_break_damage", List.of());
        assertRule(21058, "BATTLE_START", "boost_skill_damage_boost_rank1", List.of());
        assertRule(21058, "BATTLE_START", "boost_ultimate_damage_boost_rank1", List.of());
        assertRule(21060, "BATTLE_START", "boost_ultimate_damage_boost_rank1", List.of());
        assertRule(21060, "BATTLE_START", "boost_follow_up_damage_boost_rank1", List.of());
        assertRule(21062, "BATTLE_START", "boost_skill_damage_boost_1", List.of());
        assertRule(21062, "BATTLE_START", "boost_follow_up_damage_boost_1", List.of());
        assertRule(22001, "BATTLE_START", "cone22001_permanent", List.of());
        assertRule(22002, "BATTLE_START", "cone22002_attack", List.of());
        assertRule(23000, "BREAK", "cone23000_damage_up_on_break", List.of());
        assertRule(23005, "BATTLE_START", "moment_aggro_up", List.of());
        assertRule(23011, "WAVE_START", "cone23011_heal_lost_hp_per_wave", List.of());
        assertRule(23012, "BATTLE_START", "cone23012_crit_damage", List.of());
        assertRule(23014, "TAKING_HIT", "cone23014_eclipse_on_teammate_hit", List.of("target is_ally"));
        assertRule(23014, "HP_CONSUMED", "cone23014_eclipse_on_teammate_cost", List.of("actor is_ally"));
        assertRule(23016, "BATTLE_START", "cone23016_follow_up_damage", List.of());
        assertRule(23017, "ULT_CAST", "cone23017_heal_lowest_ally", List.of("actor is_ally"));
        assertRule(23018, "BATTLE_START", "cone23018_crit_damage", List.of());
        assertRule(23019, "WAVE_START", "cone23019_wave_energy", List.of());
        assertRule(23021, "BATTLE_START", "cone23021_mask_at_battle_start", List.of());
        assertRule(23021, "SKILL_POINT_GAINED", "cone23021_flame_per_point", List.of("self_stacks:彩焰 < 4.0"));
        assertRule(23021, "SKILL_POINT_GAINED", "cone23021_flame_pays_out", List.of("self_stacks:彩焰 >= 4.0"));
        assertRule(23021, "SKILL_POINT_OVERFLOWED", "cone23021_flame_per_overflow", List.of("self_stacks:彩焰 < 4.0"));
        assertRule(23021, "SKILL_POINT_OVERFLOWED", "cone23021_flame_pays_out_overflow", List.of("self_stacks:彩焰 >= 4.0"));
        assertRule(23030, "BATTLE_START", "cone23030_aggro_up", List.of());
        assertRule(23032, "DEALING_DAMAGE", "cone23032_damage_up_vs_forgetful", List.of("target has_state 忘忧"));
        assertRule(23032, "DEALING_DAMAGE", "cone23032_extra_at_high_break", List.of("target has_state 忘忧", "self_attr:breaking_effect >= 1.5"));
        assertRule(23033, "BATTLE_START", "cone23033_energy_at_start", List.of());
        assertRule(23035, "BREAK", "cone23035_burn_the_broken", List.of());
        assertRule(23038, "FOLLOW_UP", "cone23038_after_follow_up", List.of());
        assertRule(23038, "BATTLE_START", "cone23038_at_battle_start", List.of());
        assertRule(23041, "TURN_START", "cone23041_turn_energy", List.of());
        assertRule(23053, "SKILL_POINT_SPENT", "cone23053_spent_stack", List.of("self_stacks:消耗层数 < 4.0"));
        assertRule(23053, "SKILL_POINT_SPENT", "cone23053_turn_spend_count", List.of("self_stacks:本回合消耗 < 4.0"));
        assertRule(23053, "SKILL_POINT_SPENT", "cone23053_turn_spend_pays_out", List.of("self_stacks:本回合消耗 >= 4.0"));
        assertRule(23061, "SKILL_POINT_SPENT", "cone23061_turn_spend_count", List.of("actor is_ally", "actor_stacks:本回合消耗 < 4.0"));
        assertRule(23061, "SKILL_POINT_SPENT", "cone23061_crown_pays_out", List.of("actor is_ally", "actor_stacks:本回合消耗 >= 4.0"));
        assertRule(23063, "BATTLE_START", "cone23063_battle_advance", List.of());
        assertRule(23064, "WAVE_START", "cone23064_skill_point_per_wave", List.of());
        assertRule(24001, "BATTLE_START", "cone24001_crit", List.of());
        assertRule(24001, "KILL", "cone24001_attack_after_kill", List.of());
    }
}
