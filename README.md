# Haath Se (हाथ से) — learn sign language on your laptop

An app that teaches you sign language so you can talk with Deaf people. It uses your webcam to check your signs.

- **Two sign languages:** **ISL** (Indian Sign Language, the language of the Deaf community in India, shown with Hindi names) and **ASL** (American Sign Language, English).
- **App language:** English or हिंदी. Switch it in the top-right corner.
- **Learn mode:** every sign has an animated hand picture and step-by-step instructions in English and Hindi. Your webcam shows a live **match %** and tells you what to fix, for example "Thumb position — move your thumb as shown". Hold the sign correctly and it moves on to the next one.
- **Practice mode (speed test):** works like a typing-speed test. Letters, words or word-signs scroll past, and you sign each one as fast as you can while the camera checks you. At the end you get **WPM**, **signs per minute**, **accuracy**, your personal best and your slowest signs. A button then drills only your weak signs.
- **Progress:** your WPM over all sessions, and your average time for each letter.

## What's included

| | ISL (Indian) | ASL (American) |
|---|---|---|
| Alphabet | A–Z, two-handed ISL fingerspelling | A–Z, one-handed (J and Z use movement) |
| Everyday words | Namaste, thank you, yes, no, good, bad, water, food, home, friend, help, I, you, deaf, love, OK, wait | Hello, thank you, please, sorry, yes, no, I love you, good, help, name, water, eat, more, friend, stop, deaf, I, you, fine |
| Practice words | English words plus Hindi words in Roman letters (namaste, chai, pani, dost…) | English words |

> ISL has regional variations. These lessons follow common ISL charts. Check signs with Deaf signers or the
> [ISLRTC dictionary](https://islrtc.nic.in) for local versions.

## Run it

You need **Python 3** (already on most Macs and Linux; on Windows get it from python.org) and **Chrome or Edge**.

- **Windows:** double-click `start.bat`
- **Mac / Linux:** run `./start.sh` (or `python3 start.py`)

The app opens in its own window. Allow camera access when the browser asks. Keep the black terminal window open while you use the app.

The first time, the app downloads the hand-tracking model (about 8 MB) from the internet. To use the app fully offline afterwards, run this once:

```
python download_offline.py
```

You can also install it like a normal app: in Chrome or Edge, use the *Install* icon in the address bar.

## Tips for good recognition

- Sit about an arm's length from the camera, with light on your hands (not behind you).
- Keep your hands inside the camera picture. Hold each sign still for a moment.
- The pictures are drawn **like a mirror**, so copy them as if you were looking in a mirror.
- If a sign isn't recognised, hold it and press **"Record my version"** in that lesson. Your own recording will then also count as correct.
- In **Settings** you can change the strictness and how long to hold a sign, choose left-handed mode, or swap the left/right detection.

## How it works

- Hand tracking uses Google's MediaPipe Hand Landmarker. It finds 21 points on each hand and runs entirely in your browser. No video ever leaves your laptop.
- Each sign is defined once as a 3-D hand pose (`js/signs.js`). The same definition draws the lesson picture (`js/handmodel.js`) and gives the recognizer its reference shape (`js/recognizer.js`).
- The recognizer compares several features against that reference: finger bend, thumb position, finger spread, hand angle, contact between the two hands, and movement for signs like J, Z and HELLO.
- Progress is saved in your browser's local storage.

Run the consistency test with `node tests/selftest.mjs`. It checks that every sign's ideal pose matches itself and lists the signs that look alike.
