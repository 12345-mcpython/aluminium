"""`highest_hp_attack_hit`: 「被攻击目标中当前生命值最高的目标」 (2026-10-02), reading the hit set the way its sibling does.

Reader: 1403 缇宝's ultimate (140303) -- 「受到我方目标攻击后…会对<b>被攻击目标中当前生命值最高的目标</b>造成 1 次等同于缇宝 #3% 生命上限的量子属性附加伤害」 -- which 1415's
ode of passage names.

⚠ Why the first attempt failed, measured: the pool was read from `ctx.attackHitTargets()` alone, and that field belongs to `ATTACK_FINISHED`; on a
per-hit event such as `DEALING_DAMAGE` it is EMPTY. Its sibling `random_hit_enemy` documents the two carriers and reads BOTH:

    pool = !ctx.attackHitTargets().isEmpty() ? attackHitTargets() : damage().hitTargets()

so this selector does exactly the same, filters to the owner's opponents (the sentence deals damage, so the victim is one) and picks the highest
`getCurrentHp()` -- 当前生命值, absolute, not the percentage `lowest_hp_ally` reads.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

SET_ANCHOR = '            "target_else_random_enemy");'
SET_NEW = '''            "target_else_random_enemy",
            // ⭐ 「被攻击目标中**当前生命值最高**的目标」 (2026-10-02; reader: 1403 缇宝's ultimate, whose zone rider picks that unit, and 1415's
            // ode of passage, which names that rider). The sibling of `random_hit_enemy`: same pool, a different pick.
            "highest_hp_attack_hit");'''

CASE_ANCHOR = '            case TARGET_RANDOM_HIT_ENEMY -> require(randomHitEnemy(ctx), TARGET_RANDOM_HIT_ENEMY, ctx);'
CASE_NEW = '''            case TARGET_RANDOM_HIT_ENEMY -> require(randomHitEnemy(ctx), TARGET_RANDOM_HIT_ENEMY, ctx);
            // ⭐ Among the units this attack hit, the one with the highest CURRENT HP (2026-10-02).
            case "highest_hp_attack_hit" ->
                    require(highestHitTarget(ctx), "highest_hp_attack_hit", ctx);'''

HELPER_ANCHOR = "    private static CanHit randomHitEnemy(TriggerContext ctx) {"
HELPER_NEW = '''    /**
     * The unit with the highest <b>current HP</b> among the ones the attack behind this context hit (2026-10-02).
     *
     * <p>⭐ The pool is read exactly as {@link #randomHitEnemy} reads it, for the reason its own comment gives: an `ATTACK_FINISHED` context
     * carries the attack's FROZEN hit set, while a per-hit context (`DEALING_DAMAGE`) only has its instance's snapshot. ⚠ Reading
     * `attackHitTargets` alone was the first attempt, and it answered "empty" on precisely the event this selector is for.
     *
     * <p>The comparison is 当前生命值 -- absolute current HP, not a percentage -- and the pool is filtered to the owner's opponents, because the
     * sentence deals DAMAGE to the unit it picks. Returns {@code null} when nothing qualifies; the caller turns that into an error.
     */
    private static CanHit highestHitTarget(TriggerContext ctx) {
        if (ctx.battle() == null) {
            return null;
        }
        java.util.Set<CanHit> pool = !ctx.attackHitTargets().isEmpty()
                ? new java.util.LinkedHashSet<>(ctx.attackHitTargets())
                : (ctx.damage() == null ? java.util.Set.of() : ctx.damage().hitTargets());
        CanHit highest = null;
        for (CanHit hit : pool) {
            if (hit == null || hit.isDeath()
                    || !ctx.battle().getOpponents(ctx.owner()).contains(hit)
                    || !ctx.passesTargetFilter(hit)) {
                continue;
            }
            if (highest == null || hit.getCurrentHp() > highest.getCurrentHp()) {
                highest = hit;
            }
        }
        return highest;
    }

''' + HELPER_ANCHOR

for old, label in ((SET_ANCHOR, "the selector set"), (CASE_ANCHOR, "the resolver"), (HELPER_ANCHOR, "the sibling helper")):
    n = txt.count(old)
    print("anchor %-18s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

txt = txt.replace(SET_ANCHOR, SET_NEW).replace(CASE_ANCHOR, CASE_NEW).replace(HELPER_ANCHOR, HELPER_NEW)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   highest_hp_attack_hit reads both carriers, like its sibling")
