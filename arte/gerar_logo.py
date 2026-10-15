import sys, random
from PIL import Image

W = H = 128
img = Image.new("RGBA", (W, H))
px = img.load()

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)

def rect(x0, y0, x1, y1, c):
    for y in range(max(0,y0), min(H,y1)):
        for x in range(max(0,x0), min(W,x1)):
            px[x, y] = c

def blend(c, base, a):
    return tuple(int(c[i]*a + base[i]*(1-a)) for i in range(3)) + (255,)

# ---------------- céu do entardecer ----------------
top, mid, bot = hexc('#1b1440'), hexc('#5a2f6e'), hexc('#f08a4b')
for y in range(H):
    t = y / 92
    if t < 0.6:
        c = blend(mid, top, t / 0.6)
    else:
        c = blend(bot, mid, min(1, (t - 0.6) / 0.4))
    # gradiente em faixas (visual pixelado)
    for x in range(W):
        px[x, y] = c

random.seed(7)
for _ in range(38):
    x, y = random.randrange(W), random.randrange(48)
    px[x, y] = hexc('#ffffff') if random.random() < .4 else hexc('#c9b8ff')
# lua
rect(100, 10, 110, 20, hexc('#fff3c4')); rect(101, 9, 109, 21, hexc('#fff3c4')); rect(99, 11, 111, 19, hexc('#fff3c4'))
rect(103, 12, 106, 15, hexc('#e8d9a0')); rect(105, 16, 108, 18, hexc('#e8d9a0'))

# ---------------- silhueta da vila ----------------
far = hexc('#3b2350')
def house(x, w, h, roof):
    base = 84
    rect(x, base - h, x + w, base, far)
    for i in range(roof):
        rect(x - 1 + i, base - h - i - 1, x + w + 1 - i, base - h - i, far)
house(2, 14, 10, 7); house(20, 10, 14, 5); house(88, 12, 11, 6); house(104, 18, 9, 9)
rect(34, 56, 40, 84, far); rect(33, 54, 41, 56, far); rect(35, 51, 39, 54, far)  # torre do sino
rect(36, 58, 38, 61, hexc('#ffcf4a'))  # sino
# janelas acesas
for (x, y) in [(6, 78), (11, 78), (23, 74), (27, 74), (92, 78), (108, 79), (114, 79)]:
    rect(x, y, x + 2, y + 2, hexc('#ffcf4a'))

# chão (grama + terra)
rect(0, 84, W, 88, hexc('#4f9a3a')); rect(0, 86, W, 88, hexc('#3e7e2d'))
for x in range(0, W, 3):
    px[x, 84] = hexc('#6cc24a')
rect(0, 88, W, H, hexc('#6b4a2f'))
for _ in range(60):
    x, y = random.randrange(W), random.randrange(88, H)
    px[x, y] = hexc('#5a3d26')

# ---------------- desenho por mapa de caracteres ----------------
def draw(art, ox, oy, pal, s=1):
    for j, row in enumerate(art):
        for i, ch in enumerate(row):
            if ch in pal:
                rect(ox + i*s, oy + j*s, ox + (i+1)*s, oy + (j+1)*s, pal[ch])

# Aldeão (rosto + nariz + túnica)
villager = [
    "..dddddddd..",
    "..ssssssss..",
    "..SSSSSSSS..",
    "..SSSSSSSS..",
    "..HHHHHHHH..",
    "..SWGSSGWS..",
    "..SSSnnSSS..",
    "..SSSnnSSS..",
    "..SSSnnSSS..",
    "..SSSNNSSS..",
    "..SSSSSSSS..",
    "rrrrrrrrrrrr",
    "rRRRRbbRRRRr",
    "rRRRRbbRRRRr",
]
vpal = {
    'd': hexc('#7a5240'), 's': hexc('#a8775f'), 'S': hexc('#bd8b72'), 'H': hexc('#4a3026'),
    'W': hexc('#f4f4f4'), 'G': hexc('#2f8f3a'), 'n': hexc('#b0806a'), 'N': hexc('#8f604d'),
    'r': hexc('#5b3a8a'), 'R': hexc('#7a4fb3'), 'b': hexc('#ffcf4a'),
}
# Piglin
piglin = [
    "....pppppppp....",
    "..yPPPPPPPPPPP..",
    ".PPPPPPPPPPPPPPP",
    "PPP.PWKPPPPKWP.P",
    "P...PPPPPPPPPP..",
    "....PPOOOOOOPP..",
    "....PPOkOOkOPP..",
    "....PPOOOOOOPP..",
    "....PPPtPPtPPP..",
    "....PPPPPPPPPP..",
    "...gggggggggggg.",
    "...gGGGGGGGGGGg.",
    "...gGGGGGGGGGGg.",
]
ppal = {
    'p': hexc('#c98a86'), 'P': hexc('#e9a8a2'), 'W': hexc('#f4f4f4'), 'K': hexc('#2a1a1a'),
    'O': hexc('#f6c3be'), 'k': hexc('#7a3b3b'), 't': hexc('#fff2d6'), 'y': hexc('#ffcf4a'),
    'g': hexc('#c79a1e'), 'G': hexc('#ffcf4a'),
}

S = 3
vx, vy = 6, 42
draw(villager, vx, vy, vpal, S)
px_x, px_y = 72, 45
draw(piglin, px_x, px_y, ppal, 3)

# balão de fala (estilo BotW: preto translúcido com borda clara) entre os dois
bx0, by0, bx1, by1 = 46, 26, 82, 40
dark = hexc('#0b0b0f', 230)
rect(bx0 + 1, by0, bx1 - 1, by1, dark); rect(bx0, by0 + 1, bx1, by1 - 1, dark)
for x in range(bx0 + 1, bx1 - 1):
    px[x, by0] = hexc('#d8d8d8'); px[x, by1 - 1] = hexc('#d8d8d8')
for y in range(by0 + 1, by1 - 1):
    px[bx0, y] = hexc('#d8d8d8'); px[bx1 - 1, y] = hexc('#d8d8d8')
rect(52, by1, 56, by1 + 2, dark); rect(52, by1 + 2, 54, by1 + 4, dark)  # rabinho
for i, col in enumerate(['#ff5555', '#ffcf4a', '#55ff55']):
    rect(53 + i * 8, 31, 56 + i * 8, 34, hexc(col))

# marcador de missão "!" dourado acima do aldeão
mx, my = 20, 14
rect(mx, my, mx + 4, my + 12, hexc('#8a5a00')); rect(mx, my + 14, mx + 4, my + 18, hexc('#8a5a00'))
rect(mx + 1, my + 1, mx + 3, my + 11, hexc('#ffcf4a')); rect(mx + 1, my + 15, mx + 3, my + 17, hexc('#ffcf4a'))
px[mx + 1, my + 1] = hexc('#fff3b0'); px[mx + 1, my + 2] = hexc('#fff3b0')

# ---------------- título (fonte pixelada 5x7) ----------------
F = {
 'C': ["01111","10000","10000","10000","10000","10000","01111"],
 'R': ["11110","10001","10001","11110","10100","10010","10001"],
 'O': ["01110","10001","10001","10001","10001","10001","01110"],
 'N': ["10001","11001","10101","10011","10001","10001","10001"],
 'I': ["11111","00100","00100","00100","00100","00100","11111"],
 'A': ["01110","10001","10001","11111","10001","10001","10001"],
 'S': ["01111","10000","10000","01110","00001","00001","11110"],
 'D': ["11110","10001","10001","10001","10001","10001","11110"],
 'V': ["10001","10001","10001","10001","01010","01010","00100"],
 'L': ["10000","10000","10000","10000","10000","10000","11111"],
 ' ': ["00000"] * 7,
}
def text(s, cx, y, scale, fill, shadow, accents=()):
    w = len(s) * 6 * scale - scale
    x = cx - w // 2
    for idx, ch in enumerate(s):
        g = F[ch]
        for dy, (off, col) in enumerate([(scale, shadow), (0, fill)]):
            for j, row in enumerate(g):
                for i, b in enumerate(row):
                    if b == '1':
                        rect(x + i*scale + off, y + j*scale + off, x + (i+1)*scale + off, y + (j+1)*scale + off, col)
            if idx in accents:  # circunflexo do Ô
                for (ai, aj) in [(1, -2), (2, -3), (3, -2)]:
                    rect(x + ai*scale + off, y + aj*scale + off, x + (ai+1)*scale + off, y + (aj+1)*scale + off, col)
        x += 6 * scale

# caixa do título (estilo diálogo BotW)
tx0, ty0, tx1, ty1 = 6, 90, 122, 126
box = hexc('#0b0b0f', 225)
rect(tx0 + 1, ty0, tx1 - 1, ty1, box); rect(tx0, ty0 + 1, tx1, ty1 - 1, box)
for x in range(tx0 + 2, tx1 - 2):
    px[x, ty0] = hexc('#ffcf4a'); px[x, ty1 - 1] = hexc('#ffcf4a')
for y in range(ty0 + 2, ty1 - 2):
    px[tx0, y] = hexc('#ffcf4a'); px[tx1 - 1, y] = hexc('#ffcf4a')
gold, shade = hexc('#ffd24a'), hexc('#7a4a00')
text("CRONICAS", 64, 99, 2, gold, shade, accents=(2,))
text("DA VILA", 64, 116, 1, hexc('#ffffff'), hexc('#3a3a3a'))

out = sys.argv[1]
img.convert("RGB").resize((512, 512), Image.NEAREST).save(out + "/logo-curseforge-512.png")
img.convert("RGB").resize((400, 400), Image.NEAREST).save(out + "/logo-curseforge-400.png")
img.convert("RGB").resize((1024, 1024), Image.NEAREST).save(out + "/logo-curseforge-1024.png")
img.convert("RGB").save(out + "/icon-128.png")
