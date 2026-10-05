"""Re-create StackableStateBuff and re-apply the five edits (round 1656).

The plain-state probe passed, so a hand-built table DOES apply states and the earlier failure was in this patch. Rebuilt
here so the pieces can be tested one at a time; the class body is the same as before.
"""
import io
import subprocess
import sys

CLASS = "src/main/java/com/laosun/aluminium/models/buff/StackableStateBuff.java"

io.open(CLASS, "w", encoding="utf-8", newline="\n").write('''package com.laosun.aluminium.models.buff;

/**
 * A named state that <b>stacks</b> -- the engine's spelling of "this state carries a COUNT" (2026-10-02).
 *
 * <p>⭐ It opts into the accumulate path that {@link StackBuff} already uses: {@code BuffManager.addBuff} sends every buff
 * whose {@link #isStackable()} is true to {@code addStackable}, grouped by {@link #stackGroupKey()} and capped by
 * {@link #maxStacks()}. The replace rule ({@code isSameKind}, still by name) is deliberately left alone -- it answers a
 * different question and two shipped tests pin it.
 */
public class StackableStateBuff extends StateBuff {

    private final int cap;

    public StackableStateBuff(String state, int turns, boolean permanent, int maxStacks) {
        super(state, turns, permanent);
        if (maxStacks < 1) {
            throw new IllegalArgumentException(
                    "StackableStateBuff '" + state + "' needs a cap of at least 1, got " + maxStacks);
        }
        this.cap = maxStacks;
    }

    @Override
    public boolean isStackable() {
        return true;
    }

    @Override
    public Object stackGroupKey() {
        return "state:" + getState();
    }

    @Override
    public int maxStacks() {
        return cap;
    }

    /** So that {@code stacksOf(name)} counts instances of this state, like any other named buff. */
    @Override
    public String getBuffName() {
        return getState();
    }
}
''')
print("ok   StackableStateBuff re-created")

out = subprocess.run([sys.executable, "tools/patch_stackable_state.py"], capture_output=True, text=True,
                     encoding="utf-8", errors="replace")
print(out.stdout.strip())
if out.returncode != 0:
    sys.stderr.write(out.stderr)
    raise SystemExit(1)
