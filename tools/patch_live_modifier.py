"""Engine: a LIVE modifier -- one whose share is resolved when it is read (2026-10-02, item 62).

The reader family is the biggest registered one in GAPS (entry 39): FOURTEEN documents say 「每拥有 1 层…提高 X%」 in sustained
auras, where the number has to follow the stack count as it changes. Measured before this: `DoubleValue` is eager -- `get()`
returns a cached field and `compute()` runs only on add/remove -- so a snapshot can only be right at the moment it is taken,
and re-applying on every stack change would stack the buff itself (0.14 x (1+2+3+4+5)).

Three edits, each with an exactly-once ASCII anchor:

  1. `Modifier` gains an optional supplier + `rate()`/`isLive()` and a `livePercent` factory;
  2. `compute()` reads `rate()` instead of the stored `value`, so a live modifier is re-resolved on every computation;
  3. `get()` recomputes first when any attached modifier is live -- which is what makes the value follow the stacks with NO
     hook on the stack path, and costs nothing for every attribute that has no live modifier.

⚠ The content spelling (`per_stack_live` + the `modifyAttr` hook) is deliberately NOT in this patch: it touches a path this
round has not read, and the engine half is judgeable on its own.
"""
import io
import sys

PATH = "src/main/java/com/laosun/aluminium/models/DoubleValue.java"
text = io.open(PATH, encoding="utf-8").read()


def patch(old, new, label, count=1):
    global text
    found = text.count(old)
    if found != count:
        sys.stderr.write("REFUSING %s: the anchor appears %d times\n" % (label, found))
        raise SystemExit(1)
    text = text.replace(old, new, count)
    print("ok   %s" % label)


patch(
    """        private int sourceRoleId;
""",
    """        private int sourceRoleId;
        /**
         * ⭐ A modifier whose magnitude is resolved when it is READ, not when it was attached (2026-10-02).
         *
         * <p>The reader family is the 「每拥有 1 层…提高 X%」 auras (fourteen documents): their number has to follow the stack
         * count, and a stored number can only be right at the instant it was stored.
         */
        private java.util.function.DoubleSupplier live;
""",
    "Modifier.live: the optional supplier",
)

patch(
    """        public static Modifier addPercentNumber(double percentValue) {""",
    """        /**
         * ⭐ A modifier whose ADD_PERCENT share is asked for on every computation (2026-10-02).
         *
         * @param share the supplier, read each time the owning attribute is computed
         */
        public static Modifier livePercent(java.util.function.DoubleSupplier share,
                                           ModifierSource source, int sourceRoleId) {
            Modifier modifier = new Modifier(ModifierType.ADD_PERCENT, 0, source, sourceRoleId);
            modifier.live = share;
            return modifier;
        }

        /** Whether this modifier re-resolves its magnitude on every computation. */
        public boolean isLive() {
            return live != null;
        }

        /** The magnitude to use NOW: a live modifier asks its supplier, every other one keeps its stored number. */
        public double rate() {
            return live != null ? live.getAsDouble() : value;
        }

        public static Modifier addPercentNumber(double percentValue) {""",
    "Modifier: the factory and the reader",
)

patch(
    """            percentModifiersTotal += percentModifier.value;""",
    """            percentModifiersTotal += percentModifier.rate();""",
    "compute(): the add-percent share is live-aware",
)

patch(
    """            valueModifiersTotal += valueModifier.value;""",
    """            valueModifiersTotal += valueModifier.rate();""",
    "compute(): the pure value is live-aware",
)

patch(
    """            multiplyPercentTotal *= (1 + multiplyPercentModifier.value);""",
    """            multiplyPercentTotal *= (1 + multiplyPercentModifier.rate());""",
    "compute(): the multiply-percent share is live-aware",
)

patch(
    """    public double get() {
        return value;
    }""",
    """    public double get() {
        // ⭐ A live modifier has to be re-resolved here, because compute() otherwise runs only when modifiers are attached
        // or removed. Scanning costs nothing for the attributes that have none -- which is every attribute until a rule
        // asks for 「每拥有 1 层…」.
        if (hasLiveModifier()) {
            compute();
        }
        return value;
    }

    /** Whether any attached modifier resolves its share at read time. */
    private boolean hasLiveModifier() {
        for (Modifier modifier : addPercentModifiers) {
            if (modifier.isLive()) {
                return true;
            }
        }
        for (Modifier modifier : multiplyPercentModifiers) {
            if (modifier.isLive()) {
                return true;
            }
        }
        for (Modifier modifier : valueModifiers) {
            if (modifier.isLive()) {
                return true;
            }
        }
        return false;
    }""",
    "get(): recompute when a live modifier is attached",
)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("done")
