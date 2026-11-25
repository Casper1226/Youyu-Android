"""Create and reopen a synthetic vault in the minified APK on the CI emulator."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", "shell", *args], timeout=15).decode()


def wait_for(label, predicate, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            adb("uiautomator", "dump", "/data/local/tmp/youyu-release-check.xml")
            root = ET.fromstring(adb("cat", "/data/local/tmp/youyu-release-check.xml"))
            nodes = [node for node in root.iter("node") if predicate(node)]
            if nodes:
                return nodes
        except (ET.ParseError, subprocess.SubprocessError):
            pass
        time.sleep(.4)
    raise RuntimeError("Could not find " + label)


def element(label):
    return wait_for(label, lambda n: n.get("text") == label or n.get("content-desc") == label)[0]


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
    adb("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def enter_passphrase(index):
    fields = wait_for("passphrase field", lambda n: n.get("class") == "android.widget.EditText")
    tap(fields[index])
    adb("input", "text", "synthetic-release-vault")
    # On a hardware-keyboard emulator there may be no soft keyboard to dismiss.
    if "mInputShown=true" in adb("dumpsys", "input_method"):
        adb("input", "keyevent", "BACK")


if __name__ == "__main__":
    if adb("getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("An isolated emulator is required")
    enter_passphrase(0)
    enter_passphrase(1)
    tap(element("创建加密金库"))
    tap(element("锁定金库"))
    enter_passphrase(0)
    tap(element("解锁"))
    element("锁定金库")
    print("Release APK created, locked and reopened a synthetic encrypted vault.")
