"""Drives the development build on Windows without touching the user's mouse or keyboard.

The windows of the program are captured with PrintWindow (works while covered by other
windows), clicks and keys are posted as window messages to the control under the point
or with the focus. Coordinates are relative to the top-left corner of the window, the
same as in the screenshots.

Usage: python dev/guitest/win.py <command> ...
  list                           visible top-level windows of java.exe/javaw.exe
  shot OUT.png [TITLE]           screenshot of the window whose title contains TITLE
                                 (default: the main window 'DocFetcher')
  click X Y [TITLE]              left click; also: dclick (double) and rclick (right)
  key NAME [TITLE]               e.g. RETURN, ESCAPE, TAB, F2, DELETE, DOWN, or a letter;
                                 ckey NAME: with Ctrl
  type TEXT [TITLE]              types TEXT into the focused control
  close TITLE                    sends WM_CLOSE to the window
"""
import ctypes
import sys
from ctypes import wintypes

from PIL import Image

user32 = ctypes.WinDLL("user32", use_last_error=True)
gdi32 = ctypes.WinDLL("gdi32")
kernel32 = ctypes.WinDLL("kernel32")
try:
    ctypes.WinDLL("shcore").SetProcessDpiAwareness(2)  # Physical pixels
except OSError:
    pass

WM_CLOSE, WM_KEYDOWN, WM_KEYUP, WM_CHAR = 0x0010, 0x0100, 0x0101, 0x0102
WM_MOUSEMOVE, WM_LBUTTONDOWN, WM_LBUTTONUP, WM_LBUTTONDBLCLK = 0x0200, 0x0201, 0x0202, 0x0203
WM_RBUTTONDOWN, WM_RBUTTONUP = 0x0204, 0x0205
MK_LBUTTON, MK_RBUTTON = 0x0001, 0x0002
CWP_SKIPINVISIBLE = 0x0001
VK = {"RETURN": 0x0D, "ESCAPE": 0x1B, "TAB": 0x09, "SPACE": 0x20, "DELETE": 0x2E,
      "LEFT": 0x25, "UP": 0x26, "RIGHT": 0x27, "DOWN": 0x28, "HOME": 0x24, "END": 0x23,
      "BACK": 0x08, "CONTROL": 0x11, "MENU": 0x12}
for i in range(1, 13):
    VK["F%d" % i] = 0x6F + i

EnumProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)


class GUITHREADINFO(ctypes.Structure):
    _fields_ = [("cbSize", wintypes.DWORD), ("flags", wintypes.DWORD),
                ("hwndActive", wintypes.HWND), ("hwndFocus", wintypes.HWND),
                ("hwndCapture", wintypes.HWND), ("hwndMenuOwner", wintypes.HWND),
                ("hwndMoveSize", wintypes.HWND), ("hwndCaret", wintypes.HWND),
                ("rcCaret", wintypes.RECT)]


def process_name(pid):
    handle = kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not handle:
        return ""
    buf = ctypes.create_unicode_buffer(1024)
    size = wintypes.DWORD(1024)
    kernel32.QueryFullProcessImageNameW(handle, 0, buf, ctypes.byref(size))
    kernel32.CloseHandle(handle)
    return buf.value.lower()


def java_windows():
    found = []

    def callback(hwnd, _):
        if not user32.IsWindowVisible(hwnd):
            return True
        pid = wintypes.DWORD()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        name = process_name(pid.value)
        if name.endswith("\\java.exe") or name.endswith("\\javaw.exe"):
            length = user32.GetWindowTextLengthW(hwnd)
            buf = ctypes.create_unicode_buffer(length + 1)
            user32.GetWindowTextW(hwnd, buf, length + 1)
            if buf.value:
                found.append((hwnd, buf.value))
        return True

    user32.EnumWindows(EnumProc(callback), 0)
    return found


def find_window(title=None):
    windows = java_windows()
    for hwnd, text in windows:
        if title is None and "DocFetcher" in text and "-" not in text:
            return hwnd
        if title is not None and title.lower() in text.lower():
            return hwnd
    sys.exit("window not found: %s (have: %s)" % (title, [t for _, t in windows]))


def window_rect(hwnd):
    rect = wintypes.RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(rect))
    return rect


def shot(out, title=None):
    hwnd = find_window(title)
    rect = window_rect(hwnd)
    width, height = rect.right - rect.left, rect.bottom - rect.top
    hdc = user32.GetWindowDC(hwnd)
    mem = gdi32.CreateCompatibleDC(hdc)
    bmp = gdi32.CreateCompatibleBitmap(hdc, width, height)
    gdi32.SelectObject(mem, bmp)
    user32.PrintWindow(hwnd, mem, 2)  # PW_RENDERFULLCONTENT
    header = (ctypes.c_uint32 * 10)(40, width, (-height) & 0xFFFFFFFF, 1 | (32 << 16), 0, 0, 0, 0, 0, 0)
    data = ctypes.create_string_buffer(width * height * 4)
    gdi32.GetDIBits(mem, bmp, 0, height, data, header, 0)
    gdi32.DeleteObject(bmp)
    gdi32.DeleteDC(mem)
    user32.ReleaseDC(hwnd, hdc)
    Image.frombuffer("RGBA", (width, height), data, "raw", "BGRA", 0, 1).convert("RGB").save(out)
    print("%s: %dx%d" % (out, width, height))


def child_at(hwnd, x, y):
    """Returns the deepest visible child under the window point and the client point in it."""
    rect = window_rect(hwnd)
    screen = wintypes.POINT(rect.left + x, rect.top + y)
    target = hwnd
    while True:
        pt = wintypes.POINT(screen.x, screen.y)
        user32.ScreenToClient(target, ctypes.byref(pt))
        child = user32.ChildWindowFromPointEx(target, pt, CWP_SKIPINVISIBLE)
        if not child or child == target:
            return target, pt
        target = child


def lparam(pt):
    return (pt.y & 0xFFFF) << 16 | (pt.x & 0xFFFF)


def click(x, y, title=None, kind="click"):
    target, pt = child_at(find_window(title), x, y)
    lp = lparam(pt)
    user32.PostMessageW(target, WM_MOUSEMOVE, 0, lp)
    if kind == "rclick":
        user32.PostMessageW(target, WM_RBUTTONDOWN, MK_RBUTTON, lp)
        user32.PostMessageW(target, WM_RBUTTONUP, 0, lp)
        return
    user32.PostMessageW(target, WM_LBUTTONDOWN, MK_LBUTTON, lp)
    user32.PostMessageW(target, WM_LBUTTONUP, 0, lp)
    if kind == "dclick":
        user32.PostMessageW(target, WM_LBUTTONDBLCLK, MK_LBUTTON, lp)
        user32.PostMessageW(target, WM_LBUTTONUP, 0, lp)


def focus_of(hwnd):
    thread = user32.GetWindowThreadProcessId(hwnd, None)
    info = GUITHREADINFO(cbSize=ctypes.sizeof(GUITHREADINFO))
    user32.GetGUIThreadInfo(thread, ctypes.byref(info))
    return info.hwndFocus or hwnd


def key(name, title=None, ctrl=False):
    target = focus_of(find_window(title))
    code = VK.get(name.upper(), ord(name.upper()[0]))
    if ctrl:
        user32.PostMessageW(target, WM_KEYDOWN, VK["CONTROL"], 1)
    user32.PostMessageW(target, WM_KEYDOWN, code, 1)
    user32.PostMessageW(target, WM_KEYUP, code, 0xC0000001)
    if ctrl:
        user32.PostMessageW(target, WM_KEYUP, VK["CONTROL"], 0xC0000001)


def type_text(text, title=None):
    target = focus_of(find_window(title))
    for ch in text:
        user32.PostMessageW(target, WM_CHAR, ord(ch), 1)


def main(args):
    cmd = args[0]
    if cmd == "list":
        for hwnd, text in java_windows():
            r = window_rect(hwnd)
            print("%s  (%d,%d %dx%d)" % (text, r.left, r.top, r.right - r.left, r.bottom - r.top))
    elif cmd == "shot":
        shot(args[1], args[2] if len(args) > 2 else None)
    elif cmd in ("click", "dclick", "rclick"):
        click(int(args[1]), int(args[2]), args[3] if len(args) > 3 else None, cmd)
    elif cmd in ("key", "ckey"):
        key(args[1], args[2] if len(args) > 2 else None, cmd == "ckey")
    elif cmd == "type":
        type_text(args[1], args[2] if len(args) > 2 else None)
    elif cmd == "close":
        user32.PostMessageW(find_window(args[1]), WM_CLOSE, 0, 0)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:] or ["help"])
