#!/usr/bin/env python3
"""Generate packaged offline speech using macOS English voices and AAC encoding."""
import concurrent.futures, json, pathlib, subprocess, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
items=json.loads((ROOT/'app/src/main/assets/content/vocabulary.json').read_text())['items']
jobs=[(item,key,text) for item in items for key,text in [('audio',item['spoken']),('exampleAudio',item['example'])]]
def generate(job):
    item,key,text=job
    out=ROOT/'app/src/main/assets'/item[key]
    if out.exists() and out.stat().st_size>1000: return
    voice='Samantha' if item['locale']=='en-US' else 'Daniel'
    with tempfile.NamedTemporaryFile(suffix='.aiff') as tmp:
        subprocess.run(['say','-v',voice,'-r','155','-o',tmp.name,text],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
        subprocess.run(['ffmpeg','-y','-loglevel','error','-i',tmp.name,'-ac','1','-ar','24000','-c:a','aac','-b:a','48k',str(out)],check=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    for i,_ in enumerate(pool.map(generate,jobs),1):
        if i%40==0: print(f'{i}/{len(jobs)} audio clips',flush=True)
print(f'Complete: {len(jobs)} audio clips')
