# -*- coding: utf-8 -*-
"""Round 596: audit shipped content for notes that contradict each other inside one file.

Three instances were found by hand in this stretch (21017, 23057, 1111): an EARLIER note says a clause is still
registered/unwritten, while the file -- sometimes a few lines below -- implements it and a LATER note says so. The
mechanism is that when work lands, the new rule and its new note are added, but the old "still registered" sentence
is never removed. This scans every shipped file for that signature and prints the candidates.

Read-only: it changes nothing.
"""

import glob
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
WORK = r'E:\code\java\aluminium'

# "this clause is NOT written" markers
UNWRITTEN = re.compile(r'(已登记|仍登记|登记而不猜|未写|尚未写|写不出来|不能写|不写近似)')
# "this clause IS written" markers
WRITTEN = re.compile(r'(已写出|已经写好|今天就能完整表达|已写|WRITTEN|已补齐|已补)')

roots = [
    ('characters', WORK + '/src/main/resources/characters/*.json'),
    ('light_cones', WORK + '/src/main/resources/light_cones/[0-9]*.json'),
    ('relic_sets', WORK + '/src/main/resources/relic_sets/[0-9]*.json'),
]

hits = []
for kind, pattern in roots:
    for p in sorted(glob.glob(pattern)):
        text = io.open(p, encoding='utf-8', errors='replace').read()
        # find every note string and classify it
        notes = re.findall(r'"note"\s*:\s*"((?:[^"\\]|\\.)*)"', text)
        if not notes:
            continue
        unwritten = [n for n in notes if UNWRITTEN.search(n)]
        written = [n for n in notes if WRITTEN.search(n)]
        if unwritten and written:
            hits.append((kind, os.path.basename(p), len(notes),
                         len(unwritten), len(written),
                         unwritten[0][:90].replace('\\n', ' ')))

print('files carrying BOTH a still-unwritten claim and a written claim: %d' % len(hits))
print()
for kind, name, notes, un, wr, sample in hits[:25]:
    print('%-12s %-14s notes=%-3d unwritten=%-2d written=%-2d | %s' % (kind, name, notes, un, wr, sample))
if len(hits) > 25:
    print('... and %d more' % (len(hits) - 25))
