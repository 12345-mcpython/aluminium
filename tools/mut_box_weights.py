"""Mutations for the box's WEIGHTED pick (`weight` / `weight_steps`).

  uniform            weights are ignored: every pick is uniform again                  (engine)
  no_steps           the weight is read but the graded steps are not                   (engine)
  strict_thresholds  a step counts only ABOVE its threshold, not at it                 (engine)
  no_weight_refusal  the loader stops refusing a weight without `one_of`               (engine)
  content_no_steps   the points effect loses its two steps                             (content)
  content_weight     the laughs effect's base weight is 2 instead of 10                (content)

Every variant must turn a judge red; `restore` puts the files back with `git checkout`, which is safe because this work is
committed.

Usage:
    python tools/mut_box_weights.py <variant>
    python tools/mut_box_weights.py restore
"""
import json
import pathlib
import subprocess
import sys

CONTENT = pathlib.Path('src/main/resources/characters/1506.json')
INTERPRETER = pathlib.Path('src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java')
TABLE = pathlib.Path('src/main/java/com/laosun/aluminium/models/TriggerTable.java')
BOX = 'p1506_zone_loot_box_on_a_spent_point'

WEIGHTED_ROLL = """                if (!weighted) {
                    chosen = pool.get(battle.getRng().nextInt(pool.size()));
                } else {
                    long total = 0;
                    for (int weight : weights) {
                        total += weight;
                    }
                    if (total <= 0) {
                        throw new IllegalStateException("this rule's `one_of` is WEIGHTED but every weight adds up to "
                                + "nothing, so there is no distribution to roll against (source: " + rule.source() + ")");
                    }
                    if (total > Integer.MAX_VALUE) {
                        throw new IllegalStateException("this rule's `one_of` weights add up to " + total
                                + ", which no single roll can cover (source: " + rule.source() + ")");
                    }
                    int roll = battle.getRng().nextInt((int) total);
                    for (int index = 0; index < pool.size(); index++) {
                        roll -= weights[index];
                        if (roll < 0) {
                            chosen = pool.get(index);
                            break;
                        }
                    }
                }
"""
UNIFORMED = """                {
                    chosen = pool.get(battle.getRng().nextInt(pool.size()));
                }
"""
STEPS = """        for (String raw : effect.getWeightSteps()) {"""
# ⚠ It must COMPILE: a variant that fails to build is reported as red, which proves nothing about the judge. (The first
# version of this one replaced the guard with `if (true) { return weight; }` and left a dangling `for` - a compile error,
# i.e. a false "red".)
NOTHING_STEPPED = """        for (String raw : new java.util.ArrayList<String>()) {"""
STRICT = """            if (counterValue(name, ctx) >= threshold) {
                weight += delta;
            }
"""
REFUSAL = """                if (effect.getWeight() != null || effect.getWeightSteps() != null) {
                    throw new IllegalArgumentException(
                            "An effect states a pick \\"weight\\" / \\"weight_steps\\" but its rule does not state "
                                    + "\\"one_of\\": there is no pick for a weight to be a share of "
                                    + "(source: " + spec.getSource() + ")");
                }
"""


def load():
    return json.loads(CONTENT.read_text(encoding='utf-8'))


def save(data):
    CONTENT.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def box_effects(data):
    return next(r for r in data['rules'] if r['id'] == BOX)['do']


def patch(path, old, new):
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit('anchor hit %d times in %s: %r' % (text.count(old), path, old[:70]))
    path.write_text(text.replace(old, new), encoding='utf-8')


def apply(variant):
    if variant == 'uniform':
        patch(INTERPRETER, WEIGHTED_ROLL, UNIFORMED)
    elif variant == 'no_steps':
        patch(INTERPRETER, STEPS, NOTHING_STEPPED)
    elif variant == 'strict_thresholds':
        patch(INTERPRETER, STRICT, STRICT.replace('>=', '>'))
    elif variant == 'no_weight_refusal':
        patch(TABLE, REFUSAL, '')
    elif variant == 'content_no_steps':
        data = load()
        for effect in box_effects(data):
            if effect.get('resource') == '战技点':
                effect.pop('weight_steps', None)
        save(data)
    elif variant == 'content_weight':
        data = load()
        for effect in box_effects(data):
            if effect.get('resource') == '笑点':
                effect['weight'] = 2
        save(data)
    else:
        return None
    return variant


def main():
    if len(sys.argv) == 2 and sys.argv[1] == 'restore':
        subprocess.run(['git', 'checkout', '--', str(CONTENT), str(INTERPRETER), str(TABLE)], check=True)
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
