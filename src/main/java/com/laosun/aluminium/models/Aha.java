package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.utils.AttributeBuilder;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * 阿哈 (Aha): the mask unit that appears on the action bar in the 4.0 欢愉
 * system (HSR.md §3.1).
 *
 * <p>阿哈速度 (HSR.md §3.2):
 * <pre>{@code
 * 阿哈速度 = 80 + 最快欢愉角色速度/5 + 第二快/10 + 第三快/20 + 最慢/50
 * }</pre>
 *
 * <p>When Aha acts, the battle performs 「阿哈时刻」 (see
 * {@link com.laosun.aluminium.Battle#ahaMoment()}).
 */
public class Aha extends CanHit {

    /**
     * Computes Aha's speed from the speeds of the party's 欢愉 characters
     * (HSR.md §3.2).
     *
     * @param elationSpeeds the speeds of all 欢愉-path characters, any order
     * @return Aha's action speed
     */
    public static double computeAhaSpeed(java.util.List<Double> elationSpeeds) {
        java.util.List<Double> sorted = elationSpeeds.stream()
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        double a = sorted.size() > 0 ? sorted.get(0) : 0;
        double b = sorted.size() > 1 ? sorted.get(1) : 0;
        double c = sorted.size() > 2 ? sorted.get(2) : 0;
        double d = sorted.size() > 3 ? sorted.get(3) : 0;
        return 80 + a / 5 + b / 10 + c / 20 + d / 50;
    }

    public Aha(double speed) {
        super("Aha (阿哈)", Camp.NEUTRAL, buildAttributes(speed));
        setMaxEnergy(0);
        setAggro(0);
        setLevel(80);
    }

    private static DoubleValue[] buildAttributes(double speed) {
        AttributeBuilder atb = new AttributeBuilder();
        atb.setBase(HEALTH, 1)
                .setBase(DEFENCE, 0)
                .setBase(ATTACK, 0)
                .setBase(SPEED, speed);
        return atb.build();
    }

    @Override
    public String toString() {
        return "Aha";
    }
}
