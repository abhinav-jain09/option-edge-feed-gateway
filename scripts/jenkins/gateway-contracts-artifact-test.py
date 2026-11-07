#!/usr/bin/env python3
import sys
sys.dont_write_bytecode = True

import copy
import importlib.util
import io
import json
import re
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC=importlib.util.spec_from_file_location("gateway_contracts_artifact",Path(__file__).with_name("gateway-contracts-artifact.py"))
M=importlib.util.module_from_spec(SPEC);SPEC.loader.exec_module(M)
POM=b'<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>com.optionsedge</groupId><artifactId>options-edge-contracts</artifactId><version>0.85.0</version></project>'


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.raw=self.jar(POM)
        self.lock={"schemaVersion":1,"groupId":"com.optionsedge","artifactId":"options-edge-contracts","version":"0.85.0","sourceSha":"a"*40,"jenkinsJob":"options-edge-contracts","jenkinsBuild":127,"artifactPath":"target/options-edge-contracts-0.85.0.jar","bytes":len(self.raw),"jarSha256":M.sha(self.raw),"pomSha256":M.sha(POM)}
        (self.root/"ci").mkdir();self.write_lock()
        (self.root/"pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><dependencies><dependency><groupId>com.optionsedge</groupId><artifactId>options-edge-contracts</artifactId><version>0.85.0</version></dependency></dependencies></project>')
        self.metadata={"result":"SUCCESS","keepLog":True,"actions":[{"remoteUrls":["git@github.com:abhinav-jain09/options-edge-jenkins-library.git"],"lastBuiltRevision":{"SHA1":"b"*40}},{"remoteUrls":[M.SOURCE_REPO],"lastBuiltRevision":{"SHA1":"a"*40}}]}
        self.calls=[]

    @staticmethod
    def jar(pom):
        out=io.BytesIO()
        with zipfile.ZipFile(out,"w") as z:z.writestr(M.POM_ENTRY,pom)
        return out.getvalue()

    def write_lock(self):
        (self.root/"ci/contracts-release.json").write_text(json.dumps(self.lock))

    def reader(self,url,cap):
        self.calls.append(url)
        return json.dumps(self.metadata).encode() if "api/json" in url else self.raw

    def prepare(self):return M.prepare(self.root,"http://localhost:8085",self.reader)

    def test_retained_exact_release_and_embedded_pom_are_prepared(self):
        receipt=self.prepare();jar,pom,_,_=M.paths(self.root,self.lock)
        self.assertEqual(self.raw,jar.read_bytes());self.assertEqual(POM,pom.read_bytes())
        self.assertTrue(receipt["releaseValidated"]);self.assertEqual(2,len(self.calls))
        self.assertEqual(self.raw,(jar.parent/"current.jar").read_bytes())

    def test_wrong_checkout_failure_or_unretained_build_never_downloads_jar(self):
        original=copy.deepcopy(self.metadata)
        for update in ({"result":"FAILURE"},{"keepLog":False},{"actions":[]}):
            self.metadata=copy.deepcopy(original);self.metadata.update(update);self.calls=[]
            with self.assertRaises(ValueError):self.prepare()
            self.assertEqual(1,len(self.calls))
            self.assertFalse(M.paths(self.root,self.lock)[0].exists())

    def test_cache_is_verified_not_silently_replaced_and_current_pointer_is_repaired(self):
        self.prepare();jar,_,_,_=M.paths(self.root,self.lock)
        (jar.parent/"current.jar").write_bytes(b"invalid current pointer")
        self.calls=[];self.prepare();self.assertEqual(1,len(self.calls))
        self.assertEqual(self.raw,(jar.parent/"current.jar").read_bytes())
        jar.write_bytes(b"bad immutable cache")
        with self.assertRaises(ValueError):self.prepare()
        self.assertEqual(b"bad immutable cache",jar.read_bytes())

    def test_wrong_pom_identity_is_refused_even_when_archive_digest_matches(self):
        other=POM.replace(b"0.85.0",b"0.86.0");raw=self.jar(other)
        lock={**self.lock,"bytes":len(raw),"jarSha256":M.sha(raw),"pomSha256":M.sha(other)}
        with self.assertRaisesRegex(ValueError,"embedded POM differs"):M.validate_jar(raw,lock)

    def test_altered_or_truncated_archive_is_refused(self):
        for raw in (self.raw[:-1],self.raw+b"x",self.raw.replace(b"pom.xml",b"pom.xmX")):
            with self.assertRaises(ValueError):M.validate_jar(raw,self.lock)

    def test_duplicate_boolean_or_traversal_lock_fields_are_refused(self):
        for key,value in (("bytes",True),("jenkinsBuild",True),("sourceSha","x"*40),("artifactPath","../other.jar"),("version","0.85.0-SNAPSHOT")):
            lock=self.lock.copy();self.lock[key]=value;self.write_lock()
            with self.assertRaises(ValueError):M.load_lock(self.root)
            self.lock=lock
        (self.root/"ci/contracts-release.json").write_text('{"schemaVersion":1,"schemaVersion":1}')
        with self.assertRaisesRegex(ValueError,"duplicate"):M.load_lock(self.root)

    def test_gateway_pin_mismatch_and_credential_url_are_refused(self):
        with self.assertRaisesRegex(ValueError,"Jenkins origin"):M.prepare(self.root,"http://user:secret@localhost",self.reader)
        p=self.root/"pom.xml";p.write_text(p.read_text().replace("0.85.0","0.84.0"))
        with self.assertRaisesRegex(ValueError,"dependency and release lock differ"):self.prepare()

    def test_installed_jar_and_pom_must_both_match_release(self):
        _,_,jar,pom=M.paths(self.root,self.lock);M.store(jar,self.raw);M.store(pom,POM)
        self.assertEqual(self.lock,M.verify_installed(self.root))
        pom.write_bytes(b"other")
        with self.assertRaises(ValueError):M.verify_installed(self.root)
        pom.write_bytes(POM);jar.write_bytes(b"other")
        with self.assertRaises(ValueError):M.verify_installed(self.root)

    def test_download_and_cached_file_capacity_are_bounded(self):
        class Response(io.BytesIO):
            def __enter__(self):return self
            def __exit__(self,*args):self.close()
        with patch.object(M.urllib.request,"urlopen",return_value=Response(b"12345")):
            with self.assertRaisesRegex(ValueError,"capacity"):M.fetch("http://localhost/fixture",4)
        path=self.root/"large";path.write_bytes(b"12345")
        with self.assertRaisesRegex(ValueError,"capacity"):M.bounded_file(path,4)

    def test_atomic_cache_never_replaces_another_release(self):
        path=self.root/"cache";M.store(path,b"first");M.store(path,b"first")
        with self.assertRaises(ValueError):M.store(path,b"other")
        self.assertEqual(b"first",path.read_bytes());M.store(path,b"pointer",replace=True)
        self.assertEqual(b"pointer",path.read_bytes())

    def test_permitted_source_must_equal_the_retained_release(self):
        self.assertEqual(self.lock,M.permitted_lock(self.root,self.lock['sourceSha']))
        for value in [None,'','a'*12,'b'*40,'A'*40]:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):M.permitted_lock(self.root,value)

    def test_property_pin_is_resolved_and_mismatch_rejected(self):
        path=self.root/'pom.xml'
        path.write_text(path.read_text().replace('<dependencies>', '<properties><options-edge-contracts.version>0.85.0</options-edge-contracts.version></properties><dependencies>').replace('<version>0.85.0</version>', '<version>${options-edge-contracts.version}</version>'))
        self.assertEqual('0.85.0',M.load_lock(self.root)['version'])
        path.write_text(path.read_text().replace('>0.85.0<','>0.84.0<'))
        with self.assertRaises(ValueError):M.load_lock(self.root)

    def test_controller_origin_is_explicit_and_missing_origin_fails_closed(self):
        self.assertEqual('https://controller/',M.artifact_origin({'JENKINS_URL':'https://controller/'}))
        self.assertEqual('https://override/',M.artifact_origin({'JENKINS_URL':'https://controller/','JENKINS_ARTIFACT_BASE_URL':'https://override/'}))
        with self.assertRaises(ValueError):M.prepare(self.root,M.artifact_origin({}),self.reader)
        self.metadata['actions'].insert(0,None);self.prepare()


if __name__=="__main__":unittest.main()
