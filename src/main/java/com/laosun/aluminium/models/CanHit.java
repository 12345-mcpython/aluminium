package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.ToString;


@Getter
@ToString(callSuper = true)
public abstract class CanHit {
    private final String name;
    private final DoubleValue[] attributes;
    private final Camp camp;
    private final boolean death = false;

    public CanHit(String name, Camp camp, DoubleValue[] attributes) {
        this.name = name;
        this.camp = camp;
        this.attributes = attributes;
    }

    public DoubleValue getAttribute(AttributeType attributeType) {
        return attributes[attributeType.ordinal()];
    }

    public void setAttribute(AttributeType attributeType, DoubleValue value) {
        attributes[attributeType.ordinal()] = value;
    }
}