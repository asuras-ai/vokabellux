#!/usr/bin/env python3
"""Build the reviewed English-only vocabulary bundle from its editable source."""
import json, re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]
items, themes = [], []
for line in (ROOT/'scripts/vocabulary.tsv').read_text().splitlines():
    if not line or line.startswith('#'): continue
    if line.startswith('@'):
        title, headline, description = line[1:].split('|')
        theme = f'theme_{len(themes)+1}'
        themes.append(dict(id=theme,title=title,headline=headline,description=description))
        continue
    page, term, example, scene = line.split('|')
    spoken = re.sub(r'^to ', '', re.sub(r' \(.*?\)', '', term))
    ident = re.sub('[^a-z0-9]+', '_', term.lower()).strip('_')
    # Keep the two different senses of "as" distinct.
    index = len(items)
    items.append(dict(id=ident, term=term, spoken=spoken, example=example, scene=scene,
        page=int(page), theme=theme, locale='en-US' if term in ['color','neighbor','center','mom','movie'] else 'en-GB',
        image=f'images/board_{index//16+1:02}.png', panel=index%16,
        audio=f'audio/{ident}.m4a', exampleAudio=f'audio/{ident}_example.m4a',
        forms={'device':['devices'], 'to click':['click on'], 'to set up':['set up'], 'to break':['broke'], 'to steal':['stole'], 'child':['children'], 'capital city':['capital'], 'key word':['keyword'], 'neighbor':['neighbour'], 'color':['colour'], 'center':['centre'], 'movie':['film'], 'mom':['mum']}.get(term, [])))
layout_path = ROOT/'scripts/illustration-layout.json'
if layout_path.exists():
    layouts = json.loads(layout_path.read_text())
    for item in items:
        item['bounds'] = layouts[pathlib.Path(item['image']).name]['panels'][item['panel']]
bundle = dict(version=1,source='Zoe Vokabeln 2610.pdf',sourcePages=7,themes=themes,items=items)
path=ROOT/'app/src/main/assets/content/vocabulary.json'
path.write_text(json.dumps(bundle,ensure_ascii=False,indent=2)+'\n')
print(f'{len(items)} entries, {len(themes)} themes, {(len(items)+15)//16} illustration boards')
