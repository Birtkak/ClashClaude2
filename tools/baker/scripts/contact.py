"""Contact sheet of baked sprites: a few frames per sheet, both teams, plus card portraits."""
import re, sys
from PIL import Image
root = 'app/src/main/assets/sprites/'
man = open('app/src/main/java/com/clashclaude/game/ui/SpriteManifest.kt').read()
rows = []
for m in re.finditer(r'"(\w+)" to SpriteSheet\((\d+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+), (\d+)', man):
    id_, w, h, ax, ay, dirs, idle, walk, atk = m.group(1), *map(int, m.groups()[1:])
    for team in ('blue', 'red'):
        im = Image.open(f'{root}{id_}_{team}.png')
        picks = []
        for r in sorted(set([0, dirs // 2, dirs - 1])):
            picks += [(r, 0)]
            if walk: picks += [(r, idle + 2)]
            if atk: picks += [(r, idle + walk + 1), (r, idle + walk + 3)]
        cells = [im.crop((c * w, r * h, c * w + w, r * h + h)) for r, c in picks]
        rows.append(cells)
        if dirs == 1: pass
scale = 2
W = max(sum(c.width for c in r) for r in rows) * scale
H = sum(max(c.height for c in r) for r in rows) * scale
out = Image.new('RGBA', (W, H), (109, 190, 69, 255))
y = 0
for r in rows:
    x = 0
    for c in r:
        out.alpha_composite(c.resize((c.width * scale, c.height * scale), Image.LANCZOS), (x, y))
        x += c.width * scale
    y += max(c.height for c in r) * scale
out.save(sys.argv[1])
cards = ['knight','archers','giant','wizard','skeletons','minions','cannon','fireball','zap','freeze']
P = Image.new('RGBA', (312 * 5, 400 * 2), (60, 90, 160, 255))
for i, c in enumerate(cards):
    P.alpha_composite(Image.open(f'{root}card_{c}.png'), ((i % 5) * 312, (i // 5) * 400))
P.save(sys.argv[2])
