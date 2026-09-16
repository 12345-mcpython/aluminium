package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;


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
        if (user == null || user.isDeath()) {
            return false;
        }
        Skill ultra = user.getSkills().get(SkillType.ULTRA);
        if (ultra == null) {
            return false;
        }
        if (!requestSkill(ultra, user, targets)) {
            return false;
        }
        processRequests();
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
        if (target.isDeath()) {
            return 0;
        }
        double settled = assemble(damage);
        target.takeDamage(settled);
        return settled;
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

        // 2) 暴击区：只有可暴击类型才骰；全引擎唯一的随机点，用注入的 rng（可复现）
        if (damage.getType().isCrittable()) {
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
