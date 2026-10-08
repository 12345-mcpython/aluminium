"""Find and fix DANGLING javadoc blocks in TriggerInterpreter.java.

A dangling block is a `/** ... */` that is not attached to a declaration. The file has several from earlier refactors,
typically two or three javadoc blocks in a row where only the last one reaches the method (measured: `fireResourceChanged`
had THREE - "Tells the holder that a resource moved", "…WITH its name and delta", "How much of a resource exists right now"
- and only the third described the method below it).

What this fixes AUTOMATICALLY, because nothing can be lost: a block whose next non-blank line starts another `/**` - the
orphan's body is merged into the surviving block, above the survivor's own text. A block orphaned in any other way (next
real line is code that is not a declaration) is only REPORTED: that one needs a human to decide whether it describes the
following statement, belongs on a declaration elsewhere, or is simply stale.

Usage:  python tools/fix_dangling_javadoc.py [--apply]
"""
import io
import pathlib
import re
import sys

TARGET = pathlib.Path('src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java')
DECLARATION = re.compile(
    r'^\s*(@|/\*\*|//|public|protected|private|static|final|abstract|synchronized|native|transient|volatile|class|'
    r'interface|enum|record|void|<|[A-Za-z_][\w.<>\[\], ]*\s+[A-Za-z_]\w*\s*[\(;=])')


def blocks(lines):
    """Every javadoc block as (start, end) line indices, 0-based inclusive."""
    found = []
    start = None
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith('/**') and start is None:
            start = index
        if start is not None and stripped.endswith('*/') and not stripped.startswith('/**/'):
            found.append((start, index))
            start = None
    return found


def body(lines, block):
    """The text inside a block, minus its /** and */ lines."""
    start, end = block
    inner = lines[start + 1:end]
    if lines[start].strip() != '/**':
        inner = [lines[start].split('/**', 1)[1]] + inner
    if lines[end].strip() != '*/':
        inner = inner + [lines[end].rsplit('*/', 1)[0]]
    return inner


def main():
    apply = '--apply' in sys.argv
    lines = TARGET.read_text(encoding='utf-8').split('\n')
    merges = []
    reported = []
    for block in blocks(lines):
        nxt = block[1] + 1
        while nxt < len(lines) and lines[nxt].strip() == '':
            nxt += 1
        if nxt >= len(lines):
            reported.append((block, nxt, '(end of file)'))
            continue
        following = lines[nxt].strip()
        if following.startswith('/**'):
            merges.append((block, nxt))
        elif not DECLARATION.match(lines[nxt]):
            reported.append((block, nxt, lines[nxt].strip()[:90]))
    print('dangling before another javadoc (auto-mergeable): %d' % len(merges))
    for block, nxt in merges:
        print('  lines %d-%d -> merges into the block at %d' % (block[0] + 1, block[1] + 1, nxt + 1))
    print('dangling before code (needs judgement): %d' % len(reported))
    for block, nxt, text in reported:
        print('  lines %d-%d -> next is line %d: %s' % (block[0] + 1, block[1] + 1, nxt + 1, text))

    if not apply or not merges:
        if not apply:
            print('\n(dry run - pass --apply to merge)')
        return 0

    # Merge from the BOTTOM up so earlier indices stay valid.
    for block, nxt in reversed(merges):
        orphan = body(lines, block)
        orphan = [line for line in orphan if line.strip() not in ('', '*')]
        merged = ['/**']
        merged += orphan
        merged += body(lines, (nxt, _end_of_block(lines, nxt)))
        merged.append(' */')
        lines[block[0]:_end_of_block(lines, nxt) + 1] = merged
    TARGET.write_text('\n'.join(lines), encoding='utf-8')
    print('\napplied %d merge(s)' % len(merges))
    return 0


def _end_of_block(lines, start):
    index = start
    while index < len(lines):
        if lines[index].strip().endswith('*/'):
            return index
        index += 1
    raise SystemExit('unterminated javadoc at line %d' % (start + 1))


if __name__ == '__main__':
    sys.exit(main())
