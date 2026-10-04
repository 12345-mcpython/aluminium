"""Patch: a lethal blow can be DEFERRED by a state, and the deferred death is committed at the carrier's next turn.

The capability 1407's 「月茧之庇」 needs (2026-10-02, item 54). Four edits, each with an exactly-once anchor assertion:

  1. TriggerInterpreter.applyState -- apply a DeferredDeathBuff instead of a StateBuff when the effect says so;
  2. BuffManager -- a `defersDeath()` query beside `hasState`;
  3. Battle.applyDamage -- do not commit the death while such a state is on the victim;
  4. Battle.beforeMove -- commit it at the carrier's own turn if the state is still there (「否则将立即陷入无法战斗状态」).

\u26a0 Nothing here approximates: the victim really is at zero HP while the state holds the death (that is 「延后」), and the
state's removal -- by a heal or by a shield, in content -- is what saves it.
"""
import io
import sys

EDITS = []


def patch(path, old, new, label):
    text = io.open(path, encoding="utf-8").read()
    count = text.count(old)
    if count != 1:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, count))
        raise SystemExit(1)
    io.open(path, "w", encoding="utf-8", newline="\n").write(text.replace(old, new))
    print("ok   %s" % label)
    EDITS.append(label)


# 1. the state is applied as a deferring buff when the effect asks for one
patch(
    "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java",
    "withLifetime(new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);",
    "withLifetime(Boolean.TRUE.equals(effect.getDefersDeath())\n"
    "                        ? new DeferredDeathBuff(state, turns, permanent)\n"
    "                        : new StateBuff(state, turns, permanent), effect), effect, ctx), ctx);",
    "TriggerInterpreter.applyState: defers_death selects the deferring state",
)

# 2. the query, right after hasState
patch(
    "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java",
    "    public boolean hasState(String state) {",
    "    /**\n"
    "     * \u2b50 Whether any state on us <b>defers a lethal blow</b> (2026-10-02; reader: 1407's \u6708\u8327\u4e4b\u5e87).\n"
    "     *\n"
    "     * <p>Asked by {@code Battle} at the two moments that matter: before it commits a death, and at the carrier's own\n"
    "     * turn (where a still-present deferral is committed). By type, not by name -- see {@link DeferredDeathBuff}.\n"
    "     */\n"
    "    public boolean defersDeath() {\n"
    "        for (AbstractBuff buff : List.copyOf(buffs)) {\n"
    "            if (buff instanceof DeferredDeathBuff) {\n"
    "                return true;\n"
    "            }\n"
    "        }\n"
    "        return false;\n"
    "    }\n\n"
    "    public boolean hasState(String state) {",
    "BuffManager.defersDeath: the type-based query",
)

# 3. the death is held, not committed
patch(
    "src/main/java/com/laosun/aluminium/Battle.java",
    "            died = target.getCurrentHp() <= 0;\n            if (died) {\n                target.perish();\n            }",
    "            // \u2b50 \u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (1407's \u6708\u8327\u4e4b\u5e87): a state may hold the death instead of\n"
    "            // committing it. \u26a0 The victim really is at zero HP here -- that IS \u300c\u5ef6\u540e\u300d -- so this is not a heal: what\n"
    "            // saves it is the state being GONE by its own turn (a heal or a shield removes it, in content).\n"
    "            died = target.getCurrentHp() <= 0 && !target.getBuffManager().defersDeath();\n            if (died) {\n                target.perish();\n            }",
    "Battle.applyDamage: a deferring state holds the death",
)

# 4. the held death is committed at the carrier's own turn
patch(
    "src/main/java/com/laosun/aluminium/Battle.java",
    "        if (actor.isDeath()) {\n            return;\n        }",
    "        if (actor.isDeath()) {\n            return;\n        }\n"
    "        // \u2b50 \u300c\uff08\u82e5\u672a\u56de\u590d\uff09\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d (1407): the death that a deferring state held is committed\n"
    "        // HERE -- at the carrier's own turn, before that turn happens -- if nothing removed the state in between.\n"
    "        // \u26a0 Order matters: this is before the buff tick and before TURN_START, so a heal that arrives on this very\n"
    "        // turn is too late, which is what \u300c\u4e0b\u4e00\u6b21\u56de\u5408\u5f00\u59cb\u524d\u300d states.\n"
    "        if (actor.getCurrentHp() <= 0 && actor.getBuffManager().defersDeath()) {\n"
    "            actor.perish();\n"
    "            return;\n"
    "        }",
    "Battle.beforeMove: the held death is committed at the carrier's turn",
)

print("patched %d sites" % len(EDITS))
