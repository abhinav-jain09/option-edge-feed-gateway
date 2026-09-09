#!/usr/bin/env python3
"""Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.

The state column is derived, never typed: a requirement is what its mutations say it is. A
requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
this replaces is a hand-maintained table drifting away from the code it claims to describe.
"""
import json, os, re, sys, collections

def main():
    recordPath, docPath, idPattern, gates, dispositions = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4]), json.loads(sys.argv[5])
    # Requirements whose subject is not production behaviour (a test inventory) or whose behaviour
    # lives in ANOTHER repository. Neither can be probed here, and neither is a gap — but saying
    # "NOT PROBED" without saying why invites the reader to assume it is one.
    notes = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
    rec = json.load(open(recordPath))
    # Discover requirement ids from the AUTHORITATIVE part of the document only — everything before
    # the generated block. Reading the whole file lets the previous render's own rows keep a
    # requirement alive after it has been deleted upstream, and --check would then accept it.
    text = open(docPath).read()
    # Use the SAME rule as the rewriter: the marker at LINE START. Finding the first substring
    # anywhere lets a prose mention of the marker truncate discovery before the real block.
    mk = re.search(r'^<!-- BEGIN footprint-reqstate', text, re.M)
    authoritative = text if mk is None else text[:mk.start()]
    ids = []
    for line in authoritative.split('\n'):
        m = re.match(r'^\|\s*(' + idPattern + r')\s*\|', line)
        if m and m.group(1) not in ids: ids.append(m.group(1))
    per = collections.defaultdict(list)
    keys = collections.defaultdict(list)
    for k, v in rec.items():
        _rid = v.get('requirement') or re.match(r'((?:F|G|P)-(?:R|E)?\d+[a-z]?)', k).group(1)
        per[_rid].append(v)
        keys[_rid].append(k)
    # A record is only allowed to say "pinned" when it carries the evidence for it: an assertion
    # failure naming a test, and the verbatim failure lines that name was parsed from. A record that
    # merely says KILLED is a claim, not evidence — and one kill in this campaign turned out to be a
    # format-string error under the right test's name, which is exactly what this refuses.
    # The requirement text as the DOCUMENT states it, so an obligation can be checked against the
    # requirement it is filed under rather than trusted.
    # A requirement's normative text is its table ROW plus any continuation lines that follow it —
    # the state tables in these documents live below their row, and an obligation stated there is
    # still that requirement's obligation. Reading the row alone made those unquotable.
    req_text, cur = {}, None
    for line in authoritative.split('\n'):
        mm = re.match(r'^\|\s*(' + idPattern + r')\s*\|\s*(.*)$', line)
        if mm:
            t = mm.group(2).rstrip()
            if t.endswith('|'): t = t[:-1].rstrip()
            cur = mm.group(1)
            if len(t) > len(req_text.get(cur, '')): req_text[cur] = t
            continue
        if cur is None: continue
        if re.match(r'^(#{1,6} |\||---)', line):     # a new section, a new table row, or a rule
            cur = None; continue
        if line.strip(): req_text[cur] += '\n' + line.rstrip()

    problems = []
    for rid, ms in per.items():
        for m in ms:
            # ATTRIBUTION, for every record whatever its status: the obligation a row quotes must be
            # verbatim in the requirement the row sits under. `requirement` is an editable field in a
            # checked-in file, and filing a probe under the wrong requirement is how a table comes to
            # over-count one requirement and under-count another while every individual cell looks fine.
            dt = (m.get('documentText') or '').strip()
            if not dt:
                problems.append(f"{rid}: a record quotes no obligation")
            elif rid not in req_text:
                problems.append(f"{rid}: is not a requirement in the document")
            elif dt not in req_text[rid]:
                problems.append(f"{rid}: the quoted obligation is not verbatim in {rid}: {dt[:70]!r}")
            # Compare on 4-character prefixes so "dropped"/"drop" and "upserts"/"upsert" match; a
            # short obligation ("OLDER ⇒ stale_session drop;") shares few whole words with the clause
            # it states, and demanding whole-word equality rejects correct attributions.
            stem = lambda t: {w.lower()[:4] for w in re.findall(r'[A-Za-z_]{4,}', t)}
            cw, qw = stem(m.get('clause', '')), stem(dt)
            # ONE shared stem, not two. The design states obligations in symbols as often as words
            # ("(3) `gen++`"), so a two-word floor rejects correct attributions and would push the
            # author to reword the clause until the tool is satisfied — which is worse than the gap
            # it closes. This catches a quote lifted from a wholly unrelated part of the requirement;
            # whether the quote states THE clause is a review judgement, and the preamble says so.
            if cw and not (cw & qw):
                problems.append(f"{rid}: the quoted obligation shares almost nothing with the clause "
                                f"it is filed against — clause {m.get('clause','')[:60]!r} vs "
                                f"quote {dt[:60]!r}")
            if m['status'] != 'KILLED':
                continue
            # The run must have actually failed, and against the baseline this campaign proved
            # green. A record is an editable file: without these, a hand-edited entry with
            # returnCode 0 renders as pinned.
            ev = m.get('evidence', {})
            if ev.get('returnCode') in (None, 0):
                problems.append(f"{rid}: a KILLED record whose run exited {ev.get('returnCode')}")
            if not ev.get('repoCommit'):
                problems.append(f"{rid}: a KILLED record names no repository commit")
            base = m.get('baseline', {})
            if base.get('returnCode') not in (0,):
                problems.append(f"{rid}: a KILLED record whose baseline did not pass "
                                f"(returnCode {base.get('returnCode')})")
            if not ev.get('outputSha256'):
                problems.append(f"{rid}: a KILLED record carries no hash of its run output")
            # the named assertion failures must be the ones THIS run produced
            lines = m.get('evidence', {}).get('failureLines') or []
            for name in (m.get('assertionFailures') or []):
                short = name.split('.')[-1]
                carrying = [l for l in lines if short in l]
                if not carrying:
                    problems.append(f"{rid}: names assertion failure {name} that its own failure lines do not carry")
                elif all('<<< ERROR!' in l for l in carrying):
                    # surefire marks a THROW as ERROR and an assertion as FAILURE. A record whose
                    # only line for this name is an ERROR is not evidence of an assertion, whatever
                    # the field it is stored in says.
                    problems.append(f"{rid}: names {name} as an assertion failure, but every line it "
                                    f"carries for that name is an ERROR (a throw), not a FAILURE")
            if not m.get('assertionFailures'):
                # The renderer must hold this itself: a record is a checked-in FILE, and a hand-edited
                # or legacy one would otherwise be taken at its word.
                t = m.get('killedByThrow')
                lines = '\n'.join(m.get('evidence', {}).get('failureLines') or [])
                ok = (isinstance(t, dict) and t.get('test') and t.get('throws')
                      and t['test'].split('.')[-1] in lines and t['throws'] in lines
                      and all(part in lines for part in [t['test'].split('.')[-1]]))
                if not ok:
                    problems.append(f"{rid}: a KILLED record names no ASSERTION failure, and its "
                                    f"killedByThrow does not name a test and a throw that its own "
                                    f"failure lines carry ({m.get('killedBy')})")
            if not m.get('evidence', {}).get('failureLines'):
                problems.append(f"{rid}: a KILLED record carries no failure lines")
            if not m.get('evidence', {}).get('treeRestoredClean', True):
                problems.append(f"{rid}: a KILLED record ran against a tree it did not restore")
    # The hand-maintained preamble sits INSIDE the generated block, so a stale sentence in it
    # survives regeneration and --check. Hold it to the record: every probe key it names must exist,
    # and it may not state a survivor count that the record contradicts.
    pre = os.path.join(os.path.dirname(os.path.abspath(sys.argv[0])), 'footprint-reqstate.preamble')
    if os.path.exists(pre):
        text_pre = open(pre).read()
        # Take every backticked span that BEGINS with a requirement id and compare the WHOLE span
        # against the record's keys. Constraining the clause part to a shape is how the guard
        # skipped `F-R9 line50: delta ` — the one name that was actually stale.
        for named in set(re.findall(r'`((?:F|G|P)-(?:R|E)?\d+[a-z]?(?:\.\d+)?[^`]*)`', text_pre)):
            if ' ' not in named.strip():
                continue                      # a bare requirement id, not a probe reference
            if named not in rec:
                problems.append(f"the preamble names `{named}`, which is not in the record")
        surv = sum(1 for v in rec.values() if v['status'] == 'SURVIVED')
        words = {'one':1,'two':2,'three':3,'four':4,'five':5,'six':6,'seven':7,'eight':8,'nine':9,'ten':10}
        # A COUNT of survivors — "seven mutations survive", "3 probes survived" — must agree with the
        # record. Only a number that quantifies the survivors counts: a probe named `P-R3.6`, a site
        # number, a requirement id are not counts, and an earlier version of this check read them as
        # such and refused a correct preamble.
        for m2 in re.finditer(r'(?<![\w.-])(\d+|' + '|'.join(words) + r')\s+(?:\w+\s+){0,2}?'
                              r'(?:mutations?|probes?|sites?)\s+(?:\w+\s+){0,2}?surviv\w*', text_pre, re.I):
            tok = m2.group(1).lower()
            n = int(tok) if tok.isdigit() else words[tok]
            if n != surv:
                problems.append(f"the preamble says {tok} where the record has {surv} surviving")

    commits = set()
    for ms in per.values():
        for m in ms:
            c = m.get('evidence', {}).get('repoCommit')
            b = m.get('baseline', {}).get('commit')
            if not c:
                problems.append(f"{m.get('requirement')}: a record names no repository commit")
                continue
            if b and b != c:
                problems.append(f"{m.get('requirement')}: the record's commit {c[:12]} is not the "
                                f"commit its baseline ran at ({b[:12]})")
            commits.add(c)
    if len(commits) > 1:
        problems.append("the records come from " + str(len(commits)) + " different commits; a table "
                        "that mixes them is not one campaign: " + ", ".join(sorted(c[:12] for c in commits)))
    if problems:
        print("REFUSING TO EMIT:", file=sys.stderr)
        for p in problems: print("  ", p, file=sys.stderr)
        sys.exit(1)

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
            # A note is EDITORIAL: it says why this requirement has no mutations, and is marked as
            # such so it can never be read as a campaign result.
            state = (notes[rid] + " (not probed)") if rid in notes else "NOT PROBED"
        else:
            by = collections.Counter(m['status'] for m in ms)
            killed = by.pop('KILLED', 0)
            # Count PROBES, not clauses: a clause implemented at several sites is probed once per
            # site AND once for all sites together, so "n of m clauses" would triple-count it.
            # A clause probed at several SITES, or broken several WAYS (variants), is still one
            # clause; counting records as clauses triple-counts it.
            groups = len({re.sub(r' (?:site|variant)\d+\S*$', '', k) for k in keys[rid]})
            state = f"{killed} of {len(ms)} probes pinned, over {groups} clause" + ("" if groups == 1 else "s")
            rest = ", ".join(f"{n} {st.lower()}" for st, n in sorted(by.items()))
            if rest: state += f" ({rest})"
        out.append(f"| {rid} | {state} | {gate(rid)} | {disp(rid)} |")
    # Count only what the table shows. A record for a requirement the document no longer
    # renders is omitted from the rows, and must be omitted from the totals with it.
    rendered = [v for rid in ids for v in per.get(rid, [])]
    tot = collections.Counter(v['status'] for v in rendered)
    out.append("")
    probed_here = len([r for r in ids if r in per])
    out.append(f"{len(ids)} requirements; {probed_here} probed by {len(rendered)} mutations "
               f"({tot['KILLED']} killed, {tot.get('SURVIVED', 0)} surviving"
               + (", " + ", ".join(f"{n} {st.lower()}" for st, n in sorted(tot.items()) if st not in ('KILLED', 'SURVIVED')) if len(tot) > 2 else "")
               + ")."
               "\n\nRead the state column narrowly. \"n of m probes pinned\" says that breaking those "
               "clauses in the production source made a NAMED test fail an ASSERTION (or propagate a "
               "throw the spec declared and the run matched) — "
               "it does NOT say the requirement as a whole is held, because a requirement usually has "
               "more clauses than this campaign broke. \"NOT PROBED\" means this campaign did not test "
               "it and claims nothing either way; where a note appears beside it, that note is "
               "editorial and is not a campaign result. Evidence, per mutation — the patch, the file, "
               "line and enclosing declaration, the command, the exit code, the verbatim failure "
               "lines and a SHA-256 of the run output — is in the campaign record beside this "
               "document, and this table refuses to render a \"pinned\" cell for any record that does "
               "not carry it.")
    print("\n".join(out))

main()
