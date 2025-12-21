package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;

public record CharacterData(Translate name, String attribute, String mt, String id, double attack, double defence,
                            double health, int speed, @SerializedName("max_energy")
                            Double maxEnergy) {
}
