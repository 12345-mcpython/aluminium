package com.laosun.aluminium.state;

/**
 * HP, the death flag and the invulnerability flag.
 *
 * <p><b>Why this is separate.</b> "Am I dead" and "did this hit kill me" are the same state, and
 * invulnerability is orthogonal to both but read at the same settlement point. Keeping them together
 * makes the one place a lethal hit becomes a death explicit; the shield is a separate component because
 * it outlives one hit and records its own origin.
 */
public final class HealthState {

    private double hp;
    private boolean dead;
    private boolean invulnerable;

    /**
     * @return current HP
     */
    public double hp() {
        return hp;
    }

    /**
     * @param hp current HP
     */
    public void hp(double hp) {
        this.hp = hp;
    }

    /**
     * @return whether the unit is dead
     */
    public boolean isDead() {
        return dead;
    }

    /**
     * @return whether the unit is invulnerable
     */
    public boolean isInvulnerable() {
        return invulnerable;
    }

    /**
     * @param invulnerable the new flag
     */
    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    /**
     * Marks the unit dead without touching HP.
     */
    public void perish() {
        dead = true;
    }

    /**
     * Applies one hit, draining the shield first.
     *
     * @param damage the incoming damage
     * @param mayDie whether reaching 0 commits the death
     * @param shield the shield to drain
     * @return whether this hit killed the unit
     */
    public boolean takeDamage(double damage, boolean mayDie, ShieldState shield) {
        if (dead || damage <= 0) {
            return false;
        }
        damage = shield.absorb(damage);
        if (damage <= 0) {
            return false;
        }
        hp -= damage;
        if (hp <= 0) {
            hp = 0;
            if (!mayDie) {
                return false;
            }
            dead = true;
            return true;
        }
        return false;
    }

    /**
     * Spends HP without a damage instance, never below 1.
     *
     * @param amount the requested spend
     * @return the HP actually spent
     */
    public double consume(double amount) {
        if (amount <= 0 || dead) {
            return 0;
        }
        double spendable = Math.max(0, hp - 1);
        double spent = Math.min(amount, spendable);
        hp -= spent;
        return spent;
    }

    /**
     * Restores HP, capped at max HP; no effect when dead.
     *
     * @param amount the amount
     * @param maxHp  the cap
     */
    public void heal(double amount, double maxHp) {
        if (dead || amount <= 0) {
            return;
        }
        hp = Math.min(hp + amount, maxHp);
    }
}
