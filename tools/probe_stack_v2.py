"""Probe v2 (round 1657): is it the RESOURCES argument (or the extra rule) that empties a hand-built table?

Measured so far: one rule -> 1 stack, two rules -> 2 stacks, on a table built with the TWO-argument constructor. The
rolled-back judge used the THREE-argument constructor (with a declared resource) plus a STATE_ENDED rule, and read zero
everywhere. This adds those two variables one at a time, still with shipped vocabulary and no engine change.
"""
import io

PATH = "src/test/java/com/laosun/aluminium/test/HandBuiltStackProbeTest.java"
text = io.open(PATH, encoding="utf-8").read()

OLD = '''    // ==================================================================

    private static int stacksAfter(int rules) {'''
NEW = '''    /** The third variable: the same two rules, but the table also DECLARES a resource. */
    @Test
    public void twoRulesWithADeclaredResource() {
        int stacks = stacksAfter(2, true, false);
        System.out.println("[probe] rules=2 withResource=true stacks=" + stacks);
        Assertions.assertEquals(2, stacks, "declaring a resource must not empty the table");
    }

    /** And with a STATE_ENDED rule as well, which is what the rolled-back judge had. */
    @Test
    public void twoRulesPlusAStateEndedRule() {
        int stacks = stacksAfter(2, true, true);
        System.out.println("[probe] rules=2 withResource=true withStateEndedRule=true stacks=" + stacks);
        Assertions.assertEquals(2, stacks, "an extra subscriber must not empty the table");
    }

    // ==================================================================

    private static int stacksAfter(int rules) {
        return stacksAfter(rules, false, false);
    }

    private static int stacksAfter(int rules, boolean withResource, boolean withStateEndedRule) {'''
if text.count(OLD) != 1:
    raise SystemExit("REFUSING: anchor 1")
text = text.replace(OLD, NEW, 1)

OLD2 = '''        owner.setTriggerTable(new TriggerTable(OWNER, specs));'''
NEW2 = '''        if (withStateEndedRule) {
            EffectSpec record = new EffectSpec();
            TriggerSpecs.set(record, "op", "GAIN_RESOURCE");
            TriggerSpecs.set(record, "resource", "probeRecord");
            TriggerSpecs.set(record, "amount", 1.0);
            specs.add(TriggerSpecs.rule("STATE_ENDED", List.of("self state_ended " + STACK), record));
        }
        if (withResource) {
            owner.setTriggerTable(new TriggerTable(OWNER, specs,
                    List.of(new com.laosun.aluminium.beans.ResourceSpec("probeRecord", 2147483647, 0,
                            null, null, "hand-built probe", null))));
        } else {
            owner.setTriggerTable(new TriggerTable(OWNER, specs));
        }'''
if text.count(OLD2) != 1:
    raise SystemExit("REFUSING: anchor 2")
text = text.replace(OLD2, NEW2, 1)

io.open(PATH, "w", encoding="utf-8", newline="").write(text)
print("ok   two more readings added")
