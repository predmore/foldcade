"""Afterglow's app objects: lit objects for common Android apps, the Thor's own apps, and the emulators
Foldcade launches, drawn with the same parts and light as the platforms (see objects.py). Each is drawn
from what the app does or from the one thing people know its icon by, never a copy of its logo.

An app ships once, as app_<name>, the lit object on the home grid and in the hero. Its manifest slots
are `app.<package>`, one per package that should wear it, so a pack can restyle one package or all of
them. See docs/iconography.md before adding one.

    apps_android.py    Android's own apps and Google's
    apps_popular.py    popular third-party apps
    apps_emulators.py  emulators and game front-ends
"""
import apps_android, apps_emulators, apps_popular

# name -> (drawing, accent, what it stands for, packages that wear it)
APPS = {}
for _group in (apps_android, apps_popular, apps_emulators):
    _clash = APPS.keys() & _group.APPS.keys()
    assert not _clash, f"two apps share a name: {_clash}"
    APPS.update(_group.APPS)
