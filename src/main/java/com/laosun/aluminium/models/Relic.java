package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.utils.MapUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class Relic {
    public Type relicType;
    public Attribute mainAttribute;
    public List<Attribute> subAttributes;

    public static Relic create(Type relicType, Attribute mainAttribute, List<Attribute> subAttributes) {
        Relic relic = new Relic();
        relic.relicType = relicType;
        relic.mainAttribute = mainAttribute;
        relic.subAttributes = subAttributes;
        return relic;
    }

    public static Relic createRandomLevelZero(@NotNull Type relicType, int star) {
        var partValue = Constant.RELIC_MAIN_ATTRIBUTES.get(star).get(relicType.getType());
        var entry = MapUtils.getRandomEntry(partValue);
        var mainAttribute = new Attribute(entry.getKey(), entry.getValue().get("base"));
        var subAttributes = MapUtils.getRandomList(new ArrayList<>(Constant.RELIC_SUB_ATTRIBUTES.get(star).entrySet()), 3)
                .stream().map((a) -> new Attribute(a.getKey(), a.getValue().get("base"))).toList();
        return create(relicType, mainAttribute, subAttributes);
    }

    public record Attribute(String left, double right, int promoteLevel) {
        public Attribute(String left, double right) {
            this(left, right, 0);
        }
    }

    @Getter
    public enum Type {

        HEAD("head"),
        HAND("hand"),
        BODY("body"),
        BOOT("boot"),
        BALL("ball"),
        LINE("line");

        private final String type;

        Type(String string) {
            this.type = string;
        }
    }
}
