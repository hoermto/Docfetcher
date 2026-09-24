import sys, gi
gi.require_version('Gdk', '3.0')
from gi.repository import Gdk
out = sys.argv[1]
root = Gdk.get_default_root_window()
if len(sys.argv) > 2:
    x, y, w, h = map(int, sys.argv[2:6])
else:
    x, y, w, h = 0, 0, root.get_width(), root.get_height()
pb = Gdk.pixbuf_get_from_window(root, x, y, w, h)
pb.savev(out, "png", [], [])
print(out, w, h)
