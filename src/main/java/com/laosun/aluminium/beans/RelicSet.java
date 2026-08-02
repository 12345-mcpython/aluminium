package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * A relic set's set bonuses (遗器套装效果), deserialized from relic_sets.json.
 *
 * <p>The 2-piece bonus is a list of permanent stat properties; the 4-piece
 * bonus is either more permanent properties or a conditional effect described
 * by {@code desc}/{@code param}.
 */
public record RelicSet(@SerializedName("name") Translate name,
                       @SerializedName("2") SetSkill two,
                       @SerializedName("4") SetSkill four) {

    /**
     * One set bonus tier.
     */
    public record SetSkill(@SerializedName("properties") List<Property> properties,
                           Translate desc, @SerializedName("param") List<Double> param) {

        /**
         * A permanent stat property.
         */
        public record Property(String attribute, double value) {
        }
    }
}
