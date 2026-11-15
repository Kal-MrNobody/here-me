#!/usr/bin/env python3
"""Optional: download the hand-tracking model so the app works without internet.
Run once:  python download_offline.py   (files go into ./vendor, about 25 MB)"""
import os, urllib.request

VER = "0.10.14"
BASE = f"https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@{VER}"
FILES = {
    "vendor/vision_bundle.mjs": f"{BASE}/vision_bundle.mjs",
    "vendor/wasm/vision_wasm_internal.js": f"{BASE}/wasm/vision_wasm_internal.js",
    "vendor/wasm/vision_wasm_internal.wasm": f"{BASE}/wasm/vision_wasm_internal.wasm",
    "vendor/wasm/vision_wasm_nosimd_internal.js": f"{BASE}/wasm/vision_wasm_nosimd_internal.js",
    "vendor/wasm/vision_wasm_nosimd_internal.wasm": f"{BASE}/wasm/vision_wasm_nosimd_internal.wasm",
    "vendor/hand_landmarker.task": "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task",
}
root = os.path.dirname(os.path.abspath(__file__))
for rel, url in FILES.items():
    dest = os.path.join(root, rel)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print("Downloading", rel, "…")
    urllib.request.urlretrieve(url, dest)
print("Done. The app will now use these local files (fonts still need internet but are optional).")
