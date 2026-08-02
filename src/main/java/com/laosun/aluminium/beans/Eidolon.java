package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * A character's 星魂 (eidolon) rank data, deserialized from ranks.json.
 *
 * <p>Each rank carries its translated name/description, the effect {@code param}
 * values, and optional skill-level bonuses ({@code skill_add}: skill ID → levels).
 */
public record Eidolon(int rank, Translate name, Translate desc,
                      @SerializedName("param") List<Double> param,
                      @SerializedName("skill_add") Map<String, Integer> skillAdd) {
}
