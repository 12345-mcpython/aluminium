# -*- coding: utf-8 -*-
"""Round 685: the guard for the class of bug that cost this stretch about five rounds.

Content files name effect fields by JSON key. It is not always the snake_case @SerializedName value: for fields with
no annotation Gson falls back to the JAVA field name, and an unknown key is dropped with no error at all -- so a
misspelled key is a silently dead rule that passes the load check, the full suite and both demo gates (measured on
1312, rounds 668-683). The guard derives the known set from EffectSpec itself, so it maintains itself.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'
JREL = 'src/test/java/com/laosun/aluminium/test/EffectKeyDisciplineTest.java'
NL = chr(10)

JAVA = '''package com.laosun.aluminium.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.laosun.aluminium.beans.EffectSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Every key an effect object uses must be one the engine actually reads.
 *
 * <p>Gson maps a field by its {@code @SerializedName} when it has one and by the JAVA field name when it does not --
 * so the two conventions coexist (`op`, `per_stack`, `cap_amount` next to `amountFromEvent`, `amountPercent`). An
 * unknown key is not an error: it is dropped. That made 1312's `amount_from_event` / `amount_percent` a rule that
 * loaded, passed the whole suite and both demo gates, and did nothing (measured in rounds 668-683, about five rounds).
 *
 * <p>The known set is read off {@link EffectSpec} by reflection, so adding a field extends the guard automatically.
 */
public class EffectKeyDisciplineTest {
    /** `note` belongs to a RULE; an effect that repeats one is dead text, but harmless and already shipped. */
    private static final Set<String> ALSO_ALLOWED = Set.of("note");

    private static Set<String> knownKeys() {
        Set<String> known = new HashSet<>();
        for (Field f : EffectSpec.class.getDeclaredFields()) {
            SerializedName sn = f.getAnnotation(SerializedName.class);
            known.add(sn == null ? f.getName() : sn.value());
        }
        known.addAll(ALSO_ALLOWED);
        return known;
    }

    private static void walk(JsonElement el, Set<String> known, Map<String, Set<String>> unknown, String file) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("op") && o.get("op").isJsonPrimitive()) {
                for (String key : o.keySet()) {
                    if (!known.contains(key)) {
                        unknown.computeIfAbsent(key, k -> new HashSet<>()).add(file);
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                walk(e.getValue(), known, unknown, file);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                walk(e, known, unknown, file);
            }
        }
    }

    @Test
    public void everyEffectKeyIsOneTheEngineReads() throws Exception {
        Set<String> known = knownKeys();
        Assertions.assertTrue(known.size() > 30, "the reflected key set looks too small: " + known.size());
        Map<String, Set<String>> unknown = new TreeMap<>();
        int files = 0;
        for (String dir : List.of("characters", "light_cones", "relic_sets", "memosprites")) {
            Path root = Path.of("src", "main", "resources", dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.list(root)) {
                for (Path p : paths.filter(x -> x.toString().endsWith(".json")).toList()) {
                    if (p.getFileName().toString().startsWith("_")) {
                        continue;
                    }
                    files++;
                    try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        walk(JsonParser.parseReader(r), known, unknown, p.getFileName().toString());
                    }
                }
            }
        }
        Assertions.assertTrue(files > 200, "expected the shipped content, found " + files + " files");
        Assertions.assertEquals(Map.of(), unknown,
                "these effect keys are not fields of EffectSpec, so Gson drops them silently: " + unknown);
        System.out.println("[keys] " + files + " files, " + known.size() + " known keys, 0 unknown");
    }
}
'''
path = WORK + '/' + JREL
io.open(path, 'w', encoding='utf-8', newline='').write(JAVA)
print('guard written')


def bail(msg):
    if os.path.exists(path):
        os.remove(path)
    print('ROLLED BACK the guard (%s)' % msg)
    sys.exit(1)


def focused():
    r = subprocess.run([r'.\gradlew.bat', 'test', '--tests',
                        'com.laosun.aluminium.test.EffectKeyDisciplineTest', '--console=plain'],
                       cwd=WORK, capture_output=True, text=True, encoding='utf-8', errors='replace')
    import glob as g
    import xml.etree.ElementTree as E
    msgs, printed = [], []
    for p in g.glob(WORK + '/build/test-results/test/*.xml'):
        try:
            root = E.parse(p).getroot()
        except Exception:
            continue
        for el in root.iter():
            for ch in (el.text, el.tail):
                for ln in (ch or '').split(NL):
                    if ln.strip().startswith('[keys]'):
                        printed.append(ln.strip())
        for c in root.iter('testcase'):
            for k in ('failure', 'error'):
                m = c.find(k)
                if m is not None:
                    msgs.append((m.get('message') or '')[:400])
    return r.returncode, msgs, printed, ((r.stdout or '') + (r.stderr or ''))


code, msgs, printed, out = focused()
print('focused exit %d' % code)
for l in printed[:1]:
    print('  ' + l[:170])
if code != 0:
    for m in msgs[:2]:
        print('  XMLFAIL ' + m)
    for l in out.strip().split(NL)[-6:]:
        print('  RAW ' + l.strip()[:150])
    bail('the guard is red')

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
                      'test: every effect key must be one EffectSpec reads -- unknown keys are dropped silently'],
                     cwd=WORK, capture_output=True, text=True).stdout.strip().split('\n')[0][:170])
subprocess.run(['git', 'push'], cwd=WORK, capture_output=True, text=True)
print('tree: ' + (subprocess.run(['git', 'status', '--porcelain'], cwd=WORK, capture_output=True,
                                 text=True).stdout.strip() or 'clean'))
