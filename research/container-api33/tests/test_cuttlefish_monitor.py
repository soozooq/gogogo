"""Protocol fixtures; real ARM HMP numeric-address behavior is checked separately."""
import importlib.util
import json
from pathlib import Path
import socket
import tempfile
import threading
import unittest


spec = importlib.util.spec_from_file_location('boot',
    Path(__file__).resolve().parents[1] / 'tools/run-cuttlefish-arm-boot.py')
boot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(boot)


class MonitorProtocolTest(unittest.TestCase):
    def test_exact_system_fatals_only(self):
        self.assertEqual('ANDROID_SYSTEM_PROCESS_JAVA_FATAL', boot.boot_log_failure(
            b'10-24 15:17:56.471 680 680 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: main'))
        self.assertEqual('GUEST_KERNEL_PANIC', boot.boot_log_failure(b'[1.23] Kernel panic - not syncing: fixture'))
        self.assertIsNone(boot.boot_log_failure(b'E AndroidRuntime: FATAL EXCEPTION: main'))
        self.assertIsNone(boot.boot_log_failure(b'keystore2 watchdog thread idle -> terminating'))

    def test_numeric_addresses_and_missing_pc(self):
        for registers in ['CPU#0 PC=00000000ff86da00 X00=0', 'NO_PC', {'unexpected': 'shape'}]:
            with self.subTest(registers=registers), tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / 'qmp.sock'
                requests, errors = [], []
                expected = 6 if isinstance(registers, str) and 'PC=' in registers else 4
                with socket.socket(socket.AF_UNIX) as server:
                    server.bind(str(path))
                    server.listen(1)
                    server.settimeout(5)

                    def peer():
                        try:
                            connection, _ = server.accept()
                            with connection, connection.makefile('rwb', buffering=0) as stream:
                                stream.write(b'{"QMP":{"version":{},"capabilities":[]}}\r\n')
                                for _ in range(expected):
                                    request = json.loads(stream.readline())
                                    requests.append(request)
                                    stream.write(b'{"event":"FIXTURE_ASYNC_EVENT"}\r\n')
                                    value = registers if request['id'] == 'registers' else {}
                                    stream.write(json.dumps({'id': request['id'], 'return': value}).encode() + b'\r\n')
                        except Exception as error:
                            errors.append(error)

                    thread = threading.Thread(target=peer, daemon=True)
                    thread.start()
                    result = boot.qmp_boot_snapshot(path)
                    thread.join(5)
                self.assertFalse(thread.is_alive())
                self.assertFalse(errors)
                self.assertNotIn('error', result)
                self.assertEqual(expected, len(requests))
                if expected == 6:
                    self.assertEqual('x /8wx 0x00000000ff86da00', requests[-2]['arguments']['command-line'])
                    self.assertEqual('xp /8wx 0x00000000ff86da00', requests[-1]['arguments']['command-line'])
                else:
                    self.assertEqual('NO_NUMERIC_PC_IN_REGISTER_RESPONSE',
                                     result['commands']['instruction_words']['error'])
                self.assertTrue(all(x['execute'] in ['qmp_capabilities', 'query-status',
                    'query-cpus-fast', 'human-monitor-command'] for x in requests))

    def test_missing_socket_is_an_observation_error(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertIn('error', boot.qmp_boot_snapshot(Path(folder) / 'missing'))


if __name__ == '__main__':
    unittest.main()
