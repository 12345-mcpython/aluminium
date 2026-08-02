package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;

import java.util.List;

/**
 * 德谬歌 (Cyrene 昔涟's 忆灵) 忆灵技 — including her character-specific
 * support functions from servant_skills.json (SkillCY01-CY14):
 * <ul>
 *   <li>CY01 献予「创世」之诗 → 开拓者·记忆 (8007/8008)</li>
 *   <li>CY02 献予「浪漫」之诗 → 阿格莱雅 (1402)</li>
 *   <li>CY03 献予「门径」之诗 → 缇宝 (1403)</li>
 *   <li>CY04 献予「纷争」之诗 → 万敌 (1404)</li>
 *   <li>CY05 献予「生死」之诗 → 遐蝶 (1407, 新蕊机制, 不可表达)</li>
 *   <li>CY06 献予「理性」之诗 → 那刻夏 (1405)</li>
 *   <li>CY07 献予「天空」之诗 → 风堇 (1409)</li>
 *   <li>CY08 献予「诡计」之诗 → 赛飞儿 (1406)</li>
 *   <li>CY09 献予「负世」之诗 → 白厄 (1408)</li>
 *   <li>CY10 献予「海洋」之诗 → 海瑟音 (1410)</li>
 *   <li>CY11 献予「律法」之诗 → 刻律德菈 (1412)</li>
 *   <li>CY12 献予「岁月」之诗 → 长夜月 (1413)</li>
 *   <li>CY13 献予「大地」之诗 → 丹恒·腾荒 (1414)</li>
 *   <li>CY14 献予「真我」之诗 → 昔涟自身 (1415, 追忆机制, 不可表达)</li>
 * </ul>
 * 大黑塔 (1401) 无对应诗篇 — 昔涟的忆灵技不对其提供特定辅助。
 */
public class CyreneServantSkill extends ServantSkill {

    public CyreneServantSkill(int servantId, int skillIndex, int level) {
        super(servantId, skillIndex, level);
    }

    @Override
    protected void support(Battle battle, CanHit user, List<? extends CanHit> targets, List<Double> params) {
        if (targets == null || targets.isEmpty()) {
            return;
        }
        CanHit target = targets.getFirst();
        if (!(target instanceof Character character)) {
            super.support(battle, user, targets, params);
            return;
        }
        // 诗篇技能是独立的忆灵技条目 (SkillCY01-CY14, servant skill index 13-26);
        // 按目标角色查取对应诗篇的数值.
        List<Double> poemParams = CyreneServantSkill.poemParams(character.getCid());
        if (poemParams == null) {
            // 1401 大黑塔: 无特定辅助; 非目标角色: 通用增益.
            super.support(battle, user, targets, params);
            return;
        }
        switch (character.getCid()) {
            case 8007, 8008 -> cy01(battle, user, character, poemParams);
            case 1402 -> cy02(battle, user, character, poemParams);
            case 1403 -> cy03(battle, user, character, poemParams);
            case 1404 -> cy04(battle, user, character, poemParams);
            case 1405 -> cy06(battle, user, character, poemParams);
            case 1409 -> cy07(battle, user, character, poemParams);
            case 1406 -> cy08(battle, user, character, poemParams);
            case 1408 -> cy09(battle, user, character, poemParams);
            case 1410 -> cy10(battle, user, character, poemParams);
            case 1412 -> cy11(battle, user, character, poemParams);
            case 1413 -> cy12(battle, user, character, poemParams);
            case 1414 -> cy13(battle, user, character, poemParams);
            // 1407 (新蕊机制) / 1415 (追忆机制): 不可表达 → 通用增益.
            default -> super.support(battle, user, targets, params);
        }
    }

    /**
     * The poem skill parameters for the target character:
     * 开拓者·记忆→CY01(13), 阿格莱雅→CY02(14), 缇宝→CY03(15), 万敌→CY04(16),
     * 遐蝶→CY05(17), 那刻夏→CY06(18), 风堇→CY07(19), 赛飞儿→CY20(20),
     * 白厄→CY09(21), 海瑟音→CY10(22), 刻律德菈→CY11(23), 长夜月→CY12(24),
     * 丹恒·腾荒→CY13(25). 大黑塔(1401) 无对应诗篇.
     */
    public static List<Double> poemParams(int cid) {
        int index = switch (cid) {
            case 8007, 8008 -> 13;
            case 1402 -> 14;
            case 1403 -> 15;
            case 1404 -> 16;
            case 1407 -> 17;
            case 1405 -> 18;
            case 1409 -> 19;
            case 1406 -> 20;
            case 1408 -> 21;
            case 1410 -> 22;
            case 1412 -> 23;
            case 1413 -> 24;
            case 1414 -> 25;
            default -> 0;
        };
        if (index == 0) {
            return null;
        }
        SkillData data = ServantSkillData.init(11415, index);
        if (data.getSkills() != null && !data.getSkills().isEmpty()) {
            return data.getSkills().get(0);
        }
        return null;
    }

    private void log(CanHit target, String message) {
        IO.println("  [昔涟] " + target.getName() + ": " + message);
    }

    /** 献予「创世」之诗: ATK = 德谬歌生命上限×#1, 暴击率 = 德谬歌暴击率×#2, 整场生效. */
    private void cy01(Battle battle, CanHit user, Character target, List<Double> params) {
        double atk = user.getMaxHp() * params.get(0);
        double crit = user.getAttribute(AttributeType.CRIT_CHANCE) != null
                ? user.getAttribute(AttributeType.CRIT_CHANCE).get() * params.get(1) : 0;
        target.removeBuff("献予「创世」之诗");
        Buff buff = new Buff("献予「创世」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.ATTACK, DoubleValue.Modifier.pure(atk, DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "ATK+" + String.format("%.0f", atk) + " (创世之诗)");
    }

    /** 献予「浪漫」之诗: 【浪漫】SPD+#1, 伤害+#2, 无视防御#3. */
    private void cy02(Battle battle, CanHit user, Character target, List<Double> params) {
        double spd = params.get(0);
        double dmg = params.get(1);
        double defIgnore = params.get(2);
        target.removeBuff("浪漫");
        Buff buff = new Buff("浪漫", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.SPEED, DoubleValue.Modifier.pure(spd, DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                        DoubleValue.Modifier.ModifierSource.BUFF))
                .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "【浪漫】 SPD+" + String.format("%.0f", spd) + ", 伤害+"
                + String.format("%.0f%%", dmg * 100));
    }

    /** 献予「门径」之诗: 缇宝伤害无视防御#2, 整场生效. */
    private void cy03(Battle battle, CanHit user, Character target, List<Double> params) {
        double defIgnore = params.get(1);
        target.removeBuff("献予「门径」之诗");
        Buff buff = new Buff("献予「门径」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.DEFENCE_IGNORE, DoubleValue.Modifier.pure(defIgnore,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "无视防御 " + String.format("%.0f%%", defIgnore * 100) + " (门径之诗)");
    }

    /** 献予「纷争」之诗: 万敌暴击伤害+#1 (本次攻击), 不处于血仇状态则行动提前#2. */
    private void cy04(Battle battle, CanHit user, Character target, List<Double> params) {
        double critDmg = params.get(0);
        double advance = params.get(1);
        target.removeBuff("献予「纷争」之诗");
        Buff buff = new Buff("献予「纷争」之诗", Buff.Category.BUFF, user, target, 1)
                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        battle.advanceByPercent(target, advance);
        log(target, "暴击伤害+" + String.format("%.0f%%", critDmg * 100) + " 并行动提前");
    }

    /** 献予「理性」之诗: 恢复#4个战技点, 那刻夏立即行动, 并挂载诗篇标记. */
    private void cy06(Battle battle, CanHit user, Character target, List<Double> params) {
        int sp = (int) Math.round(params.get(3));
        battle.addSkillPoints(sp);
        battle.advanceByPercent(target, 1.0);
        // 诗篇标记: 那刻夏下一次施放普攻/战技时触发【真知】.
        Buff marker = new Buff("献予「理性」之诗", Buff.Category.BUFF, user, target, 1);
        battle.applyBuff(target, marker);
        log(target, "恢复" + sp + "个战技点并立即行动 (理性之诗)");
    }

    /** 献予「天空」之诗: 风堇恢复#2点能量, 并挂载诗篇标记. */
    private void cy07(Battle battle, CanHit user, Character target, List<Double> params) {
        double energy = params.get(1);
        target.gainEnergy(energy);
        Buff marker = new Buff("献予「天空」之诗", Buff.Category.BUFF, user, target, -1);
        battle.applyBuff(target, marker);
        log(target, "恢复" + String.format("%.0f", energy) + "点能量 (天空之诗)");
    }

    /** 献予「诡计」之诗: 赛飞儿伤害+#1, 整场生效. */
    private void cy08(Battle battle, CanHit user, Character target, List<Double> params) {
        double dmg = params.get(0);
        target.removeBuff("献予「诡计」之诗");
        Buff buff = new Buff("献予「诡计」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "伤害+" + String.format("%.0f%%", dmg * 100) + " (诡计之诗)");
    }

    /** 献予「负世」之诗: 白厄暴击率+#1 (持有永续的燃烧时). */
    private void cy09(Battle battle, CanHit user, Character target, List<Double> params) {
        double crit = params.get(0);
        target.removeBuff("献予「负世」之诗");
        Buff buff = new Buff("献予「负世」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.CRIT_CHANCE, DoubleValue.Modifier.pure(crit,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "暴击率+" + String.format("%.0f%%", crit * 100) + " (负世之诗)");
    }

    /** 献予「海洋」之诗: 海瑟音伤害+#1, 恢复#4点能量. */
    private void cy10(Battle battle, CanHit user, Character target, List<Double> params) {
        double dmg = params.get(0);
        double energy = params.get(3);
        target.removeBuff("献予「海洋」之诗");
        Buff buff = new Buff("献予「海洋」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        target.gainEnergy(energy);
        log(target, "伤害+" + String.format("%.0f%%", dmg * 100) + " 并恢复"
                + String.format("%.0f", energy) + "点能量 (海洋之诗)");
    }

    /** 献予「律法」之诗: 军功角色暴击伤害+#1 (代理: 对刻律德菈自身). */
    private void cy11(Battle battle, CanHit user, Character target, List<Double> params) {
        double critDmg = params.get(0);
        target.removeBuff("献予「律法」之诗");
        Buff buff = new Buff("献予「律法」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.CRIT_ATTACK, DoubleValue.Modifier.pure(critDmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "暴击伤害+" + String.format("%.0f%%", critDmg * 100) + " (律法之诗)");
    }

    /** 献予「岁月」之诗: 长夜月伤害+#1, 整场生效. */
    private void cy12(Battle battle, CanHit user, Character target, List<Double> params) {
        double dmg = params.get(0);
        target.removeBuff("献予「岁月」之诗");
        Buff buff = new Buff("献予「岁月」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "伤害+" + String.format("%.0f%%", dmg * 100) + " (岁月之诗)");
    }

    /** 献予「大地」之诗: 同袍伤害+#1 (代理: 对丹恒·腾荒自身), 龙灵行动提前. */
    private void cy13(Battle battle, CanHit user, Character target, List<Double> params) {
        double dmg = params.get(0);
        target.removeBuff("献予「大地」之诗");
        Buff buff = new Buff("献予「大地」之诗", Buff.Category.BUFF, user, target, -1)
                .stat(AttributeType.ALL_DAMAGE_TYPE_BOOST, DoubleValue.Modifier.pure(dmg,
                        DoubleValue.Modifier.ModifierSource.BUFF));
        battle.applyBuff(target, buff);
        log(target, "伤害+" + String.format("%.0f%%", dmg * 100) + " (大地之诗)");
    }
}
