"""`ally_cid:` resolves in the PLURAL path, so an absent character means "the clause does nothing" (2026-10-02, round 63).

Why: the singular path's convention IS to throw -- `random_ally_below_half_energy` calls `require(...)` too -- but the plural path has the opposite, explicit convention, written
next to `lowest_hp_ally`:

    return lowest == null ? List.of() : List.of(lowest);      // "clause does nothing ... empty list, not an error"

And for a selector that NAMES a character, absence is not a bug: it is a battle without that character in it. Round 62 shipped content that reached this branch with nobody to
find and threw, turning 22 unrelated judges red. Handling it in the plural path is the fix the engine's own convention points at.
"""
import io
import sys

INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
JUDGE = "src/test/java/com/laosun/aluminium/test/SkyOdeStackTest.java"

interp = io.open(INT, encoding="utf-8").read()

ANCHOR = """        if (TARGET_LOWEST_HP_ALLY.equals(selector)) {"""
NEW = """        if (selector.startsWith(ALLY_CID_PREFIX)) {
            // ⭐ 「使**风堇**获得…」 (2026-10-02): a selector that NAMES a character is handled HERE, in the plural path, because absence is not an error --
            // it is a battle that does not contain that character, and the clause simply does nothing. The singular path's convention is the opposite (it throws), which is why
            // this must not be left to it. Same shape as `lowest_hp_ally` just below.
            int cid = Integer.parseInt(selector.substring(ALLY_CID_PREFIX.length()).trim());
            CanHit named = allyWithCid(ctx, cid);
            return named == null ? List.of() : List.of(named);
        }
        if (TARGET_LOWEST_HP_ALLY.equals(selector)) {"""

if interp.count(ANCHOR) != 1:
    sys.exit("REFUSING: the lowest_hp_ally anchor occurs %d times" % interp.count(ANCHOR))
io.open(INT, "w", encoding="utf-8", newline="\n").write(interp.replace(ANCHOR, NEW))
print("ok   ally_cid now resolves in the plural path (absent -> empty list)")

# ---- a judge for the absence, which is exactly what broke 22 tests ----
s = io.open(JUDGE, encoding="utf-8").read()
if "theClauseDoesNothingWhenSheIsNotThere" in s:
    sys.exit("REFUSING: the absence test is already there")

EXTRA = '''
    /**
     * ⭐ The OTHER half of `ally_cid:`: a battle that does not contain the named character. The clause must do nothing -- not throw. ⚠ This is not hypothetical: content that
     * reached this branch with nobody to find turned 22 unrelated judges red before the fix.
     */
    @Test
    public void theClauseDoesNothingWhenSheIsNotThere() {
        Character cyrene = CharacterFactory.create(CYRENE, LEVEL);
        Character other = CharacterFactory.create(OTHER, LEVEL);
        Battle battle = new Battle(List.of(cyrene, other),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        cyrene = battle.characters.get(0);
        other = battle.characters.get(1);

        var demiurge = battle.summonServant(cyrene);
        battle.processRequests();
        var ode = demiurge.skillAt(19);
        Assertions.assertNotNull(ode, "precondition: slot 19");

        // no assertion about layers -- the reading IS that this does not throw
        Assertions.assertDoesNotThrow(
                () -> {
                    SkillExecutor.execute(battle, ode, demiurge, List.of(battle.enemies.getFirst()));
                    battle.processRequests();
                },
                "\\u540d\\u70b9\\u7684\\u89d2\\u8272\\u4e0d\\u5728\\u573a\\u65f6\\uff0c\\u8fd9\\u6761\\u5e94\\u8be5\\u4ec0\\u4e48\\u4e5f\\u4e0d\\u505a");
        Assertions.assertEquals(0, other.getBuffManager().stacksOf(MARK),
                "and it lands on nobody -- not on a bystander");
        System.out.println("[sky_stacks] with the named character absent: no throw, and the bystander has "
                + other.getBuffManager().stacksOf(MARK));
    }
}
'''
s = s.rstrip()
if not s.endswith("}"):
    sys.exit("REFUSING: the judge does not end in a closing brace")
s = s[: s.rfind("}")].rstrip() + "\n" + EXTRA
io.open(JUDGE, "w", encoding="utf-8", newline="\n").write(s)
print("ok   the judge now reads BOTH halves: she is there, and she is not")
