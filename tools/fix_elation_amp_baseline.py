"""Make ElationAmpTest independent of Pearl's baseline Elation.

The test uses Pearl as a fixture and asserts `amp / plain == 2.0`, where `plain` is a run with no granted attributes.
That works only while her baseline Elation is zero. It is not: measured, `plain` already carries 0.1 - and after the
fix to inspect why, the fixture REPLACES her whole trigger table, so the 0.1 is not from her rules at all but baked
into the panel `CharacterFactory` builds (`SkillTrace` runs at construction, from the generated data). With a
baseline `b`, granting +100% multiplies the factor from `1+b` to `1+b+1`, i.e. by `(2+b)/(1+b)` = 1.909 at b = 0.1 -
so the assertion, not the engine, is what needs the baseline.

The rewrite reads `b` off the character and states each expectation in terms of it, so the case keeps testing the
MULTIPLY-vs-ADD question whatever her panel carries.

Usage:
    python tools/fix_elation_amp_baseline.py
    python tools/fix_elation_amp_baseline.py restore
"""
import pathlib
import shutil
import sys

TARGET = pathlib.Path("src/test/java/com/laosun/aluminium/test/engine/ElationAmpTest.java")
BACKUP = TARGET.with_suffix(".java.bak")

OLD_TEST = '''    @Test
    public void theAmpMultipliesWithElationBoostRatherThanAddingToIt() {
        double plain = damage(false, 0.0, 0.0);
        double amp = damage(false, 0.0, 1.0);
        double boost = damage(false, 1.0, 0.0);
        double both = damage(false, 1.0, 1.0);
        double normal = damage(true, 1.0, 1.0);
        System.out.println("[amp] plain=" + plain + " amp(+100%)=" + amp + " boost(+100%)=" + boost
                + " both=" + both + " (ratio " + (both / plain) + ") ; normal with both = " + normal);

        Assertions.assertEquals(2.0, amp / plain, 1e-6, "增笑 +100% doubles the instance");
        Assertions.assertEquals(2.0, boost / plain, 1e-6, "欢愉度 +100% doubles it too");
        Assertions.assertEquals(4.0, both / plain, 1e-6,
                "and together they QUADRUPLE it: (1+1) x (1+1), not 1+1+1");
        Assertions.assertEquals(plain, normal, 1e-9, "a NORMAL instance sees neither attribute");
    }'''

NEW_TEST = '''    @Test
    public void theAmpMultipliesWithElationBoostRatherThanAddingToIt() {
        // Note: The fixture's baseline is NOT zero, and it is not from her own rules either - this case REPLACES her
        // trigger table, so what it reads comes from the panel `CharacterFactory` builds (`SkillTrace` runs at
        // construction). So the baseline is measured and each expectation is stated in terms of it: granting +100%
        // takes the factor from (1 + b) to (1 + b + 1), i.e. it multiplies by (2 + b) / (1 + b). Asserting a flat
        // x2.0 would be asserting that her panel starts at zero, which is a statement about a different clause.
        double baseline = baselineElation();
        double plain = damage(false, 0.0, 0.0);
        double amp = damage(false, 0.0, 1.0);
        double boost = damage(false, 1.0, 0.0);
        double both = damage(false, 1.0, 1.0);
        double normal = damage(true, 1.0, 1.0);
        double oneHundred = (2 + baseline) / (1 + baseline);
        double twoHundred = (3 + baseline) / (1 + baseline);
        System.out.println("[amp] baseline Elation=" + baseline + " plain=" + plain + " amp(+100%)=" + amp
                + " boost(+100%)=" + boost + " both=" + both + " (ratio " + (both / plain) + ")"
                + " ; expected amp ratio " + oneHundred + " ; normal with both = " + normal);

        Assertions.assertEquals(oneHundred, amp / plain, 1e-6, "增笑 +100% raises the factor by exactly 1");
        Assertions.assertEquals(oneHundred, boost / plain, 1e-6, "欢愉度 +100% raises it by exactly 1 too");
        // Two separate +1s on the factor stack to +2, not to +1: that is the whole question this case exists for.
        Assertions.assertEquals(twoHundred, both / plain, 1e-6,
                "and together they add 2 to the factor, not 1 - multiplicative, not additive");
        Assertions.assertEquals(plain, normal, 1e-9, "a NORMAL instance sees neither attribute");
    }

    /** Her Elation (欢愉度) with no rules granted - read off the same fixture the cases below use. */
    private static double baselineElation() {
        Character pearl = CharacterFactory.create(PEARL, 80);
        Battle battle = new Battle(List.of(pearl, CharacterFactory.create(1204, 80)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        return battle.characters.getFirst()
                .getAttribute(com.laosun.aluminium.enums.AttributeType.ELATION_DAMAGE_BOOST).get();
    }'''


def main():
    if len(sys.argv) == 2 and sys.argv[1] == "restore":
        if not BACKUP.exists():
            print("no backup at", BACKUP)
            return 1
        shutil.copyfile(BACKUP, TARGET)
        print("restored", TARGET)
        return 0

    text = TARGET.read_text(encoding="utf-8")
    if OLD_TEST not in text:
        print("anchor not found - the test body has changed; nothing written")
        return 2
    if not BACKUP.exists():
        shutil.copyfile(TARGET, BACKUP)
        print("backup written to", BACKUP)
    TARGET.write_text(text.replace(OLD_TEST, NEW_TEST), encoding="utf-8")

    landed = TARGET.read_text(encoding="utf-8")
    if "baselineElation" not in landed or OLD_TEST in landed:
        print("WRITE CHECK FAILED - restoring")
        shutil.copyfile(BACKUP, TARGET)
        return 3
    print("rewrote the amp assertions in terms of the measured baseline")
    return 0


if __name__ == "__main__":
    sys.exit(main())
