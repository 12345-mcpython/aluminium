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

`src/main/resources/data` is ignored by `.gitignore`, with three exceptions the generator cannot produce:

| file | why it is source |
|---|---|
| `monster_attack_modify_ratio.json` | a hand-written patch table |
| `skill_effects.json` | hand-maintained: how to read a non-damaging skill |
| `enemy_skills.json` | self-built: the game data has no enemy skill table |

Everything else - `skills.json` included - must be generated before the engine can start, and a missing table fails
loudly with its own file name.