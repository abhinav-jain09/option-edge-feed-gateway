#!/usr/bin/env python3
"""Finite isolated source mutants; only focused unit methods, never a gateway/feed/replay."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/main/java/app/feedgateway/FeedGatewayService.java'
JAVA=Path(os.environ['JAVA_HOME'])/'bin'
METHODS={
 'bindings':'allFourSourceModelsUseReleasedReadersAndOnlyDeclaredRetainedNullsAreAllowed',
 'ordering':'legacyNullAndTypedDeletionClearWithoutOlderConsumerRecordsResurrectingState',
 'invalid':'malformedFalseOversizeWrongIdentityAndEscapedDeletionHaveExplicitSemantics',
 'expiry':'markerExpiresWhenNoEarlierPushCanStillBeFresh',
 'replay':'cachedReplayAndWithdrawalUseTheSameEmitLock',
}


def main():
    import argparse
    p=argparse.ArgumentParser();p.add_argument('--classpath',type=Path,required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args()
    deps=args.classpath.read_text().strip();args.out.mkdir(parents=True,exist_ok=True)
    original=SOURCE.read_text();digest=hashlib.sha256(SOURCE.read_bytes()).hexdigest();rows=[]
    cases=[]
    for getter,schema in [('directionCurrentTopic','directionCurrent()'),('directionPushTopic','directionPush()'),('directionAlertTopic','directionAlert()'),('directionScorecardTopic','directionScorecard()')]:
        entry='                Map.entry(settings.'+getter+'(), com.optionsedge.contracts.cvd.CvdRelatedSchemas.'+schema+')'
        # Remove the whole entry and keep Map.ofEntries syntactically valid, including final entry.
        if schema=='directionScorecard()': old=',\n'+entry;new=''
        else: old=entry+',\n';new=''
        cases.append((getter+'-binding',old,new,'bindings'))
    cases.extend([
      ('aligned-strike-binding','                Map.entry(settings.esStrikeIntelSpxAlignedTopic(), com.optionsedge.contracts.cvd.CvdRelatedSchemas.ALIGNED_STRIKE_INTELLIGENCE),\n','','bindings'),
      ('retained-push-null','settings.esStrikeIntelSpxAlignedTopic(), settings.directionPushTopic());','settings.esStrikeIntelSpxAlignedTopic());','bindings'),
      ('withdrawal-order','incoming.offset() < previous.position().offset()','false','ordering'),
      ('withdrawal-partition','!previous.position().partition().equals(incoming.partition())','false','ordering'),
      ('withdrawal-once','forward && !withdrawal.emitted()','forward','ordering'),
      ('newer-current-wins','current.offset() >= incoming.offset()','false','ordering'),
      ('delete-true',' || !body.path("tombstone").booleanValue()','','invalid'),
      ('delete-boolean-type','!body.path("tombstone").isBoolean() || !body.path("tombstone").booleanValue()','!body.path("tombstone").asBoolean()','invalid'),
      ('delete-symbol','\n                                || !"SPX".equals(body.path("symbol").asText())','','invalid'),
      ('delete-cap','json.length() > 4096','false','invalid'),
      ('delete-escaped-key',' || json.contains("\\\\u")','','invalid'),
      ('withdrawal-expiry','> TimeUnit.MILLISECONDS.toNanos(settings.directionPushTtlMs())','> Long.MAX_VALUE','expiry'),
    ])
    cases.append(('replay-emit-lock','''        synchronized (directionPushEmitLock) {
            long pushNowMs''','''        {
            long pushNowMs''','replay'))
    basecp=os.pathsep.join([str(ROOT/'target/test-classes'),str(ROOT/'target/classes'),deps])
    def probe(cp,method,name):
        r=subprocess.run([str(JAVA/'java'),'-Xmx1536m','-cp',cp,'app.feedgateway.DirectionReaderNegativeProbe',method],cwd=ROOT,text=True,capture_output=True,timeout=20)
        (args.out/(name+'.stdout')).write_text(r.stdout);(args.out/(name+'.stderr')).write_text(r.stderr)
        lines=[line for line in r.stdout.splitlines() if line.startswith('{')]
        if len(lines)!=1:raise AssertionError(('structured result missing',name,r.returncode))
        return r.returncode,json.loads(lines[0])
    for key,method in METHODS.items():
        code,result=probe(basecp,method,'baseline-'+key)
        assert code==0 and result['status']=='PASS',(key,result)
        rows.append(dict(control='baseline-'+key,**result))
    code,result=probe(basecp,'missing_method','incidental-error')
    assert code==2 and result['status']=='INCIDENTAL_ERROR' and not result['assertion'],result
    rows.append(dict(control='incidental-is-not-kill',**result))
    for name,old,new,key in cases:
        assert original.count(old)==1,(name,'mutation must match one site',original.count(old))
        with tempfile.TemporaryDirectory(prefix='direction-reader-control-') as temp:
            temp=Path(temp);file=temp/'FeedGatewayService.java';file.write_text(original.replace(old,new));classes=temp/'classes';classes.mkdir()
            compile=subprocess.run([str(JAVA/'javac'),'-J-Xmx256m','-encoding','UTF-8','-cp',basecp,'-d',str(classes),str(file)],cwd=ROOT,text=True,capture_output=True,timeout=45)
            (args.out/(name+'.compile')).write_text(compile.stdout+compile.stderr)
            if compile.returncode:raise AssertionError((name,'compilation is not a kill'))
            code,result=probe(str(classes)+os.pathsep+basecp,METHODS[key],name)
            assert code==1 and result['status']=='ASSERTION_KILLED' and result['assertion'],(name,result)
            rows.append(dict(control=name,**result))
    assert digest==hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    (args.out/'report.json').write_text(json.dumps(dict(sourceSha256=digest,controls=rows),indent=2)+'\n')
    print(json.dumps(dict(assertionKills=len(cases),baselines=len(METHODS),incidentalRejected=True,sourceUnchanged=True)))


if __name__=='__main__':main()
