package com.laosun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.ExtraBasicPromote;
import com.laosun.aluminium.models.Relic;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buffs.SuperBreakBuff;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * aluminium 引擎演示：一场完整的战斗。
 *
 * <p>这份 `main` 只走引擎当前**真正支持**的东西：
 * <ul>
 *   <li>角色面板：真实数据（character_data → 等级缩放 → 光锥 → 遗器 → 行迹 → 额外加成）</li>
 *   <li>敌人面板：真实数据（{@link EnemyFactory#create} = 模板 × 等级组 × 实例系数）</li>
 *   <li>行动条：{@code 10000 / 速度}，击破推条 25%</li>
 *   <li>回合流程：{@code stepForward → beforeMove → 出手 → afterMove}</li>
 *   <li>伤害：完整乘区（增伤 / 暴击 / 防御 / 抗性 / 易伤）+ 事件钩子</li>
 *   <li>韧性：削韧 → 击破伤害 → 推条 → 挂 DOT → 击破回能</li>
 *   <li>超击破：带 {@link SuperBreakBuff} 时，超出韧性条的削韧转化成一发额外伤害</li>
 *   <li>DOT：敌人回合开始时按"先上先结算"结算</li>
 *   <li>能量：技能回能 / 受击回能 / 击杀回能 / 终结技清零再回 5 / 满能量才能放大招</li>
 *   <li>生死：HP 归零 → 移出行动条；任一方全灭 → 战斗结束（P7-3 状态机）</li>
 * </ul>
 *
 * <p><b>这份 demo 故意不用的东西</b>：
 * <ul>
 *   <li>关卡/波次（P7-4/P7-5 已实现，见 {@code StageFactory}）—— 这里手搭 3 只固定敌人，
 *       因为要逐条展示"弱点 / 抗性 / 韧性 / 技能倍率"这些数据，逐波进怪反而看不清；</li>
 *   <li>忆灵（P9）、欢愉体系（P10）—— 引擎还没有。</li>
 * </ul>
 *
 * <p>随机数全程走注入的 {@link Random}：固定种子 → 整场可复现。
 */
public class Main {

    public static void main() {
        System.out.println("=".repeat(78));
        System.out.println(" aluminium 战斗演示：姬子 / 三月七 / 罗刹  vs  冰锋 + 基层员工·外勤 + 次元扑满");
        System.out.println("=".repeat(78));
        System.out.println();

        List<Character> team = List.of(himeko(), march7th(), luocha());

        // ── 敌人：真实数据。三只简单杂兵，各有弱点/抗性，且都装了 enemy_skills.json 的普攻 ──
        //  冰锋 1002011   弱火/雷、冰抗 0.2、韧性 60
        //  基层员工 8032010  物理
        //  次元扑满 8002040  低倍率（0.6）的小怪
        List<Enemy> enemies = new ArrayList<>(List.of(
                EnemyFactory.create(1002011, 90, 1),
                EnemyFactory.create(8032010, 90, 1),
                EnemyFactory.create(8002040, 90, 1)));
        for (Enemy enemy : enemies) {
            // ⚠ 这个数值随"技能倍率是否真实"变过两次：
            //   - 早先六个槽位全解析到**普攻**（P8-2 修的），普攻对单只有几百伤害 → 定 12000；
            //   - 槽位修好后战技/终结技用上**真实倍率**（姬子战技打 3 目标、对单上万），
            //     12000 会被一发秒掉 → 提到 30000，让战斗回到"几十次行动"的量级。
            // 生命上限存在属性数组的 HEALTH 槽里；currentHp 没有 setter，所以提高上限后 heal 补满。
            enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(30_000));
            enemy.heal(30_000);
            enemy.setMaxEnergy(0);               // 怪物没有能量条：maxEnergy == 0 → 所有回能 no-op
            printEnemy(enemy);
        }
        System.out.println();

        // ── 开战 ───────────────────────────────────────────────────────────────
        Battle battle = new Battle(team, enemies, new Random(20260919));
        battle.startBattle();

        // 对应开拓者·同谐【伴舞】的简化：队友身上挂一个超击破标记
        team.getFirst().getBuffManager().addBuff(new SuperBreakBuff(99));
        System.out.println("[开场] 姬子 获得 SuperBreakBuff（超击破标记）");
        printQueue(battle);
        System.out.println();

        int actions = 0;
        while (!battle.isOver() && actions < 60) {
            actions++;
            // P7-1：轮次由行动条的累计行动值推算（首轮 150、之后每轮 100），不再自己数
            System.out.println("────────── 第 " + battle.getRound() + " 轮（第 " + actions
                    + " 次行动，累计行动值 " + fmt(battle.queue.getElapsed()) + "）──────────");
            step(battle);
            System.out.println();
        }

        System.out.println("=".repeat(78));
        // P7-3：胜负由 Battle 的状态机给，不在 demo 里自己数活人
        System.out.println(switch (battle.getStatus()) {
            case WIN -> " 战斗结束：我方胜利（" + battle.getRound() + " 轮 / " + actions + " 次行动）";
            case LOSE -> " 战斗结束：我方全灭（" + battle.getRound() + " 轮 / " + actions + " 次行动）";
            default -> " 达到行动次数上限，战斗未结束（剩余敌人 "
                    + battle.targetableEnemies().size() + " 只）";
        });
        System.out.println("=".repeat(78));
        battle.printHp();
    }

    // ==================================================================
    // 一个回合
    // ==================================================================

    private static void step(Battle battle) {
        battle.stepForward();
        Signal current = battle.queue.getCurrentActor();
        if (current == null) {
            System.out.println("[行动条] 没有可行动的单位");
            return;
        }
        CanHit actor = current.getCanHit();

        // 1) 回合开始：敌人先结算 DOT，再跑 buff 与实体的 beforeMove 钩子
        battle.beforeMove();
        if (actor.isDeath()) {
            System.out.println("[死亡] " + actor.getName() + " 在自己回合开始前被 DOT 结算掉了");
            battle.afterMove();
            return;
        }

        // 2) 出手
        if (actor instanceof Enemy enemy) {
            enemyTurn(battle, enemy);
        } else {
            characterTurn(battle, (Character) actor);
        }

        // 3) 回合结束：行动值归位 / 死者移出行动条 / buff 结算
        battle.afterMove();
        printQueue(battle);
    }

    /** 我方回合：能量满就放大招，否则用战技（非弱点退回普攻）。 */
    private static void characterTurn(Battle battle, Character hero) {
        System.out.println("[我方] " + hero.getName()
                + "  HP " + fmt(hero.getCurrentHp()) + "/" + fmt(hero.getMaxHp())
                + "  能量 " + fmt(hero.getCurrentEnergy()) + "/" + fmt(hero.getMaxEnergy()));

        Enemy target = firstAliveEnemy(battle);
        if (target == null) {
            return;
        }

        // 攒够开大阈值 → 终结技（引擎会先清零、结算本体、再回自身 5 点）
        // P3-4：判据是 battle.isUltraReady（读技能数据的 sp_need），不必攒满上限
        if (battle.isUltraReady(hero) && hero.getSkills().containsKey(SkillType.ULTRA)) {
            System.out.println("        → 能量已达到开大阈值，释放【终结技】");
            double hpBefore = target.getCurrentHp();
            if (!battle.castUltra(hero, List.of(target))) {
                System.out.println("        → 终结技释放失败");
                return;
            }
            report(hero, target, hpBefore);
            return;
        }

        // 战技形状分流（P6-2 / P6-3）：治疗技给自己人回血、护盾技给自己人上盾，
        // 其余（伤害类）照旧打敌人。
        Skill skill = hero.getSkills().get(SkillType.SKILL);
        if (skill != null && skill.getData() != null) {
            switch (skill.getData().getEffect()) {
                case RESTORE -> {
                    healTurn(battle, hero, skill);
                    return;
                }
                case DEFENCE -> {
                    shieldTurn(battle, hero, skill);
                    return;
                }
                default -> {
                }
            }
        }

        boolean weaknessHit = skill != null && skill.getData() != null
                && target.isWeakTo(skill.getData().getElement());
        if (!weaknessHit) {
            skill = hero.getSkills().get(SkillType.COMMON);      // 非弱点 → 普攻
        }
        if (skill == null) {
            return;
        }
        System.out.println("        → 使用" + (weaknessHit ? "【战技】" : "【普攻】"));
        double hpBefore = target.getCurrentHp();
        if (!battle.performAction(skill, List.of(target))) {
            System.out.println("        → 出手失败（死亡 / 被控 / 行动条状态不对）");
            return;
        }
        // ⚠️ performAction 只是**排队**；真正的结算在 afterMove() 的 processRequests() 里。
        //    这里显式结算一次，好让下面的战报拿到真实数值（真实战斗循环里由 afterMove 负责）。
        battle.processRequests();
        report(hero, target, hpBefore);
    }

    /**
     * 治疗（P6-2）：基础量 = 攻击力 × 倍率，再过"治疗加成 × 受疗加成"。
     *
     * <p>引擎只提供 {@code Battle.heal(healer, target, base)}；选谁当目标、倍率取哪个
     * 参数位（治疗技的 {@code param_list[0][0]}）是**调用方**的事。
     */
    private static void healTurn(Battle battle, Character hero, Skill skill) {
        Character patient = lowestHpRateCharacter(battle);
        if (patient == null) {
            return;
        }
        double multiplier = skill.getData().getSkills()
                .get(Math.min(skill.getLevel(), skill.getData().getSkills().size()) - 1).getFirst();
        double base = hero.getAttribute(AttributeType.ATTACK).get() * multiplier;
        System.out.println("        → 使用【战技·治疗】，目标 " + patient.getName());
        double before = patient.getCurrentHp();
        double healed = battle.heal(hero, patient, base);
        System.out.println("        → 基础治疗 " + fmt(base) + " → 实际回复 " + fmt(healed)
                + "：" + patient.getName() + " HP " + fmt(before) + " → " + fmt(patient.getCurrentHp())
                + "/" + fmt(patient.getMaxHp()));
        hero.gainEnergy(com.laosun.aluminium.models.energy.EnergyGain.normal(30));
    }

    /**
     * 护盾（P6-3）：基础量 = 防御力 × 倍率（三月七战技的 {@code param_list[0][0]} 是护盾系数）。
     *
     * <p>引擎只提供 {@code Battle.grantShield(target, amount)}，量由调用方算。
     */
    private static void shieldTurn(Battle battle, Character hero, Skill skill) {
        Character ally = lowestHpRateCharacter(battle);
        if (ally == null) {
            return;
        }
        double multiplier = skill.getData().getSkills()
                .get(Math.min(skill.getLevel(), skill.getData().getSkills().size()) - 1).getFirst();
        double base = hero.getAttribute(AttributeType.DEFENCE).get() * multiplier;
        System.out.println("        → 使用【战技·护盾】，目标 " + ally.getName());
        double shield = battle.grantShield(ally, base);
        System.out.println("        → 护盾量 " + fmt(shield) + "（基础 " + fmt(base) + "）→ "
                + ally.getName() + " 护盾 " + fmt(ally.getShield()));
        hero.gainEnergy(com.laosun.aluminium.models.energy.EnergyGain.normal(30));
    }

    /**
     * 敌方回合（P5-5）：**引擎自己的 AI**，不再是手工打人。
     *
     * <p>流程：击破中 → 跳过；否则用 {@link TargetSelector} 按仇恨加权选一个活着的我方目标，
     * 再用敌人自己的 {@link com.laosun.aluminium.models.EnemySkill} 出手
     * （技能来自 {@code enemy_skills.json}，倍率是猜的，见该文件说明）。
     */
    private static void enemyTurn(Battle battle, Enemy enemy) {
        System.out.println("[敌方] " + enemy.getName()
                + "  HP " + fmt(enemy.getCurrentHp()) + "/" + fmt(enemy.getMaxHp())
                + (enemy.isBroken() ? "  【已被击破 " + enemy.getBrokenElement() + "】" : "")
                + "  韧性 " + fmt(enemy.getStance()) + "/" + fmt(enemy.getMaxStance()));

        // 击破中：由调用方主动问，返回 true 表示"本回合跳过"
        if (battle.handleBrokenTurn(enemy)) {
            System.out.println("        → 处于击破状态，本回合不行动（剩 "
                    + enemy.getBrokenRemainTurns() + " 回合恢复）");
            return;
        }

        // 候选集 = 活着的我方（"谁能被选中"由调用方过滤，别选到尸体）
        List<CanHit> candidates = new ArrayList<>();
        for (Character c : battle.characters) {
            if (!c.isDeath()) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        // P5-4：按仇恨加权随机选目标（存护 150 比常规 100 更容易被打）
        CanHit target = TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, battle.getRng());
        Skill attack = enemy.getSkills().get(SkillType.COMMON);
        if (target == null || attack == null) {
            System.out.println("        → 没有可攻击的目标或技能");
            return;
        }

        System.out.println("        → 选中 " + target.getName()
                + "（仇恨 " + fmt(battle.aggroOf(target)) + "，全队总 "
                + fmt(candidates.stream().mapToDouble(battle::aggroOf).sum()) + "）");
        double hpBefore = target.getCurrentHp();
        if (!battle.performAction(attack, List.of(target))) {
            System.out.println("        → 出手失败");
            return;
        }
        battle.processRequests();                    // 同上：排队后显式结算，好让战报拿到真实数值
        System.out.println("        → " + enemy.getName() + " 造成 " + fmt(hpBefore - target.getCurrentHp())
                + "：" + target.getName() + " HP " + fmt(target.getCurrentHp())
                + "/" + fmt(target.getMaxHp())
                + (target.getShield() > 0 ? "（护盾 " + fmt(target.getShield()) + "）" : "")
                + "，受击回能 → " + fmt(target.getCurrentEnergy()));
        if (target.isDeath()) {
            System.out.println("        → " + target.getName() + " 被击败，移出行动条");
        }
    }

    // ==================================================================
    // 输出
    // ==================================================================

    private static void report(Character hero, Enemy target, double hpBefore) {
        System.out.println("        → " + hero.getName() + " 造成 " + fmt(hpBefore - target.getCurrentHp())
                + "，能量 " + fmt(hero.getCurrentEnergy()) + "/" + fmt(hero.getMaxEnergy())
                + "；" + target.getName()
                + " HP " + fmt(target.getCurrentHp()) + "/" + fmt(target.getMaxHp())
                + "，韧性 " + fmt(target.getStance()) + "/" + fmt(target.getMaxStance())
                + (target.isBroken() ? " 【击破 " + target.getBrokenElement() + "】" : "")
                + (target.getDots().isEmpty() ? "" : "  DOT×" + target.getDots().size()));
        if (target.isDeath()) {
            System.out.println("        → " + target.getName() + " 被击败");
        }
    }

    private static void printEnemy(Enemy enemy) {
        System.out.println("[敌人] " + enemy.getName()
                + "  Lv" + enemy.getLevel()
                + "  HP " + fmt(enemy.getMaxHp())
                + "  攻 " + fmt(enemy.getAttribute(AttributeType.ATTACK).get())
                + "  防 " + fmt(enemy.getAttribute(AttributeType.DEFENCE).get())
                + "  速 " + fmt(enemy.getAttribute(AttributeType.SPEED).get()));
        System.out.println("        弱点 " + enemy.getStanceWeak()
                + "  韧性 " + fmt(enemy.getMaxStance())
                + "  抗性 " + enemy.getDamageResist());
    }

    private static void printQueue(Battle battle) {
        List<String> names = new ArrayList<>();
        for (Signal signal : battle.getQueueSnapshot()) {
            names.add(signal.getCanHit().getName()
                    + "(" + fmt(battle.queue.getTimeRemaining(signal)) + ")");
        }
        System.out.println("[行动条] " + String.join(" → ", names));
    }

    // ==================================================================
    // 组队（真实面板）
    // ==================================================================

    private static Character himeko() {
        // 姬子 1003：火 / 速度 96 / 能量上限 120
        RelicSuit relics = new RelicSuit();
        relics.addMore(
                relic(RelicType.HEAD, AttributeType.HEALTH, 705.6, AttributeType.CRIT_CHANCE, 0.12),
                relic(RelicType.HAND, AttributeType.ATTACK, 352.8, AttributeType.ATTACK_PERCENT, 0.18),
                relic(RelicType.BODY, AttributeType.ATTACK_PERCENT, 0.5, AttributeType.CRIT_ATTACK, 0.24),
                relic(RelicType.BOOT, AttributeType.SPEED, 25, AttributeType.BREAKING_EFFECT, 0.3),
                relic(RelicType.BALL, AttributeType.FIRE_DAMAGE_BOOST, 0.4, AttributeType.CRIT_CHANCE, 0.1),
                relic(RelicType.LINE, AttributeType.ATTACK_PERCENT, 0.6, AttributeType.CRIT_ATTACK, 0.2));

        Character hero = Character.builder()
                .cid(1003)
                .level(80)
                .weapon(Weapon.build(23001, 80))
                .relicSuit(relics)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 0, 0, 0, 0.12, 0))
                .build();
        return hero;
    }

    private static Character march7th() {
        Character hero = Character.builder().cid(1001).level(80).build();
        return hero;
    }

    private static Character luocha() {
        Character hero = Character.builder().cid(1203).level(80).build();
        return hero;
    }

    /** 手工造一件遗器：主词条 + 一条副词条（真实随机生成见 {@code Relic.createRandomLevelZero}）。 */
    private static Relic relic(RelicType type, AttributeType main, double mainValue,
                               AttributeType sub, double subValue) {
        return Relic.create(15, 5, type,
                new Relic.Attribute(main, mainValue),
                List.of(new Relic.Attribute(sub, subValue)));
    }

    // ==================================================================
    // 工具
    // ==================================================================

    private static Enemy firstAliveEnemy(Battle battle) {
        List<Enemy> alive = battle.targetableEnemies();
        return alive.isEmpty() ? null : alive.getFirst();
    }

    /** 血量比例最低的存活角色（治疗/护盾的简化选目标策略）。 */
    private static Character lowestHpRateCharacter(Battle battle) {
        Character worst = null;
        double worstRate = Double.MAX_VALUE;
        for (Character c : battle.characters) {
            if (c.isDeath()) {
                continue;
            }
            double rate = c.getCurrentHp() / c.getMaxHp();
            if (rate < worstRate) {
                worstRate = rate;
                worst = c;
            }
        }
        return worst;
    }

    private static String fmt(double value) {
        return String.format("%.0f", value);
    }
}
