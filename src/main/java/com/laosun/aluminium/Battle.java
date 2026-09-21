package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.energy.EnergyGain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class Battle {
    /**
     * 战斗状态机（P7-3）。
     *
     * <pre>
     *   NOT_STARTED ──startBattle()──▶ RUNNING ──一方全灭──▶ WIN / LOSE
     *                                   ▲                      │
     *                                   └──── (不回退) ────────┘
     * </pre>
     *
     * <p>{@code WIN} / {@code LOSE} 是**终态**：{@link #stepForward()} 不再推进行动条。
     */
    public enum Status {
        NOT_STARTED, RUNNING, WIN, LOSE
    }

    public Queue queue;

    public List<Character> characters;

    public List<Enemy> enemies;

    public Signal currentMove;

    /**
     * 当前战斗状态（P7-3）。开场是 {@link Status#NOT_STARTED}，由 {@link #startBattle()} 转成
     * {@link Status#RUNNING}。
     */
    private Status status = Status.NOT_STARTED;

    /**
     * 波次管理（P7-4）；非波次战斗为 {@code null}。
     *
     * <p>存在的唯一理由是让 {@link #checkResult()} 知道"敌队是空的"到底是
     * **打赢了**还是**这一波还没进**。
     */
    private WaveManager waveManager;

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
        // P7 修正 E2：把"速度变化 → 重排行动条"接上。没有这条，加速/减速不会立刻生效
        // （Signal 缓存的 speed 只会在 initialize/setTopZero/resetSignal 这三个时机被刷新）。
        for (Character c : characterQueue) {
            c.setSpeedChangeListener(this::onSpeedChanged);
        }
        for (Enemy e : enemyQueue) {
            e.setSpeedChangeListener(this::onSpeedChanged);
        }
    }

    /**
     * 某个单位的速度变了 → 按"已积累的行动进度"重排他的行动时间（P7 修正 E2）。
     *
     * <p>调用方是 {@link CanHit#notifySpeedChanged()}；它会先比对旧值，
     * 只有真的变化了才通知，所以这里不需要再做判重。
     *
     * @param target 速度发生变化的单位
     */
    private void onSpeedChanged(CanHit target) {
        if (target == null || target.isDeath()) {
            return;
        }
        queue.refreshSpeed(target);
        currentMove = queue.getCurrentActor();       // 重排可能改了谁是下一个
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
        // P7-2：额外回合期间禁止插入**别人**的终结技。
        // 规则见 HSR.md §3.1；不拦的话"额外回合"可以被终结技无限续下去。
        CanHit extraTurnActor = queue.getExtraTurnActor();
        if (extraTurnActor != null && extraTurnActor != user) {
            return false;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        if (ultra == null) {
            return false;
        }
        if (!requestSkill(ultra, user, targets)) {
            return false;
        }
        // H-5：**先清零**（ROADMAP P3-2 的顺序），再让大招本体结算。
        // 顺序反了会吃掉大招自己赚的能量：processRequests() 里的击杀回能 / 击破回能都记给
        // damage.getAttacker()（= 放大招的人），先结算后清零就把那几笔抹掉了。
        user.setCurrentEnergy(0);
        processRequests();                      // 大招本体结算：Ultra 槽在 onSkillCast 里不回能，不会重复给
        EnergyGain ultraGain = user.getEnergyProvider().onUltCast(user, ultra);
        if (ultraGain != null) {
            applyEnergyGain(user, ultraGain);   // 再回自身的 5 点（× 回能效率）
        }
        return true;
    }

    public void startBattle() {
        status = Status.RUNNING;
        for (Signal signal : queue.snapshot()) {
            signal.getCanHit().onBattleStart(this);
        }
        processRequests();
        checkResult();
    }

    public void stepForward() {
        if (isOver()) {
            return;                                  // 终态：不再推进行动条（P7-3）
        }
        queue.move();
        currentMove = queue.getCurrentActor();
    }

    /**
     * 当前战斗状态（P7-3）。
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 战斗是否已经结束（胜或负）。
     */
    public boolean isOver() {
        return status == Status.WIN || status == Status.LOSE;
    }

    /**
     * 判定胜负并落状态（P7-3）。**幂等**：已经是终态就什么都不做（终态不回退）。
     *
     * <p>口径：
     * <ul>
     *   <li>某一方**全灭**即判负 —— 用 {@code allMatch(isDeath)}，所以**空列表也算全灭**
     *       （空的一方就是被清光了）。</li>
     *   <li>{@link Status#NOT_STARTED} 时不判：战斗还没开场，谈不上胜负。
     *       所以这个判定只在 {@link #startBattle()} 之后生效。</li>
     *   <li>两边同时全灭 → {@code LOSE}（先判负后判胜，且**不会**从终态继续判定）。</li>
     * </ul>
     *
     * @return 判定之后的当前状态
     */
    public Status checkResult() {
        if (status != Status.RUNNING) {
            return status;
        }
        // P7-4：还有没进的波 → 敌队为空只代表"这一波还没进"，不能判胜。
        boolean pendingWaves = waveManager != null && waveManager.hasPendingWaves();
        if (characters.stream().allMatch(CanHit::isDeath)) {
            status = Status.LOSE;                    // 我方全灭是真输了，有没有待进的波都一样
        } else if (enemies.stream().allMatch(CanHit::isDeath) && !pendingWaves) {
            status = Status.WIN;
        }
        return status;
    }

    /**
     * 登记波次管理器（P7-4）。由 {@link WaveManager} 的构造函数调用，业务代码不用手调。
     */
    public void setWaveManager(WaveManager waveManager) {
        this.waveManager = waveManager;
    }

    /**
     * 当前波次管理器（P7-4）；非波次战斗为 {@code null}。
     */
    public WaveManager getWaveManager() {
        return waveManager;
    }

    /**
     * 给 {@code actor} 一个**额外回合**（P7-2）：下一次 {@link #stepForward()} 由他行动，
     * 且**不消耗行动值**（时钟不动 → 轮次不变，见 {@link #getRound()}）。
     *
     * <p>典型用法是击杀型天赋（希儿等，ROADMAP P5-9）：在 {@code afterMove()} 里
     * ——也就是 {@code queue.setTopZero()} 之后——调用，这样他的**正常**回合排期原封不动，
     * 额外回合是白送的一次。
     *
     * <p>额外回合期间**不能插入别人的终结技**（见 {@link #castUltra}）——
     * 这是规则要求；在额外回合里再插一次终结技会把"额外"变成"无限连"。
     *
     * @param actor 获得额外回合的单位
     * @return {@code true} = 已安排；目标已死 / 不在队列里则 {@code false}
     */
    public boolean grantExtraTurn(CanHit actor) {
        return queue.grantExtraTurn(actor);
    }

    /**
     * 当前安排的额外回合行动者（P7-2）；没有则 {@code null}。
     */
    public CanHit getExtraTurnActor() {
        return queue.getExtraTurnActor();
    }

    /**
     * 当前轮次（P7-1）：由行动条的累计行动值推算，首轮 = 1。
     *
     * <p>一轮 = 100 行动值、首轮 = 150（见 {@code Queue.initialize()}）。
     * 这只是查询口；真正的轮次驱动（胜负判定、关卡回合上限）在 P7-3。
     *
     * @return 轮次，从 1 开始
     */
    public int getRound() {
        return queue.getRound();
    }

    public void beforeMove() {
        if (currentMove == null) {
            return;
        }
        CanHit actor = currentMove.getCanHit();
        if (actor instanceof Enemy enemy) {
            tickDots(enemy);                         // P4-5：敌人回合开始时先结算持续伤害
            if (enemy.isDeath()) {
                return;
            }
        }
        actor.getBuffManager().beforeMove();
        if (actor.isDeath()) {
            return;
        }
        actor.beforeMove(this);
    }

    /**
     * 结算一个敌人身上的持续伤害（P4-5）：**先上先结算**（按施加顺序）。
     *
     * <p>DOT 走完整乘区（吃增伤、吃防御/抗性，易伤/减伤由 {@code onDamage} 钩子注入），
     * 但不可暴击——由 {@link DamageType#DOT} 的 {@code crittable=false} 表达。
     *
     * @param enemy 目标
     * @return 本次结算的总伤害（所有 DOT 之和）
     */
    public double tickDots(Enemy enemy) {
        if (enemy == null || enemy.isDeath()) {
            return 0;
        }
        double total = 0;
        for (Dot dot : new ArrayList<>(enemy.getDots())) {       // 快照迭代：结算中可能移除
            if (enemy.isDeath()) {
                break;                                           // 被 DOT 打死 → 剩下的不再结算
            }
            Damage damage = new Damage(dot.getSource(), enemy, dot.getElement(),
                    DamageType.DOT, dot.getBaseDamage());
            // KILL_ONLY：DOT 不是"一次攻击行为"，不给受击方回能；但 DOT 击杀仍记给施加者
            total += applyDamage(enemy, damage, EnergyGrant.KILL_ONLY);
            if (dot.tick()) {
                enemy.removeDot(dot);
            }
        }
        return total;
    }

    /**
     * 击破附带持续伤害（P4-5）：只有火/雷/物理/风 才有
     * （冰=冻结、量子=纠缠、虚数=禁锢，P10-1 统一成表）。
     *
     * @param attacker 击破者（DOT 的来源，也是伤害的攻击者）
     * @param enemy    被击破的目标
     * @param element  击破元素
     */
    private void attachBreakDot(CanHit attacker, Enemy enemy, DamageElement element) {
        if (!Constant.DOT_ELEMENTS.contains(element)) {
            return;
        }
        enemy.addDot(new Dot(attacker, element,
                BreakDamageCalculator.breakBaseOf(attacker) * Constant.DOT_RATIO, Constant.DOT_TURNS));
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
        return applyDamage(target, damage, EnergyGrant.ALL);
    }

    /**
     * 内部结算入口：比公开版本多一个"这一段允许结算哪种回能"的参数。
     *
     * <p>为什么需要它（2026-09-19 口径）：**一次攻击行为只给受击方回一次能**。
     * 一次攻击可以派生多种伤害类型（技能伤害 → 击破伤害 → 超击破伤害），如果每段都给受击方
     * 回能，受击方就会因为"被打得更狠"而回更多能 —— 这不对。所以只有这一发的**主段**带
     * {@link EnergyGrant#ALL}，派生段一律 {@link EnergyGrant#KILL_ONLY}。
     *
     * @param grant 这一段允许结算的回能种类
     */
    private double applyDamage(CanHit target, Damage damage, EnergyGrant grant) {
        if (target.isDeath() || target.isInvulnerable()) {
            return 0;                  // 尸体 / 转阶段无敌：不结算（也就不会鞭尸）
        }
        double settled = assemble(damage);                       // 乘区后的伤害（这是"打出去多少"）
        double hpBefore = target.getCurrentHp();
        boolean died = target.takeDamage(settled);
        double hpLoss = hpBefore - target.getCurrentHp();
        double shieldAbsorbed = target.getLastShieldAbsorbed();
        grantHitAndKillEnergy(target, damage, died, grant);     // P3-2：受击回能 / 击杀回能
        // 返回值 = 这一击**实际生效**的伤害 = 被盾吸走的 + 真的掉的血。
        // 目标是"打在有盾的目标上不能显示成 0"，同时**不能**把 settled 与盾吸收量相加
        // （settled 是"打出去的量"，盾吸走的那部分本来就在里面，相加会正好翻倍）。
        // 恒等式：settled == shieldAbsorbed + hpLoss（盾先吃、吃完才扣血）；分开算只是为了两段都可观察。
        return shieldAbsorbed + hpLoss;
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
     * 削韧 + 击破触发（P4-2）：**战斗内唯一的削韧入口**，{@link SkillExecutor} 每段伤害结算后调用。
     *
     * <p>规则（HSR.md §3.2 / §7.1）：
     * <ul>
     *   <li><b>只有命中弱点才削韧</b>——非弱点元素一点都不削（"无视弱点削韧"是乱破/姬子·启行这类
     *       角色特性，等 P8-7 数据化，别在这里开默认口子）</li>
     *   <li><b>但超击破不受弱点限制</b>：敌人**已处于击破状态**时，整发标称削韧都算"超出部分"，
     *       与元素是否命中弱点无关（官方文案的条件里只有"敌人处于弱点击破状态"）。
     *       非弱点攻击打已击破的敌人照样产生超击破段</li>
     *   <li>没有韧性条（数据里确有 {@code stance = 0} 的怪）/ 已击破 / 已死亡 → 不削</li>
     *   <li>韧性归零 → 由这里触发击破（{@code Enemy.reduceStance} 自己不做判定）</li>
     * </ul>
     *
     * <p>击破链的顺序（后续任务往这里加东西）：击破状态 → 击破伤害（P4-3）→ 推条（P4-4）→
     * 挂 DOT（P4-5）→ 击破回能（P3-3）。
     *
     * <p><b>返回值同时给出"超出部分"（P4-6）</b>：设技能标称削韧 {@code S}、剩余韧性 {@code T}，
     * 这一个 {@code S} 会被**拆给两条链**——击破伤害用 {@code min(S,T)}（{@link StanceResult#consumed()}），
     * 超击破伤害用 {@code max(0, S-T)}（{@link StanceResult#overkill()}），相加恒等于 {@code S}。
     * 所以调用方不能只留一个数，否则破韧的那一发会漏掉超击破段。
     *
     * @param attacker     攻击者（击破伤害与击破回能都记给他）
     * @param enemy        挨打的目标
     * @param element      这一段伤害的元素（决定是否弱点，也是击破元素）
     * @param stanceDamage 削韧点数（技能 {@code stance_list} 的值，单位「点」；**不是**每段固定值，
     *                     弹射类由 {@link SkillExecutor} 把总值均摊到每一段）
     * @return 这一段的削韧结果（实际削掉多少 / 超出多少 / 是否触发击破）
     */
    public StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage) {
        if (attacker == null || enemy == null || stanceDamage <= 0) {
            return StanceResult.NONE;
        }
        // 弱点判定必须在"已击破"分支**之前**：超击破是"把打不进韧性条的削韧转化掉"，
        // 前提仍是"这一发本来就削得动韧性"（只有弱点才削）。若先判 broken 就返回整发超出，
        // 非弱点攻击打已击破的敌人也会凭空产生超击破 —— 这是错的。
        if (enemy.isDeath()) {
            return StanceResult.NONE;
        }
        if (enemy.isBroken() || !enemy.hasToughnessBar()) {
            // 韧性条已空（已击破 / 数据里 stance = 0 的怪）⇒ 整发标称削韧都落不进韧性条，
            // 全部算"超出部分"，这就是超击破的输入（P4-6）。
            //
            // **这里刻意不判弱点**：官方文案是"攻击处于弱点击破状态的敌人后，会将本次攻击的
            // 削韧值转化为 1 次超击破伤害"——条件里只有"敌人已处于击破状态"，没有元素限制。
            // 所以非弱点元素打已击破的敌人**照样**产生超击破（用整发标称削韧值）。
            // 对比下面的常规削韧：那一步仍然严格"只有弱点才削"。
            return new StanceResult(0, stanceDamage, 0, false);
        }
        if (!enemy.isWeakTo(element)) {
            return StanceResult.NONE;        // 未击破时：非弱点一点都不削，也就没有超出部分
        }
        // H-4：击破伤害按**这一段实际削掉的值**算，不是技能的标称削韧值
        double consumed = enemy.reduceStance(stanceDamage);
        double overkill = stanceDamage - consumed;                 // P4-6：超出部分 = 超击破的输入
        if (enemy.getStance() > 0) {
            return new StanceResult(consumed, 0, 0, false);        // 没打空：没有超出部分可用
        }
        enemy.breakEnemy(element);
        enemy.setBrokenRemainTurns(Constant.BROKEN_REMAIN_TURNS);
        // 击破伤害在这里就结算掉了，所以必须把结算值带出去 —— 它属于**这一次攻击**，
        // 漏掉会让 AttackEvent.totalDamage 少算一整条击破链。
        // KILL_ONLY：击破是主段派生的额外伤害，不给受击方回能（一次攻击只回一次）；
        // 但若主段没打死、击破补刀打死，击杀回能仍然记给攻击者。
        double breakDamage = applyDamage(enemy,
                BreakDamageCalculator.build(attacker, enemy, element, consumed), EnergyGrant.KILL_ONLY); // P4-3
        delayMovePercent(enemy, Constant.BREAK_DELAY_RATIO);                                       // P4-4 推条
        attachBreakDot(attacker, enemy, element);                                                  // P4-5 DOT
        gainBreakEnergy(attacker, enemy);            // P3-3
        return new StanceResult(consumed, overkill, breakDamage, true);
    }

    /**
     * 一次削韧的结果（P4-6）。
     *
     * <p>两个削韧数**相加恒等于这一段的标称削韧值**，不重不漏。
     *
     * @param consumed    真正从韧性条上扣掉的点数（击破伤害用这个）
     * @param overkill    超出剩余韧性的点数（超击破伤害用这个；没打空时为 0）
     * @param breakDamage 这一次触发的**击破伤害结算值**（0 = 没触发击破）。
     *                    它是在本方法内部经 {@link #applyDamage} 结算的，调用方拿不到，
     *                    所以必须由返回值带出去 —— 否则一次攻击的"总伤害"会漏掉击破链
     *                    （见 {@code AttackEvent#totalDamage}）
     * @param broke       这一段是否把韧性打空并触发了击破
     */
    public record StanceResult(double consumed, double overkill, double breakDamage, boolean broke) {

        /**
         * 这一发没削到任何东西（没削韧 / 非弱点 / 目标已死）：两条链都不产生。
         */
        public static final StanceResult NONE = new StanceResult(0, 0, 0, false);

        /**
         * 超击破的削韧值输入：{@code max(0, S - T)} 的等价形式（见 {@code Battle.reduceToughness}）。
         */
        public double superBreakStance() {
            return overkill;
        }
    }

    /**
     * 按目标行动周期的百分比推条（P4-4）：{@code delay = 周期 × percent}，
     * 周期 = {@code 10000 / 速度}（与 {@code Queue} 的 {@code ACTION_THRESHOLD} 一致）。
     *
     * @param target  被推条的目标
     * @param percent 推条比例（0 ~ 1，HSR 击破 = 0.25）
     * @return {@code true} = 目标在行动条里且真的被推了
     */
    public boolean delayMovePercent(CanHit target, double percent) {
        if (target == null || percent <= 0) {
            return false;
        }
        double speed = target.getAttribute(AttributeType.SPEED).get();
        if (speed <= 0) {
            return false;
        }
        return queue.delayAction(target, 10000.0 / speed * percent);
    }

    /**
     * 击破中的敌人轮到自己回合时调用（P4-4）：递减击破回合数，到 0 就恢复韧性，并返回"本回合被跳过"。
     *
     * <p>调用方（现在的 {@code Main} 演示、P5-5 的敌方回合执行）拿到 {@code true} 就不要让他行动，
     * 直接走 {@link #afterMove()}。
     *
     * @param enemy 轮到行动的那个敌人
     * @return {@code true} = 他还在击破中，本回合不行动
     */
    public boolean handleBrokenTurn(Enemy enemy) {
        if (enemy == null || !enemy.isBroken()) {
            return false;
        }
        enemy.setBrokenRemainTurns(enemy.getBrokenRemainTurns() - 1);
        if (enemy.getBrokenRemainTurns() <= 0) {
            enemy.recoverFromBroken();
        }
        return true;
    }

    /**
     * 某个单位当前的仇恨值（P5-1/P5-2）：决定敌人选中它的概率。
     *
     * <p>取值优先级：角色数据里的 {@code aggro}（它就是游戏倍率本身：存护 150 / 毁灭 125 /
     * 其他 100 / 巡猎·智识 75）→ 没有数据时退回命途的默认档 → 非角色（敌人/召唤物）给 100。
     *
     * <p>**嘲讽不在这里**：嘲讽是"只能选中"的硬约束，由 {@link TargetSelector} 处理。
     * 用乘法把它塞进仇恨值只能提高概率，永远做不到"只能选中"。
     *
     * @param entity 要查询的单位
     * @return 仇恨值（&gt; 0）
     */
    public double aggroOf(CanHit entity) {
        if (entity instanceof Character character) {
            if (character.getAggro() > 0) {
                return character.getAggro();
            }
            return character.getPath().getAggro();
        }
        return 100;                                  // 敌人 / 召唤物：没有命途，给常规档
    }

    /**
     * 仇恨表：{@code 单位 → 受击概率}（P5-2）。概率之和为 1。
     *
     * @param allies 参选单位（调用方负责先过滤死亡目标）
     * @return 有序的 单位 → 概率 映射；空列表返回空表
     */
    public Map<CanHit, Double> getAggroTable(List<? extends CanHit> allies) {
        Map<CanHit, Double> table = new LinkedHashMap<>();
        double total = 0;
        for (CanHit ally : allies) {
            total += aggroOf(ally);
        }
        if (total <= 0) {
            return table;
        }
        for (CanHit ally : allies) {
            table.put(ally, aggroOf(ally) / total);
        }
        return table;
    }

    /**
     * 负面效果的生效概率（P6-1）：
     *
     * <pre>
     * 生效概率 = 基础概率 × (1 + 施加方效果命中%) × (1 - 受击方效果抵抗%) × (1 - 特定负面效果抵抗%)
     * </pre>
     *
     * <p>结果 clamp 到 {@code [0, 1]}：
     * <ul>
     *   <li>效果命中是**乘区**，所以命中 32% 时 80% 基础概率 → {@code 0.8 × 1.32 = 1.056 → 1.0}
     *       （不会超过 100%，但也不会有"超额命中转成别的收益"）；</li>
     *   <li>效果抵抗同样乘算：抵抗 30% 时 100% 基础概率 → {@code 0.7}；</li>
     *   <li>{@code specificResistKey} 是数据里的 {@code STAT_*} 串（见 {@code Enemy.debuffResist}）：
     *       冰锋 {@code {"STAT_CTRL_Frozen": 1}} → 关键因子 {@code (1 - 1) = 0} → **完全免疫**。
     *       只有 {@link Enemy} 有这张表，角色没有。</li>
     * </ul>
     *
     * <p>**只算概率，不掷骰**：掷骰在 {@link #rollDebuff}（用注入的 rng），
     * 这样 AI 可以"只看期望"而不消耗随机数。
     *
     * @param caster            施加者（读 {@code EFFECT_HIT_RATE}）
     * @param target            受击者（读 {@code EFFECT_RESISTANCE} 与可能的特定抵抗）
     * @param baseChance        技能面板上的基础概率（0.8 = 80%）
     * @param specificResistKey 特定抵抗键；{@code null} 或目标不是敌人 → 不查
     * @return 生效概率，落在 {@code [0, 1]}
     */
    public double hitChance(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        if (caster == null || target == null) {
            return 0;
        }
        double hit = caster.getAttribute(AttributeType.EFFECT_HIT_RATE).get();
        double resist = target.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double specific = 0;
        if (target instanceof Enemy enemy && specificResistKey != null) {
            specific = enemy.getDebuffResist().getOrDefault(specificResistKey, 0.0);
        }
        return Math.clamp(baseChance * (1 + hit) * (1 - resist) * (1 - specific), 0, 1);
    }

    /**
     * 失败判定之后，是否真的把这次负面效果挂上去（P6-1）。
     *
     * <p>用**注入的 {@link #rng}** 掷骰：同一个种子 → 同一场战斗可复现。
     *
     * @param caster          施加者（读它的效果命中）
     * @param target          受击者（读它的效果抵抗 / 特定抵抗）
     * @param baseChance      技能面板上的基础概率（0.8 = 80%）
     * @param specificResistKey 特定负面效果抵抗的键（数据里的 {@code STAT_*} 串），{@code null} = 不查
     * @return {@code true} = 命中，可以挂 buff
     */
    public boolean rollDebuff(CanHit caster, CanHit target, double baseChance, String specificResistKey) {
        return rng.nextDouble() < hitChance(caster, target, baseChance, specificResistKey);
    }

    /**
     * 挂一个负面效果：先过命中判定，命中才 {@code addBuff}（P6-1）。
     *
     * <p>这是"技能侧施加 debuff"的统一入口 —— 别在技能里直接调 {@code addBuff}，
     * 否则效果命中与抵抗就被绕过去了。
     *
     * @param caster           施加者
     * @param target           目标
     * @param buff             要挂的 buff
     * @param baseChance       基础概率
     * @param specificResistKey 特定抵抗键（可为 {@code null}）
     * @return {@code true} = 挂上了
     */
    public boolean tryApplyDebuff(CanHit caster, CanHit target, com.laosun.aluminium.models.AbstractBuff buff,
                                  double baseChance, String specificResistKey) {
        if (caster == null || target == null || buff == null || target.isDeath()) {
            return false;
        }
        if (!rollDebuff(caster, target, baseChance, specificResistKey)) {
            return false;
        }
        target.getBuffManager().addBuff(buff);
        return true;
    }

    /**
     * 治疗量（P6-2）：**不碰 {@code Damage}**，是独立的一套乘区。
     *
     * <pre>
     * 治疗量 = 基础量 × (1 + 治疗加成) × (1 + 受疗加成)
     * </pre>
     *
     * <p>两个因子都是乘算，且分别来自**不同的人**：
     * {@code OUTGOING_HEALING_BOOST} 读施加治疗的人（奶妈的行迹/光锥），
     * {@code HEAL_TAKEN_RATIO} 读被治疗的人（受疗加成；**负数就是治疗降低** ——
     * 游戏里没有单独的"治疗降低"属性，见 {@code AttributeType}）。
     *
     * <p>只算数值，**不改 HP**：执行在 {@link #heal(CanHit, CanHit, double)}。
     *
     * @param healer     施加治疗的人（{@code null} → 只算受疗侧）
     * @param target     被治疗的人
     * @param baseAmount 基础治疗量（技能倍率 × 属性 + 固定值，由调用方算好）
     * @return 最终治疗量（可能为负 —— 治疗降低 > 100% 时；调用方按 0 处理会由 heal 挡掉）
     */
    public double calculateHeal(CanHit healer, CanHit target, double baseAmount) {
        if (target == null) {
            return 0;
        }
        double outgoing = healer == null ? 0
                : healer.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        double taken = target.getAttribute(AttributeType.HEAL_TAKEN_RATIO).get();
        return baseAmount * (1 + outgoing) * (1 + taken);
    }

    /**
     * 执行一次治疗（P6-2）：先算治疗量，再落到目标 HP 上（{@code CanHit.heal} 自己封顶、死者无效）。
     *
     * @return **实际回复的 HP**（被上限截断后；目标已死或治疗量 ≤ 0 时是 0）
     */
    public double heal(CanHit healer, CanHit target, double baseAmount) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double amount = calculateHeal(healer, target, baseAmount);
        if (amount <= 0) {
            return 0;
        }
        double before = target.getCurrentHp();
        target.heal(amount);
        return target.getCurrentHp() - before;
    }

    /**
     * 获得护盾（P6-3）。
     *
     * <p><b>不叠加</b>：直接覆盖当前护盾值（游戏里护盾通常不可叠加；同源刷新按覆盖处理）。
     * 因为 {@link CanHit#takeDamage} 是"先扣盾再扣血"，所以"盾破前不死"是自动成立的。
     *
     * <p>🚧 护盾提高词条（护盾量提高 / 获得护盾量提高）**还没有对应属性**
     * （{@code AttributeType} 里没有），所以现在护盾量就是传入值 —— 等有真实效果引用时再加。
     *
     * @param target 获得护盾的人（已死则无效）
     * @param amount 护盾量（≤ 0 视为清除护盾）
     * @return 实际设置后的护盾值
     */
    public double grantShield(CanHit target, double amount) {
        if (target == null || target.isDeath()) {
            return 0;
        }
        double value = Math.max(0, amount);
        target.setShield(value);
        return value;
    }

    /**
     * 某个单位的对手阵营成员（P5-5）：我方 → 敌人；敌人 → 我方。
     *
     * <p>**不过滤死亡**（调用方按需过滤）：这里只回答"阵营是谁"，不回答"谁能被打"。
     * 若将来引入第三方阵营（{@link com.laosun.aluminium.enums.Camp#NEUTRAL}），
     * 这个方法的语义需要重新定义。
     *
     * @param self 查询者
     * @return 对手阵营的列表（就是 {@code characters} / {@code enemies} 本身，不是拷贝）
     */
    public List<? extends CanHit> getOpponents(CanHit self) {
        if (self == null || self.getCamp() == null) {
            return List.of();
        }
        return self.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER ? enemies : characters;
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
     * <p><b>两条口径不同，别用同一个开关卡：</b>
     * <ul>
     *   <li><b>受击回能</b>：要求这一发「算一次攻击」（{@code countsAsAttack}）。
     *       附加伤害 / 真实伤害按官方定义「不视为造成了 1 次攻击」→ 挨打方不回能。</li>
     *   <li><b>击杀回能</b>：只看"这一发有没有把目标打死"，**与该伤害是否算攻击无关**。
     *       任何归属到攻击者的伤害（普攻、战技、击破、超击破、DOT、附加伤害、真伤……）
     *       只要打死了怪，就给 {@code damage.getAttacker()} 结算击杀回能。</li>
     * </ul>
     *
     * <p>两条分开的原因：附加伤害/真伤可以击杀，但"击杀"这件事本身仍然发生了 ——
     * 用 {@code countsAsAttack} 一起卡掉会让附加伤害补刀拿不到击杀回能。
     *
     * @param target 挨打的人
     * @param damage 这一发伤害
     * @param died   这一发是否打死了 {@code target}
     */
    private void grantHitAndKillEnergy(CanHit target, Damage damage, boolean died, EnergyGrant grant) {
        if (!died) {
            grantHitEnergy(target, damage, grant);
            return;
        }
        grantKillEnergy(target, damage, grant);
    }

    /**
     * 受击回能：只有**这一次攻击的主段**才给挨打的那一方回能。
     *
     * <p>两道门槛：① 这一发必须「算一次攻击」（附加伤害 / 真伤不视为攻击）；
     * ② {@code grant} 必须允许受击回能（击破 / 超击破 / DOT 这些派生段不允许，
     * 否则一次攻击会因为"打出了更多伤害类型"而给受击方回更多能）。
     */
    private void grantHitEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant != EnergyGrant.ALL || !damage.isCountsAsAttack()) {
            return;
        }
        EnergyGain hitGain = target.getEnergyProvider().onTakingHit(target, damage);
        if (hitGain != null) {
            applyEnergyGain(target, hitGain);
        }
    }

    /**
     * 击杀回能：记给 {@code damage.getAttacker()}，**不看** {@code countsAsAttack}
     * （任何归属到角色的伤害击杀了怪都该回能，2026-09-19 口径）。
     *
     * <p>但**看 {@code grant}**：击杀只结算一次。所以主段带 {@link EnergyGrant#ALL}，
     * 派生段（击破 / 超击破 / DOT / 附加伤害 / 真伤）带 {@link EnergyGrant#KILL_ONLY} ——
     * 这样"主段没打死、派生段补刀打死"时击杀回能不会漏，
     * 而"主段已经打死"时派生段也不会重复给（它本来就因为目标已死而不结算）。
     */
    private void grantKillEnergy(CanHit target, Damage damage, EnergyGrant grant) {
        if (grant == EnergyGrant.NONE) {
            return;
        }
        CanHit attacker = damage.getAttacker();
        if (attacker == null) {
            return;
        }
        EnergyGain killGain = attacker.getEnergyProvider().onKill(attacker, target);
        if (killGain != null) {
            applyEnergyGain(attacker, killGain);
        }
    }

    /**
     * 这一段伤害允许结算哪些回能（见 {@code Battle.applyDamage} 的内部重载）。
     */
    private enum EnergyGrant {
        /** 主段：受击回能 + 击杀回能都结算（角色主动施放技能的伤害段）。 */
        ALL,
        /** 派生段：只结算击杀回能（击破 / 超击破 / DOT / 附加伤害 / 真伤）。 */
        KILL_ONLY,
        /** 什么都不结算（预留）。 */
        NONE
    }

    /**
     * 附加伤害：面板型 base（攻击力 / 生命上限 × 倍率），**走完整乘区**（增伤/防御/抗性/易伤都吃）。
     *
     * <p>官方定义：「使受击者额外受到 1 次伤害，本次伤害不视为造成了 1 次攻击」——
     * 所以置 {@code notCountsAsAttack()}（**受击方**不回能、不削韧、不触发攻击级事件）。
     * 但它**归属攻击者**，因此击杀时照样给攻击者结算击杀回能（见 {@link #grantKillEnergy}）。
     *
     * @param base 已经算好的基础值（例：知更鸟 120% 攻击力 / 缇宝 12% 生命上限）
     * @return 该段结算值（0 = 未造成伤害）
     */
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage extra = new Damage(attacker, target, element, DamageType.ADDITIONAL, base);
        // KILL_ONLY：附加伤害是某次攻击派生的额外伤害，不给受击方回能；击杀仍记给攻击者
        return applyDamage(target, extra.notCountsAsAttack(), EnergyGrant.KILL_ONLY);
    }

    /**
     * 真实伤害：固定数额，或"本次攻击总伤害 × %"这类衍生值——**跳过全部乘区**，不视为一次攻击。
     *
     * <p>同样置 {@code notCountsAsAttack()}：受击方不回能、不削韧；但归属攻击者，
     * 击杀时照给攻击者结算击杀回能（见 {@link #grantKillEnergy}）。
     *
     * @param base 真伤数额（不再受防御/抗性/增伤/暴击/易伤影响）
     * @return 该段结算值（0 = 未造成伤害）
     */
    public double applyTrueDamage(CanHit attacker, CanHit target, DamageElement element, double base) {
        Damage trueDamage = new Damage(attacker, target, element, DamageType.TRUE, base);
        // KILL_ONLY：真伤同样是派生伤害，不给受击方回能；击杀仍记给攻击者
        return applyDamage(target, trueDamage.trueDamage().notCountsAsAttack(), EnergyGrant.KILL_ONLY);
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
        checkResult();                               // P7-3：清完尸体后顺手判胜负
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
