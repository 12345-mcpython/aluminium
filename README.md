# aluminium

a game like HSR written by java

## generator

You can use [this](https://gist.github.com/12345-mcpython/7a4032165da00a74dbc7bccdbe3d9500#file-generate_data-py) Python script and [turnbasedgamedata](https://gitlab.com/Dimbreath/turnbasedgamedata) to generate data.

The generated files must be placed under **`src/main/resources/data`** (that directory is the
target of the `/data/` classpath prefix that `JSONReader` reads from). Placing them in
`src/main/resources/` itself will not work.

From the repository root (the script writes into `src/main/resources/data`):

```
python /path/to/generate_data.py
```

## what is checked in

`src/main/resources/data` is ignored by `.gitignore`, with three exceptions. The generator does not produce two of
them at all, and the third it only ever **merges**:

| file | why it is source |
|---|---|
| `monster_attack_modify_ratio.json` | a hand-written patch table; the generator never writes it |
| `enemy_skills.json` | self-built: the game data has no enemy skill table, so the generator never writes it |
| `skill_effects.json` | generated, but **merged**: hand-added keys and hand-written rows survive only when the file already exists, so deleting it loses them |

`data_path.txt` is ignored on purpose - it records where `turnbasedgamedata` lives on *this* machine, and the generator
writes its output next to it. Everything else must be generated before the engine can start, and a missing table fails
loudly with its own file name.