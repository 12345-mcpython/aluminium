package com.laosun.aluminium.beans;

import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.enums.AttributeType;

import java.util.List;

public record SkillPoint(@SerializedName("point_id") int pointId, @SerializedName("pre_point") List<Integer> prePoint,
                         @SerializedName("point_type") String pointType, Attribute attribute) {
    public record Attribute(AttributeType name, double value) {
    }
}
