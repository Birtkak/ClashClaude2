#!/usr/bin/env python3
"""Builds the Card Forge page (tools/card-forge/card-forge.html) from template.html,
inlining the current card stats from Cards.kt so the page's balance panel stays current.

    python3 tools/card-forge/build.py

Then republish the result to the Card Forge artifact (URL in CLAUDE.md).
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
CARDS_KT = os.path.join(HERE, "..", "..", "app", "src", "main", "java", "com", "clashclaude", "game", "data", "Cards.kt")


def parse_cards():
    src = open(CARDS_KT).read()
    cards = []
    for block in re.findall(r'CardDef\(\s*("\w+".*?)\n        \),', src, re.S):
        m = re.match(r'"(\w+)", "([^"]+)", "[^"]*", (\d+), CardType\.(\w+), Rarity\.(\w+),\s*"([^"]*)",(.*)', block, re.S)
        cid, name, cost, typ, rarity, desc, rest = m.groups()
        kv = dict(re.findall(r"(\w+) = ([^,\n]+)", rest))

        def num(key, default=None):
            v = kv.get(key)
            return float(v.rstrip("f")) if v else default

        # Defaults mirror CardDef's constructor defaults.
        card = {"id": cid, "name": name, "cost": int(cost), "type": typ.lower(), "rarity": rarity.lower(), "desc": desc}
        if typ == "SPELL":
            card.update(
                damage=int(num("damage", 0)), spellRadius=num("spellRadius", 0),
                towerDamagePct=round(num("towerDamagePct", 1) * 100), stun=num("stun", 0),
            )
        else:
            card.update(
                hp=int(num("hp", 0)), damage=int(num("damage", 0)), hitSpeed=num("hitSpeed", 1),
                range=num("range", 0.5), count=int(num("count", 1)), speed=num("speed", 0),
                splash=num("splash", 0), lifetime=num("lifetime", 0),
                targets={"TargetType.GROUND": "ground", "TargetType.BUILDINGS": "buildings"}.get(kv.get("targets"), "air & ground"),
                flying=kv.get("flying") == "true",
            )
        cards.append(card)
    return cards


if __name__ == "__main__":
    cards = parse_cards()
    page = open(os.path.join(HERE, "template.html")).read().replace("__CARDS__", json.dumps(cards, separators=(",", ":")))
    out = os.path.join(HERE, "card-forge.html")
    open(out, "w").write(page)
    print(f"{out}: {len(cards)} cards")
