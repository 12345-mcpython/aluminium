package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

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

    /**
     * Per-element resistance (0.2 = 20% RES). An element missing from the table has no
     * resistance. P2-2 fills this from {@code monster_config.json}'s
     * {@code damage_resistance}, so the shape stays the same.
     */
    private Map<DamageElement, Double> damageResist = Map.of();

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
