# usage: xin.py move X Y | click [button] | dclick | key KEYSYM... | type TEXT | rclick
import sys, time
from Xlib import X, display, XK
from Xlib.ext import xtest
d = display.Display()
def move(x, y):
    d.screen().root.warp_pointer(x, y); d.sync()
def click(btn=1, n=1):
    for _ in range(n):
        xtest.fake_input(d, X.ButtonPress, btn); d.sync(); time.sleep(0.05)
        xtest.fake_input(d, X.ButtonRelease, btn); d.sync(); time.sleep(0.08)
def key(name):
    ks = XK.string_to_keysym(name)
    if ks == 0:
        raise SystemExit('unknown keysym ' + name)
    kc = d.keysym_to_keycode(ks)
    # Determine which modifier level produces this keysym on the current layout
    level = None
    for idx in range(4):
        if d.keycode_to_keysym(kc, idx) == ks:
            level = idx; break
    mods = []
    if level in (1, 3):
        mods.append(d.keysym_to_keycode(XK.string_to_keysym('Shift_L')))
    if level in (2, 3):
        mods.append(d.keysym_to_keycode(XK.string_to_keysym('ISO_Level3_Shift')))
    for m in mods:
        xtest.fake_input(d, X.KeyPress, m); d.sync(); time.sleep(0.02)
    xtest.fake_input(d, X.KeyPress, kc); d.sync(); time.sleep(0.03)
    xtest.fake_input(d, X.KeyRelease, kc); d.sync(); time.sleep(0.03)
    for m in reversed(mods):
        xtest.fake_input(d, X.KeyRelease, m); d.sync(); time.sleep(0.02)
NAMES = {' ': 'space', '/': 'slash', '.': 'period', '-': 'minus', '_': 'underscore',
         ':': 'colon', '*': 'asterisk', '\\': 'backslash', '@': 'at', '~': 'asciitilde'}
def type_text(s):
    for ch in s:
        key(NAMES.get(ch, ch))
args = sys.argv[1:]
i = 0
while i < len(args):
    c = args[i]
    if c == 'move': move(int(args[i+1]), int(args[i+2])); i += 3
    elif c == 'click': click(1); i += 1
    elif c == 'drag' or c == 'dragslow':
        # dragslow moves in many small steps, which GTK needs for drag and
        # drop between two widgets
        steps, pause = (10, 0.03) if c == 'drag' else (40, 0.05)
        x1, y1, x2, y2 = (int(args[i+1]), int(args[i+2]), int(args[i+3]), int(args[i+4]))
        move(x1, y1); time.sleep(0.2)
        xtest.fake_input(d, X.ButtonPress, 1); d.sync(); time.sleep(0.3)
        for s in range(1, steps + 1):
            move(x1 + (x2 - x1) * s // steps, y1 + (y2 - y1) * s // steps); time.sleep(pause)
        time.sleep(0.4)
        xtest.fake_input(d, X.ButtonRelease, 1); d.sync(); time.sleep(0.5); i += 5
    elif c == 'rclick': click(3); i += 1
    elif c == 'dclick': click(1, 2); i += 1
    elif c == 'key': key(args[i+1]); i += 2
    elif c == 'type': type_text(args[i+1]); i += 2
    elif c == 'sleep': time.sleep(float(args[i+1])); i += 2
    else: raise SystemExit('bad arg ' + c)
