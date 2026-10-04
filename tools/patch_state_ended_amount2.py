"""Precondition ② of objective ①-a: `STATE_ENDED` carries the magnitude, and the sweep announces ONCE.

Two changes on top of the ready-made patch:

  1. `fireStateEnded(carrier, name, magnitude)` -- the overload from `tools/patch_state_ended_amount.py` (re-run here);
  2. `BuffManager.removeState` fires ONCE with the total, instead of once per removed instance.

\u2b50 Why (2) is not optional: measured in the previous judge attempt, three instances removed in one sweep fired three times,
so a reader that turns "50% of it" into a resource would apply its 50% three times over three different totals. The sentence
is about the state ending, which happens once.

\u26a0 The spent-duration path keeps firing per instance: there each instance really does expire on its own, and that is the
moment the event is about.
"""
import io
import subprocess
import sys

out = subprocess.run([sys.executable, "tools/patch_state_ended_amount.py"], capture_output=True, text=True,
                     encoding="utf-8", errors="replace")
print(out.stdout.strip())
if out.returncode != 0:
    sys.stderr.write(out.stderr)
    raise SystemExit(1)

MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
text = io.open(MANAGER, encoding="utf-8").read()

OLD = """            if (battle != null) {
                battle.fireStateEnded(instance, buff.getState(), instance.getBuffManager().stacksOf(buff.getState()));
            }
            removeBuff(buff);"""
NEW = """            removeBuff(buff);"""

# the firing moves out of the loop: one announcement per sweep, carrying how many instances it took
OLD_HEAD = """        String wanted = state.trim();
        int removed = 0;"""
NEW_HEAD = """        String wanted = state.trim();
        int removed = 0;
        // \u2b50 ONE announcement per sweep, with the total (2026-10-02): a sweep can take several instances of one state, and
        // the rule that reads the amount (\u300c\u5c06\u5176\u4e2d\u7684 50% \u8f6c\u5316\u4e3a\u81ea\u8eab\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d) must see "how much ended" once, not once per
        // instance with a shrinking number.
        int ending = 0;
        for (AbstractBuff carried : List.copyOf(buffs)) {
            if (carried instanceof StateBuff buff && wanted.equals(buff.getState())) {
                ending++;
            }
        }
        if (ending > 0 && battle != null) {
            battle.fireStateEnded(instance, wanted, ending);
        }"""

for old, new, label in ((OLD_HEAD, NEW_HEAD, "removeState announces once with the total"),
                        (OLD, NEW, "the in-loop firing is gone")):
    if text.count(old) != 1:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, text.count(old)))
        raise SystemExit(1)
    text = text.replace(old, new, 1)
    print("ok   %s" % label)
io.open(MANAGER, "w", encoding="utf-8", newline="\n").write(text)
print("done")
