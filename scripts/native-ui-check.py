#!/usr/bin/env python3
"""Checks a real Android UIAutomator dump collected by the emulator workflow."""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

def main():
    if len(sys.argv)!=3:
        raise SystemExit("usage: native-ui-check.py navigate|verify <dump.xml>")
    action, filename=sys.argv[1:]
    root=ET.parse(filename).getroot()
    nodes=list(root.iter("node"))
    if action=="navigate":
        targets=[n for n in nodes if "生词选项卡" in n.attrib.get("content-desc","")]
        if not targets: raise SystemExit("Native wordbook tab missing from accessibility hierarchy")
        numbers=[int(i) for i in re.findall(r"\d+",targets[0].attrib.get("bounds",""))]
        if len(numbers)!=4:raise SystemExit("Malformed wordbook tab bounds")
        x=(numbers[0]+numbers[2])//2
        y=(numbers[1]+numbers[3])//2
        subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
        print("Clicked native wordbook tab at",x,y)
    elif action=="verify":
        texts=[n.attrib.get("text","") for n in nodes]
        if not any("你的生词本" in t for t in texts):
            raise SystemExit("Native wordbook navigation failed; found: "+repr(texts[:30]))
        print("Native wordbook accessibility evidence PASS")
    else:raise SystemExit("unsupported check")
if __name__=="__main__":
    main()
