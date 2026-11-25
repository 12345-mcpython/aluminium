package com.laosun.aluminium.engine.port;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.Countdown;
import com.laosun.aluminium.entity.Summon;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.state.Resource;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.trigger.TriggerTable;

import java.util.List;
import java.util.Random;

/**
 * Everything a trigger rule may ask of the battlefield, as one fat interface.
 *
 * <p><b>Why this exists.</b> Before it, every op handler held a {@link Battle} - the whole runtime, 159 public methods
 * - so an op could reach anything, and no op could be exercised without standing up a real battle. This interface is
 * the <b>union</b> of what the trigger package actually calls (74 names, 87 declarations once overloads are counted),
 * extracted from {@code Battle} rather than designed: the point is that the dependency is now a list, and the list can
 * be shortened. Splitting it into narrow ports ({@code Roster}, {@code TurnOrder}, {@code TriggerSink},
 * {@code RngSource}, ...) is the next step; a fat port first means the handlers never have to be touched twice.
 *
 * <p><b>What is deliberately absent.</b> Nothing is here "for completeness": a method belongs to this interface only
 * because a rule path calls it. {@code Battle} implements it with no body changes, which is the other half of the
 * argument - the seam is new, the behaviour is not.
 *
 * <p><b>Note:</b> {@code TriggerTable.TriggerContext} still holds the concrete {@link Battle}. That is deliberate for
 * now: a handler passes {@code ctx.battle()} (a {@code Battle}) into the helpers, and a {@code Battle} is assignable
 * to this interface, so the ripple stops at the handler boundary instead of reaching into the condition machinery.
 */
public interface BattlePort {

    /**
     * The concrete engine behind this port.
     *
     * <p><b>Why an escape hatch exists at all.</b> Two call sites hand the battlefield to a {@code Skill} or an
     * {@code AttackSequence}, whose {@code execute} still takes a {@link Battle} - they are the next layer to be
     * ported, and changing them now would drag the skill, enemy and battle packages into this commit. Keeping the cast
     * here rather than in each handler means there is <b>one</b> place to delete it from, and grepping for
     * {@code engine()} finds every caller.
     *
     * @return this port's engine
     */
    Battle engine();

    public Battle.PendingCast currentCast();
    public Battle.StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage, SkillCategory fromCast, boolean ignoreWeakness);
    public Battle.StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage, SkillCategory fromCast);
    public Battle.StanceResult reduceToughness(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage);
    public boolean delayMovePercent(CanHit target, double percent);
    public boolean firstThisTurn(String key);
    public boolean grantExtraTurn(CanHit actor);
    public boolean insertAction(CanHit actor);
    public boolean rollChance(double chance);
    public boolean singleUseLeft(Skill skill);
    public boolean tryApplyDebuff(CanHit caster, CanHit target, AbstractBuff buff, double baseChance, String specificResistKey);
    public CanHit currentRulePick();
    public CanHit randomAdjacentEnemy(CanHit center);
    public CanHit randomOpponent(CanHit owner);
    public DebuffClass lastAppliedDebuffClass();
    public Resource partyResource(String id);
    public Countdown startCountdown(CanHit owner, String name, double speed);
    public Countdown startCountdown(String name, double speed);
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base, Double fixedCritRate, Double fixedCritDamage, DamageType type, SkillCategory castCategory, boolean unboosted);
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base, Double fixedCritRate, Double fixedCritDamage, DamageType type, SkillCategory castCategory);
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base, Double fixedCritRate, Double fixedCritDamage, DamageType type);
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base, Double fixedCritRate, Double fixedCritDamage);
    public double applyAdditionalDamage(CanHit attacker, CanHit target, DamageElement element, double base);
    public double applyDamage(CanHit target, Damage damage);
    public double applyEnergyGain(CanHit target, EnergyGain gain);
    public double applyTrueDamage(CanHit attacker, CanHit target, DamageElement element, double base);
    public double elationLaughMultiplier();
    public double grantEnergy(CanHit target, double amount);
    public double grantShield(CanHit provider, CanHit target, double amount, String ruleId);
    public double grantShield(CanHit provider, CanHit target, double amount);
    public double grantShield(CanHit target, double amount);
    public double heal(CanHit healer, CanHit target, double baseAmount);
    public double hpLostThisAction(CanHit unit);
    public double settledDamageThisEvent();
    public double tickDotStateNow(CanHit target, DamageElement element, double percent);
    public int attackSequence();
    public int castAppliedCount(String stateName);
    public int countedLaughPoints();
    public int fire(TriggerPayload.Cast payload);
    public int fire(TriggerPayload.ResourceChanged payload);
    public int fire(TriggerPayload.Simple payload);
    public int fireForAlly(TriggerPayload.AllySubject payload);
    public int getSkillPointMax();
    public int getSkillPoints();
    public int partyResourceValue(String id);
    public int summonCountOf(CanHit master);
    public int waveSequence();
    public List<? extends CanHit> getOpponents(CanHit self);
    public List<? extends CanHit> getSideOf(CanHit self);
    public List<CanHit> adjacentEnemies(CanHit center);
    public List<CanHit> allies();
    public List<CanHit> enemies();
    public List<CanHit> lethallyHitThisAction();
    public List<CanHit> lostHpThisAction();
    public List<CanHit> mapAttackers();
    public List<CanHit> waveMonsters();
    public List<Character> characters();
    public List<Countdown> countdowns();
    public List<Countdown> countdownsOf(CanHit owner);
    public List<Enemy> enemyUnits();
    public List<Signal> getQueueSnapshot();
    public List<Summon> summonsOf(CanHit master);
    public Queue queue();
    public SkillPointPolicy getSkillPointPolicy();
    public String getLastStackGainedName();
    public String getLastStateEndedName();
    public String lastChangedResource();
    public Summon memospriteOf(CanHit master);
    public Summon placePanelLessSummon(Character master, String name, Double speed);
    public Summon placePanelLessSummon(Character master, String name);
    public Summon summonAnotherMemosprite(Character master);
    public Summon summonMemosprite(Character master, String name);
    public Summon summonMemosprite(Character master);
    public Summon summonOf(CanHit master);
    public Summon summonServant(Character master);
    public void announceControl(CanHit applier, CanHit target, String name);
    public void announceRuleFired(CanHit owner, String op);
    public void beginRulePick(CanHit pick);
    public void deferSharedDamage(CanHit attacker, CanHit receiver, double base, com.laosun.aluminium.enums.DamageElement element);
    public void deferUntilWave(TriggerTable.CompiledRule rule, TriggerTable.TriggerContext context);
    public void endRulePick();
    public void fireStackGained(CanHit carrier, String stackName);
    public void gainSkillPoint(int n);
    public void markNextCastCountedLaughs(int laughs);
    public void noteElationGift(CanHit holder, int turns);
    public void recordCastApplied(String stateName);
    Random getRng();
}
