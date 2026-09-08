#!/usr/bin/env python3
"""Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.

The state column is derived, never typed: a requirement is what its mutations say it is. A
requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
this replaces is a hand-maintained table drifting away from the code it claims to describe.
"""
import json, re, sys, collections

def main():
    recordPath, docPath, idPattern, gates, dispositions = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4]), json.loads(sys.argv[5])
    # Requirements whose subject is not production behaviour (a test inventory) or whose behaviour
    # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
    # "NOT PROBED" without saying why invites the reader to assume it is one.
    notes = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
    rec = json.load(open(recordPath))
    ids = []
    for line in open(docPath):
        m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
        if m and m.group(1) not in ids: ids.append(m.group(1))
    per = collections.defaultdict(list)
    for k, v in rec.items():
        per[v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)].append(v)
    def gate(rid):
        for pat, g in gates.items():
            if re.fullmatch('(?:' + pat + ')', rid): return g
        return '—'
    def disp(rid):
        for pat, d in dispositions.items():
            if re.fullmatch('(?:' + pat + ')', rid): return d
        return ''
    out = ["| id | Conformance | Gate | Disposition |", "|----|-------------|------|-------------|"]
    for rid in sorted(ids, key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x)):
        ms = per.get(rid, [])
        if not ms:
            state = notes.get(rid, "NOT PROBED")
        else:
            killed = sum(1 for m in ms if m['status'] == 'KILLED')
            state = f"{killed}/{len(ms)} pinned" + ("" if killed == len(ms) else f" — {len(ms)-killed} characterised")
        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
    tot = collections.Counter(v['status'] for v in rec.values())
    out.append("")
    out.append(f"{len(ids)} requirements; {len(per)} probed by {len(rec)} mutations "
               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving). "
               "\"n/n pinned\" means every clause this campaign broke in that requirement made a NAMED "
               "test fail. \"NOT PROBED\" means this campaign did not test it and claims nothing "
               "either way. Evidence, per mutation, is in the campaign record beside this document.")
    print("\n".join(out))

main()
