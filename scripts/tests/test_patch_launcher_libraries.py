import copy
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'patch_launcher_libraries.py'
SPEC = importlib.util.spec_from_file_location('launcher_patch', SCRIPT)
patch = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(patch)


class LauncherPatchTest(unittest.TestCase):
    def setUp(self):
        self.versions = {'lz4': '1.11.4', 'guava': '33.8.0-jre', 'netty': '4.2.21.Final', 'log4j': '2.26.1'}
        coordinates = ['at.yawk.lz4:lz4-java:1.10.1', 'com.google.guava:guava:33.6.0-jre',
                       'io.netty:netty-handler:4.2.16.Final',
                       'io.netty:netty-transport-native-epoll:4.2.16.Final:linux-x86_64',
                       'org.apache.logging.log4j:log4j-api:2.26.0', 'org.example:unchanged:1.0']
        self.source = {'id': '26.3', 'mainClass': 'net.minecraft.client.main.Main',
                       'downloads': {'client': {'url': 'unchanged-client'}},
                       'libraries': [{'name': c, 'downloads': {'artifact': {'url': 'old-url'}},
                                      'rules': [{'action': 'allow', 'os': {'name': 'linux'}}]} for c in coordinates]}
        self.resolve = lambda path: {'path': path, 'url': patch.CENTRAL + path, 'sha1': 'a' * 40, 'size': 42}

    def test_replaces_native_paths_hashes_and_versions_without_mutating_source(self):
        original = copy.deepcopy(self.source)
        result, changes = patch.patch_document(self.source, self.versions, self.resolve)
        self.assertEqual(self.source, original)
        self.assertEqual(len(changes), 5)
        native = result['libraries'][3]
        self.assertEqual(native['name'], 'io.netty:netty-transport-native-epoll:4.2.21.Final:linux-x86_64')
        self.assertTrue(native['downloads']['artifact']['path'].endswith('4.2.21.Final-linux-x86_64.jar'))
        self.assertEqual(native['downloads']['artifact']['sha1'], 'a' * 40)
        self.assertEqual(result['downloads'], original['downloads'])
        for old, new in zip(original['libraries'], result['libraries']):
            self.assertEqual(old['rules'], new['rules'])
        self.assertEqual(result['libraries'][-1], original['libraries'][-1])

    def test_rejects_wrong_version_and_inherited_profiles(self):
        for changes in ({'id': '1.21.11'}, {'inheritsFrom': '26.3'}):
            with self.assertRaises(ValueError):
                patch.patch_document({**self.source, **changes}, self.versions, self.resolve)

    def test_rejects_missing_library_families_and_legacy_native_formats(self):
        incomplete = copy.deepcopy(self.source)
        incomplete['libraries'] = incomplete['libraries'][1:]
        with self.assertRaises(ValueError):
            patch.patch_document(incomplete, self.versions, self.resolve)
        legacy = copy.deepcopy(self.source)
        legacy['libraries'][3]['natives'] = {'linux': 'natives-linux'}
        with self.assertRaises(ValueError):
            patch.patch_document(legacy, self.versions, self.resolve)

    def test_rejects_path_traversal_in_native_classifier(self):
        malicious = copy.deepcopy(self.source)
        malicious['libraries'][3]['name'] = 'io.netty:netty-transport-native-epoll:4.2.16.Final:../../outside'
        with self.assertRaises(ValueError):
            patch.patch_document(malicious, self.versions, self.resolve)

    def test_tcnative_has_its_own_version_scheme(self):
        self.assertIsNone(patch.family('io.netty', 'netty-tcnative-boringssl-static'))
        self.assertEqual(patch.family('io.netty', 'netty-transport-native-kqueue'), 'netty')


if __name__ == '__main__':
    unittest.main()
