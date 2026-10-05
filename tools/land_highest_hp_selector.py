"""`highest_hp_attack_hit`: 「被攻击目标中当前生命值最高的目标」 (2026-10-02).

Reader: 1403 缇宝's ultimate (140303) -- 「受到我方目标攻击后，每有1名目标受到攻击，会对<b>被攻击目标中当前生命值最高的目标</b>造成 1 次等同于缇宝 #3% 生命上限的量子属性附加伤害」
-- which 1415's ode of passage then names (「缇宝的结界的附加伤害」).

What is already there, measured:
  * the CANDIDATE SET: `Damage.hitTargets` (the units this attack connected with), carried on the context as `attackHitTargets` -- so "被攻击目标" is
    a real list, not something to reconstruct;
  * the closed selector set `TARGET_SELECTORS`, and `lowest_hp_ally` as the shape to mirror -- ⚠ but that one is OUR side and reads a PERCENTAGE;
    this sentence says 当前生命值 (current HP, absolute) and is about the units that were hit.
"""
import io
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

SET_ANCHOR = '            "target_else_random_enemy");'
SET_NEW = '''            "target_else_random_enemy",
            // ⭐ 「被攻击目标中**当前生命值最高**的目标」 (2026-10-02; reader: 1403 缇宝's ultimate, whose zone rider picks that unit, and 1415's
            // ode of passage, which names that rider). The candidates are the units THIS attack connected with (`attackHitTargets`), and the
            // comparison is 当前生命值 -- absolute current HP, not the percentage `lowest_hp_ally` reads on our own side.
            "highest_hp_attack_hit");'''

CASE_ANCHOR = '            case TARGET_RANDOM_HIT_ENEMY -> require(randomHitEnemy(ctx), TARGET_RANDOM_HIT_ENEMY, ctx);'
CASE_NEW = '''            case TARGET_RANDOM_HIT_ENEMY -> require(randomHitEnemy(ctx), TARGET_RANDOM_HIT_ENEMY, ctx);
            // ⭐ Among the units this attack hit, the one with the highest CURRENT HP (2026-10-02). Fails loudly on an empty set: a rule that
            // names a victim it cannot find is the silence this engine refuses, and "the attack hit nobody" is a fact worth hearing about.
            case "highest_hp_attack_hit" -> {
                CanHit highest = null;
                for (CanHit hit : ctx.attackHitTargets()) {
                    if (hit == null || hit.isDeath()) {
                        continue;
                    }
                    if (highest == null || hit.getCurrentHp() > highest.getCurrentHp()) {
                        highest = hit;
                    }
                }
                yield require(highest, "highest_hp_attack_hit", ctx);
            }'''

for old, label in ((SET_ANCHOR, "the selector set"), (CASE_ANCHOR, "the resolver")):
    n = txt.count(old)
    print("anchor %-18s : %d" % (label, n))
    if n != 1:
        sys.exit("REFUSING: %s occurs %d times -- nothing written" % (label, n))

txt = txt.replace(SET_ANCHOR, SET_NEW).replace(CASE_ANCHOR, CASE_NEW)
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   highest_hp_attack_hit is registered and resolved")
