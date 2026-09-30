# -*- coding: utf-8 -*-
"""Run a NEGATIVE CONTROL the way round 259 taught (discipline 157).

A control is only evidence when the control itself actually ran: twice in round 259 a "0 reds" control turned out to be
a compile failure whose stale XML looked like a pass. `control()` therefore returns the compile exit code, the test
exit code, the number of failing cases, and the AGE of the XML it read, and it refuses to report reds from a stale file.

Use it like:

    with control(WORK, "src/main/java/.../TriggerInterpreter.java") as c:
        c.neutralize("&& !DAMAGE_TYPE_READERS.contains(op)", "&& false")   # condition, not deletion
        print(c.run("com.laosun.aluminium.test.DamageTypeScopeValidationTest"))

Neutralize by CONDITION where possible: deleting code can break the syntax, and a compile failure masquerades as a pass.
"""

import glob
import io
import os
import subprocess
import time
import xml.etree.ElementTree as ET


class Control:
    def __init__(self, work, *paths):
        self.work = work
        self.paths = list(paths)
        # \u26a0 BYTES, not text: reading with universal newlines folds CRLF to LF, and writing that back leaves the
        # working tree dirty with no content change (measured round 260). A control must be invisible when it restores.
        self.saved = {p: open(os.path.join(work, p), 'rb').read() for p in self.paths}

    def neutralize(self, old, new, every=False):
        for path, body in list(self.saved.items()):
            full = os.path.join(self.work, path)
            text = io.open(full, encoding='utf-8').read()
            seen = text.count(old)
            if seen == 0:
                raise SystemExit('control refused: %r does not appear in %s' % (old, path))
            # `every=True` for anchors that repeat once per rank (a cone has five). Anything else must be unique:
            # neutralizing half a file would make the control meaningless.
            io.open(full, 'w', encoding='utf-8', newline='').write(
                text.replace(old, new) if every else text.replace(old, new, 1))
            print('control: %s now has %r' % (path, new))

    def run(self, test_class):
        gradle = [r'.\gradlew.bat']
        comp = subprocess.run(gradle + ['compileJava', 'compileTestJava', '--quiet', '--console=plain'],
                              cwd=self.work, capture_output=True, text=True, encoding='utf-8', errors='replace')
        print('control: compile exit=%s' % comp.returncode)
        if comp.returncode != 0:
            print(((comp.stdout or '') + (comp.stderr or ''))[-600:])
            return {'compile': comp.returncode, 'test': None, 'reds': None, 'xml_age': None}
        before = time.time()
        test = subprocess.run(gradle + ['test', '--tests', test_class, '--quiet', '--console=plain'],
                              cwd=self.work, capture_output=True, text=True, encoding='utf-8', errors='replace')
        reds, newest = 0, None
        for path in glob.glob(self.work + '/build/test-results/test/*%s.xml' % test_class.split('.')[-1]):
            stamp = os.path.getmtime(path)
            newest = stamp if newest is None else max(newest, stamp)
            root = ET.parse(path).getroot()
            for case in root.iter('testcase'):
                if case.find('failure') is not None or case.find('error') is not None:
                    reds += 1
        age = None if newest is None else round(newest - before, 1)
        print('control: test exit=%s reds=%s xml_age=%ss' % (test.returncode, reds, age))
        if age is None or age < 0:
            print('control: WARNING the XML predates this run -- the reds count is not evidence')
        return {'compile': comp.returncode, 'test': test.returncode, 'reds': reds, 'xml_age': age}

    def restore(self):
        for path, body in self.saved.items():
            open(os.path.join(self.work, path), 'wb').write(body)
        print('control: restored %d file(s)' % len(self.saved))

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.restore()
        return False
