package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.models.GenericPassives;
import com.laosun.aluminium.models.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The fallback kit for characters without a hand-written implementation
 * (12-series and later): interprets their 行迹 and 星魂 from the
 * game data via {@link GenericPassives}.
 *
 * <p>Skills of these characters are executed entirely by the generic
 * {@code DataSkill} handlers, so no {@code SkillBehavior} is registered.
 */
public final class GenericKit implements CharacterKit {

    private final int cid;

    public GenericKit() {
        this.cid = 0;
    }

    public GenericKit(int cid) {
        this.cid = cid;
    }

    @Override
    public int cid() {
        return cid;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        if (points == null) {
            return List.of();
        }
        List<Trace> result = new ArrayList<>();
        for (SkillPoint point : points.values()) {
            if (!"skill".equals(point.pointType()) || point.desc() == null
                    || point.desc().chinese() == null) {
                continue;
            }
            result.addAll(GenericPassives.interpretTrace(point.desc().chinese(), point.param()));
        }
        return result;
    }

    @Override
    public Trace eidolon(int rank, Eidolon data) {
        String desc = data.desc() != null && data.desc().chinese() != null ? data.desc().chinese() : "";
        if (desc.contains("等级+2") || desc.contains("等级+1")) {
            return null; // 星魂3/5 纯技能等级加成, 已在构建时处理
        }
        List<Trace> interpreted = GenericPassives.interpretEidolon(desc, data.param());
        return interpreted.isEmpty() ? null : interpreted.getFirst();
    }
}
