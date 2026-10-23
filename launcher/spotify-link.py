#!/usr/bin/env python3
"""Link Glass Home's Now Playing panel to a Spotify account (spotify_connect), and check it from the Mac first.

    spotify-link.py login --client-id <id>   sign in once in the browser; the link is kept in ~/.cleanyourtv/spotify.json
    spotify-link.py check                    what the account plays now, as the TV's panel would show it
    TV_HOST=<tv ip> spotify-link.py push     sign in again and send that link to the TV over ADB (ADB must be on)
    TV_HOST=<tv ip> spotify-link.py unlink   remove the link from the TV

The client ID comes from an app you create at developer.spotify.com (Spotify requires its owner to have Premium), with
the redirect URI http://127.0.0.1:8888/callback. Sign-in is Authorization Code with PKCE: there is no client secret to
keep. The TV gets its own refresh token from `push`, so the Mac's and the TV's never replace each other.
"""
import base64
import hashlib
import http.server
import json
import os
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path

REDIRECT = "http://127.0.0.1:8888/callback"
SCOPES = "user-read-playback-state user-modify-playback-state user-read-currently-playing"
LINK = Path.home() / ".cleanyourtv" / "spotify.json"
TOOLS = Path(__file__).resolve().parent.parent / "tools"


def fail(msg):
    sys.exit("spotify-link: " + msg)


def load():
    try:
        return json.loads(LINK.read_text())
    except (OSError, ValueError):
        return {}


def save(data):
    LINK.parent.mkdir(mode=0o700, exist_ok=True)
    LINK.write_text(json.dumps(data, indent=2))
    os.chmod(LINK, 0o600)   # a refresh token is a credential


def post_form(url, form):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(form).encode(),
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        fail(f"Spotify refused the sign-in ({e.code}): {e.read().decode(errors='replace')[:300]}")


def authorize(client_id):
    """Browser sign-in with PKCE; returns the token response (access_token, refresh_token)."""
    verifier = secrets.token_urlsafe(64)[:96]
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    state = secrets.token_urlsafe(16)
    url = "https://accounts.spotify.com/authorize?" + urllib.parse.urlencode({
        "client_id": client_id, "response_type": "code", "redirect_uri": REDIRECT, "scope": SCOPES,
        "code_challenge_method": "S256", "code_challenge": challenge, "state": state})
    got = {}

    class Callback(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            q = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if urllib.parse.urlparse(self.path).path != "/callback":
                self.send_response(404)
                self.end_headers()
                return
            got.update({k: v[0] for k, v in q.items()})
            ok = got.get("state") == state and "code" in got
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(("Signed in. You can close this tab." if ok else "Sign-in failed; see the terminal.").encode())

        def log_message(self, *args):
            pass

    server = http.server.HTTPServer(("127.0.0.1", 8888), Callback)
    server.timeout = 300
    print("Opening Spotify's sign-in page. If it does not open, visit:\n  " + url)
    webbrowser.open(url)
    # One request at a time until the callback arrives (the browser may ask for a favicon first).
    while not got:
        server.handle_request()
    server.server_close()
    if got.get("state") != state:
        fail("the sign-in answer did not match this request (state); run it again")
    if "error" in got or "code" not in got:
        fail("Spotify did not sign in: " + got.get("error", "no code"))
    return post_form("https://accounts.spotify.com/api/token", {
        "grant_type": "authorization_code", "code": got["code"], "redirect_uri": REDIRECT,
        "client_id": client_id, "code_verifier": verifier})


def access_token(link):
    t = post_form("https://accounts.spotify.com/api/token", {
        "grant_type": "refresh_token", "refresh_token": link["refresh_token"], "client_id": link["client_id"]})
    if t.get("refresh_token") and t["refresh_token"] != link["refresh_token"]:
        link["refresh_token"] = t["refresh_token"]
        save(link)
    return t["access_token"]


def describe(player):
    """The panel's view of /v1/me/player: the same fields SpotifyPlayback.parse takes."""
    item = player.get("item") or {}
    if not item.get("name"):
        return "Nothing to show (an ad, a private session, or no item)."
    if item.get("artists") is not None:
        artist = ", ".join(a.get("name", "") for a in item["artists"] if a.get("name"))
        images = (item.get("album") or {}).get("images") or []
    else:
        show = item.get("show") or {}
        artist = show.get("name", "")
        images = item.get("images") or show.get("images") or []
    device = (player.get("device") or {}).get("name", "")
    sized = [i for i in images if (i.get("width") or 0) >= 300]
    cover = min(sized, key=lambda i: i["width"])["url"] if sized else (
        max(images, key=lambda i: i.get("width") or 0)["url"] if images else "(none)")
    line = " · ".join(x for x in (artist, device) if x)
    return (f"{'Playing' if player.get('is_playing') else 'Paused'}\n  {item['name']}\n  {line}\n"
            f"  cover: {cover}")


def check():
    link = load()
    if not link.get("refresh_token"):
        fail("not signed in yet: run  spotify-link.py login --client-id <id>")
    req = urllib.request.Request("https://api.spotify.com/v1/me/player?additional_types=episode",
                                 headers={"Authorization": "Bearer " + access_token(link)})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            body = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        fail(f"Spotify answered {e.code}: {e.read().decode(errors='replace')[:300]}")
    if status == 204 or not body:
        print("Nothing is playing on the account right now. Start a song on any device and run check again.")
        return
    print(describe(json.loads(body)))


def tv(command):
    py = TOOLS / "venv" / "bin" / "python"
    if not os.environ.get("TV_HOST"):
        fail("set TV_HOST to the TV's IP address (ADB must be on)")
    return subprocess.run([str(py), "-I", str(TOOLS / "tv.py"), command], capture_output=True, text=True)


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return
    cmd = argv[0]
    if cmd == "login":
        if len(argv) < 3 or argv[1] != "--client-id":
            fail("usage: spotify-link.py login --client-id <id>")
        t = authorize(argv[2])
        save({"client_id": argv[2], "refresh_token": t["refresh_token"]})
        print(f"Signed in. Saved to {LINK}. Now try:  spotify-link.py check")
    elif cmd == "check":
        check()
    elif cmd == "push":
        link = load()
        if not link.get("client_id"):
            fail("run login first, so the client ID is known")
        print("Signing in once more for the TV's own link.")
        t = authorize(link["client_id"])
        for value in (link["client_id"], t["refresh_token"]):
            # Only token characters go into the shell command on the TV.
            if not all(c.isalnum() or c in "-_.~" for c in value):
                fail("unexpected characters in the client ID or token; not sending it")
        r = tv("am broadcast -a com.gathin.tvhome.action.SPOTIFY_LINK -n com.gathin.tvhome/.SpotifyLink"
               f" --es client_id {link['client_id']} --es refresh_token {t['refresh_token']}")
        out = (r.stdout + r.stderr).strip()
        if 'data="linked"' not in out:
            fail("the TV did not take the link:\n" + out[-600:])
        print("Linked. The Now Playing panel picks it up the next time Home comes to the front.")
    elif cmd == "unlink":
        r = tv("am broadcast -a com.gathin.tvhome.action.SPOTIFY_UNLINK -n com.gathin.tvhome/.SpotifyLink")
        print((r.stdout + r.stderr).strip()[-300:])
    else:
        fail(f"unknown command {cmd!r}; see --help")


if __name__ == "__main__":
    main(sys.argv[1:])
