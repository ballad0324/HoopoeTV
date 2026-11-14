from PIL import Image
im = Image.open(r"D:\Projects\Box\shots\r139_admin_nologin.png").convert("L")
w, h = im.size
y0, y1 = 700, 830
# 找该行卡片左右边框亮点（玻璃卡描边较亮）
for y in range(y0, y1, 6):
    row = [im.getpixel((x, y)) for x in range(w)]
    bright = [x for x, v in enumerate(row) if v > 120]
    if bright:
        print(y, bright[0], bright[-1], len(bright))
