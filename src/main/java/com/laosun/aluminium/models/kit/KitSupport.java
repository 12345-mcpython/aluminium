package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DataSkill;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.SkillData;

import java.util.List;
import java.util.Map;

/**
 * Shared helpers used by {@link CharacterKit} implementations: parameter
 * lookup from skill-tree nodes / eidolon data, and the damage multiplier of
 * the owner's last action skill.
 */
public final class KitSupport {

    private KitSupport() {
    }

    /** Groups the trace nodes by point ID. */
    public static Map<Integer, SkillPoint> byId(Map<Integer, SkillPoint> points) {
        return points != null ? points : Map.of();
    }

    /** Reads a trace node's parameter, with a fallback. */
    public static double param(Map<Integer, SkillPoint> byId, int pointId, int index, double fallback) {
        SkillPoint point = byId.get(pointId);
        if (point == null || point.param() == null || point.param().size() <= index) {
            return fallback;
        }
        return point.param().get(index);
    }

    /** Reads a trace node's parameter as an int, with a fallback. */
    public static int intParam(Map<Integer, SkillPoint> byId, int pointId, int index, int fallback) {
        return (int) Math.round(param(byId, pointId, index, fallback));
    }

    /** Reads an eidolon's parameter, with a fallback. */
    public static double param(Eidolon e, int index, double fallback) {
        if (e == null || e.param() == null || e.param().size() <= index) {
            return fallback;
        }
        return e.param().get(index);
    }

    /** Reads an eidolon's parameter as an int, with a fallback. */
    public static int intParam(Eidolon e, int index, int fallback) {
        return (int) Math.round(param(e, index, fallback));
    }

    /**
     * The damage multiplier of the owner's last action skill of the given type
     * (used by eidolons that deal extra hits scaled by the skill's multiplier).
     */
    public static double actionMultiplier(Character owner, SkillType type) {
        Skill skill = owner.getSkills().get(type);
        if (skill instanceof DataSkill dataSkill && dataSkill.getData() != null
                && dataSkill.getData().getSkills() != null
                && !dataSkill.getData().getSkills().isEmpty()) {
            List<Double> params = dataSkill.getData().getSkills().get(0);
            if (params != null && !params.isEmpty()) {
                return params.getFirst();
            }
        }
        return 1.0;
    }

    /** The character's current skill data, if available. */
    public static SkillData skillData(Character owner, SkillType type) {
        Skill skill = owner.getSkills().get(type);
        return skill != null ? skill.getData() : null;
    }

    /**
     * The designated ally target of a friendly skill: the first target when it
     * belongs to the user's camp, otherwise the whole friendly camp (the engine
     * convention for friendly skills whose designated target is absent).
     */
    public static List<? extends CanHit> friendlyTargets(Battle battle, CanHit user,
                                                         List<? extends CanHit> targets) {
        if (targets != null && !targets.isEmpty()) {
            CanHit first = targets.getFirst();
            if (first.getCamp() == user.getCamp() && !first.isDeath()) {
                return List.of(first);
            }
        }
        return user.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER
                ? battle.getAlivePlayerUnits() : battle.getAliveEnemies();
    }
}
