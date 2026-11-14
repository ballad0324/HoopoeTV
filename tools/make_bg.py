from PIL import Image
import os, shutil

res = r"D:\Projects\Box\app\src\main\res\drawable-nodpi"
shots = r"D:\Projects\Box\shots"
bak = os.path.join(shots, "bg-backup")
os.makedirs(bak, exist_ok=True)

pairs = [
    ("bg-new-dark.png", "bg_glass_dark.webp"),
    ("bg-new-light.png", "bg_glass_light.webp"),
]

for src_name, dst_name in pairs:
    src = os.path.join(shots, src_name)
    dst = os.path.join(res, dst_name)
    if os.path.exists(dst) and not os.path.exists(os.path.join(bak, dst_name)):
        shutil.copy2(dst, os.path.join(bak, dst_name))
    im = Image.open(src).convert("RGB")
    im = im.resize((1080, 1920), Image.LANCZOS)
    im.save(dst, "WEBP", quality=80, method=6)
    print(dst_name, os.path.getsize(dst))
