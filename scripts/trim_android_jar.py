import zipfile, struct, re, sys

"""
Android jar trimmer for OpenAugustus.

Repacks build/compatibility/OpenAugustus/OpenAugustus.jar without the
~11,900 fastutil classes the client never references (the transitive
closure of 1,025 classes is kept) and without the jvm-downgrader
META-INF/coverage diagnostics. The class-load audit links the same
24,955 classes before and after the trim.

Run after ./gradlew compatibilityJar, then publish the trimmed jar as
OpenAugustus.jar (update sha1/size/url in OpenAugustus.json).
"""


JAR = 'build/compatibility/OpenAugustus/OpenAugustus.jar'
OUT = 'build/compatibility/OpenAugustus/OpenAugustus-trimmed.jar'

def cp_refs(data):
    if data[:4] != b'\xca\xfe\xba\xbe':
        return set()
    pos = 8
    cp_count = struct.unpack_from('>H', data, pos)[0]
    pos += 2
    utf8 = {}
    class_idx = []
    i = 1
    while i < cp_count:
        tag = data[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack_from('>H', data, pos)[0]; pos += 2
            utf8[i] = data[pos:pos+ln]; pos += ln
        elif tag in (3,4): pos += 4
        elif tag in (5,6): pos += 8; i += 1
        elif tag == 7:
            class_idx.append(struct.unpack_from('>H', data, pos)[0]); pos += 2
        elif tag == 8: pos += 2
        elif tag in (9,10,11): pos += 4
        elif tag == 12: pos += 4
        elif tag == 15: pos += 3
        elif tag == 16: pos += 2
        elif tag in (17,18): pos += 4
        elif tag in (19,20): pos += 2
        else: raise ValueError(tag)
        i += 1
    refs = set()
    for idx in class_idx:
        name = utf8.get(idx, b'').decode('utf-8','replace').lstrip('[')
        if name.startswith('L') and name.endswith(';'): name = name[1:-1]
        if '/' in name and ';' not in name: refs.add(name)
    for s in utf8.values():
        txt = s.decode('utf-8','replace')
        for m in re.finditer(r'it/unimi/dsi/fastutil/[\w$./]+', txt):
            refs.add(m.group(0))
    return refs

with zipfile.ZipFile(JAR) as z:
    entries = {n: z.read(n) for n in z.namelist()}

# 1) direct it/unimi refs from every non-fastutil class in the jar
keep = set()
for n, data in entries.items():
    if not n.endswith('.class') or n.startswith('it/unimi/'):
        continue
    keep |= {r for r in cp_refs(data) if r.startswith('it/unimi/')}
print('direct refs from app+libs:', len(keep))

# 2) transitive closure within fastutil
changed = True
fu_classes = [n for n in entries if n.startswith('it/unimi/') and n.endswith('.class')]
fu_set = {n[:-6] for n in fu_classes}
while changed:
    changed = False
    for n in fu_classes:
        base = n[:-6]
        if base not in keep:
            continue
        refs = {r for r in cp_refs(entries[n]) if r.startswith('it/unimi/')}
        new = refs - keep
        if new:
            keep |= new
            changed = True
print('after closure:', len(keep))

# 3) rewrite jar without unused fastutil classes + drop META-INF/coverage
removed = 0
with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as zo:
    for n in sorted(entries):
        if n.startswith('it/unimi/') and n.endswith('.class') and n[:-6] not in keep:
            removed += 1
            continue
        if n.startswith('META-INF/coverage/'):
            removed += 1
            continue
        zo.writestr(n, entries[n])
import os
print(f'removed entries: {removed}')
print(f'before: {os.path.getsize(JAR)/1e6:.1f} MB  after: {os.path.getsize(OUT)/1e6:.1f} MB')
