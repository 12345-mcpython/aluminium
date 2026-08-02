package com.laosun.aluminium.models.kit;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.Eidolon;
import com.laosun.aluminium.beans.SkillPoint;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Buff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.SkillData;
import com.laosun.aluminium.models.Trace;

import java.util.List;
import java.util.Map;

import static com.laosun.aluminium.models.kit.KitSupport.intParam;
import static com.laosun.aluminium.models.kit.KitSupport.param;

/**
 * 寒鸦 (Hanya, cid 1215) — 物理属性 同谐.
 *
 * <p>核心机制【承负】: 战技对指定敌方单体附加印记 (仅对最新目标生效), 我方目标对其
 * 每施放2次普攻、战技、终结技后立即恢复1个战技点, 触发#2次战技点回复效果后自动解除。
 * 由行迹 录事 的计数器字段代理; 【承负】使目标受到的伤害提高 (代理为易伤 buff),
 * 罚恶 (天赋) 使攻击承负目标的队友伤害提高, 由 录事 在 onAllyAction 中一并施加。
 */
public final class HanyaKit implements CharacterKit {

    @Override
    public int cid() {
        return 1215;
    }

    @Override
    public List<Trace> traces(Map<Integer, SkillPoint> points) {
        Map<Integer, SkillPoint> byId = KitSupport.byId(points);
        return List.of(
                new HanyaRecord(param(byId, 1215101, 0, 0.1), intParam(byId, 1215101, 1, 1)),
                new HanyaUnderworld(intParam(byId, 1215102, 0, 1), intParam(byId, 1215102, 1, 1)),
                new HanyaReturn(param(byId, 1215103, 0, 2)));
    }

    @Override
    public Trace eidolon(int rank, Eidolon e) {
        return switch (rank) {
            case 1 -> new HanyaE1(param(e, 0, 0.15), intParam(e, 1, 1));
            case 2 -> new HanyaE2(param(e, 0, 0.2), intParam(e, 1, 1));
            case 4 -> new HanyaE4(intParam(e, 0, 1));
            case 6 -> new HanyaE6(param(e, 0, 0.1));
            default -> null;
        };
    }

    @Override
    public SkillBehavior skillBehavior(int skillId) {
        return switch (skillId) {
            case 2 -> HanyaKit::hanyaSkill;
            case 3 -> HanyaKit::hanyaUlt;
            default -> null;
        };
    }

    // ─── 技能行为 ───────────────────────────────────────────────────────

    /**
     * 战技 生灭系缚: 对指定敌方单体造成#1%攻击力的物理属性伤害, 并使其陷入【承负】状态
     * (仅对最新被施加的目标生效)。
     */
    static void hanyaSkill(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double multiplier = ctx.firstParam();
        CanHit main = targets.getFirst();
        ctx.dealDamage(battle, user, main, multiplier);
        battle.breakToughness(user, main, ctx.stanceSingle());
        if (user instanceof Character character) {
            HanyaRecord record = findTrace(character, HanyaRecord.class);
            if (record != null) {
                record.applyMark(battle, character, main);
            }
        }
    }

    /**
     * 终结技 十王敕令，遍土遵行: 使指定我方单体速度提高, 提高数值等同于寒鸦速度的#3,
     * 并使该目标攻击力提高#1, 持续#2回合 (星魂4: 持续时间额外增加#1回合)。
     */
    static void hanyaUlt(Battle battle, SkillContext ctx, CanHit user, List<? extends CanHit> targets) {
        double atkPercent = ctx.firstParam();
        int turns = ctx.intParam(1, 2);
        double speedRatio = ctx.param(2, 0.15);
        if (user instanceof Character character) {
            HanyaE4 e4 = findTrace(character, HanyaE4.class);
            if (e4 != null) {
                turns += e4.extraTurns;
            }
        }
        List<? extends CanHit> allies = KitSupport.friendlyTargets(battle, user, targets);
        if (allies.isEmpty()) {
            return;
        }
        CanHit target = allies.getFirst();
        double userSpeed = user.getAttribute(AttributeType.SPEED) != null
                ? user.getAttribute(AttributeType.SPEED).get() : 0;
        target.removeBuff("十王敕令");
        Buff buff = new Buff("十王敕令", Buff.Category.BUFF, user, target, turns)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                        DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(userSpeed * speedRatio,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        IO.println("  " + target.getName() + " gains 【十王敕令】 (ATK +"
                + String.format("%.0f%%", atkPercent * 100) + ", SPD +"
                + String.format("%.0f", userSpeed * speedRatio) + ", " + turns + " turns)");
    }

    // ─── 行迹 ───────────────────────────────────────────────────────────

    /**
     * 录事: 触发【承负】战技点回复效果的我方单位攻击力提高#1, 持续#2回合。
     * 兼作天赋 罚恶 / 承负 的代理:
     * <ul>
     *   <li>战技附加【承负】印记 (仅最新目标生效), 印记使目标受到的伤害提高
     *   (代理为易伤, 数值取天赋 罚恶 的伤害提高值)。</li>
     *   <li>我方目标对承负目标每施放2次普攻/战技/终结技 → 恢复1个战技点 (触发#2次后解除),
     *   触发时给攻击者施加 录事 攻击力 buff 与 罚恶 伤害提高 buff。</li>
     *   <li>敌方目标被消灭时, 若承负的战技点回复触发次数小于等于 幽府 的#1, 额外回复#2点
     *   战技点 (由 幽府 的参数判断); 触发回复时寒鸦恢复能量 (由 还阳 提供参数)。</li>
     * </ul>
     */
    static class HanyaRecord implements Trace {
        private final double atkPercent;
        private final int atkTurns;
        private CanHit marked = null;
        private int attacksSinceRestore = 0;
        private int restoresOnMark = 0;

        HanyaRecord(double atkPercent, int atkTurns) {
            this.atkPercent = atkPercent;
            this.atkTurns = atkTurns;
        }

        @Override
        public String getName() {
            return "录事";
        }

        /** 战技: 施加【承负】, 仅对最新被施加的目标生效. */
        void applyMark(Battle battle, Character owner, CanHit target) {
            if (marked != null && !marked.isDeath()) {
                marked.removeBuff("承负");
            }
            marked = target;
            attacksSinceRestore = 0;
            restoresOnMark = 0;
            // 承负使目标受到的伤害提高: 代理为易伤 (数值取天赋 罚恶 的伤害提高值).
            double vuln = 0.15;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && !talent.getSkills().getFirst().isEmpty()) {
                vuln = talent.getSkills().getFirst().get(0);
            }
            target.removeBuff("承负");
            Buff buff = new Buff("承负", Buff.Category.DEBUFF, owner, target, -1)
                    .stat(AttributeType.VULNERABILITY, DoubleValue.Modifier.pure(vuln,
                            DoubleValue.Modifier.ModifierSource.DEBUFF));
            battle.applyBuff(target, buff);
            IO.println("  " + target.getName() + " gains 【承负】 (vuln +"
                    + String.format("%.0f%%", vuln * 100) + ")");
        }

        @Override
        public void onAllyAction(Battle battle, Character owner, Character actor, SkillType type,
                                 List<? extends CanHit> targets) {
            if (marked == null || marked.isDeath()) {
                return;
            }
            if (type != SkillType.COMMON && type != SkillType.SKILL && type != SkillType.ULTRA) {
                return;
            }
            if (targets == null || !targets.contains(marked)) {
                return;
            }
            // 罚恶 (天赋): 我方目标对承负目标施放普攻/战技/终结技时伤害提高#1, 持续#2回合.
            double talentBonus = 0.15;
            int talentTurns = 2;
            SkillData talent = KitSupport.skillData(owner, SkillType.TALENT);
            if (talent != null && talent.getSkills() != null && !talent.getSkills().isEmpty()
                    && talent.getSkills().getFirst().size() > 1) {
                talentBonus = talent.getSkills().getFirst().get(0);
                talentTurns = (int) Math.round(talent.getSkills().getFirst().get(1));
            }
            // 星魂6: 天赋的伤害提高效果额外提高#1.
            HanyaE6 e6 = findTrace(owner, HanyaE6.class);
            if (e6 != null) {
                talentBonus += e6.extraBonus;
            }
            actor.removeBuff("罚恶");
            Buff penal = new Buff("罚恶", Buff.Category.BUFF, owner, actor, talentTurns)
                    .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(talentBonus,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(actor, penal);
            // 承负: 每2次攻击恢复1个战技点.
            attacksSinceRestore++;
            if (attacksSinceRestore < 2) {
                return;
            }
            attacksSinceRestore = 0;
            restore(battle, owner, actor);
        }

        /** 触发【承负】战技点回复效果. */
        private void restore(Battle battle, Character owner, CanHit actor) {
            battle.addSkillPoints(1);
            restoresOnMark++;
            // 录事: 触发承负战技点回复效果的我方单位攻击力提高.
            actor.removeBuff("录事");
            Buff record = new Buff("录事", Buff.Category.BUFF, owner, actor, atkTurns)
                    .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(actor, record);
            // 还阳: 触发承负战技点恢复效果时, 寒鸦恢复#1点能量.
            HanyaReturn ret = findTrace(owner, HanyaReturn.class);
            if (ret != null) {
                owner.gainEnergy(ret.energy);
            }
            IO.println("  [行迹] 【承负】 restores 1 SP (" + actor.getName() + "); "
                    + owner.getName() + " +" + String.format("%.0f", ret != null ? ret.energy : 0)
                    + " energy (录事/还阳)");
            // 承负在触发#2次战技点回复效果后自动解除.
            if (restoresOnMark >= maxRestores(owner)) {
                if (!marked.isDeath()) {
                    marked.removeBuff("承负");
                }
                marked = null;
                IO.println("  [行迹] 【承负】 expires (" + restoresOnMark + " SP restores)");
            }
        }

        private int maxRestores(Character owner) {
            SkillData skill = KitSupport.skillData(owner, SkillType.SKILL);
            if (skill != null && skill.getSkills() != null && !skill.getSkills().isEmpty()
                    && skill.getSkills().getFirst().size() > 1) {
                return (int) Math.round(skill.getSkills().getFirst().get(1));
            }
            return 2;
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (victim != marked) {
                return;
            }
            // 幽府: 持有承负的敌方目标被消灭时, 若承负的回复触发次数≤#1, 额外回复#2点战技点.
            HanyaUnderworld underworld = findTrace(owner, HanyaUnderworld.class);
            if (underworld != null && restoresOnMark <= underworld.maxTriggers) {
                battle.addSkillPoints(underworld.extraSP);
                IO.println("  [行迹] " + victim.getName() + " died with 【承负】: +"
                        + underworld.extraSP + " SP (幽府)");
            }
        }
    }

    /**
     * 幽府: 持有【承负】的敌方目标被消灭时，如果【承负】为全队回复战技点的触发次数
     * 小于等于#1，则额外回复#2点战技点。 (由行迹 录事 在击杀时读取.)
     */
    static class HanyaUnderworld implements Trace {
        private final int maxTriggers;
        private final int extraSP;

        HanyaUnderworld(int maxTriggers, int extraSP) {
            this.maxTriggers = maxTriggers;
            this.extraSP = extraSP;
        }

        @Override
        public String getName() {
            return "幽府";
        }
    }

    /** 还阳: 当【承负】战技点恢复效果被触发时，自身恢复#1点能量。 (由行迹 录事 读取.) */
    static class HanyaReturn implements Trace {
        private final double energy;

        HanyaReturn(double energy) {
            this.energy = energy;
        }

        @Override
        public String getName() {
            return "还阳";
        }
    }

    // ─── 星魂 ───────────────────────────────────────────────────────────

    /**
     * 星魂1 一心: 持有终结技效果的我方目标消灭敌方目标时，寒鸦行动提前#1，该效果每回合
     * 只能触发#2次。
     */
    static class HanyaE1 implements Trace {
        private final double advance;
        private final int maxPerTurn;
        private int usedThisTurn = 0;

        HanyaE1(double advance, int maxPerTurn) {
            this.advance = advance;
            this.maxPerTurn = maxPerTurn;
        }

        @Override
        public String getName() {
            return "星魂1";
        }

        @Override
        public void onTurnStart(Battle battle, Character owner) {
            usedThisTurn = 0;
        }

        @Override
        public void onKill(Battle battle, Character owner, CanHit victim) {
            if (usedThisTurn >= maxPerTurn) {
                return;
            }
            boolean allyWithUlt = battle.getAlivePlayerUnits().stream()
                    .anyMatch(u -> u.hasBuffNamed("十王敕令"));
            if (!allyWithUlt) {
                return;
            }
            usedThisTurn++;
            battle.advanceByPercent(owner, advance);
            IO.println("  [星魂] " + owner.getName() + " advances "
                    + String.format("%.0f%%", advance * 100) + " (一心)");
        }
    }

    /** 星魂2 二观: 施放战技后，速度提高#1，持续#2回合。 */
    static class HanyaE2 implements Trace {
        private final double speedPercent;
        private final int turns;

        HanyaE2(double speedPercent, int turns) {
            this.speedPercent = speedPercent;
            this.turns = turns;
        }

        @Override
        public String getName() {
            return "星魂2";
        }

        @Override
        public void afterAction(Battle battle, Character owner, SkillType type, List<? extends CanHit> targets) {
            if (type != SkillType.SKILL) {
                return;
            }
            owner.removeBuff("二观");
            Buff buff = new Buff("二观", Buff.Category.BUFF, owner, owner, turns)
                    .stat(AttributeType.SPEED, DoubleValue.Modifier.addPercent(speedPercent,
                            DoubleValue.Modifier.ModifierSource.BUFF));
            battle.applyBuff(owner, buff);
            IO.println("  [星魂] " + owner.getName() + " speeds up (+"
                    + String.format("%.0f%%", speedPercent * 100) + ", 二观)");
        }
    }

    /** 星魂4 四谛: 终结技的持续时间额外增加#1回合。 (由终结技行为读取.) */
    static class HanyaE4 implements Trace {
        private final int extraTurns;

        HanyaE4(int extraTurns) {
            this.extraTurns = extraTurns;
        }

        @Override
        public String getName() {
            return "星魂4";
        }
    }

    /** 星魂6 六正: 天赋的伤害提高效果额外提高#1。 (由行迹 录事 读取.) */
    static class HanyaE6 implements Trace {
        private final double extraBonus;

        HanyaE6(double extraBonus) {
            this.extraBonus = extraBonus;
        }

        @Override
        public String getName() {
            return "星魂6";
        }
    }

    // ─── 工具 ───────────────────────────────────────────────────────────

    private static <T extends Trace> T findTrace(CanHit user, Class<T> type) {
        if (!(user instanceof Character character)) {
            return null;
        }
        for (Trace trace : character.getTraces()) {
            if (type.isInstance(trace)) {
                return type.cast(trace);
            }
        }
        return null;
    }
}
