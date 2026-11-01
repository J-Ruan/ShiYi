#!/usr/bin/env python3
"""Expose synthetic test failure summaries through normal GitHub check annotations."""
from pathlib import Path
import xml.etree.ElementTree as ET

def escaped(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')

for report in Path('app/build').rglob('TEST-*.xml'):
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            label = case.get('classname', '') + '#' + case.get('name', '')
            detail = (failure.text or failure.get('message', ''))[:3500]
            print('::error::' + escaped(label + '\n' + detail))
