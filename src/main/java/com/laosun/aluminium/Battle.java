package com.laosun.aluminium;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Element;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DamageCalculator.DamageContext;
import com.laosun.aluminium.models.DamageCalculator.DamageType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Orchestrates an HSR-style turn-based battle (HSR.md).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Turn flow: {@link #stepForward()} → {@link #beforeMove()} →
 *   action → {@link #afterMove()}</li>
 *   <li>Skill points (战技点, max 5) and energy (能量, HSR.md §3.3)</li>
 *   <li>Weakness break with elemental break effects (HSR.md §3.2)</li>
 *   <li>Status ticking (buff durations & DoT, HSR.md §5.4)</li>
 *   <li>Enemy AI targeting by aggro (HSR.md §3.4)</li>
 *   <li>Victory / defeat detection</li>
 * </ul>
 */
public class Battle {
    /** Maximum skill points (战技点). */
    public static final int MAX_SKILL_POINTS = 5;
    /** Skill points at battle start. */
    public static final int INITIAL_SKILL_POINTS = 3;

    public Queue queue;

    public List<Character> characters;

    public List<Enemy> enemies;

    public Signal currentMove;

    public ArrayList<CanHit> addRequestItems = new ArrayList<>();

    public ArrayList<AdvanceRequest> advanceRequests = new ArrayList<>();

    private final ArrayList<SkillRequest> skillRequests = new ArrayList<>();

    private int skillPoints = INITIAL_SKILL_POINTS;

    /** The skill type of the skill currently being executed (for energy/SP). */
    private SkillType currentSkillType = SkillType.COMMON;

    /**
     * 笑点 counter (HSR.md §3.1): accumulates elation damage dealt; the more
     * laugh points, the stronger Aha's damage.
     */
    private double laughPoints = 0;

    /** Cap on laugh points to keep the demo balanced. */
    public static final double MAX_LAUGH_POINTS = 1000;

    private boolean over = false;
    private boolean playerWon = false;

    public record AdvanceRequest(CanHit object, double rate) {
    }

    public record SkillRequest(Skill skill, CanHit object, List<? extends CanHit> target) {
    }

    public Battle(List<Character> characterQueue, List<Enemy> enemyQueue) {
        characters = characterQueue;
        enemies = enemyQueue;
        queue = new Queue();
        queue.addCombatants(characterQueue);
        queue.addCombatants(enemyQueue);
        queue.initialize();
    }

    // ─── Turn flow ─────────────────────────────────────────────────────

    public void startBattle() {
        for (Signal signal : queue.snapshot()) {
            CanHit canHit = signal.getCanHit();
            canHit.onBattleStart(this);
            // Characters start with 50% of their max energy.
            if (canHit.getMaxEnergy() > 0) {
                canHit.gainEnergy(canHit.getMaxEnergy() * 0.5);
            }
        }
        // 好活当赏 (HSR.md §3.1): battle-start state granting each 欢愉 character
        // a small permanent boost.
        for (Character character : characters) {
            if (hasElationSkill(character)) {
                Buff reward = new Buff("好活当赏", Buff.Category.BUFF, character, character, -1)
                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(0.15,
                                DoubleValue.Modifier.ModifierSource.BUFF));
                character.applyBuff(reward);
                IO.println("[ELATION] " + character.getName() + " gains 好活当赏 (+15% ATK)");
            }
        }
        // 阿哈 (HSR.md §3.2): joins the action bar when the party has 欢愉 characters.
        List<Double> elationSpeeds = characters.stream()
                .filter(this::hasElationSkill)
                .map(c -> c.getAttribute(AttributeType.SPEED).get())
                .toList();
        if (!elationSpeeds.isEmpty()) {
            Aha aha = new Aha(Aha.computeAhaSpeed(elationSpeeds));
            queue.addCombatant(aha);
            IO.println("[ELATION] Aha joins the action bar at speed "
                    + String.format("%.1f", aha.getAttribute(AttributeType.SPEED).get()) + "!");
        }
        // 行迹技能 battle-start hooks (HSR.md §5).
        for (Character character : characters) {
            for (Trace trace : character.getTraces()) {
                trace.onBattleStart(this, character);
            }
        }
        processRequests();
    }

    /**
     * Whether the given character has a 欢愉技 (an ELATION skill).
     */
    public boolean hasElationSkill(Character character) {
        return character.getSkills() != null && character.getSkills().containsKey(SkillType.ELATION);
    }

    /**
     * Returns all 欢愉-path characters in the party (HSR.md §3.1).
     */
    public List<Character> getElationCharacters() {
        return characters.stream().filter(this::hasElationSkill).toList();
    }

    public void stepForward() {
        if (isOver()) {
            return;
        }
        queue.move();
        currentMove = queue.getCurrentActor();
    }

    /**
     * Runs pre-action logic for the current actor: status ticking (DoTs,
     * durations), control expiry, and broken-enemy toughness recovery.
     *
     * @return {@code true} if the actor is incapacitated and must skip its action
     */
    public boolean beforeMove() {
        if (currentMove == null) {
            return false;
        }
        CanHit actor = currentMove.getCanHit();
        if (actor.isDeath()) {
            return false;
        }

        // Broken enemies recover toughness when their turn arrives (HSR.md §3.2).
        if (actor instanceof Enemy enemy && enemy.isBroken()) {
            enemy.recoverToughness();
            IO.println("[BREAK] " + enemy.getName() + " recovered toughness!");
        }
        if (actor instanceof Enemy enemy) {
            enemy.tickTemporaryWeaknesses();
        }

        // Tick DoTs and buff durations (HSR.md §5.4: 先上先结算).
        actor.tickStatuses(this);

        // 行迹技能 hook at the owner's turn start (e.g. Himeko 道标).
        if (actor instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                trace.onTurnStart(this, character);
            }
        }

        // Control expiry: freeze/imprisonment consumes the turn (HSR.md §3.2).
        boolean incapacitated = false;
        if (actor.getControlState() != null) {
            Buff controlBuff = null;
            for (Buff buff : actor.getBuffs()) {
                if (buff.getControl() != null) {
                    controlBuff = buff;
                    break;
                }
            }
            if (controlBuff != null) {
                controlBuff.onExpire(this);
                controlBuff.remove();
                actor.getBuffs().remove(controlBuff);
            }
            actor.setControlState(null);
            incapacitated = true;
        }

        actor.beforeMove();
        return incapacitated;
    }

    public void performAction() {
        if (currentMove == null) {
            return;
        }
        CanHit canHit = currentMove.getCanHit();
        if (canHit.isDeath()) {
            return;
        }
    }

    /**
     * The current actor uses one of their skills (basic attack / skill).
     *
     * @param skill  the skill to cast (must belong to the current actor)
     * @param target target list
     * @return {@code true} if the action was requested
     */
    public boolean useSkill(Skill skill, List<? extends CanHit> target) {
        if (currentMove == null || skill == null || target == null) {
            return false;
        }
        CanHit user = currentMove.getCanHit();
        if (user.isDeath()) {
            return false;
        }
        return requestSkill(skill, user, target);
    }

    /**
     * Executes a skill immediately (outside the turn flow), applying energy
     * and skill-point costs. Used by tests and external controllers.
     *
     * @param skill  the skill to execute
     * @param user   the caster
     * @param target target list
     * @return whether the skill was executed
     */
    public boolean executeSkill(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || user.isDeath() || target == null || target.isEmpty()) {
            return false;
        }
        SkillType type = resolveSkillType(user, skill);
        if (type == SkillType.SKILL && skillPoints <= 0) {
            return false;
        }
        currentSkillType = type;
        skill.execute(this, user, target);
        applyActionCosts(user, type);
        // 强化状态: skills/ults with the "Enhance" effect swap basic & skill.
        if (user instanceof Character character && type != SkillType.COMMON) {
            SkillData data = skill.getData();
            if (data != null && "Enhance".equals(data.getSkillEffect())
                    && hasEnhancedSkill(character.getCid(), 8)) {
                enterEnhancedState(character);
            }
        }
        triggerAfterAction(user, type, target);
        currentSkillType = SkillType.COMMON;
        processRequests();
        removeDeadCombatants();
        checkBattleEnd();
        return true;
    }

    /**
     * Casts an ultimate: requires full energy (HSR.md §3.3).
     */
    public boolean castUltra(CanHit user, List<? extends CanHit> targets) {
        if (user == null || user.isDeath()) {
            return false;
        }
        if (user.getMaxEnergy() <= 0 || user.getEnergy() < user.getMaxEnergy()) {
            IO.println("[ENERGY] " + user.getName() + " energy not full ("
                    + String.format("%.0f/%.0f", user.getEnergy(), user.getMaxEnergy()) + ")!");
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

    public void afterMove() {
        if (currentMove == null) {
            return;
        }

        CanHit actor = currentMove.getCanHit();

        if (actor instanceof Character character && character.isEnhanced()) {
            exitEnhancedState(character);
        }

        if (actor.isDeath()) {
            queue.removeCombatant(actor);
        } else {
            queue.setTopZero();
        }

        currentMove = null;

        removeDeadCombatants();

        processRequests();
        checkBattleEnd();
    }

    // ─── Skill requests ────────────────────────────────────────────────

    private boolean requestSkill(Skill skill, CanHit user, List<? extends CanHit> target) {
        if (skill == null || user == null || target == null) {
            return false;
        }
        if (user.isDeath()) {
            return false;
        }
        SkillType type = resolveSkillType(user, skill);
        if (type == SkillType.SKILL && skillPoints <= 0) {
            IO.println("[SP] Not enough skill points!");
            return false;
        }
        if (type == SkillType.ULTRA && user.getEnergy() < user.getMaxEnergy()) {
            IO.println("[ENERGY] Not enough energy!");
            return false;
        }
        if (type == SkillType.ULTRA) {
            user.consumeEnergy();
        }
        skillRequests.add(new SkillRequest(skill, user, target));
        return true;
    }

    /**
     * Resolves which {@link SkillType} a skill instance is registered under.
     */
    private SkillType resolveSkillType(CanHit user, Skill skill) {
        if (user.getSkills() == null) {
            return SkillType.COMMON;
        }
        for (var entry : user.getSkills().entrySet()) {
            if (entry.getValue() == skill) {
                return entry.getKey();
            }
        }
        return SkillType.COMMON;
    }

    /**
     * Returns the {@link SkillType} of the skill currently being executed.
     * Used by {@link DataSkill} to pick the correct damage-type boost.
     */
    public SkillType getCurrentSkillType() {
        return currentSkillType;
    }

    public void processRequests() {
        processSkillRequests();
        processAddRequests();
        processAdvanceRequests();
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
            SkillType type = resolveSkillType(req.object(), req.skill());
            currentSkillType = type;
            IO.println("== " + req.object().getName() + " 使用 " + skillTypeLabel(type) + "! ==");
            req.skill().execute(this, req.object(), req.target());
            applyActionCosts(req.object(), type);
            // 强化状态: skills/ults with the "Enhance" effect swap the character's
            // basic & skill to the enhanced versions (skill IDs 8/9/10).
            if (req.object() instanceof Character character && type != SkillType.COMMON) {
                SkillData data = req.skill().getData();
                if (data != null && "Enhance".equals(data.getSkillEffect())
                        && hasEnhancedSkill(character.getCid(), 8)) {
                    enterEnhancedState(character);
                }
            }
            triggerAfterAction(req.object(), type, req.target());
        }
        currentSkillType = SkillType.COMMON;
    }

    private static String skillTypeLabel(SkillType type) {
        return switch (type) {
            case COMMON -> "普攻";
            case SKILL -> "战技";
            case ULTRA -> "终结技";
            case TALENT -> "天赋";
            case SUMMON_SKILL, SUMMON_TALENT -> "忆灵技";
            case ELATION -> "欢愉技";
        };
    }

    private boolean hasEnhancedSkill(int cid, int skillId) {
        return Constant.SKILLS.get(cid) != null && Constant.SKILLS.get(cid).containsKey(skillId);
    }

    /**
     * Enters the enhanced state: swaps the character's COMMON skill to skill ID 8
     * (enhanced basic) and SKILL to ID 9 (or 10) if present.
     */
    public void enterEnhancedState(Character character) {
        if (character.isEnhanced()) {
            return;
        }
        character.setEnhanced(true);
        character.setUnenhancedSkills(new EnumMap<>(character.getSkills()));
        int cid = character.getCid();
        Skill common = character.getSkills().get(SkillType.COMMON);
        Skill skill = character.getSkills().get(SkillType.SKILL);
        if (common != null && hasEnhancedSkill(cid, 8)) {
            character.getSkills().put(SkillType.COMMON, new DataSkill(cid, 8, common.getLevel()));
        }
        int enhancedSkillId = hasEnhancedSkill(cid, 9) ? 9 : hasEnhancedSkill(cid, 10) ? 10 : 0;
        if (enhancedSkillId > 0 && skill != null) {
            character.getSkills().put(SkillType.SKILL, new DataSkill(cid, enhancedSkillId, skill.getLevel()));
        }
        IO.println("  " + character.getName() + " enters the enhanced state (强化状态)!");
    }

    /**
     * Exits the enhanced state, restoring the original skills.
     */
    public void exitEnhancedState(Character character) {
        if (!character.isEnhanced()) {
            return;
        }
        character.setEnhanced(false);
        if (character.getUnenhancedSkills() != null) {
            character.setSkills(character.getUnenhancedSkills());
            character.setUnenhancedSkills(null);
        }
        IO.println("  " + character.getName() + " leaves the enhanced state.");
    }

    /**
     * Invokes the 行迹技能 {@code afterAction} hooks (HSR.md §5) and the
     * party-wide {@code onAllyAction} hooks for the other characters.
     */
    private void triggerAfterAction(CanHit user, SkillType type, List<? extends CanHit> targets) {
        if (user instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                trace.afterAction(this, character, type, targets);
            }
            for (Character other : getAliveCharacters()) {
                if (other != character) {
                    for (Trace trace : other.getTraces()) {
                        trace.onAllyAction(this, other, character, type, targets);
                    }
                }
            }
            triggerPoemAfterAction(character, type, targets);
        } else if (user instanceof com.laosun.aluminium.models.Summon summon) {
            // 忆灵行动: 通知其主人的 trace (光锥被动如 将光阴织成黄金/致长夜的星光/爱如此刻永恒).
            CanHit summonOwner = summon.getOwner();
            if (summonOwner instanceof Character ownerCharacter) {
                for (Trace trace : ownerCharacter.getTraces()) {
                    trace.onSummonAction(this, ownerCharacter, summon, type, targets);
                }
            }
        }
    }

    /**
     * 昔涟诗篇的机制型增益 (改机制的增益): 持诗者行动后触发 —
     * <ul>
     *   <li>献予「创世」: 记忆开拓者行动后 → 德谬歌获得额外回合 (自动施放【花与箭的舞曲】)</li>
     *   <li>浪漫: 阿格莱雅/衣匠攻击后 → 消耗【浪漫】恢复能量</li>
     *   <li>献予「海洋」: 海瑟音攻击后 → 恢复能量, 且普攻/战技引爆目标的持续伤害</li>
     *   <li>献予「理性」: 那刻夏施放普攻/战技 → 【真知】: 智识角色攻击力/战技伤害提升</li>
     * </ul>
     */
    private void triggerPoemAfterAction(Character character, SkillType type, List<? extends CanHit> targets) {
        for (Buff buff : new ArrayList<>(character.getBuffs())) {
            List<Double> poem = CyreneServantSkill.poemParams(character.getCid());
            switch (buff.getName()) {
                case "献予「创世」之诗" -> {
                    // 记忆开拓者施放强化普攻后 → 德谬歌立即获得额外回合.
                    Summon demouge = cyreneDemiurge();
                    if (demouge != null && !demouge.isDeath() && type == SkillType.COMMON) {
                        advanceByPercent(demouge, 1.0);
                        IO.println("  [昔涟] 德谬歌获得额外回合!");
                    }
                }
                case "浪漫" -> {
                    // 攻击后消耗【浪漫】恢复 #1 点能量 (单次).
                    double energy = poem != null && !poem.isEmpty() ? poem.get(0) : 70;
                    character.gainEnergy(energy);
                    removeBuff(character, buff);
                    IO.println("  [昔涟] " + character.getName() + " 消耗【浪漫】恢复 "
                            + String.format("%.0f", energy) + " 能量");
                }
                case "献予「海洋」之诗" -> {
                    // 攻击后消耗【暖流】恢复 #4 能量; 普攻/战技使目标承受的持续伤害立即产生伤害.
                    if (poem != null && poem.size() >= 4) {
                        double energy = poem.get(3);
                        character.gainEnergy(energy);
                        IO.println("  [昔涟] " + character.getName() + " 恢复 "
                                + String.format("%.0f", energy) + " 能量 (暖流)");
                        if (type == SkillType.COMMON || type == SkillType.SKILL) {
                            double ratio = type == SkillType.COMMON ? poem.get(1) : poem.get(2);
                            for (CanHit target : targets) {
                                if (target.isDeath() || target.getDots().isEmpty()) {
                                    continue;
                                }
                                for (Buff.Dot dot : new ArrayList<>(target.getDots())) {
                                    dealAttackDamageBase(character, target, dot.getDamage() * ratio,
                                            DamageContext.of(DamageType.NORMAL, character.getElement()));
                                }
                                IO.println("  [昔涟] " + target.getName() + " 的持续伤害被引爆 ("
                                        + String.format("%.0f%%", ratio * 100) + ")");
                            }
                        }
                    }
                    removeBuff(character, buff);
                }
                case "献予「理性」之诗" -> {
                    // 那刻夏施放普攻/战技 → 【真知】: 「智识」命途角色攻击力 +#3, 战技伤害 +#2, 1回合.
                    if (poem != null && poem.size() >= 3 && (type == SkillType.COMMON || type == SkillType.SKILL)) {
                        double atkPercent = poem.get(2);
                        double skillBoost = poem.get(1);
                        for (Character ally : getAliveCharacters()) {
                            if (isErudition(ally)) {
                                ally.removeBuff("真知");
                                Buff knowledge = new Buff("真知", Buff.Category.BUFF, character, ally, 1)
                                        .stat(AttributeType.ATTACK, DoubleValue.Modifier.addPercent(atkPercent,
                                                DoubleValue.Modifier.ModifierSource.BUFF))
                                        .stat(AttributeType.SKILL_DAMAGE_BOOST, DoubleValue.Modifier.pure(skillBoost,
                                                DoubleValue.Modifier.ModifierSource.BUFF));
                                applyBuff(ally, knowledge);
                            }
                        }
                        IO.println("  [昔涟] 【真知】: 智识角色攻击力+"
                                + String.format("%.0f%%", atkPercent * 100));
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Whether the character is of the 智识 (Erudition) path (mt = "all"). */
    private boolean isErudition(Character character) {
        com.laosun.aluminium.beans.CharacterData data = Constant.CHARACTERS.get(character.getCid());
        return data != null && "all".equals(data.mt());
    }

    /** 昔涟 (1415) 的忆灵 德谬歌. */
    private Summon cyreneDemiurge() {
        for (Character character : getAliveCharacters()) {
            if (character.getCid() == 1415) {
                return character.getSummons().stream().filter(s -> !s.isDeath()).findFirst().orElse(null);
            }
        }
        return null;
    }

    /**
     * Adds skill points (战技点) to the battle pool (used by eidolons).
     */
    public void addSkillPoints(int amount) {
        skillPoints = Math.max(0, Math.min(MAX_SKILL_POINTS, skillPoints + amount));
    }

    /**
     * Energy and skill point changes (HSR.md §3.3):
     * basic attack +20 energy & +1 SP, skill +30 energy & -1 SP, ultimate +5 energy.
     */
    private void applyActionCosts(CanHit user, SkillType type) {
        switch (type) {
            case COMMON -> {
                user.gainEnergy(20);
                if (user.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER) {
                    addSkillPoint();
                }
            }
            case SKILL -> {
                user.gainEnergy(30);
                if (user.getCamp() == com.laosun.aluminium.enums.Camp.PLAYER) {
                    spendSkillPoint();
                }
            }
            case ULTRA -> user.gainEnergy(5);
            default -> {
            }
        }
    }

    // ─── Skill points ──────────────────────────────────────────────────

    public int getSkillPoints() {
        return skillPoints;
    }

    private void addSkillPoint() {
        skillPoints = Math.min(MAX_SKILL_POINTS, skillPoints + 1);
    }

    private void spendSkillPoint() {
        skillPoints = Math.max(0, skillPoints - 1);
    }

    // ─── Damage pipeline ───────────────────────────────────────────────

    /**
     * Deals a pre-computed base damage through the full HSR.md §2 pipeline.
     * Used by HP-scaling skills (e.g. 死龙: 20% 生命上限).
     */
    public void dealAttackDamageBase(CanHit attacker, CanHit defender, double base,
                                     DamageContext context) {
        if (attacker.isDeath() || defender.isDeath()) {
            return;
        }
        double traceMultiplier = 1.0;
        double extraCrit = 0;
        if (attacker instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                traceMultiplier *= trace.damageMultiplier(this, character, defender, currentSkillType);
                extraCrit += trace.critChanceBonus(this, character, defender, currentSkillType);
            }
        }
        DamageContext effectiveContext = extraCrit != 0 ? context.withExtraCrit(extraCrit) : context;
        announceAttack(attacker, defender, context);
        double damage = DamageCalculator.calculateDamage(attacker, defender, base * traceMultiplier, effectiveContext);
        applyDamage(defender, damage, attacker);
        // Being hit restores 10 energy (HSR.md §3.3).
        defender.gainEnergy(10);
    }

    /**
     * Deals a multiplier-based attack: {@code base = multiplier × ATK + flat},
     * then through the full HSR.md §2 pipeline. 行迹技能 conditional damage
     * bonuses apply on top.
     */
    public void dealAttackDamage(CanHit attacker, CanHit defender, double multiplier,
                                 double flatBonus, DamageContext context) {
        if (attacker.isDeath() || defender.isDeath()) {
            return;
        }
        double attack = attacker.getAttribute(AttributeType.ATTACK) != null
                ? attacker.getAttribute(AttributeType.ATTACK).get() : 0;
        double base = attack * multiplier + flatBonus;
        double traceMultiplier = 1.0;
        double extraCrit = 0;
        if (attacker instanceof Character character) {
            for (Trace trace : character.getTraces()) {
                traceMultiplier *= trace.damageMultiplier(this, character, defender, currentSkillType);
                extraCrit += trace.critChanceBonus(this, character, defender, currentSkillType);
            }
        }
        DamageContext effectiveContext = extraCrit != 0 ? context.withExtraCrit(extraCrit) : context;
        announceAttack(attacker, defender, context);
        double damage = DamageCalculator.calculateDamage(attacker, defender, base * traceMultiplier, effectiveContext);
        applyDamage(defender, damage, attacker);
        // Being hit restores 10 energy (HSR.md §3.3).
        defender.gainEnergy(10);
    }

    /**
     * 战斗叙事: 攻击宣告 (谁向谁发起攻击, 何种元素).
     */
    private static void announceAttack(CanHit attacker, CanHit defender, DamageContext context) {
        String element = context != null && context.element() != null
                ? context.element().string : "无属性";
        IO.println("  " + attacker.getName() + " 向 " + defender.getName()
                + " 发起攻击! (" + element + ")");
    }

    /**
     * Reduces the target's toughness if the attacker's element matches a weakness
     * (HSR.md §3.2). On break, deals break damage and applies the elemental effect.
     *
     * <p>超击破 (HSR.md 超击破): attacking an already-broken enemy while the
     * 开拓者·同谐 ult's 【舞梦】 buff is active converts the attack's toughness
     * damage into 超击破伤害 instead.
     *
     * @param units toughness units removed (1 unit = 30 stance)
     */
    public void breakToughness(CanHit attacker, CanHit target, double units) {
        if (!(target instanceof Enemy enemy) || units <= 0) {
            return;
        }
        // 技能最终削韧值 = 技能基础削韧值 × (1 + 削韧值提高) × (1 + 弱点击破效率提高)
        double finalUnits = units;
        if (attacker.getAttribute(AttributeType.TOUGHNESS_BOOST) != null) {
            finalUnits *= 1 + attacker.getAttribute(AttributeType.TOUGHNESS_BOOST).get();
        }
        if (attacker.getAttribute(AttributeType.WEAKNESS_BREAK_EFFICIENCY) != null) {
            finalUnits *= 1 + attacker.getAttribute(AttributeType.WEAKNESS_BREAK_EFFICIENCY).get();
        }

        if (enemy.isBroken()) {
            // 超击破: attacking a broken enemy with 【舞梦】 active.
            if (attacker.hasBuffNamed("舞梦")) {
                dealSuperBreakDamage(attacker, enemy, finalUnits);
            }
            return;
        }
        if (!enemy.isWeakTo(attacker.getElement())) {
            return;
        }
        boolean broke = enemy.reduceToughness(finalUnits, attacker.getElement());
        IO.println("[TOUGHNESS] " + enemy.getName() + " -" + String.format("%.1f", finalUnits)
                + " (" + String.format("%.0f/%.0f", enemy.getCurrentToughness(), enemy.getMaxToughness()) + ")");
        if (broke) {
            onBreak(attacker, enemy);
        }
    }

    /**
     * Deals 超击破伤害 (HSR.md 超击破): only triggers when attacking a broken
     * enemy with the 【舞梦】 buff active.
     */
    public void dealSuperBreakDamage(CanHit attacker, Enemy defender, double toughnessUnits) {
        if (attacker.isDeath() || defender.isDeath()) {
            return;
        }
        double damage = DamageCalculator.calculateSuperBreakDamage(attacker, defender, toughnessUnits);
        IO.println("  [SUPER BREAK] " + attacker.getName() + " converts " + String.format("%.1f", toughnessUnits)
                + " toughness into " + String.format("%.0f", damage) + " super break DMG!");
        applyDamage(defender, damage);
    }

    /**
     * Break resolution (HSR.md §3.2): break damage + elemental break effect +
     * 25% action delay.
     */
    private void onBreak(CanHit attacker, Enemy enemy) {
        double units = Math.max(1, enemy.getMaxToughness() / 30.0);
        double breakDamage = DamageCalculator.calculateBreakDamage(attacker, units);
        IO.println("[BREAK!] " + enemy.getName() + " broken by "
                + attacker.getElement().string + " for " + String.format("%.0f", breakDamage) + " break DMG!");
        applyDamage(enemy, breakDamage);

        Element element = attacker.getElement();
        switch (element.getBreakEffect()) {
            case BLEED, BURN, SHOCK, WIND_SHEAR -> {
                Buff.Dot dot = new Buff.Dot(element.string + " DoT", attacker, enemy,
                        breakDamage * 0.2, element, 2);
                enemy.applyDot(dot);
                IO.println("  -> " + enemy.getName() + " takes " + element.string + " DoT (2 turns)");
            }
            case FREEZE -> {
                Buff freeze = new Buff("Freeze", Buff.Category.DEBUFF, attacker, enemy, 1)
                        .control(Buff.ControlType.FROZEN)
                        .damageOnExpire(breakDamage * 0.3);
                enemy.applyBuff(freeze);
                enemy.setControlState(Buff.ControlType.FROZEN);
                IO.println("  -> " + enemy.getName() + " is FROZEN!");
            }
            case ENTANGLEMENT -> {
                Buff entanglement = new Buff("Entanglement", Buff.Category.DEBUFF, attacker, enemy, 1)
                        .control(Buff.ControlType.IMPRISONED)
                        .delayOnExpire(0.2)
                        .damageOnExpire(breakDamage * 0.6);
                enemy.applyBuff(entanglement);
                enemy.setControlState(Buff.ControlType.IMPRISONED);
                IO.println("  -> " + enemy.getName() + " is ENTANGLED!");
            }
            case IMPRISONMENT -> {
                Buff imprisonment = new Buff("Imprisonment", Buff.Category.DEBUFF, attacker, enemy, 1)
                        .control(Buff.ControlType.IMPRISONED)
                        .delayOnExpire(0.3);
                enemy.applyBuff(imprisonment);
                enemy.setControlState(Buff.ControlType.IMPRISONED);
                IO.println("  -> " + enemy.getName() + " is IMPRISONED!");
            }
        }

        // Breaking delays the enemy's next action by 25% (HSR.md §3.2).
        delayByPercent(enemy, 0.25);

        // 行迹技能 hook: e.g. Silver Wolf 生成 implants a defect on break.
        for (Character character : getAliveCharacters()) {
            for (Trace trace : character.getTraces()) {
                trace.onEnemyBreak(this, character, enemy);
            }
        }
    }

    /**
     * Applies a DoT tick (HSR.md §5.4).
     */
    public void applyDotDamage(CanHit owner, Buff buff) {
        double damage = DamageCalculator.calculateDotDamage(buff.getSource(), owner,
                buff.getDotDamage(), buff.getDotElement());
        IO.println("  [DOT] " + buff.getName() + " hits " + owner.getName() + " for "
                + String.format("%.0f", damage));
        applyDamage(owner, damage);
    }

    /**
     * Applies a DoT tick (HSR.md §5.4).
     */
    public void applyDotDamage(CanHit owner, Buff.Dot dot) {
        double damage = DamageCalculator.calculateDotDamage(dot.getSource(), owner,
                dot.getDamage(), dot.getElement());
        IO.println("  [DOT] " + dot.getName() + " hits " + owner.getName() + " for "
                + String.format("%.0f", damage));
        applyDamage(owner, damage);
        // 星魂 hook: e.g. Kafka 把宣叙呈献给 (energy on shock ticks).
        if (dot.getSource() instanceof Character source) {
            for (Trace trace : source.getTraces()) {
                trace.onDotDamage(this, source, owner, damage);
            }
        }
    }

    /**
     * Applies direct true damage that ignores all regions.
     */
    public void applyTrueDamage(CanHit target, double amount, String reason) {
        if (target.isDeath()) {
            return;
        }
        IO.println("  [TRUE] " + reason + " -> " + target.getName() + ": " + String.format("%.0f", amount));
        applyDamage(target, amount);
    }

    public void applyDamage(CanHit target, double damage) {
        applyDamage(target, damage, null);
    }

    /**
     * Applies damage with a known attacker (for trace hooks).
     */
    public void applyDamage(CanHit target, double damage, CanHit attacker) {
        if (target.isDeath() || damage <= 0) {
            return;
        }
        boolean died = target.takeDamage(damage);
        String hpInfo = String.format("%.0f/%.0f", target.getCurrentHp(), target.getMaxHp()) + " HP"
                + (target.getShield() > 0 ? ", " + String.format("%.0f", target.getShield()) + " 护盾" : "");
        if (attacker != null) {
            IO.println("  " + target.getName() + " 受到 " + attacker.getName() + " 的 "
                    + String.format("%.0f", damage) + " 点伤害! (" + hpInfo + ")");
        } else {
            IO.println("  " + target.getName() + " 受到 " + String.format("%.0f", damage)
                    + " 点伤害! (" + hpInfo + ")");
        }
        if (died) {
            IO.println("  *** " + target.getName() + " 被击败了! ***");
            checkBattleEnd();
        }
        // 行迹技能 / 星魂 hooks (HSR.md §5).
        if (target instanceof Character damaged) {
            for (Trace trace : damaged.getTraces()) {
                trace.onDamaged(this, damaged, attacker, damage);
            }
        }
        if (died) {
            for (Character character : getAliveCharacters()) {
                for (Trace trace : character.getTraces()) {
                    trace.onKill(this, character, target);
                }
            }
        }
    }

    // ─── Healing & shielding (HSR.md §4) ───────────────────────────────

    public void healTarget(CanHit healer, CanHit target, double baseHeal) {
        if (target.isDeath()) {
            return;
        }
        double heal = DamageCalculator.calculateHeal(healer, target, baseHeal);
        target.heal(heal);
        IO.println("  " + healer.getName() + " 为 " + target.getName() + " 恢复 "
                + String.format("%.0f", heal) + " 点生命! (HP "
                + String.format("%.0f/%.0f", target.getCurrentHp(), target.getMaxHp()) + ")");
    }

    public void applyShield(CanHit target, double amount, CanHit source) {
        if (target.isDeath()) {
            return;
        }
        // HSR shields last ~2 of the owner's turns by default.
        target.setShield(amount, 2);
        IO.println("  " + target.getName() + " gains " + String.format("%.0f", amount) + " shield (2 turns)!");
    }

    // ─── Buffs & effect hit rate (HSR.md §3.5) ─────────────────────────

    public void applyBuff(CanHit target, Buff buff) {
        if (target.isDeath()) {
            return;
        }
        // 玲可星魂2 (求生抵抗): 持有【求生反应】的目标可抵抗1次负面效果施加.
        if (buff.getCategory() == Buff.Category.DEBUFF) {
            Buff resist = target.getBuffs().stream()
                    .filter(b -> "求生抵抗".equals(b.getName()))
                    .findFirst().orElse(null);
            if (resist != null) {
                removeBuff(target, resist);
                IO.println("  " + target.getName() + " resists [" + buff.getName() + "] (求生抵抗)");
                return;
            }
        }
        target.applyBuff(buff);
        IO.println("  " + target.getName() + " 获得 [" + buff.getName() + "] ("
                + (buff.getDuration() >= 0 ? buff.getDuration() + " 回合" : "整场战斗") + ")");
        queue.refreshSpeed(target);
    }

    /**
     * Removes a buff (and its stat modifiers) from a target.
     */
    public void removeBuff(CanHit target, Buff buff) {
        buff.remove();
        target.getBuffs().remove(buff);
        queue.refreshSpeed(target);
    }

    /**
     * Effect application check (HSR.md §3.5):
     * {@code 生效概率 = 基础概率 × (1 + 施加方效果命中%) × (1 - 受击方效果抵抗%)}.
     */
    public boolean checkEffectHit(CanHit source, CanHit target, double baseChance) {
        double hitRate = source.getAttribute(AttributeType.EFFECT_HIT_RATE) != null
                ? source.getAttribute(AttributeType.EFFECT_HIT_RATE).get() : 0;
        double resistance = target.getAttribute(AttributeType.EFFECT_RESISTANCE) != null
                ? target.getAttribute(AttributeType.EFFECT_RESISTANCE).get() : 0;
        double chance = baseChance * (1 + hitRate) * (1 - resistance);
        chance = Math.max(0, Math.min(1, chance));
        boolean landed = Math.random() < chance;
        IO.println("  [EFFECT] " + source.getName() + " -> " + target.getName()
                + " chance " + String.format("%.1f%%", chance * 100) + (landed ? " LANDED" : " MISSED"));
        return landed;
    }

    // ─── Action manipulation ───────────────────────────────────────────

    public void delayByPercent(CanHit canHit, double percent) {
        queue.delayActionByPercent(canHit, percent);
    }

    public void advanceByPercent(CanHit canHit, double percent) {
        queue.advanceActionByPercent(canHit, percent);
    }

    public void advanceRequest(CanHit canHit, double rate) {
        if (canHit != null && rate >= 0 && rate <= 1) {
            advanceRequests.add(new AdvanceRequest(canHit, rate));
        }
    }

    // ─── Enemy AI (HSR.md §3.4) ────────────────────────────────────────

    /**
     * Runs the enemy's turn: picks a skill and targets by aggro weight.
     * 忆灵 can be targeted by enemies (HSR.md §2.1).
     */
    public void enemyTurn(Enemy enemy) {
        List<Enemy.EnemySkill> skills = enemy.getEnemySkills();
        if (skills == null || skills.isEmpty()) {
            IO.println(enemy.getName() + " has no skills!");
            return;
        }
        Enemy.EnemySkill skill = skills.get(0);
        if (skill.attackType() == com.laosun.aluminium.enums.SkillAttackType.ALL
                && getAlivePlayerUnits().size() > 1) {
            List<? extends CanHit> targets = getAlivePlayerUnits();
            IO.println("== " + enemy.getName() + " 对全体发起攻击 [" + skill.name() + "]! ==");
            for (CanHit target : targets) {
                dealAttackDamage(enemy, target, skill.multiplier(), 0,
                        DamageContext.of(DamageType.NORMAL, enemy.getElement()));
            }
        } else {
            CanHit target = selectTargetByAggro(getAlivePlayerUnits());
            IO.println("== " + enemy.getName() + " 向 " + target.getName()
                    + " 发起攻击 [" + skill.name() + "]! ==");
            dealAttackDamage(enemy, target, skill.multiplier(), 0,
                    DamageContext.of(DamageType.NORMAL, enemy.getElement()));
        }
    }

    /**
     * Picks a target weighted by current aggro (HSR.md §3.4):
     * {@code 受击概率 = 角色当前仇恨值 / 全队总仇恨值}.
     * 行迹技能 aggro modifiers (e.g. Dan Heng 潜龙) apply.
     */
    public CanHit selectTargetByAggro(List<? extends CanHit> candidates) {
        List<? extends CanHit> alive = candidates.stream().filter(c -> !c.isDeath()).toList();
        if (alive.isEmpty()) {
            return null;
        }
        double[] weights = new double[alive.size()];
        double total = 0;
        for (int i = 0; i < alive.size(); i++) {
            double aggro = Math.max(1, alive.get(i).getAggro());
            if (alive.get(i) instanceof Character character) {
                for (Trace trace : character.getTraces()) {
                    aggro *= trace.aggroMultiplier(this, character);
                }
            }
            weights[i] = aggro;
            total += aggro;
        }
        double roll = Math.random() * total;
        double cumulative = 0;
        for (int i = 0; i < alive.size(); i++) {
            cumulative += weights[i];
            if (roll <= cumulative) {
                return alive.get(i);
            }
        }
        return alive.getLast();
    }

    // ─── Battle state ──────────────────────────────────────────────────

    private void checkBattleEnd() {
        if (over) {
            return;
        }
        boolean allEnemiesDead = enemies.stream().allMatch(CanHit::isDeath);
        boolean allCharactersDead = characters.stream().allMatch(CanHit::isDeath);
        if (allEnemiesDead || allCharactersDead) {
            over = true;
            playerWon = allEnemiesDead;
        }
    }

    public boolean isOver() {
        return over;
    }

    public boolean isPlayerWon() {
        return playerWon;
    }

    public void processAddRequests() {
        if (addRequestItems.isEmpty()) {
            return;
        }
        for (CanHit canHit : addRequestItems) {
            queue.addCombatant(canHit);
        }
        addRequestItems.clear();
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

    // ─── Elation / Aha (HSR.md §3) ─────────────────────────────────────

    public double getLaughPoints() {
        return laughPoints;
    }

    /**
     * Accumulates 笑点 from elation damage dealt (HSR.md §3.1: 笑点记录欢愉伤害).
     */
    public void addLaughPoints(double amount) {
        laughPoints = Math.min(MAX_LAUGH_POINTS, laughPoints + Math.max(0, amount));
    }

    /**
     * Deals 欢愉伤害 (HSR.md §3.4) and accumulates 笑点.
     */
    public void dealElationDamage(CanHit attacker, CanHit defender, double multiplier) {
        if (attacker.isDeath() || defender.isDeath()) {
            return;
        }
        double damage = DamageCalculator.calculateElationDamage(attacker, defender,
                multiplier, laughPoints, 0, 0);
        addLaughPoints(damage * 0.005);
        IO.println("  [ELATION] " + attacker.getName() + " deals " + String.format("%.0f", damage)
                + " elation DMG to " + defender.getName()
                + " (笑点 " + String.format("%.0f", laughPoints) + ")");
        applyDamage(defender, damage);
    }

    /**
     * 「阿哈时刻」 (HSR.md §3.1): when Aha acts —
     * <ol>
     *   <li>removes control states from all 欢愉 characters</li>
     *   <li>every character that can cast a 欢愉技 casts it, in 参演编号 order
     *   (team position order)</li>
     *   <li>all 笑点 are consumed after the actions</li>
     * </ol>
     * If nobody can cast, Aha uses 「要有笑声」 instead (scaled by 笑点).
     */
    public void ahaMoment() {
        IO.println();
        IO.println("===== 阿哈时刻 (Aha Moment) =====");
        List<Character> elation = getElationCharacters().stream()
                .filter(c -> !c.isDeath()).toList();

        for (Character character : elation) {
            character.setControlState(null);
            for (Buff buff : character.getBuffs()) {
                if (buff.getControl() != null) {
                    buff.remove();
                    character.getBuffs().remove(buff);
                    break;
                }
            }
        }
        IO.println("Control states cleared from " + elation.size() + " 欢愉 character(s).");

        // 欢愉光锥被动 (如 嗤笑): 阿哈时刻发动时触发.
        for (Character character : elation) {
            for (Trace trace : character.getTraces()) {
                trace.onAhaMoment(this, character);
            }
        }

        boolean anyoneCast = false;
        for (Character character : elation) {
            Skill elationSkill = character.getSkills().get(SkillType.ELATION);
            if (elationSkill == null || character.isDeath()) {
                continue;
            }
            anyoneCast = true;
            IO.println("== " + character.getName() + " casts 欢愉技 [参演#" + characters.indexOf(character) + "] ==");
            executeSkill(elationSkill, character, getAliveEnemies());
        }

        if (!anyoneCast) {
            // 要有笑声 (HSR.md §3.1): laugh-point scaled damage when nobody can cast.
            IO.println("== Aha uses 要有笑声 (Laughter Required) ==");
            for (Enemy enemy : getAliveEnemies()) {
                dealElationDamage(this.getAha(), enemy, 1.0);
            }
        }

        IO.println("笑点 consumed: " + String.format("%.0f", laughPoints));
        laughPoints = 0;
        IO.println("===== 阿哈时刻 ends =====");
    }

    private Aha cachedAha = null;

    private Aha getAha() {
        if (cachedAha == null || cachedAha.isDeath()) {
            cachedAha = new Aha(120);
        }
        return cachedAha;
    }

    // ─── Summons / 忆灵 (HSR.md §2) ────────────────────────────────────

    /**
     * Summons the user's 忆灵 (memosprite) into battle (HSR.md §2.1).
     * Only Memory-path (记忆命途) characters have 忆灵 — their servant config
     * is keyed by {@code cid + 10000} in servant_config.json.
     * A living memosprite is not re-summoned.
     */
    public void summonRequest(CanHit user) {
        if (user == null || user.isDeath()) {
            return;
        }
        int servantId = servantIdOf(user);
        if (servantId <= 0 || Constant.SERVANT_CONFIGS.get(servantId) == null) {
            return;
        }
        if (user instanceof Character character) {
            boolean alreadyAlive = character.getSummons().stream().anyMatch(s -> !s.isDeath());
            if (alreadyAlive) {
                IO.println("  " + character.getName() + "'s memosprite is already on the field!");
                return;
            }
        }
        Summon summon = new Summon(user, servantId);
        if (user instanceof Character character) {
            character.getSummons().add(summon);
        }
        IO.println("  " + user.getName() + " summons " + summon.getName() + "!");
        addRequestItems.add(summon);
        processRequests();
    }

    /**
     * The 忆灵's servant ID: {@code cid + 10000} for Memory-path characters,
     * 0 otherwise (传统召唤物如账账不是忆灵, HSR.md §4.2).
     */
    public int servantIdOf(CanHit user) {
        if (!(user instanceof Character character)) {
            return 0;
        }
        com.laosun.aluminium.beans.CharacterData data = Constant.CHARACTERS.get(character.getCid());
        if (data == null || !"memory".equals(data.mt())) {
            return 0;
        }
        return character.getCid() + 10000;
    }

    /**
     * The 忆灵's own turn: support-type 忆灵技 (Skill02) targets an ally,
     * otherwise the 忆灵普攻 (Skill01) attacks a weakness-aware target.
     */
    public void summonAction(Summon summon) {
        Skill secondary = summon.getSecondarySkill();
        if (secondary != null && secondary.getData() != null
                && "Support".equals(secondary.getData().getSkillEffect())) {
            CanHit target = pickSupportTarget(summon);
            if (target == null) {
                return;
            }
            IO.println("== " + summon.getName() + " 对 " + target.getName() + " 使用忆灵技! ==");
            executeSkill(secondary, summon, List.of(target));
            return;
        }
        CanHit target = pickWeaknessTarget(summon);
        if (target == null) {
            return;
        }
        Skill skill = summon.getSkills().get(SkillType.SUMMON_SKILL);
        if (skill == null) {
            return;
        }
        IO.println("== " + summon.getName() + " 向 " + target.getName() + " 发起攻击! ==");
        executeSkill(skill, summon, List.of(target));
    }

    private CanHit pickSupportTarget(Summon summon) {
        List<CanHit> allies = getAlivePlayerUnits();
        if (allies.isEmpty()) {
            return null;
        }
        // 昔涟的忆灵技为特定角色提供诗篇辅助 (除大黑塔1401外的所有14** + 记忆开拓者).
        for (CanHit ally : allies) {
            if (ally instanceof Character c) {
                int cid = c.getCid();
                boolean supportedByCyrene = cid == 8007 || cid == 8008
                        || (cid >= 1402 && cid <= 1414 && cid != 1401);
                if (supportedByCyrene) {
                    return ally;
                }
            }
        }
        return allies.stream().min(java.util.Comparator.comparingDouble(CanHit::getHpPercent)).orElse(allies.getFirst());
    }

    private CanHit pickWeaknessTarget(CanHit attacker) {
        List<Enemy> alive = getAliveEnemies();
        if (alive.isEmpty()) {
            return null;
        }
        return alive.stream()
                .filter(e -> e.getWeaknesses().contains(attacker.getElement()))
                .findFirst()
                .orElse(alive.getFirst());
    }

    // ─── Query helpers ─────────────────────────────────────────────────

    public List<Enemy> getAliveEnemies() {
        return enemies.stream().filter(e -> !e.isDeath()).toList();
    }

    public List<Character> getAliveCharacters() {
        return characters.stream().filter(c -> !c.isDeath()).toList();
    }

    /**
     * All alive friendly units: characters plus their 忆灵 (HSR.md §2.1:
     * 忆灵可作为受击目标与单体技能目标).
     */
    public List<CanHit> getAlivePlayerUnits() {
        List<CanHit> units = new ArrayList<>();
        for (Character character : characters) {
            if (!character.isDeath()) {
                units.add(character);
            }
            for (Summon summon : character.getSummons()) {
                if (!summon.isDeath()) {
                    units.add(summon);
                }
            }
        }
        return units;
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
        System.out.println("Skill Points: " + skillPoints
                + (laughPoints > 0 ? " | 笑点 " + String.format("%.0f", laughPoints) : ""));
        for (Character c : characters) {
            System.out.printf("%s: %.0f / %.0f HP | energy %.0f/%.0f%s%n",
                    c.getName(), c.getCurrentHp(), c.getMaxHp(), c.getEnergy(), c.getMaxEnergy(),
                    c.getShield() > 0 ? " | shield " + String.format("%.0f", c.getShield()) : "");
            for (Summon summon : c.getSummons()) {
                if (!summon.isDeath()) {
                    System.out.printf("  └─ %s: %.0f / %.0f HP%s%n",
                            summon.getName(), summon.getCurrentHp(), summon.getMaxHp(),
                            summon.getShield() > 0 ? " | shield " + String.format("%.0f", summon.getShield()) : "");
                }
            }
        }
        for (Enemy e : enemies) {
            System.out.printf("%s: %.0f / %.0f HP | toughness %.0f/%.0f%s%s%n",
                    e.getName(), e.getCurrentHp(), e.getMaxHp(),
                    e.getCurrentToughness(), e.getMaxToughness(),
                    e.isBroken() ? " | BROKEN" : "",
                    e.getControlState() != null ? " | " + e.getControlState() : "");
        }
        System.out.println("================");
    }

    // ─── Legacy API kept for compatibility ─────────────────────────────

    /**
     * Simplified damage calculation (used by tests / custom skills):
     * applies extra modifiers and a crit roll without the full region pipeline.
     */
    public double calculateDamage(CanHit attacker, CanHit defender,
                                  double baseDamage, List<DoubleValue.Modifier> extraModifiers) {
        DoubleValue damage = new DoubleValue(baseDamage);

        if (extraModifiers != null) {
            for (DoubleValue.Modifier mod : extraModifiers) {
                if (mod.getModifierType() == DoubleValue.Modifier.ModifierType.ADD_PERCENT) {
                    damage.addModifier(mod);
                }
            }
        }

        double critRate = attacker.getAttribute(AttributeType.CRIT_CHANCE) != null
                ? attacker.getAttribute(AttributeType.CRIT_CHANCE).get() : 0;
        double critDmg = attacker.getAttribute(AttributeType.CRIT_ATTACK) != null
                ? attacker.getAttribute(AttributeType.CRIT_ATTACK).get() : 0;
        if (Math.random() < critRate) {
            damage.addModifier(DoubleValue.Modifier.multiplyPercent(critDmg,
                    DoubleValue.Modifier.ModifierSource.BUFF));
            System.out.println("crit attack!");
        }

        return Math.max(1, damage.get());
    }
}
