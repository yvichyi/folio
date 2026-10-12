#!/usr/bin/env bash
set -euo pipefail
mkdir -p native-witness
gradle -p android :app:assembleDebug --no-daemon
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.folio.dailyenglish.nativepreview/com.folio.dailyenglish.MainActivity
sleep 10
adb shell pidof com.folio.dailyenglish.nativepreview
python3 - <<'PY'
import re,subprocess,time,xml.etree.ElementTree as ET
for attempt in range(4):
    subprocess.run(['adb','shell','uiautomator','dump','/sdcard/dialog-check.xml'],
                   stdout=subprocess.DEVNULL,check=True)
    subprocess.run(['adb','pull','/sdcard/dialog-check.xml','native-witness/dialog-check.xml'],
                   stdout=subprocess.DEVNULL,check=True)
    root=ET.parse('native-witness/dialog-check.xml').getroot()
    names=[node.attrib.get('text','') for node in root.iter('node')]
    if not any("Pixel Launcher isn't responding" in name for name in names):
        break
    button=next((node for node in root.iter('node') if node.attrib.get('text')=='Close app'),None)
    if button is None: raise SystemExit('Unhandled system dialog')
    points=[int(s) for s in re.findall(r'\d+',button.attrib.get('bounds',''))]
    subprocess.run(['adb','shell','input','tap',str((points[0]+points[2])//2),
                    str((points[1]+points[3])//2)],check=True)
    time.sleep(3)
else:
    raise SystemExit('Android launcher remains unresponsive')
PY
adb shell screencap -p /sdcard/folio-home.png
adb pull /sdcard/folio-home.png native-witness/home.png
adb shell uiautomator dump /sdcard/folio-ui.xml
adb pull /sdcard/folio-ui.xml native-witness/home-ui.xml
python3 - <<'PY'
import re,subprocess,xml.etree.ElementTree as ET
root=ET.parse('native-witness/home-ui.xml').getroot()
target=next((node for node in root.iter('node') if '生词选项卡' in node.attrib.get('content-desc','')),None)
if target is None:
    raise SystemExit('Native vocabulary tab not found')
bounds=target.attrib.get('bounds','')
points=list(map(int,re.findall(r'\d+',bounds)))
if len(points)!=4: raise SystemExit('Unexpected bounds: '+bounds)
x=(points[0]+points[2])//2
y=(points[1]+points[3])//2
subprocess.run(['adb','shell','input','tap',str(x),str(y)],check=True)
PY
sleep 2
adb shell screencap -p /sdcard/folio-words.png
adb pull /sdcard/folio-words.png native-witness/words.png
adb shell uiautomator dump /sdcard/folio-words-ui.xml
adb pull /sdcard/folio-words-ui.xml native-witness/words-ui.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse('native-witness/words-ui.xml').getroot()
texts=[x.attrib.get('text','') for x in root.iter('node')]
if not any('你的生词本' in t for t in texts):
    raise SystemExit('Vocabulary navigation failed: '+repr(texts[:40]))
print('Native startup and wordbook navigation smoke PASS')
PY

# Verify SQLite durability against a real Android database, not just Java mocks.
gradle -p android :app:connectedDebugAndroidTest --no-daemon
