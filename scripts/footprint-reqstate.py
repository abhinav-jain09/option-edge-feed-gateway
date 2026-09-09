#!/usr/bin/env python3
"""Render the §9 requirement-reconciliation table from the CHECKED-IN campaign record.

The state column is derived, never typed: a requirement is what its mutations say it is. A
requirement the campaign did not probe says exactly that, and claims nothing else — the failure mode
this replaces is a hand-maintained table drifting away from the code it claims to describe.
"""
import hashlib, json, os, re, subprocess, sys, collections

def occurrences(text, needle):
    """Counted the way the harness's splice locator scans: advancing by one character, so
    overlapping matches are found. str.count does not, and counting differently from the locator
    would report drift where there is none."""
    n, at = 0, 0
    while True:
        i = text.find(needle, at)
        if i < 0: return n
        n += 1; at = i + 1


def assertion_line(line):
    """Is this surefire line an ASSERTION failure rather than a thrown exception?

    `<<< FAILURE!` says so outright. The numbered summary is NOT enough on its own: surefire prints
    `Class.method:123 » RuntimeException boom` for a THROW in exactly the same shape, and accepting
    any `:LINE ` let a forged record pair one `<<< ERROR!` line with an error summary and pass as an
    assertion kill. So take the summary only when it is not the ` » Exception` form.
    """
    if '<<< ERROR!' in line:
        return False
    if '<<< FAILURE!' in line:
        return True
    return bool(re.search(r':\d+ ', line)) and ' » ' not in line

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

    # The whole pinned column rests on the baseline: the claim is "this failure came from the
    # mutation, because the same command passed on the same tree without it". Commit, command and
    # returnCode are three hand-editable fields, and matching them establishes only that someone
    # wrote them consistently — a baseline block reduced to {commit, command, returnCode: 0} carries
    # no evidence that any run happened at all. Demand the run's own output, and demand that it say
    # what the record claims it said.
    BASELINE_TAIL_CAP = 1500     # what the harness records; a SHORTER tail is the whole output


    # Everything above checks the record against itself, and a record is a file someone can write.
    # This is the one check that reaches OUTSIDE it: the clause the mutation claims to have broken
    # must actually be in the repository, at the commit the record names, exactly as many times as
    # the record says. A fabricated record now has to be fabricated against real source — and if it
    # is, the thing it names is real.
    _blob = {}
    def clause_is_in_the_tree(rid, m):
        commit = m.get('evidence', {}).get('repoCommit')
        if not commit:
            return                      # already reported by the commit checks
        patch = m.get('patch') or {}
        sites = patch.get('sites') or [{'file': m.get('file'), 'old': patch.get('old'),
                                        'occurrence': m.get('occurrence', 1),
                                        'occurrencesInFile': patch.get('occurrencesInFile')}]
        for site in sites:
            path, old = site.get('file'), site.get('old')
            if not path or not old:
                problems.append(f"{rid}: a mutation site names no file or no clause")
                continue
            key = (commit, path)
            repo = os.path.dirname(os.path.abspath(sys.argv[2])) or '.'
            if key not in _blob:
                try:
                    _blob[key] = subprocess.run(['git', 'show', f'{commit}:{path}'],
                                                capture_output=True, text=True, cwd=repo).stdout
                except Exception:
                    _blob[key] = ''
            blob = _blob[key]
            if not blob:
                # Two very different reasons the blob is unreadable, and saying the wrong one sends
                # the reader looking for a deleted file when the truth is a shallow checkout. CI
                # clones at depth 1, so a record's commit — an ancestor — is simply not in the copy.
                # Either way this fails: a check that cannot run is not a check that passed.
                # `git cat-file -e` exits 1 for a commit this copy does not have — but it also
                # exits non-zero when git cannot run at all, when the directory is not a repository,
                # and when the name is malformed, and prescribing `fetch-depth: 0` for any of those
                # sends the reader somewhere useless. Only exit 1 with nothing on stderr is the
                # absent-commit answer; everything else is reported as git's own failure, in git's
                # own words. And the call is inside the try, so a git that cannot be executed at all
                # produces this refusal rather than a traceback.
                try:
                    # `rev-parse --verify --quiet`, not `cat-file -e`: cat-file prints
                    # "fatal: Not a valid object name" for a commit the copy does not have, so its
                    # stderr cannot separate "absent" from "git is unhappy". rev-parse --quiet exits
                    # 1 SILENTLY for a name it cannot resolve and keeps stderr for real trouble —
                    # not a repository, an unreadable object database, git missing entirely.
                    probe = subprocess.run(['git', 'rev-parse', '--verify', '--quiet',
                                            f'{commit}^{{commit}}'],
                                           capture_output=True, text=True, cwd=repo)
                    absent = probe.returncode == 1 and not probe.stderr.strip()
                    trouble = None if (probe.returncode in (0, 1) and not probe.stderr.strip()) \
                              else (probe.stderr.strip().splitlines() or [f'exit {probe.returncode}'])[0]
                except Exception as exc:
                    absent, trouble = False, f'{type(exc).__name__}: {exc}'
                if trouble:
                    problems.append(f"{rid}: git could not be asked whether commit {commit[:12]} is "
                                    f"in this copy of the repository ({trouble}), so the clause this "
                                    f"mutation broke cannot be looked up")
                elif absent:
                    problems.append(f"{rid}: commit {commit[:12]} is not in this copy of the "
                                    f"repository, so the clause this mutation broke cannot be "
                                    f"looked up — check out with full history (fetch-depth: 0)")
                else:
                    problems.append(f"{rid}: {path} cannot be read at {commit[:12]}, so nothing says "
                                    f"the clause this mutation broke was ever there")
                continue
            n = occurrences(blob, old)
            said = site.get('occurrencesInFile')
            if n == 0:
                problems.append(f"{rid}: the clause this mutation claims to have broken is not in "
                                f"{path} at {commit[:12]}")
            # `is None` is not the same as "absent is fine": a site that states no count states
            # nothing, and skipping the comparison when the field is missing made the check optional
            # — a fabricated site had only to omit it.
            elif not isinstance(said, int) or said < 1:
                problems.append(f"{rid}: a mutation site in {path} states no occurrence count, so "
                                f"there is nothing to compare against the {n} in the tree")
            elif n != said:
                problems.append(f"{rid}: the record says the clause occurs {said} time(s) in "
                                f"{path}; at {commit[:12]} it occurs {n}")
            elif n < (site.get('occurrence') or 1):
                problems.append(f"{rid}: the record mutates occurrence {site.get('occurrence')} of a "
                                f"clause that occurs {n} time(s) in {path}")

    def baseline_ran(rid, m, base, ev):
        tail = base.get('outputTail')
        if not base.get('outputSha256'):
            problems.append(f"{rid}: the baseline carries no hash of its output, so nothing says "
                            f"the green run it claims ever happened")
            return
        if not tail:
            problems.append(f"{rid}: the baseline carries no output, so nothing says what it "
                            f"actually reported")
            return
        if base.get('outputSha256') == ev.get('outputSha256'):
            problems.append(f"{rid}: the baseline and the mutation record the SAME output hash; "
                            f"a passing run and a failing one are not the same run")
        # A tail shorter than the cap is the COMPLETE output, so its hash is checkable. This is the
        # only place the recorded hash can be verified rather than trusted, and short suites — the
        # node ones — land here.
        if len(tail) < BASELINE_TAIL_CAP:
            if hashlib.sha256(tail.encode()).hexdigest() != base.get('outputSha256'):
                problems.append(f"{rid}: the baseline's output hash is not the hash of the output "
                                f"it recorded")
        # And it must SAY it passed, in the idiom of the runner that produced it. `returnCode: 0` is
        # one editable integer; the runner's own verdict lines are the thing that has to agree with
        # it. They are pulled from the WHOLE baseline output, not the tail — the tail of these
        # suites is whatever the last test logged.
        verdict = base.get('resultLines')
        if not verdict:
            problems.append(f"{rid}: the baseline records no verdict from its runner, so the only "
                            f"thing saying it passed is its own return code")
        elif runner_of(m) == 'maven':
            if not any('BUILD SUCCESS' in l for l in verdict):
                problems.append(f"{rid}: the baseline claims to have passed, but its runner did not "
                                f"report BUILD SUCCESS ({verdict!r})")
            for l in verdict:
                hit = re.search(r'Tests run: \d+, Failures: (\d+), Errors: (\d+)', l)
                if hit and (hit.group(1) != '0' or hit.group(2) != '0'):
                    problems.append(f"{rid}: the baseline claims to have passed, but its runner "
                                    f"reported {hit.group(1)} failures and {hit.group(2)} errors")
                    break
        else:
            if not any(l.strip() == '# fail 0' for l in verdict):
                problems.append(f"{rid}: the baseline claims to have passed, but its runner did not "
                                f"report zero failures ({verdict!r})")
        # The baseline is the run WITHOUT the mutation. Its output carrying this record's own
        # failures would mean the tree was already red, which is the one thing the baseline exists
        # to rule out.
        for l in (ev.get('failureLines') or []):
            if l.strip() and l.strip() in tail:
                problems.append(f"{rid}: the baseline's own output carries this record's failure "
                                f"{l.strip()[:60]!r}, so the tree was red before the mutation")
                break

    # The runner is whatever the recorded COMMAND ran, not whatever its lines look like. Inferring
    # it from failureLines let a single forged `not ok …` line switch the whole record into node
    # mode and skip the surefire assertion check entirely.
    def runner_of(m0):
        """The runner, taken from the command AND cross-checked against the lines it produced.

        Neither field alone is enough: inferring from the lines let one forged `not ok` line switch
        a Maven record into node mode, and trusting the command let a forged `node --test` command do
        the same. A record whose command and evidence disagree is refused rather than guessed at.
        """
        c = m0.get('command', '')
        # `npm test`, `npm run test:js`, `yarn test` and `pnpm test` are node runners too; matching
        # only `node ` / ` --test` classified them as maven and then refused their valid `not ok`
        # evidence.
        by_command = 'node' if (' --test' in c or re.match(r'^(node|npm|yarn|pnpm|npx)\b', c)) else 'maven'
        lines = m0.get('evidence', {}).get('failureLines') or []
        looks_maven = any(l.startswith('[ERROR]') for l in lines)
        looks_node = any(l.startswith('not ok ') for l in lines)
        if lines and looks_maven and looks_node:
            # both shapes in one record is not a runner this campaign ran; refuse rather than pick
            problems.append(f"{m0.get('requirement')}: the failure lines carry BOTH surefire and node "
                            f"shapes, so nothing says which runner produced them")
            return 'maven'
        if lines and looks_maven and by_command != 'maven':
            problems.append(f"{m0.get('requirement')}: the command says node but the failure lines are surefire's")
            return 'maven'
        if lines and looks_node and by_command != 'node':
            problems.append(f"{m0.get('requirement')}: the command says maven but the failure lines are node's")
            return 'maven'
        return by_command
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
                # `status` is an editable field. A genuine kill relabelled SURVIVED, with its
                # non-zero exit left in place, was rendered as a survivor — so validate the other
                # statuses against their own evidence too.
                rc = m.get('evidence', {}).get('returnCode')
                if m['status'] == 'SURVIVED':
                    if rc != 0:
                        # `!= 0` and not `if rc`: a MISSING return code was reading as success, so a
                        # kill relabelled SURVIVED with its exit code deleted rendered as a survivor.
                        problems.append(f"{rid}: a SURVIVED record whose run exited {rc!r}")
                    evidence_of_failure = (m.get('assertionFailures') or m.get('killedBy')
                                           or m.get('anyFailures') or m.get('failureCount')
                                           or m.get('evidence', {}).get('failureLines'))
                    if evidence_of_failure:
                        problems.append(f"{rid}: a SURVIVED record that carries failure evidence")
                elif not rc:
                    problems.append(f"{rid}: a {m['status']} record whose run exited 0")
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
            # The baseline must be the baseline OF THIS RUN. Both fields are editable, so a record
            # could otherwise pair a green run of an unrelated command with failing evidence from
            # another runner and still render as pinned.
            if not m.get('command'):
                problems.append(f"{rid}: a KILLED record names no command")
            elif base.get('command') != m.get('command'):
                problems.append(f"{rid}: the baseline ran a different command than the mutation "
                                f"({base.get('command')!r} vs {m.get('command')!r})")
            if base.get('returnCode') not in (0,):
                problems.append(f"{rid}: a KILLED record whose baseline did not pass "
                                f"(returnCode {base.get('returnCode')})")
            if not ev.get('outputSha256'):
                problems.append(f"{rid}: a KILLED record carries no hash of its run output")
            baseline_ran(rid, m, base, ev)
            clause_is_in_the_tree(rid, m)
            # the named assertion failures must be the ones THIS run produced
            lines = m.get('evidence', {}).get('failureLines') or []
            runner_of(m)   # cross-check command against evidence for EVERY kill, not only the
                           # ones that name an assertion — a killedByThrow record was never checked
            for name in (m.get('assertionFailures') or []):
                short = name.split('.')[-1]
                carrying = [l for l in lines if short in l]
                if not carrying:
                    problems.append(f"{rid}: names assertion failure {name} that its own failure lines do not carry")
                elif runner_of(m) == 'maven' and not any(assertion_line(l) for l in carrying):
                    # Surefire marks a THROW as ERROR and an assertion as FAILURE, and prints the
                    # assertion's own `Class.method:LINE message` summary. Requiring the ABSENCE of
                    # ERROR was not enough: adding the ordinary summary line to an ERROR record
                    # satisfied it. Require a line that positively says FAILURE, or the numbered
                    # assertion summary.
                    problems.append(f"{rid}: names {name} as an assertion failure, but no line it "
                                    f"carries for that name is a FAILURE or an assertion summary")
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
            if m.get('evidence', {}).get('treeRestoredClean') is not True:
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
            if not b:
                problems.append(f"{m.get('requirement')}: a record names no baseline commit, so "
                                f"nothing says its baseline ran at the commit it claims")
            elif b != c:
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
