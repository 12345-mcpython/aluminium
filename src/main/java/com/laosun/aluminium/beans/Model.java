package com.laosun.aluminium.beans;

import java.util.Map;

public class Model {
    public static int TEST = 1;
    public static final Map<Integer, String> PART_MAPPING = Map.of(1, "head", 2, "hand",
            3, "body", 4, "boot", 5, "ball", 6, "line");
    public static final Map<String, String> RELIC_MAPPING = Map.ofEntries(
            Map.entry("HPDelta", "health"),
            Map.entry("AttackDelta", "attack"),
            Map.entry("HPAddedRatio", "health_percent"),
            Map.entry("AttackAddedRatio", "attack_percent"),
            Map.entry("DefenceAddedRatio", "defence_percent"),
            Map.entry("CriticalChanceBase", "crit_chance"),
            Map.entry("CriticalDamageBase", "crit_attack"),
            Map.entry("HealRatioBase", "outgoing_healing_boost"),
            Map.entry("StatusProbabilityBase", "effect_hit_rate"),
            Map.entry("SpeedDelta", "speed"),
            Map.entry("PhysicalAddedRatio", "physical_damage_boost"),
            Map.entry("FireAddedRatio", "fire_damage_boost"),
            Map.entry("IceAddedRatio", "ice_damage_boost"),
            Map.entry("ThunderAddedRatio", "thunder_damage_boost"),
            Map.entry("WindAddedRatio", "wind_damage_boost"),
            Map.entry("QuantumAddedRatio", "quantum_damage_boost"),
            Map.entry("ImaginaryAddedRatio", "imaginary_damage_boost"),
            Map.entry("BreakDamageAddedRatioBase", "breaking_effect"),
            Map.entry("SPRatioBase", "energy_regeneration_rate"),
            Map.entry("DefenceDelta", "defence"),
            Map.entry("StatusResistanceBase", "effect_resistance"));
}
