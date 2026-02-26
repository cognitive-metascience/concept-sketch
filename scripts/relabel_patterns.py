import json, re

path=r'd:\\git\\concept-sketch\\grammars\\relations.json'
with open(path,'r',encoding='utf-8') as f:
    data=json.load(f)

for r in data['relations']:
    pat=r.get('pattern','')
    # split keeping spaces
    parts = re.split(r'\s+', pat.strip())
    # remove existing numeric labels
    clean = [re.sub(r'^\d+:','',p) for p in parts]
    hp=r.get('head_position')
    cp=r.get('collocate_position')
    # positions are 1-indexed
    labeled=[]
    for i,p in enumerate(clean, start=1):
        if i==hp:
            labeled.append('1:'+p)
        elif i==cp:
            labeled.append('2:'+p)
        else:
            labeled.append(p)
    r['pattern']=' '.join(labeled)

with open(path,'w',encoding='utf-8') as f:
    json.dump(data,f,indent=2,ensure_ascii=False)
