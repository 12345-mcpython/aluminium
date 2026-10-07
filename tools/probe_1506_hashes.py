# -*- coding: utf-8 -*-
"""Decode the loot box's weight expressions by testing the hash hypothesis.

The roll template refers to values by numeric hashes (e.g. -419154903) while the assignments name their keys as strings
("Item2Chance"). If a hash is simply a hash of that name, the expressions become readable - so this computes the usual
candidates (CRC32, CRC32 of the UTF-8 bytes, FNV-1a 32/64, and the same over "TDF_"-prefixed spellings) for every name the
template mentions and matches them against the numbers it uses.

Usage:
    python tools/probe_1506_hashes.py
"""
import io
import json
import pathlib
import sys
import zlib

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

SOURCE = pathlib.Path(r'E:\turnbasedgamedata\Config\ConfigAbility\Avatar\Avatar_SilverWolf999_00_Ability.json')
TEMPLATE = 'SilverWolf999_00_RollItemIndex'
# Every number the template's expressions carry, plus the ones the surrounding ability file used.
NUMBERS = [350671164, -419154903, -419121078, -419053556, 682823203, -788883517,
           1414305234, -185165829, 73664679, -1338392010, 350671164]


def signed32(value):
    value &= 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def fnv1a32(text):
    h = 0x811C9DC5
    for byte in text.encode('utf-8'):
        h = ((h ^ byte) * 0x01000193) & 0xFFFFFFFF
    return signed32(h)


def fnv1a64_low32(text):
    h = 0xCBF29CE484222325
    for byte in text.encode('utf-8'):
        h = ((h ^ byte) * 0x100000001B3) & 0xFFFFFFFFFFFFFFFF
    return signed32(h & 0xFFFFFFFF)


def murmur3_32(text, seed=0):
    data = text.encode('utf-8')
    c1, c2 = 0xCC9E2D51, 0x1B873593
    h = seed & 0xFFFFFFFF
    tail = len(data) & ~0x3
    for i in range(0, tail, 4):
        k = int.from_bytes(data[i:i + 4], 'little')
        k = (k * c1) & 0xFFFFFFFF
        k = ((k << 15) | (k >> 17)) & 0xFFFFFFFF
        k = (k * c2) & 0xFFFFFFFF
        h ^= k
        h = ((h << 13) | (h >> 19)) & 0xFFFFFFFF
        h = (h * 5 + 0xE6546B64) & 0xFFFFFFFF
    k = 0
    rest = data[tail:]
    if len(rest) == 3:
        k ^= rest[2] << 16
    if len(rest) >= 2:
        k ^= rest[1] << 8
    if len(rest) >= 1:
        k ^= rest[0]
        k = (k * c1) & 0xFFFFFFFF
        k = ((k << 15) | (k >> 17)) & 0xFFFFFFFF
        k = (k * c2) & 0xFFFFFFFF
        h ^= k
    h ^= len(data)
    h ^= h >> 16
    h = (h * 0x85EBCA6B) & 0xFFFFFFFF
    h ^= h >> 13
    h = (h * 0xC2B2AE35) & 0xFFFFFFFF
    h ^= h >> 16
    return signed32(h)


def xxh32(text, seed=0):
    data = text.encode('utf-8')
    p1, p2, p3, p4, p5 = 2654435761, 2246822519, 3266489917, 668265263, 374761393
    mask = 0xFFFFFFFF

    def rotl(value, bits):
        return ((value << bits) | (value >> (32 - bits))) & mask

    def round_(acc, value):
        acc = (acc + value * p2) & mask
        acc = rotl(acc, 13)
        return (acc * p1) & mask

    index, length = 0, len(data)
    if length >= 16:
        v1 = (seed + p1 + p2) & mask
        v2 = (seed + p2) & mask
        v3 = seed & mask
        v4 = (seed - p1) & mask
        while index <= length - 16:
            v1 = round_(v1, int.from_bytes(data[index:index + 4], 'little')); index += 4
            v2 = round_(v2, int.from_bytes(data[index:index + 4], 'little')); index += 4
            v3 = round_(v3, int.from_bytes(data[index:index + 4], 'little')); index += 4
            v4 = round_(v4, int.from_bytes(data[index:index + 4], 'little')); index += 4
        h = (rotl(v1, 1) + rotl(v2, 7) + rotl(v3, 12) + rotl(v4, 18)) & mask
    else:
        h = (seed + p5) & mask
    h = (h + length) & mask
    while index <= length - 4:
        h = (h + int.from_bytes(data[index:index + 4], 'little') * p3) & mask
        h = (rotl(h, 17) * p4) & mask
        index += 4
    while index < length:
        h = (h + data[index] * p5) & mask
        h = (rotl(h, 11) * p1) & mask
        index += 1
    h ^= h >> 15
    h = (h * p2) & mask
    h ^= h >> 13
    h = (h * p3) & mask
    h ^= h >> 16
    return signed32(h)


def names(data):
    """Every string name the template mentions, plus the obvious TDF_/Chance spellings."""
    found = set()
    text = json.dumps(data, ensure_ascii=False)
    for word in ('Item1Chance', 'Item2Chance', 'Item3Chance', 'CurrentItem', 'CurScore', 'TDF_CurBP', 'TDF_MaxBP',
                 'RollItemIndex', 'ItemInsertCheck'):
        found.add(word)
    # names actually present as DynamicKey values
    def walk(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key == 'DynamicKey' and isinstance(value, dict) and isinstance(value.get('Value'), str):
                    found.add(value['Value'])
                elif key == 'DynamicKey' and isinstance(value, str):
                    found.add(value)
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)
    walk(data)
    return sorted(found)


def main():
    data = json.loads(SOURCE.read_text(encoding='utf-8'))
    template = [t for t in data['GlobalTemplates'] if t.get('Name') == TEMPLATE]
    candidates = {}
    for name in names(data):
        for spelling in (name, 'TDF_' + name, name.lower(), name.upper()):
            raw = spelling.encode('utf-8')
            candidates[signed32(zlib.crc32(raw))] = 'crc32(%s)' % spelling
            candidates[fnv1a32(spelling)] = 'fnv1a32(%s)' % spelling
            candidates[fnv1a64_low32(spelling)] = 'fnv1a64low(%s)' % spelling
            candidates[murmur3_32(spelling)] = 'murmur3_32(%s)' % spelling
            candidates[xxh32(spelling)] = 'xxh32(%s)' % spelling
            candidates[xxh32(spelling, 1)] = 'xxh32@1(%s)' % spelling
    print('names considered: %s\n' % ', '.join(names(data)))
    for number in dict.fromkeys(NUMBERS):
        print('%-16s -> %s' % (number, candidates.get(number, '(no match among the usual hashes)')))


if __name__ == '__main__':
    main()
