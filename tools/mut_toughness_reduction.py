"""Mutations for the toughness damage reduction (「韧性条不为0时10%的减伤」).

  none            the reduction is never added at all                          (engine)
  additive        it is summed with the other reductions (1 - sigma r)          (engine)
  always          it applies even to a BROKEN enemy                             (engine)
  break_too       the BREAK instance is not excepted (so break damage loses it) (engine)
  barless         an enemy with NO toughness bar takes it too                   (engine)

Every variant must turn a judge red. `restore` puts the files back with `git checkout`, which is safe because this work is
committed.

Usage:
    python tools/mut_toughness_reduction.py <variant>
    python tools/mut_toughness_reduction.py restore
"""
import pathlib
import subprocess
import sys

BATTLE = pathlib.Path('src/main/java/com/laosun/aluminium/Battle.java')
DAMAGE = pathlib.Path('src/main/java/com/laosun/aluminium/models/Damage.java')

STEP = """        if (defender instanceof Enemy toughnessHolder && toughnessHolder.hasToughnessBar()
                && (!toughnessHolder.isBroken() || damage.getType() == DamageType.BREAK)) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }
"""
NOTHING = """        if (false) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }
"""
ALWAYS = """        if (defender instanceof Enemy toughnessHolder && toughnessHolder.hasToughnessBar()) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }
"""
BREAK_INCLUDED = """        if (defender instanceof Enemy toughnessHolder && toughnessHolder.hasToughnessBar()
                && !toughnessHolder.isBroken()) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }
"""
BARLESS_TOO = """        if (defender instanceof Enemy toughnessHolder) {
            damage.addReduction(Constant.TOUGHNESS_DAMAGE_REDUCTION);
        }
"""
ADDITIVE_ANCHOR = """        public ReductionArea add(double r) {
            return add(r, ModifierSource.UNKNOWN, 0);
        }

        public ReductionArea add(double r, ModifierSource source, int roleId) {
            multiplyPercent(-Math.clamp(r, 0, 1), source, roleId);
            return this;
        }
"""
ADDITIVE = """        public ReductionArea add(double r) {
            return add(r, ModifierSource.UNKNOWN, 0);
        }

        public ReductionArea add(double r, ModifierSource source, int roleId) {
            addPercent(-Math.clamp(r, 0, 1), source, roleId);
            return this;
        }
"""


def patch(path, old, new):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times in %s: %r' % (text.count(old), path, old[:70]))
    path.write_text(text.replace(old, new), encoding='utf-8')


def apply(variant):
    if variant == 'none':
        patch(BATTLE, STEP, NOTHING)
    elif variant == 'always':
        patch(BATTLE, STEP, ALWAYS)
    elif variant == 'break_too':
        patch(BATTLE, STEP, BREAK_INCLUDED)
    elif variant == 'barless':
        patch(BATTLE, STEP, BARLESS_TOO)
    elif variant == 'additive':
        patch(DAMAGE, ADDITIVE_ANCHOR, ADDITIVE)
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--', str(BATTLE), str(DAMAGE)], check=True)
        print('restored with git checkout')
        return 0
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    applied = apply(sys.argv[1])
    if applied is None:
        print('unknown variant:', sys.argv[1])
        return 2
    print('applied', applied)
    return 0


if __name__ == '__main__':
    sys.exit(main())
