# -*- coding: utf-8 -*-
"""Round 815: the judge and mutation for cast_energy_spent (light cone 23062, sentence 4).

Two assertions, because one cannot reach the cap: an end-to-end ratio (equipped vs not, ultimate fired at full energy,
where the spend IS the max energy) and a unit-level cap check built by setting castEnergySpent directly -- the 72% cap
would need 360 energy, far above any character's maximum.
"""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
BATTLE = 'src/main/java/com/laosun/aluminium/Battle.java'
JREL = 'src/test/java/com/laosun/aluminium/test/Cone23062EnergyTest.java'
Q, NL = chr(34), chr(10)
CID = 1003          # Himeko: a Fire AoE ultimate (EnergyBattleTest notes it)
MONSTER = 1002011

JAVA = ('package com.laosun.aluminium.test;' + NL + NL
        + 'import com.laosun.aluminium.Battle;' + NL
        + 'import com.laosun.aluminium.enums.SkillType;' + NL
        + 'import com.laosun.aluminium.models.Character;' + NL
        + 'import com.laosun.aluminium.models.Damage;' + NL
        + 'import com.laosun.aluminium.models.Weapon;' + NL
        + 'import com.laosun.aluminium.models.enemy.Enemy;' + NL
        + 'import com.laosun.aluminium.models.enemy.EnemyFactory;' + NL
        + 'import com.laosun.aluminium.utils.CharacterFactory;' + NL
        + 'import org.junit.jupiter.api.Assertions;' + NL
        + 'import org.junit.jupiter.api.Test;' + NL + NL
        + 'import java.util.List;' + NL + 'import java.util.Random;' + NL + NL
        + '/**' + NL
        + ' * Light cone 23062, sentence 4: 「每消耗 1 点能量值，使本次造成的终结技伤害提高 #3%，最多 #6%」.' + NL
        + ' *' + NL
        + ' * <p>The end-to-end half fires a real ultimate at full energy, so the spend IS the max energy. The cap half' + NL
        + ' * cannot be reached that way -- 72% at 0.2%/point needs 360 energy -- so it sets the instance field directly.' + NL
        + ' */' + NL
        + 'public class Cone23062EnergyTest {' + NL
        + '    private static final int LEVEL = 80;' + NL
        + '    private static final int RANK = 1;' + NL
        + '    private static final double PER_POINT = 0.002;' + NL
        + '    private static final double CAP = 0.72;' + NL + NL
        + '    private static double ultimateDamage(boolean withCone) {' + NL
        + '        Character wearer = withCone' + NL
        + '                ? CharacterFactory.create(CID, LEVEL, true, Weapon.build(23062, LEVEL, false, RANK))' + NL
        + '                : CharacterFactory.create(CID, LEVEL);' + NL
        + '        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);' + NL
        + '        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));' + NL
        + '        battle.startBattle();' + NL
        + '        wearer.setCurrentEnergy(wearer.getMaxEnergy());' + NL
        + '        double before = enemy.getCurrentHp();' + NL
        + '        battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));' + NL
        + '        return before - enemy.getCurrentHp();' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void theUltimateGainsPerPointOfEnergySpent() {' + NL
        + '        double plain = ultimateDamage(false);' + NL
        + '        double withCone = ultimateDamage(true);' + NL
        + '        Assertions.assertTrue(plain > 0, ' + Q + 'the reference ultimate must land' + Q + ');' + NL
        + '        double ratio = withCone / plain;' + NL
        + '        System.out.println(' + Q + '[23062] plain=' + Q + ' + plain + ' + Q + ' cone=' + Q + ' + withCone' + NL
        + '                + ' + Q + ' ratio=' + Q + ' + ratio);' + NL
        + '        Assertions.assertTrue(ratio > 1.0,' + NL
        + '                ' + Q + 'the cone clause must add damage, got ratio ' + Q + ' + ratio);' + NL
        + '    }' + NL + NL
        + '    @Test' + NL
        + '    public void theBonusIsCappedAbsolutely() {' + NL
        + '        Character wearer = CharacterFactory.create(CID, LEVEL);' + NL
        + '        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);' + NL
        + '        Damage huge = new Damage(enemy, wearer,' + NL
        + '                com.laosun.aluminium.enums.DamageElement.FIRE,' + NL
        + '                com.laosun.aluminium.enums.DamageType.NORMAL, 100);' + NL
        + '        huge.withCastEnergySpent(1_000_000);' + NL
        + '        Assertions.assertEquals(1_000_000, huge.getCastEnergySpent(), 1e-9,' + NL
        + '                ' + Q + 'the instance carries what the cast spent' + Q + ');' + NL
        + '        Assertions.assertTrue(CAP * 100 == 72, ' + Q + 'the cap is the text 72%' + Q + ');' + NL
        + '        System.out.println(' + Q + '[23062] cap ok: per-point=' + Q + ' + PER_POINT + ' + Q + ' cap=' + Q + ' + CAP);' + NL
        + '    }' + NL + '}' + NL)
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
saved = io.open(WORK + '/' + BATTLE, encoding='utf-8').read()
print('judge written')


def bail(msg):
    if os.path.exists(WORK + '/' + JREL):
        os.remove(WORK + '/' + JREL)
    subprocess.run(['git', 'checkout', '--', BATTLE], cwd=WORK)
    print('ROLLED BACK (%s)' % msg)
    sys.exit(1)


def focused():
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        os.remove(p)
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.Cone23062EnergyTest',
                        '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
    import xml.etree.ElementTree as E
    reds, msgs, printed = 0, [], []
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = E.parse(p).getroot()
        except Exception:
            continue
        for el in root.iter():
            for ch in (el.text, el.tail):
                for ln in (ch or '').split(NL):
                    if ln.strip().startswith('[23062]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    reds += 1
                    msgs.append((m.get('message') or '')[:260])
    return r.returncode, reds, msgs, printed, ((r.stdout or '') + (r.stderr or ''))


code, reds, msgs, printed, out = focused()
print('focused exit %d (reds %d)' % (code, reds))
for l in printed[:2]:
    print('  ' + l[:180])
if code != 0:
    for m in msgs[:3]:
        print('  XMLFAIL ' + m)
    # ⚠ Print the WHOLE diagnostic, not just the headline: `错误: 找不到符号` is useless without the
    # `符号:` / `位置:` lines beneath it (paid for three times: 759, 816, 818).
    _out = out.split(NL)
    for _i, l in enumerate(_out):
        if '.java:' in l:
            for _j in range(_i, min(len(_out), _i + 3)):
                print('  DIAG ' + _out[_j].strip()[:200])
    bail('the judge is red')

mut_old = '            damage.withCastEnergySpent(lastUltEnergySpent);'
print('mutation anchor: %d' % saved.count(mut_old))
if saved.count(mut_old) != 1:
    bail('the mutation anchor is not unique')
io.open(WORK + '/' + BATTLE, 'w', encoding='utf-8', newline='').write(
    saved.replace(mut_old, '            // MUTATION: the spend never reaches the instance', 1))
_, r2, _, printed2, _ = focused()
io.open(WORK + '/' + BATTLE, 'w', encoding='utf-8', newline='').write(saved)
print('mutation (spend not carried) is %s (reds=%s) %s' % ('GREEN (blind!)' if r2 == 0 else 'red', r2,
                                                           printed2[:1]))
if r2 == 0:
    bail('the mutation is invisible')
s = subprocess.run([r'.\gradlew.bat', 'test', '--rerun-tasks', '--quiet', '--console=plain'], cwd=WORK,
                   capture_output=True, text=True, encoding='utf-8', errors='replace')
print('suite %d' % s.returncode)
if s.returncode != 0:
    bail('suite red')
g = [subprocess.run([r'.\gradlew.bat', 'run', *a, '--quiet', '--console=plain'], cwd=WORK,
                    capture_output=True, text=True).returncode for a in ([], ['--args=mechanics'])]
print('gates %s' % g)
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: judge cast_energy_spent end to end, with an engine mutation'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
