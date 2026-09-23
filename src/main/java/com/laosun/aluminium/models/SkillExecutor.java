package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.buffs.SuperBreakBuff;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.lang.IO.println;

/**
 * Turns one skill activation into the right number of {@link Damage} objects.
 *
 * <p>A skill is always "N hits on M targets"; every hit is settled independently through
 * {@link Battle#applyDamage(CanHit, Damage)}（每段独立判定暴击、独立结算）.
 *
 * <p><b>Targeting:</b> the caller only picks the <b>main target</b>
 * ({@code targets.getFirst()}); how many targets actually get hit is a property of the
 * effect, not of the caller:
 * <ul>
 *   <li>{@code SINGLE_ATTACK} / {@code MAZE_ATTACK} → the main target only</li>
 *   <li>{@code AOE_ATTACK} → every targetable enemy on the field</li>
 *   <li>{@code BLAST} → the main target plus its battlefield neighbours (left / right)</li>
 *   <li>{@code BOUNCE} → N hits (N = the second skill param), re-targeting a living enemy each hit</li>
 * </ul>
 *
 * <p>Non-damaging effects (heal / shield / buff / control / summon) return immediately and
 * are dispatched in later phases — importantly, their params are <i>not</i> damage
 * multipliers (e.g. cid 1001 slot 2 is a shield whose first param is a shield ratio).
 *
 * <p>After the segments are settled the attack-level event
 * {@link com.laosun.aluminium.models.event.AttackEvent} is broadcast to every ally, carrying
 * the actually-hit targets and the total settled damage — that is where 知更鸟【协奏】/
 * 缇宝结界 spawn 附加伤害 / 真伤 from someone else's attack.
 *
 * @see SkillEffectType#isDamaging()
 */
public final class SkillExecutor {

    private SkillExecutor() {
    }

    /**
     * Expands one skill activation into hits, settles them and broadcasts the attack event.
     *
     * <p>这里是**技能回能的唯一挂点**（P3-2）：不管这条技能有没有伤害（增益/护盾/治疗也要回能），
     * 也不管参数是否为空，施放结束都会给施放者结算一次
     * {@link Battle#grantSkillEnergy(CanHit, Skill, Set)}。
     *
     * @param battle  the running battle (targets are taken from {@code battle.targetableEnemies()})
     * @param skill   the skill being used (its {@link SkillData} decides the shape)
     * @param user    the caster
     * @param targets the caller's selection; only the first entry (main target) is used
     */
    public static void execute(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets) {
        Set<CanHit> hitTargets = new LinkedHashSet<>();   // 实际命中过的目标（含当场死亡的）
        resolveHits(battle, skill, user, targets, hitTargets);
        battle.grantSkillEnergy(user, skill, hitTargets);
    }

    /**
     * 非伤害技能的**未分派诊断日志**开关（P8-2 计划第 3 条）。
     *
     * <p>{@link #resolveHits} 在"不是伤害类技能"时**静默 return** —— 也就是说治疗/护盾/buff/
     * 控制/召唤这些技能被施放后**什么都不发生**（只有回能照给）。这在真实队伍里很难察觉：
     * 日志上看技能"放出去了"，只是没有任何效果。所以这里给一个可开关的诊断。
     *
     * <p>为什么默认**关**：{@code Main} 的 demo 每回合都在放治疗与护盾，默认开会刷屏。
     * 计划里原本想直接 {@code IO.println}，实测会污染 demo 输出，故改为显式开关。
     */
    private static boolean logNotDispatched = false;

    /**
     * 打开/关闭"非伤害技能未分派"的诊断日志。测试与排查时打开，正式跑保持关闭。
     */
    public static void setLogNotDispatched(boolean enabled) {
        logNotDispatched = enabled;
    }

    /**
     * 记录"这条技能没有被打出去"。同时标注它**归哪个阶段**实现 ——
     * 免得看到"施法成功但没效果"时无从下手。
     */
    private static void logNotDispatched(Skill skill, CanHit user, SkillEffectType effect,
                                        List<? extends CanHit> targets) {
        if (!logNotDispatched) {
            return;
        }
        String phase = switch (effect.getCategory()) {
            case HEAL -> "P6-2 已实现（走 Battle.heal，不经本执行器）";
            case BUFF -> "P10-3 Buff 体系";
            case CONTROL -> "P10-6 Debuff / 控制";
            case SUMMON -> "P9-4 召唤物";
            case PASSIVE -> "纯被动，本就不该作为行动施放";
            case DAMAGE -> "伤害类（不该走到这里）";
        };
        var data = skill.getData();
        println("[SkillExecutor] 未分派：" + data.getSkillType() + " / " + effect
                + "（" + user.getName() + "，目标 "
                + (targets == null ? "null" : targets.size() + " 个")
                + "）→ 归属 " + phase);
    }

    /**
     * 把一次技能施放展开成 N 段伤害并结算（能量不在这里给，见 {@link #execute}）。
     *
     * @param hitTargets 输出参数：实际命中集
     */
    private static void resolveHits(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets,
                                    Set<CanHit> hitTargets) {
        SkillData data = skill.getData();
        SkillEffectType effect = data.getEffect();

        // 1) 先判是否伤害技能：护盾/治疗/buff 技的 param 第 1 项不是伤害倍率
        if (!effect.isDamaging() || targets == null || targets.isEmpty()) {
            logNotDispatched(skill, user, effect, targets);
            return;                                  // TODO P6/P7/P9：治疗/护盾/控制/召唤再分派
        }

        // 2) 伤害技能必有元素；缺了属于数据错误，fail fast 好过让这一击静默消失
        DamageElement element = data.getElement();
        if (element == null) {
            throw new IllegalStateException("Damaging skill without element: "
                    + data.getSkillType() + " (" + effect + ")");
        }

        // 3) 再取倍率：空参数是真实存在的（cid 1001 槽位 6 的 param_list = [[]]）
        List<List<Double>> levels = data.getSkills();
        int index = skill.getLevel() - 1;
        if (index < 0 || index >= levels.size()) {
            return;
        }
        List<Double> params = levels.get(index);
        if (params == null || params.isEmpty()) {
            return;
        }
        double base = user.getAttribute(AttributeType.ATTACK).get() * params.getFirst();
        CanHit mainTarget = targets.getFirst();

        double totalDamage = 0;

        switch (effect) {
            case SINGLE_ATTACK, MAZE_ATTACK ->
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets,
                            data.getStanceList().single());

            case AOE_ATTACK -> {
                double stance = data.getStanceList().all();
                for (Enemy target : battle.targetableEnemies()) {
                    totalDamage += hit(battle, data, user, element, base, target, hitTargets, stance);
                }
            }

            case BLAST -> {
                List<Enemy> alive = battle.targetableEnemies();
                int center = alive.indexOf(mainTarget);      // 站位顺序 = battle.enemies 顺序
                double centreStance = data.getStanceList().single();
                double neighbourStance = data.getStanceList().spread();
                if (center < 0) {
                    totalDamage += hit(battle, data, user, element, base, mainTarget, hitTargets, centreStance);
                } else {
                    totalDamage += hit(battle, data, user, element, base, alive.get(center), hitTargets, centreStance);
                    if (center > 0) {
                        totalDamage += hit(battle, data, user, element, base, alive.get(center - 1),
                                hitTargets, neighbourStance);
                    }
                    if (center < alive.size() - 1) {
                        totalDamage += hit(battle, data, user, element, base, alive.get(center + 1),
                                hitTargets, neighbourStance);
                    }
                }
            }

            case BOUNCE -> {
                int hits = params.size() > 1 ? (int) (double) params.get(1) : 1;   // 段数缺省 1
                // H-3：弹射的 single 是**整个技能的总削韧**，要均摊到每一段，否则段数越多削得越多
                double perHitStance = stanceValue(data, true) / Math.max(1, hits);
                for (int i = 0; i < hits; i++) {
                    // 每段重新取存活目标：中途击杀就换人，而不是把段数空放给尸体
                    List<Enemy> alive = battle.targetableEnemies();
                    if (alive.isEmpty()) {
                        break;                                     // 全死 → 剩余段数作废
                    }
                    totalDamage += hit(battle, data, user, element, base,
                            alive.get(battle.getRng().nextInt(alive.size())), hitTargets, perHitStance);
                }
            }

            default -> {
            }
        }

        broadcastAfterAttack(battle, user, mainTarget, hitTargets, totalDamage);
    }

    /**
     * Fires {@link com.laosun.aluminium.models.event.AttackEvent} on every ally（知更鸟/缇宝挂在
     * 自己身上的"我方攻击后"效果就是这么收到的）。附加伤害 / 真伤不走这里，所以不会递归触发。
     */
    private static void broadcastAfterAttack(Battle battle, CanHit attacker, CanHit mainTarget,
                                            Set<CanHit> hitTargets, double totalDamage) {
        if (hitTargets.isEmpty()) {
            return;                                  // 一段都没打中 → 不算一次攻击
        }
        List<CanHit> targets = List.copyOf(hitTargets);
        for (Character ally : battle.characters) {
            ally.afterAttack(battle, attacker, mainTarget, targets, totalDamage);
        }
    }

    /**
     * Settles one hit, reduces toughness (P4-2) and accumulates it into the attack summary.
     *
     * <p>削韧走"两条链分同一个标称值"的口径（P4-6）：{@link Battle#reduceToughness} 会同时给出
     * 实际削掉的值与超出部分，后者在施放方持有 {@link SuperBreakBuff} 时变成一发超击破伤害。
     *
     * @param stanceDamage 这一段要削的韧性点数（已由调用方按技能形状与段数算好：AOE 用 {@code all}、
     *                     BLAST 中心 {@code single} / 相邻 {@code spread}、BOUNCE 为总值均摊到每段）
     * @return the settled damage of this hit (0 if the target was dead / invulnerable)
     */
    private static double hit(Battle battle, SkillData data, CanHit user, DamageElement element, double base,
                              CanHit target, Set<CanHit> hitTargets, double stanceDamage) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        hitTargets.add(target);                      // 命中事实（含随后死亡的）——"每有 1 名目标受到攻击"
        // 4 参构造器 → DamageType.NORMAL；P8-2 接真实槽位后再按 普攻/战技/终结技 映射
        Damage damage = new Damage(user, target, element, base);
        double settled = battle.applyDamage(target, damage);
        settled += applyStanceDamage(battle, user, element, damage, target, stanceDamage);
        return settled;
    }

    /**
     * 削韧（P4-2）+ 超击破（P4-6）：只有「算一次攻击」的伤害才削韧。
     *
     * <p>数据实测（{@code skills.json} 全量统计）：单体/秘技/弹射用 {@code single}（30=1 单位、60=2、90=3），
     * 群攻用 {@code all}，**扩散用 {@code single}（中心）+ {@code spread}（相邻）**——
     * 例：姬子战技 = {@code 60/0/30}。弹射的 {@code single} 是整个技能的**总值**，按段数均摊（H-3）。
     *
     * @param stanceDamage 这一段实际要削的点数（0 = 这个形状不削韧）
     * @return 本段**额外**结算的伤害（击破伤害 + 超击破伤害；0 = 都没有）。它们是在
     *         {@code Battle.reduceToughness} 内部 / {@link #applySuperBreak} 里结算的，
     *         所以要靠返回值累加进本次攻击的总额 —— 否则 {@code AttackEvent.totalDamage}
     *         会漏掉整条击破链。
     *         <p>这两段都是**派生段**（已置 {@code notCountsAsAttack()}）：不给受击方回能
     *         （一次攻击行为只回一次，由主段负责），但击杀时仍给攻击者回能
     */
    private static double applyStanceDamage(Battle battle, CanHit user, DamageElement element,
                                            Damage damage, CanHit target, double stanceDamage) {
        if (stanceDamage <= 0 || !damage.isCountsAsAttack() || !(target instanceof Enemy enemy)) {
            return 0;                                // 附加伤害 / 真伤不削韧
        }
        Battle.StanceResult stance = battle.reduceToughness(user, enemy, element, stanceDamage);
        return stance.breakDamage() + applySuperBreak(battle, user, enemy, element, stance.superBreakStance());
    }

    /**
     * 超击破（P4-6）：把"打不进韧性条的那部分削韧值"转化成一发 {@link DamageType#SUPER_BREAK} 伤害。
     *
     * <p>触发条件只有两个：施放方持有 {@link SuperBreakBuff}（纯标记），且
     * {@code superBreakStance > 0}（敌人本来就已经击破，或这一发把它打破）。
     *
     * <p>注意 {@code superBreakStance} 是**超出部分**而不是整发削韧值：破韧的那一发里，
     * 前一半削韧已经用于击破（{@link BreakDamageCalculator}），这里只能用超出的那一半，
     * 否则同一个标称削韧值会被用两次。
     *
     * @param superBreakStance 超出剩余韧性的那部分削韧值
     * @return 本段结算的超击破伤害（0 = 没触发）
     */
    private static double applySuperBreak(Battle battle, CanHit user, Enemy enemy, DamageElement element,
                                          double superBreakStance) {
        if (superBreakStance <= 0 || !user.getBuffManager().hasBuff(SuperBreakBuff.class)) {
            return 0;
        }
        Damage superBreak = BreakDamageCalculator.buildSuperBreak(user, enemy, element, superBreakStance);
        return battle.applyDamage(enemy, superBreak);
    }

    /**
     * 该技能**单段**打在一个目标上的削韧点数（不含弹射的段数均摊，均摊在 {@code BOUNCE} 分支里做）。
     */
    private static double stanceValue(SkillData data, boolean mainTarget) {
        // 注意：StanceList 在 beans.Skill 里（与 models.Skill 同名不同包），这里用全限定名
        com.laosun.aluminium.beans.Skill.StanceList stance = data.getStanceList();
        return switch (data.getEffect()) {
            case AOE_ATTACK -> stance.all();
            case BLAST -> mainTarget ? stance.single() : stance.spread();
            default -> stance.single();
        };
    }
}
