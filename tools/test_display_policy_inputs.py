#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Host regression for private display generation and input refusal."""
import hashlib,json,shutil,tempfile,unittest
from pathlib import Path
from prepare_display_policy import prepare
from verify_display_policy_inputs import verify

ROOT=Path(__file__).resolve().parents[1]
class DisplayPolicyInputsTest(unittest.TestCase):
 def fixture(self):
  tmp=tempfile.TemporaryDirectory();self.addCleanup(tmp.cleanup);root=Path(tmp.name)
  shutil.copyfile(ROOT/"display-policy-inputs.json",root/"display-policy-inputs.json")
  shutil.copytree(ROOT/"displayplugin/stock-policy-assets/proprietary",
                  root/"displayplugin/stock-policy-assets/proprietary")
  return root
 def test_reproduces_all_four_exact_payloads(self):
  root=self.fixture();data=verify(root)
  for row in data["generated"]:(root/row["proprietary_path"]).unlink()
  self.assertEqual(4,prepare(root))
  self.assertEqual(10,len(data["source_inputs"])+len(data["generated"]))
  verify(root)
 def test_each_corrupted_source_refuses_before_writing(self):
  for item in verify(ROOT)["source_inputs"]:
   with self.subTest(source=item["id"]):
    root=self.fixture();file=root/item["proprietary_path"]
    raw=bytearray(file.read_bytes());raw[0]^=1;file.write_bytes(raw)
    before={r["proprietary_path"]:hashlib.sha256((root/r["proprietary_path"]).read_bytes()).hexdigest()
            for r in verify(ROOT)["generated"]}
    with self.assertRaises(ValueError):prepare(root)
    self.assertEqual(before,{path:hashlib.sha256((root/path).read_bytes()).hexdigest() for path in before})
 def test_each_corrupted_generated_payload_refuses(self):
  for item in verify(ROOT)["generated"]:
   with self.subTest(payload=item["proprietary_path"]):
    root=self.fixture();file=root/item["proprietary_path"];raw=bytearray(file.read_bytes())
    raw[0]^=1;file.write_bytes(raw)
    with self.assertRaises(ValueError):verify(root)
 def test_symlink_input_refuses(self):
  root=self.fixture();item=verify(root)["source_inputs"][0];file=root/item["proprietary_path"]
  source=file.with_name("actual.xml");file.rename(source);file.symlink_to(source)
  with self.assertRaises(ValueError):prepare(root)

if __name__=="__main__":unittest.main()
