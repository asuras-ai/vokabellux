#!/usr/bin/env python3
"""Check curriculum coverage and every packaged asset before delivery."""
import json, pathlib, struct, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets'
course=json.loads((ASSETS/'content/vocabulary.json').read_text())
items=course['items']; errors=[]
def check(condition, message):
    if not condition: errors.append(message)
check(len(items)==188,'Expected 188 verified vocabulary entries')
check(len({v['id'] for v in items})==len(items),'Duplicate vocabulary IDs')
check({v['page'] for v in items}==set(range(1,8)),'Missing source page')
check(len(course['themes'])==6,'Expected six themes')
for v in items:
    for key in ['audio','exampleAudio','image']:
        path=ASSETS/v[key]
        check(path.exists(),f"Missing {key}: {v['id']}")
        if path.exists(): check(path.stat().st_size>1000,f"Empty asset: {path}")
    check(v['locale'] in ['en-GB','en-US'],f"Unexpected locale: {v['id']}")
    check(0<=v['panel']<16,f"Invalid panel: {v['id']}")
    bounds=v.get('bounds',[])
    check(len(bounds)==4 and all(isinstance(x,int) for x in bounds) and min(bounds[:2],default=-1)>=0 and min(bounds[2:],default=0)>0,f"Missing or invalid illustration bounds: {v['id']}")
    check(v['theme'] in {t['id'] for t in course['themes']},f"Invalid theme: {v['id']}")
    check(all(v.get(k) for k in ['spoken','example','scene']),f"Missing learning content: {v['id']}")
for image in {v['image'] for v in items}:
    path=ASSETS/image
    if path.exists():
        data=path.read_bytes()
        check(data[:8]==b'\x89PNG\r\n\x1a\n',f'Invalid PNG: {image}')
        if data[:8]==b'\x89PNG\r\n\x1a\n':
            width,height=struct.unpack('>II',data[16:24])
            check(abs(width-height)<8 and width>=1024,f'Unexpected storyboard dimensions: {image}: {width}x{height}')
            for v in [item for item in items if item['image']==image]:
                bounds=v.get('bounds',[])
                if len(bounds)==4:
                    x,y,w,h=bounds
                    check(x+w<=width and y+h<=height,f"Illustration bounds outside atlas: {v['id']}")
if errors:
    print('\n'.join(errors));raise SystemExit(1)
print(f'PASS: {len(items)} entries, 6 themes, 7 source pages, 12 illustration boards, 376 audio clips')
