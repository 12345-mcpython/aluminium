package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillEffectType;

import java.util.List;

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
 *   <li>{@code AOE_ATTACK} → every alive enemy on the field</li>
 *   <li>{@code BLAST} → the main target plus its battlefield neighbours (left / right)</li>
 *   <li>{@code BOUNCE} → N hits (N = the second skill param), each on a random alive enemy</li>
 * </ul>
 *
 * <p>Non-damaging effects (heal / shield / buff / control / summon) return immediately and
 * are dispatched in later phases — importantly, their params are <i>not</i> damage
 * multipliers (e.g. cid 1001 slot 2 is a shield whose first param is a shield ratio).
 *
 * @see SkillEffectType#isDamaging()
 */
public final class SkillExecutor {

    private SkillExecutor() {
    }

    /**
     * Expands one skill activation into hits and settles them.
     *
     * @param battle  the running battle (targets are taken from {@code battle.enemies})
     * @param skill   the skill being used (its {@link SkillData} decides the shape)
     * @param user    the caster
     * @param targets the caller's selection; only the first entry (main target) is used
     */
    public static void execute(Battle battle, Skill skill, CanHit user, List<? extends CanHit> targets) {
        SkillData data = skill.getData();
        SkillEffectType effect = data.getEffect();

        // 1) 先判是否伤害技能：护盾/治疗/buff 技的 param 第 1 项不是伤害倍率
        if (!effect.isDamaging() || targets == null || targets.isEmpty()) {
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

        switch (effect) {
            case SINGLE_ATTACK, MAZE_ATTACK -> hit(battle, user, element, base, mainTarget);

            case AOE_ATTACK -> {
                for (Enemy target : battle.targetableEnemies()) {
                    hit(battle, user, element, base, target);
                }
            }

            case BLAST -> {
                List<Enemy> alive = battle.targetableEnemies();
                int center = alive.indexOf(mainTarget);      // 站位顺序 = battle.enemies 顺序
                if (center < 0) {
                    hit(battle, user, element, base, mainTarget);   // 主目标已死 → hit 内部会跳过
                    return;
                }
                hit(battle, user, element, base, alive.get(center));
                if (center > 0) {
                    hit(battle, user, element, base, alive.get(center - 1));
                }
                if (center < alive.size() - 1) {
                    hit(battle, user, element, base, alive.get(center + 1));
                }
            }

            case BOUNCE -> {
                int hits = params.size() > 1 ? (int) (double) params.get(1) : 1;   // 段数缺省 1
                for (int i = 0; i < hits; i++) {
                    // 每段重新取存活目标：中途击杀就换人，而不是把段数空放给尸体
                    List<Enemy> alive = battle.targetableEnemies();
                    if (alive.isEmpty()) {
                        return;                                     // 全死 → 剩余段数作废
                    }
                    hit(battle, user, element, base, alive.get(battle.getRng().nextInt(alive.size())));
                }
            }

            default -> {
            }
        }
    }

    private static void hit(Battle battle, CanHit user, DamageElement element, double base, CanHit target) {
        if (target == null || target.isDeath()) {
            return;
        }
        // 4 参构造器 → DamageType.NORMAL；P8-2 接真实槽位后再按 普攻/战技/终结技 映射
        battle.applyDamage(target, new Damage(user, target, element, base));
    }
}
