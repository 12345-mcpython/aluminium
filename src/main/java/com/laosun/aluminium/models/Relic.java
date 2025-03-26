package com.laosun.aluminium.models;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.exceptions.RelicException;
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
        if(star <= 2 || star > 5){
            throw new RelicException("Star level expected star 2-5 but given " + star);
        }
        var partValue = Constant.RELIC_MAIN_ATTRIBUTES.get(star).get(relicType.getType());
        var entry = MapUtils.getRandomEntry(partValue);
        var mainAttribute = new Attribute(entry.getKey(), entry.getValue().get("base"));
        var subCandidates = new ArrayList<>(
                Constant.RELIC_SUB_ATTRIBUTES.get(star).entrySet()
        );
        subCandidates.removeIf(e -> e.getKey().equals(mainAttribute.left()));

        var selectedSubEntries = MapUtils.getRandomList(subCandidates, 3);
        var subAttributes = selectedSubEntries.stream()
                .map(e -> new Attribute(e.getKey(), e.getValue().get("base")))
                .toList();
        return create(relicType, mainAttribute, subAttributes);
    }

    public static Relic createBySetting(@NotNull Type relicType, Relic.Config setting) {
        return null;
    }

    @Getter
    public static class Config {
        private final Pair mainAttribute;
        private final List<Pair> subAttributes;

        public Config(Pair mainAttributeL, List<Pair> subAttributesL) {
            this.mainAttribute = mainAttributeL;
            this.subAttributes = subAttributesL;
        }

        public record Pair(String left, int right) {
        }
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
