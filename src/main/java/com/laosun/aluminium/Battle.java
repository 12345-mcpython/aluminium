package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;


public class Battle {
    public Queue queue;

    public List<Character> characters;

    public List<Enemy> enemies;

    public Signal currentMove;

    public ArrayList<CanHit> addRequestItems = new ArrayList<>();

    public ArrayList<AdvanceRequest> advanceRequests = new ArrayList<>();

    private final ArrayList<SkillRequest> skillRequests = new ArrayList<>();

    /**
     * Injected random source (crit rolls / target selection); a fixed seed makes a
     * whole battle reproducible.
     */
    private final Random rng;

    public record AdvanceRequest(CanHit object, double rate) {
    }

    public record SkillRequest(Skill skill, CanHit object, List<? extends CanHit> target) {
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue) {
        this(characterQueue, enemyQueue, new Random());
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue, Random rng) {
        characters = characterQueue;
        enemies = enemyQueue;
        this.rng = rng;
        queue = new Queue();
        queue.addCombatants(characterQueue);
        queue.addCombatants(enemyQueue);
        queue.initialize();
    }

    public Random getRng() {
        return rng;
    }

    public void castImmediate(Skill skill, CanHit user, List<? extends CanHit> targets) {
        if (skill == null || user == null || targets == null) return;
        if (user.isDeath()) return;

        skill.execute(this, user, targets);

        processRequests();

        removeDeadCombatants();
    }

    public boolean requestSkill(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return false;
        }
        if (user.isDeath()) {
            return false;
        }
        skillRequest(skill, user, target);
        return true;
    }

    // After releasing ultra skill must call processRequests()!
    // NO BEFAN YOY DID IT
    public boolean castUltra(CanHit user, List<? extends CanHit> targets) {
        if (user == null || user.isDeath() || !user.isEnergyFull()) {
            return false;                       // 没满能量放不了（没有能量条的角色永远放不了）
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        if (ultra == null) {
            return false;
        }
        if (!requestSkill(ultra, user, targets)) {
            return false;
        }
        processRequests();                      // 大招本体结算：Ultra 槽在 onSkillCast 里不回能，不会重复给
        user.setCurrentEnergy(0);               // 先清零
        EnergyGain ultraGain = user.getEnergyProvider().onUltCast(user, ultra);
        if (ultraGain != null) {
            applyEnergyGain(user, ultraGain);   // 再回自身的 5 点（× 回能效率）
        }
        return true;
    }

    public void startBattle() {
        for (Signal signal : queue.snapshot()) {
            signal.getCanHit().onBattleStart(this);
        }
        processRequests();
    }

    public void stepForward() {
        queue.move();
        currentMove = queue.getCurrentActor();
    }

    public void beforeMove() {
        if (currentMove == null) {
            return;
        }
        CanHit actor = currentMove.getCanHit();
        actor.getBuffManager().beforeMove();
        if (actor.isDeath()) {
            return;
        }
        actor.beforeMove(this);
    }

    public boolean performAction(Skill skill, List<? extends CanHit> targets) {
        if (currentMove == null || skill == null || targets == null) {
            return false;
        }
        CanHit actor = currentMove.getCanHit();
        if (actor.isDeath()) {
            return false;
        }
        if (!actor.getBuffManager().canAct()) {
            return false;
        }
        return useSkill(skill, targets);
    }

    public boolean useSkill(Skill skill, List<? extends CanHit> target) {
        if (currentMove == null || skill == null || target == null) {
            return false;
        }
        CanHit user = currentMove.getCanHit();
        if (user.isDeath()) {
            return false;
        }
        skillRequest(skill, user, target);
        return true;
    }

    public void afterMove() {
        if (currentMove == null) {
            return;
        }

        CanHit actor = currentMove.getCanHit();

        if (actor.isDeath()) {
            actor.getBuffManager().clearAll();
            queue.removeCombatant(actor);
        } else {
            queue.setTopZero();
        }
        currentMove = null;
        removeDeadCombatants();
        if (!actor.isDeath()) {
            actor.afterMove(this);
            actor.getBuffManager().afterMove();
        }
        processRequests();
    }

    public void processRequests() {
        processSkillRequests();
        processAddRequests();
        processAdvanceRequests();
        removeDeadCombatants();
    }

    private void processSkillRequests() {
        if (skillRequests.isEmpty()) {
            return;
        }
        List<SkillRequest> requests = new ArrayList<>(skillRequests);
        skillRequests.clear();

        for (SkillRequest req : requests) {
            if (req.object.isDeath()) {
                continue;
            }
            req.skill().execute(this, req.object(), req.target());
        }
    }

    private void skillRequest(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return;
        }
        skillRequests.add(new SkillRequest(skill, user, target));
    }

    private void addRequest(CanHit canHit) {
        addRequestItems.add(canHit);
    }


    /**
     * Assembles every damage zone on the given hit, settles it and applies it to the
     * target, returning the damage actually settled.
     *
     * <p>This is the <b>only</b> public settlement entry point. To learn how much a hit
     * deals, use the returned value — do not assemble it a second time: assembly is
     * additive ({@code addBoost} appends a modifier to the boost zone), so assembling the
     * same {@link Damage} twice would count 增伤/易伤/减伤/虚弱 twice.
     *
     * @param target the combatant taking the hit
     * @param damage the hit (约定：一段伤害 = 一个 Damage 对象)
     * @return the settled damage, or {@code 0} if the target was already dead
     */
    public double applyDamage(CanHit target, Damage damage) {
        if (target.isDeath() || target.isInvulnerable()) {
            return 0;                  // 尸体 / 转阶段无敌：不结算（也就不会鞭尸）
        }
        double settled = assemble(damage);
        boolean died = target.takeDamage(settled);
        grantHitAndKillEnergy(target, damage, died);     // P3-2：受击回能 / 击杀回能
        return settled;
    }

    /**
     * 战斗内**唯一**回能入口（P3-2）：规则由 {@code target} 自己的
     * {@link com.laosun.aluminium.models.energy.EnergyProvider} 决定；团队充能（停云/藿藿/星期日）
     * 将来也从这里给别的目标回能。
     *
     * @param target 回能的人
     * @param gain   一次回能描述（基础值 + 是否吃回能效率）
     * @return 实际入账值（被上限截断后），入账不了就是 0
     */
    public double applyEnergyGain(CanHit target, EnergyGain gain) {
        return target == null ? 0 : target.gainEnergy(gain);
    }

    /**
     * 便捷入口：按基础值给某人回能（走回能效率）。
     *
     * @param target 回能的人
     * @param amount 基础回能值
     * @return 实际入账值
     */
    public double grantEnergy(CanHit target, double amount) {
        return applyEnergyGain(target, EnergyGain.normal(amount));
    }

    /**
     * 技能回能挂点（P3-2）：由 {@link SkillExecutor} 在技能执行处调用——只有那里知道
     * **实际命中集**（AOE 打全场、BLAST 打三格、BOUNCE 每段换目标）。
     *
     * <p>注意区分：这里是"施放技能的"回能（普攻 20 / 战技 30），终结技不走 {@code onSkillCast}
     * （它在 {@link #castUltra} 里先清零再回 5）。
     *
     * @param user       施放者
     * @param skill      施放的技能
     * @param hitTargets 实际命中集（增益/治疗类技能为空集）
     * @return 实际入账值
     */
    public double grantSkillEnergy(CanHit user, Skill skill, Set<? extends CanHit> hitTargets) {
        if (user == null) {
            return 0;
        }
        EnergyGain gain = user.getEnergyProvider().onSkillCast(user, skill, hitTargets);
        return gain == null ? 0 : applyEnergyGain(user, gain);
    }

    /**
     * 击破回能（P3-3）：击破瞬间由 P4-4 调这**一个**口子，规则仍归击破者自己的 provider
     * （标准实现给 5；乱破 +10、同谐开拓者 +10、忘归人 +3 这类等真做角色时再各自实现）。
     *
     * @param attacker 造成击破的人
     * @param target   被击破的目标
     * @return 实际入账值
     */
    public double gainBreakEnergy(CanHit attacker, CanHit target) {
        if (attacker == null) {
            return 0;
        }
        EnergyGain gain = attacker.getEnergyProvider().onBreak(attacker, target);
        return gain == null ? 0 : applyEnergyGain(attacker, gain);
    }

    /**
     * 受击回能 + 击杀回能（P3-2）。
     *
     * <p>口径：附加伤害 / 真实伤害「不视为造成了 1 次攻击」→ 两边都不回能；
     * 击杀了目标的那一发只结算击杀回能（记给 {@code damage.getAttacker()}），
     * 挨打的那一方已经死了就不必再涨能量。
     *
     * @param target 挨打的人
     * @param damage 这一发伤害
     * @param died   这一发是否打死了 {@code target}
     */
    private void grantHitAndKillEnergy(CanHit target, Damage damage, boolean died) {
        if (!damage.isCountsAsAttack()) {
            return;
        }
        if (!died) {
            EnergyGain hitGain = target.getEnergyProvider().onTakingHit(target, damage);
            if (hitGain != null) {
                applyEnergyGain(target, hitGain);
            }
            return;
        }
        CanHit attacker = damage.getAttacker();
        if (attacker != null) {
            EnergyGain killGain = attacker.getEnergyProvider().onKill(attacker, target);
            if (killGain != null) {
                applyEnergyGain(attacker, killGain);
            }
        }
    }

    /**
     * 附加伤害：面板型 base（攻击力 / 生命上限 × 倍率），**走完整乘区**（增伤/防御/抗性/易伤都吃）。
     *
     * <p>官方定义：「使受击者额外受到 1 次伤害，本次伤害不视为造成了 1 次攻击」——
     * 所以置 {@code notCountsAsAttack()}（不回能、不削韧、不触发攻击级事件）。
     *
     * @param base 已经算好的基础值（例：知更鸟 120% 攻击力 / 缇宝 12% 生命上限）
     * @return 该段结算值（0 = 未造成伤害）
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage extra = new Damage(attacker, target, element, DamageType.ADDITIONAL, base);
        return applyDamage(target, extra.notCountsAsAttack());
    }

    /**
     * 真实伤害：固定数额，或"本次攻击总伤害 × %"这类衍生值——**跳过全部乘区**，不视为一次攻击。
     *
     * @param base 真伤数额（不再受防御/抗性/增伤/暴击/易伤影响）
     * @return 该段结算值（0 = 未造成伤害）
     */
    public double applyTrueDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage trueDamage = new Damage(attacker, target, element, DamageType.TRUE, base);
        return applyDamage(target, trueDamage.trueDamage().notCountsAsAttack());
    }

    /**
     * Enemies that may be selected as attack targets (= alive), in battlefield order.
     *
     * <p>Single source of truth for "who can be hit": {@link SkillExecutor} uses it today,
     * the target selector (P5-4) and wave handling (P7-4) must use the same judgement so
     * that no caller ever picks a corpse (那才是鞭尸的来源).
     *
     * @return a fresh list of alive enemies
     */
    public List<Enemy> targetableEnemies() {
        List<Enemy> targets = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (!enemy.isDeath()) {
                targets.add(enemy);
            }
        }
        return targets;
    }

    /**
     * Zone assembly + settlement: 增伤 → 暴击 → 防御 → 抗性 → {@link Damage#toValue()}.
     *
     * <p>Private on purpose: the only way in is {@link #applyDamage(CanHit, Damage)}, which
     * makes "the same hit assembled twice" structurally impossible.
     *
     * @param damage the hit to assemble and settle
     * @return the final damage of this hit, floored at 1
     */
    private double assemble(Damage damage) {
        CanHit attacker = damage.getAttacker();
        CanHit defender = damage.getDefender();

        // 1) 增伤区：元素增伤 + 全增伤（击破/超击破/真伤会被 BoostArea.applies() 自动跳过）
        AttributeType elementBoost = AttributeType.getBoostByElement(damage.getElement());
        if (elementBoost != null) {
            damage.addBoost(attacker.getAttribute(elementBoost).get());
        }
        damage.addBoost(attacker.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());

        // 2) 暴击区：可暴击类型才骰；效果已指定双暴（fixedCrit）时不再覆盖
        if (damage.getType().isCrittable() && !damage.isCritFixed()) {
            double critRate = attacker.getAttribute(AttributeType.CRIT_CHANCE).get();
            boolean isCrit = critRate > 0 && rng.nextDouble() < critRate;
            damage.crit(isCrit, attacker.getAttribute(AttributeType.CRIT_ATTACK).get());
        }

        // 3) 防御区：攻击者等级 / 受击者防御 / 攻击者无视防御
        damage.defence(attacker.getLevel(),
                defender.getAttribute(AttributeType.DEFENCE).get(),
                attacker.getAttribute(AttributeType.DEFENCE_IGNORE).get());

        // 4) 抗性区：受击者抗性 - 攻击者穿透，再 clamp（HSR.md §2.5，负抗全效）
        //    注：弱点击破不改变抗性（§2.5；P4 复核）
        double rawResist = defender instanceof Enemy enemy
                ? enemy.getDamageResist().getOrDefault(damage.getElement(), 0.0)
                : 0.0;
        damage.resist(rawResist, attacker.getAttribute(AttributeType.DAMAGE_PENETRATION).get());

        // 5) 钩子：实体级 DamageEvent（HSR.md §2.2：虚弱=攻击方负面、易伤=受击方负面、减伤=受击方增益）
        //    双方都发；默认实现转发给各自的 BuffManager，子类重写可做天赋/Boss 机制
        attacker.onDamage(this, damage);
        defender.onDamage(this, damage);

        return Math.max(1, damage.toValue());
    }

    private void processAddRequests() {
        if (addRequestItems.isEmpty()) {
            return;
        }
        for (CanHit canHit : addRequestItems) {
            queue.addCombatant(canHit);
        }
        addRequestItems.clear();
    }

    public void advanceRequest(CanHit canHit, double rate) {
        if (canHit != null && rate >= 0 && rate <= 1) {
            advanceRequests.add(new AdvanceRequest(canHit, rate));
        }
    }

    private void processAdvanceRequests() {
        if (advanceRequests.isEmpty()) {
            return;
        }
        for (AdvanceRequest advanceRequest : advanceRequests) {
            queue.advanceActionByPercent(advanceRequest.object, advanceRequest.rate);
        }
        advanceRequests.clear();
    }

    private void removeDeadCombatants() {
        for (Signal signal : queue.snapshot()) {
            if (signal.getCanHit().isDeath()) {
                queue.removeCombatant(signal.getCanHit());
            }
        }
    }

    public List<Signal> getQueueSnapshot() {
        return queue.snapshot();
    }

    public void printBattle() {
        printHp();
        queue.printActionQueue();
    }

    public void printHp() {
        System.out.println("=== HP Status ===");
        for (Character c : characters) {
            System.out.printf("%s: %.0f / %.0f%n", c.getName(), c.getCurrentHp(), c.getMaxHp());
        }
        for (Enemy e : enemies) {
            System.out.printf("%s: %.0f / %.0f%n", e.getName(), e.getCurrentHp(), e.getMaxHp());
        }
        System.out.println("================");
    }
}
