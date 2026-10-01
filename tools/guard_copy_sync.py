# -*- coding: utf-8 -*-
"""Round 549: the deferred copy-sync guard, done the way the tool's comment says."""

import glob
import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/TestRelicSetDataIsInSyncTest.java'
NAME = 'TestRelicSetDataIsInSyncTest'

JAVA = '''package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The test-side copy of relic_sets.json may differ from the shipped one ONLY by the synthetic sets (2026-09-30).
 *
 * <p>Why it exists: a test fixture needs a set that will never be authored, so the test classpath carries a copy with
 * two synthetic entries (99001 for the SummonOpTest fixture, 99002 for NO_RULE_SET), both marked
 * {@code release_version: "test"}. The relic censuses skip anything so marked. A copy can drift, and a drifting copy
 * would silently change what every relic judge is looking at -- so the drift must fail loudly here.
 *
 * <p>⚠ Measured 2026-09-30: the first version of this guard read BOTH files through {@code getResourceAsStream} with
 * the same path, so it saw null ("/data/relic_sets.json is not on the classpath"). The classpath can only ever show
 * one of the two copies; the two REAL files must be read from disk by path.
 */
public class TestRelicSetDataIsInSyncTest {
    private static final Path MAIN = Path.of("src/main/resources/data/relic_sets.json");
    private static final Path TEST = Path.of("src/test/resources/data/relic_sets.json");

    private static Map<String, String> load(Path p) {
        try {
            Map<String, Object> raw = new Gson().fromJson(Files.readString(p, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Object>>() { }.getType());
            Map<String, String> flat = new TreeMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                flat.put(e.getKey(), new Gson().toJson(e.getValue()));
            }
            return flat;
        } catch (Exception e) {
            throw new AssertionError("cannot read " + p.toAbsolutePath(), e);
        }
    }

    private static boolean isSynthetic(String json) {
        return json.contains("\\"release_version\\":\\"test\\"");
    }

    @Test
    public void theTestSideCopyDiffersFromTheShippedFileOnlyByTheSyntheticSets() {
        Assertions.assertTrue(Files.exists(TEST),
                "the test-side copy must exist -- the fixture and NO_RULE_SET live on synthetic sets: " + TEST);
        Map<String, String> main = load(MAIN);
        Map<String, String> test = load(TEST);

        TreeSet<String> synthetic = test.entrySet().stream()
                .filter(e -> isSynthetic(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
        Assertions.assertEquals(new TreeSet<>(java.util.List.of("99001", "99002")), synthetic,
                "exactly the two synthetic sets may be marked release_version \\"test\\"");
        Assertions.assertEquals(main.size() + 2, test.size(),
                "the copy is the shipped file plus those two sets");
        Assertions.assertTrue(main.values().stream().noneMatch(TestRelicSetDataIsInSyncTest::isSynthetic),
                "no SHIPPED set may be marked test");

        for (Map.Entry<String, String> e : main.entrySet()) {
            Assertions.assertEquals(e.getValue(), test.get(e.getKey()),
                    "set " + e.getKey() + " drifted between src/main and src/test -- re-copy the file");
        }
    }
}
'''
io.open(WORK + '/' + JREL, 'w', encoding='utf-8', newline='').write(JAVA)
print('guard written')


def run(*a, q=True):
    return subprocess.run([r'.\gradlew.bat'] + list(a) + (['--quiet'] if q else []) + ['--console=plain'],
                          cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')


res = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                      '--console=plain'], cwd=WORK, capture_output=True, text=True,
                     encoding='utf-8', errors='replace')
print('focused exit: %d' % res.returncode)
if res.returncode != 0:
    for l in ((res.stdout or '') + (res.stderr or '')).strip().split('\n')[-12:]:
        print('  RAW ' + l.strip()[:175])
    import xml.etree.ElementTree as ET
    for p in glob.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = ET.parse(p).getroot()
        except Exception:
            continue
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                n = c.find(k)
                if n is not None:
                    print('  FAIL ' + (n.get('message') or '')[:300].replace('\n', ' '))
    os.remove(WORK + '/' + JREL)
    print('removed the guard')
    sys.exit(1)
# mutation: make the copy actually drift (change one shipped set's data in the copy only)
copy_path = WORK + '/src/test/resources/data/relic_sets.json'
fixed = io.open(copy_path, encoding='utf-8').read()
io.open(copy_path, 'w', encoding='utf-8', newline='').write(fixed.replace('"value": 0.06', '"value": 0.07', 1))
drift = subprocess.run([r'.\gradlew.bat', 'test', '--tests', 'com.laosun.aluminium.test.' + NAME,
                        '--console=plain'], cwd=WORK, capture_output=True, text=True,
                       encoding='utf-8', errors='replace')
io.open(copy_path, 'w', encoding='utf-8', newline='').write(fixed)
print('drift mutation: %s' % ('red (catches drift)' if drift.returncode != 0 else 'GREEN (blind!)'))
if drift.returncode == 0:
    os.remove(WORK + '/' + JREL)
    sys.exit(1)
suite = run('test', '--rerun-tasks')
print('suite exit: %d' % suite.returncode)
if suite.returncode != 0:
    os.remove(WORK + '/' + JREL)
    sys.exit(1)
print('gates: %s' % [run('run', *a).returncode for a in ([], ['--args=mechanics'])])
subprocess.run(['git', 'add', '-A', 'src'], cwd=WORK, check=True)
print(subprocess.run(['git', 'commit', '-m',
                      'test: the test-side set-data copy may differ only by the synthetic sets'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:180])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
