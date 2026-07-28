package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * An enemy combatant in the action queue.
 *
 * <p>Enemies are typically aligned with {@link Camp#ENEMY} and may have
 * their attributes scaled by difficulty-level multipliers.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {

    public Enemy(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }

    public Enemy(String name, DoubleValue[] attributes) {
        super(name, Camp.ENEMY, attributes);
    }

    public static Enemy fromAttributes(String name, double health, double defence, double attack, double speed) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        attributeBuilder.setBase(HEALTH, health);
        attributeBuilder.setBase(DEFENCE, defence);
        attributeBuilder.setBase(ATTACK, attack);
        attributeBuilder.setBase(SPEED, speed);
        return new Enemy(name, attributeBuilder.build());
    }
}
