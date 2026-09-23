package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * 按 {@code (cid, 槽位, 等级)} 从数据里解析技能的**通用**实现。
 *
 * <p>⚠ 这里原先挂着 `// TODO: DELETE IT BECAUSE OF EVERY CHARACTERS HAS EVERY SKILLS` ——
 * 那条注释是**误导**的，已删除。它想表达的可能是"不该一个技能一个 Java 类"，
 * 但那是**另一个方向**（P8-0 的三分法：机制走数据/触发器，逃生舱才写类），
 * 恰恰说明本类是对的：技能是**数据**，用 {@code (cid, 槽位, 等级)} 三个键就能定位，
 * 不需要 93 个角色 × 6 个技能 = 558 个类。
 *
 * <p>"每个角色都有每个技能"这句本身也不成立：角色只装配
 * {@link com.laosun.aluminium.Constant#SKILL_SLOT} 里的**常驻**槽位
 * （地图普攻/秘技由 {@code Battle.startBattle()} 附加，召唤物槽位属 P9-4）。
 */
public class DefaultSkill extends Skill {
    private final int cid;
    private final int skillId;
    private final int level;

    private static final ConcurrentMap<String, SkillData> DATA_CACHE = new ConcurrentHashMap<>();

    public DefaultSkill(int cid, int skillId, int level) {
        this.cid = cid;
        this.skillId = skillId;
        this.level = level;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public SkillData getData() {
        String key = cid + "_" + skillId;
        return DATA_CACHE.computeIfAbsent(key, k -> SkillData.init(cid, skillId));
    }

    // Default behavior: apply a target to the damage
    @Override
    public void execute(Battle battle, CanHit user, List<? extends CanHit> target) {
        SkillExecutor.execute(battle, this, user, target);
    }
}
