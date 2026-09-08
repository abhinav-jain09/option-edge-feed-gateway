#!/usr/bin/env python3
"""Render the conformance matrix from the CHECKED-IN campaign records and the CHECKED-IN documents.

Reproducibility is the point: this reads only files that live in the three repositories, at paths
given on the command line, and nothing from a scratch directory. Two self-checks refuse to emit:

  * an obligation that is not a verbatim substring of the requirement it is attached to — the failure
    that transposed fourteen requirements in the first version of this document;
  * a survivor with no characterisation — so the summary can never claim "nothing is unpinned" while
    an uncharacterised gap sits in the table.
"""
import json, re, sys, collections, os

def load_requirements(path, pattern):
    rows = {}
    for i, l in enumerate(open(path).read().split('\n'), 1):
        m = re.match(r'^\|\s*(' + pattern + r')\s*\|\s*(.*)$', l)
        if not m: continue
        t = m.group(2).rstrip()
        if t.endswith('|'): t = t[:-1].rstrip()
        if m.group(1) in rows and len(t) <= len(rows[m.group(1)]['text']): continue
        lead = re.match(r'\*\*(.+?)\*\*', t)
        rows[m.group(1)] = {'line': i, 'text': t, 'lead': (lead.group(1).rstrip('.') if lead else '')}
    return rows

# Why each surviving mutation survives. A survivor with no entry here stops the render.
WHY = {
 'F-R5.1 no-half-apply':
   ("UNOBSERVABLE. Any failure after the premature insert calls latch(), which CLEARS the level map — "
    "so the half-applied state it would leave cannot be seen from outside. The clause is not "
    "falsifiable as written, and no test claims to pin it."),
 'F-E1 reason-precedence-overflow':
   ("INERT MUTATION. FootprintBar.quality() returns a single enum value with OVERFLOW selected before "
    "the volume-based QUIET classification, so the two are mutually exclusive and swapping those arms "
    "changes no output. The precedence is real only between the LATER arms, which are killed."),
 'F-R9 line50: delta ':
   ("ALGEBRAICALLY IMPLIED. Every route to violating it trips an earlier invariant first for any "
    "reachable CVD bar. The other 27 checks in Invariants are each killed by a named test."),
 'G-R7 epoch-guard-relaxed':
   ("INERT MUTATION, kept as the record of that fact. At afterMs == EPOCH_MAX_MS the +1 is safe and hi "
    "is capped, so lo > hi and the page is empty either way. The clause's load-bearing half is "
    "epoch-guard-removed, which IS killed."),
}

def main():
    specs = json.loads(sys.argv[1])   # [{repo,label,doc,docPath,idPattern,recordPath}]
    out, rows, problems = [], [], []
    reqs = {}
    for s in specs:
        reqs[s['repo']] = load_requirements(s['docPath'], s['idPattern'])
        rec = json.load(open(s['recordPath']))
        for key, v in rec.items():
            rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', key).group(1)
            text = v.get('documentText')
            if rid not in reqs[s['repo']]:
                problems.append(f"{key}: {rid} is not a requirement in {s['doc']}"); continue
            if not text or text.strip() not in reqs[s['repo']][rid]['text']:
                problems.append(f"{key}: obligation is not verbatim in {rid}"); continue
            if v['status'] == 'SURVIVED' and key not in WHY:
                problems.append(f"{key}: SURVIVED with no characterisation"); continue
            rows.append(dict(repo=s['repo'], key=key, rid=rid, text=text, **v))
    if problems:
        print("REFUSING TO EMIT:"); [print("  ", p) for p in problems]; sys.exit(1)

    tot = collections.Counter(r['status'] for r in rows)
    out.append("# ES Footprint — conformance by mutation\n")
    out.append("Every requirement of the three ES Footprint design documents, and for each clause this campaign\n"
               "probed, the evidence that its behaviour is held by a test.\n")
    out.append("## What a verdict means\n")
    out.append("| | |\n|---|---|")
    out.append("| **KILLED** | the clause was broken and a NAMED test failed |")
    out.append("| **SURVIVED** | the clause was broken and the suite passed |")
    out.append("| **NOT PROBED** | this campaign did not test it. Nothing is claimed either way |")
    out.append("\nThe harness refuses to start against a dirty tree or a red baseline — a campaign against a red\n"
               "suite reports everything as pinned, which is what happened to an earlier run of this one. Every\n"
               "entry in the records carries the repository commit, the patch and the occurrence, line and\n"
               "declaration it was applied at, the command, the process exit code, a SHA-256 of the run output\n"
               "with its tail, and confirmation that the tree was restored clean.\n")
    out.append(f"**{len(rows)} mutations: {tot['KILLED']} killed, {tot.get('SURVIVED',0)} surviving.** "
               "Obligation text is quoted from the design document; where a requirement is longer than the cell, "
               "the quotation is a PREFIX of it and the document line is given so the rest can be read.\n")
    for s in specs:
        sub = [r for r in rows if r['repo'] == s['repo']]
        ids = sorted(reqs[s['repo']], key=lambda x: (re.sub(r'\d', '', x), int(re.search(r'\d+', x).group()), x))
        probed = {r['rid'] for r in sub}
        out.append(f"\n## {s['label']} — `{s['doc']}`\n")
        out.append(f"{len(ids)} requirements; {len(probed)} probed by {len(sub)} mutations. "
                   f"Record: `{os.path.basename(s['recordPath'])}`.\n")
        out.append("| Req | Obligation — quoted from the document | Clause broken | Verdict | Killed by |")
        out.append("|---|---|---|---|---|")
        for rid in ids:
            mine = sorted([r for r in sub if r['rid'] == rid], key=lambda x: x['key'])
            if not mine:
                out.append(f"| **{rid}**<br><sub>{s['doc']}:{reqs[s['repo']][rid]['line']}</sub> | "
                           f"*{reqs[s['repo']][rid]['lead']}* | — | NOT PROBED | — |")
                continue
            for i, r in enumerate(mine):
                head = f"**{rid}**<br><sub>{s['doc']}:{reqs[s['repo']][rid]['line']}</sub>" if i == 0 else ""
                q = r['text'].replace('|', '\\|')
                q = q if len(q) <= 260 else q[:260].rsplit(' ', 1)[0] + ' …'
                tag = "**KILLED**" if r['status'] == 'KILLED' else "⚠️ **SURVIVED**"
                by = (r.get('killedBy') or ['—'])[0].replace('|', '\\|')
                out.append(f"| {head} | {q} | `{r['key'].split(' ', 1)[-1]}` <br><sub>{r['file'].split('/')[-1]}:{r['line']}</sub> | {tag} | `{by}` |")
    surv = [r for r in rows if r['status'] == 'SURVIVED']
    out.append("\n## What nothing holds, and why\n")
    out.append("A surviving mutation is not automatically a gap. Each is named for what it is.\n")
    for r in surv:
        out.append(f"**{r['rid']} — `{r['key'].split(' ', 1)[-1]}`** ({r['file'].split('/')[-1]}:{r['line']})\n")
        out.append(f"> {r.get('clause','')}\n")
        out.append(WHY[r['key']] + "\n")
    out.append("Every survivor above is unobservable, algebraically implied, or an inert mutation whose\n"
               "load-bearing counterpart is killed. **That says nothing about the requirements this campaign did\n"
               "not probe** — those are marked NOT PROBED and no claim is made about them.\n")
    print("\n".join(out))

main()
