#!/usr/bin/env python3
"""Start Haath Se on your laptop.

Runs a tiny local web server (the camera only works on http://localhost or https)
and opens the app in Chrome/Edge "app mode" if available, otherwise your default browser.
Usage:  python start.py   (or double-click start.bat on Windows / ./start.sh on Mac & Linux)
"""
import http.server, os, shutil, socketserver, subprocess, sys, threading, webbrowser

PORT = int(os.environ.get("PORT", "8765"))
ROOT = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map,
                      ".js": "text/javascript", ".mjs": "text/javascript", ".wasm": "application/wasm",
                      ".webmanifest": "application/manifest+json", ".task": "application/octet-stream"}

    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def log_message(self, *args):
        pass


def find_chrome():
    candidates = [
        r"C:\Program Files\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
        r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    for name in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "microsoft-edge"):
        p = shutil.which(name)
        if p:
            return p
    return None


def main():
    socketserver.TCPServer.allow_reuse_address = True
    try:
        httpd = socketserver.ThreadingTCPServer(("127.0.0.1", PORT), Handler)
    except OSError:
        print(f"Port {PORT} is busy - the app may already be running. Opening it…")
        webbrowser.open(f"http://localhost:{PORT}/")
        return
    url = f"http://localhost:{PORT}/"
    print(f"Haath Se is running at {url}\nKeep this window open. Press Ctrl+C to quit.")
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    chrome = find_chrome()
    if chrome and "--browser" not in sys.argv:
        subprocess.Popen([chrome, f"--app={url}", "--window-size=1280,860"])
    else:
        webbrowser.open(url)
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        print("\nBye!")
        httpd.shutdown()


if __name__ == "__main__":
    main()
