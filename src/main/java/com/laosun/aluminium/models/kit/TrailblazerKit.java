package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

/**
 * 开拓者·同谐 (Trailblazer·Harmony, cids 8005/8006).
 *
 * <p>当前实现: 天赋回能 (敌方韧性被击破时恢复能量)。
 */
public final class TrailblazerKit implements CharacterKit {

    private final int cid;

    public TrailblazerKit(int cid) {
        this.cid = cid;
    }

    @Override
    public int cid() {
        return cid;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        return List.of(new TrailblazerHarmony(5));
    }

    /** 天赋: 敌方目标韧性被击破时，开拓者恢复5点能量。 */
    static class TrailblazerHarmony implements Trace {
        private final double energy;

        TrailblazerHarmony(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "天赋·回能";
        }

        @Override
        public void onBattleStart(Battle battle, Character owner) {
            // Demo pacing: get the 【舞梦】 ultimate online sooner.
            owner.gainEnergy(40);
            IO.println("  [行迹] " + owner.getName() + " starts with bonus energy");
        }

        @Override
        public void onEnemyBreak(Battle battle, Character owner, Enemy enemy) {
            owner.gainEnergy(energy);
            IO.println("  [行迹] " + owner.getName() + " gains " + String.format("%.0f", energy)
                    + " energy (enemy broken)");
        }
    }
}
