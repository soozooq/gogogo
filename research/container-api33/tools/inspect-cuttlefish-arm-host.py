#!/usr/bin/env python3
"""Record ARM Linux host prerequisites. Never boots Android or starts apps."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--output', required=True)
p.add_argument('--prepare-host', action='store_true',
               help='Load modules and grant this user device access on an ephemeral GitHub runner only')
a = p.parse_args()
out = Path(a.output).resolve()
out.mkdir(parents=True, exist_ok=False)
report = {
    'scope': 'ARM_LINUX_HOST_PREREQUISITES_ONLY_NOT_ANDROID_OR_APP_COMPATIBILITY',
    'state': 'PENDING', 'probe_completed': False, 'eligible_for_boot_attempt': False,
    'guest_boot_tested': False, 'apps_tested': False, 'wechat_tested': False,
    'host_system': platform.system(), 'host_arch': platform.machine(),
    'kernel_release': platform.release(), 'uid': os.getuid(),
    'script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
}

def command(args, label, timeout=20):
    r = subprocess.run(args, capture_output=True, timeout=timeout)
    (out / (label + '.txt')).write_bytes(r.stdout + r.stderr)
    return r

def inspect():
    if report['host_system'] != 'Linux' or report['host_arch'] not in ['aarch64', 'arm64']:
        report.update(state='HOST_ARCH_NOT_ARM_LINUX', probe_completed=True)
        return
    if a.prepare_host:
        if os.environ.get('GITHUB_ACTIONS') != 'true':
            raise RuntimeError('Host changes are limited to the ephemeral GitHub workflow')
        report['module_load_exit'] = {}
        for module in ['vhost_vsock', 'tun']:
            report['module_load_exit'][module] = command(
                ['sudo', '-n', 'modprobe', module], 'module-' + module).returncode
        misc = {}
        for line in Path('/proc/misc').read_text().splitlines():
            fields = line.split()
            if len(fields) == 2 and fields[0].isdigit():
                misc[fields[1]] = int(fields[0])
        for path, driver in [('/dev/vhost-vsock', 'vhost-vsock'), ('/dev/net/tun', 'tun')]:
            # Create a node only for an actually registered kernel driver.
            if not Path(path).exists() and driver in misc:
                command(['sudo', '-n', 'mkdir', '-p', str(Path(path).parent)], 'mkdir-' + driver)
                command(['sudo', '-n', 'mknod', '-m', '0660', path, 'c', '10',
                         str(misc[driver])], 'node-' + driver)
            if Path(path).is_char_device():
                command(['sudo', '-n', 'chgrp', str(os.getgid()), path], 'group-' + driver)
                command(['sudo', '-n', 'chmod', 'g+rw', path], 'mode-' + driver)
    report['kvm_device_exists'] = Path('/dev/kvm').is_char_device()
    report['disk_free_bytes'] = shutil.disk_usage(out).free
    report['memory_bytes'] = os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES')
    qemu = shutil.which('qemu-system-aarch64')
    qemu_ok = False
    if qemu:
        binary = Path(qemu).resolve()
        with binary.open('rb') as stream:
            header = stream.read(20)
        machine = int.from_bytes(header[18:20], 'little') if header[:4] == b'\x7fELF' else None
        digest = hashlib.sha256()
        with binary.open('rb') as stream:
            while block := stream.read(1024 * 1024):
                digest.update(block)
        version = command([qemu, '--version'], 'qemu-version')
        accelerators = command([qemu, '-accel', 'help'], 'qemu-accelerators')
        tcg = accelerators.returncode == 0 and bool(re.search(rb'\btcg\b', accelerators.stdout))
        report['qemu'] = {'binary': qemu, 'sha256': digest.hexdigest(),
                          'elf_machine': machine, 'version_exit': version.returncode,
                          'accelerators_exit': accelerators.returncode, 'tcg_advertised': tcg}
        qemu_ok = machine == 183 and version.returncode == 0 and tcg
    else:
        report['qemu'] = {'state': 'NOT_INSTALLED'}
    report['device_access'] = {}
    for path, name in [('/dev/vhost-vsock', 'vhost_vsock'), ('/dev/net/tun', 'tun')]:
        result = {'char_device': Path(path).is_char_device(), 'open_read_write': False}
        fd = None
        try:
            fd = os.open(path, os.O_RDWR | os.O_CLOEXEC)
            result['open_read_write'] = True
            if name == 'vhost_vsock':
                features = bytearray(8)
                # VHOST_GET_FEATURES reads capabilities; it does not start a guest.
                fcntl.ioctl(fd, 0x8008AF00, features, True)
                result.update(get_features_succeeded=True,
                              features_hex=hex(int.from_bytes(features, 'little')))
        except OSError as error:
            result.update(error_errno=error.errno, error=str(error))
        finally:
            if fd is not None:
                os.close(fd)
        report['device_access'][name] = result
    try:
        with socket.socket(socket.AF_VSOCK, socket.SOCK_STREAM) as probe:
            probe.bind((socket.VMADDR_CID_ANY, socket.VMADDR_PORT_ANY))
        report['vsock_socket_bind'] = {'succeeded': True}
    except OSError as error:
        report['vsock_socket_bind'] = {'succeeded': False, 'error_errno': error.errno,
                                      'error': str(error)}
    devices = report['device_access']
    eligible = qemu_ok and devices['vhost_vsock'].get('get_features_succeeded') is True \
        and devices['tun']['open_read_write'] and report['vsock_socket_bind']['succeeded']
    report.update(probe_completed=True, eligible_for_boot_attempt=eligible,
                  state='HOST_PREREQUISITES_PRESENT' if eligible else 'HOST_PREREQUISITES_UNMET')

try:
    inspect()
except Exception as error:
    report.update(state='HOST_PROBE_ERROR', error=type(error).__name__ + ': ' + str(error)[:1500])
finally:
    (out / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
# A completed diagnostic is successful collection, never Android compatibility.
# Any future boot stage must explicitly require eligible_for_boot_attempt == true.
raise SystemExit(0 if report['probe_completed'] else 1)
