import json

character: list[dict] = []

attacker: list[dict] = []


def read_characters():
    global character
    if not character:
        with open("data/characters.json", encoding="utf-8") as f:
            character_json = json.load(f)
            # name mt level health defensive attack
            for i in character_json:
                character.append(i)

    global attacker
    if not attacker:
        with open("data/attackers.json", encoding="utf-8") as f:
            attacker_json = json.load(f)
            for i in attacker_json:
                attacker.append(i)
