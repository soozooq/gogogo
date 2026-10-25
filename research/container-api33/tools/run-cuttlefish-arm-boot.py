#!/usr/bin/env python3
"""Qualify one owned ARM CI VM; optionally observe system-installed originals."""
import argparse
import grp
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import socket
import subprocess
import sys
import tarfile
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def private_tap_network(routes):
    existing = [ipaddress.ip_network(x['dst'], strict=False) for x in routes
                if x.get('dst', 'default') not in ['default', '0.0.0.0/0']]
    for candidate in ['192.168.252.0/30', '192.168.253.0/30', '172.30.255.0/30']:
        proposed = ipaddress.ip_network(candidate)
        if not any(proposed.overlaps(network) for network in existing):
            return proposed
    raise RuntimeError('No free dedicated private TAP subnet; existing routes preserved')


def owned_qemu_active(record):
    process = Path('/proc') / str(record['pid'])
    try:
        return (process / 'exe').resolve(strict=True) == Path('/usr/bin/qemu-system-aarch64').resolve() \
            and (process / 'cmdline').read_bytes().split(b'\0')[:-1] == [record['binary'].encode()] \
                + [x.encode() for x in record['effective_arguments']] \
            and (process / 'stat').read_text().rsplit(')', 1)[1].split()[0] != 'Z'
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return False


def boot_log_failure(data):
    if b'Kernel panic - not syncing:' in data:
        return 'GUEST_KERNEL_PANIC'
    if re.search(rb'AndroidRuntime:\s+\*\*\* FATAL EXCEPTION IN SYSTEM PROCESS: main', data):
        return 'ANDROID_SYSTEM_PROCESS_JAVA_FATAL'
    return None


def qmp_boot_snapshot(path):
    """Observe this instance only. Commands never stop/resume/reset/change the VM."""
    result = {'read_only': True, 'commands': {}}
    commands = [('status', {'execute': 'query-status'}),
                ('cpus', {'execute': 'query-cpus-fast'}),
                ('registers', {'execute': 'human-monitor-command',
                               'arguments': {'command-line': 'info registers'}}),
                ('instruction_words', {'execute': 'human-monitor-command'}),
                ('physical_instruction_words', {'execute': 'human-monitor-command'})]
    try:
        with socket.socket(socket.AF_UNIX) as connection:
            connection.settimeout(2)
            connection.connect(str(path))
            with connection.makefile('rwb', buffering=0) as stream:
                greeting = json.loads(stream.readline(65536))
                if 'QMP' not in greeting:
                    raise RuntimeError('Expected the QMP greeting')
                result['greeting'] = greeting
                for name, request in [('capabilities', {'execute': 'qmp_capabilities'}), *commands]:
                    if name in ['instruction_words', 'physical_instruction_words']:
                        # This ARM HMP backend does not expose a $pc expression.
                        # Accept only a complete numeric PC from our register dump;
                        # no free-form monitor command or address is accepted.
                        registers = result['commands'].get('registers', {}).get('return')
                        pc = re.search(r'\bPC=([0-9a-fA-F]{16})(?![0-9a-fA-F])', registers) \
                            if isinstance(registers, str) else None
                        if pc is None:
                            result['commands'][name] = {'error': 'NO_NUMERIC_PC_IN_REGISTER_RESPONSE'}
                            continue
                        # Distribution QEMU may omit ARM's disassembler too.
                        # Read eight raw words; decode offline against the input.
                        prefix = 'xp' if name == 'physical_instruction_words' else 'x'
                        request['arguments'] = {'command-line': prefix + ' /8wx 0x' + pc[1]}
                    request['id'] = name
                    stream.write(json.dumps(request).encode() + b'\r\n')
                    for _ in range(12):
                        response = json.loads(stream.readline(65536))
                        if response.get('id') == name:
                            result['commands'][name] = response
                            break
                    else:
                        raise RuntimeError('No matching QMP response id')
    except (OSError, ValueError, RuntimeError) as error:
        result['error'] = type(error).__name__ + ': ' + str(error)[:500]
    return result


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--workdir', required=True)
    p.add_argument('--host-report', required=True)
    p.add_argument('--boot-seconds', type=int, choices=[300, 1200, 2400], default=2400,
                   help='300 is a bounded bootloader diagnostic; neither duration implies OS success')
    p.add_argument('--original-baselines', action='store_true',
                   help='After qualification only: ordinary original welcome/privacy UI; no container or accounts')
    p.add_argument('--container-apks', help='Exact same-CI own host/test pair; also requires --original-baselines')
    p.add_argument('--owned-location-check', action='store_true',
                   help='After successful target startup controls, run independent own Guest contracts')
    args = p.parse_args()
    if args.container_apks and not args.original_baselines:
        p.error('--container-apks requires the original ordinary controls first')
    if args.owned_location_check and not args.container_apks:
        p.error('--owned-location-check requires verified same-run container APKs')
    if os.environ.get('GITHUB_ACTIONS') != 'true' or platform.system() != 'Linux' \
            or platform.machine() not in ['aarch64', 'arm64']:
        raise SystemExit('Use the dedicated native ARM Linux workflow, never a phone')
    work = Path(args.workdir).resolve()
    if not work.is_relative_to(Path(os.environ['RUNNER_TEMP']).resolve()):
        raise SystemExit('Dedicated runner temporary directory required')
    work.mkdir(parents=True, exist_ok=False)
    out = work / 'evidence'
    out.mkdir()
    spec_dir = Path(__file__).resolve().parents[1]
    spec = json.loads((spec_dir / 'cuttlefish-inputs.json').read_text())
    host_report = json.loads(Path(args.host_report).read_text())
    report = {'scope': 'CUTTLEFISH_ARM64_SYSTEM_BOOT_ONLY_NOT_APP_OR_VIVO',
              'build_id': spec['build_id'], 'state': 'PENDING', 'passed': False,
              'research_commit': subprocess.check_output(['git', '-C', str(spec_dir), 'rev-parse', 'HEAD'], text=True).strip(),
              'boot_completed': False, 'input_archives_modified': False,
              'boot_wait_limit_seconds': args.boot_seconds,
              'host_and_qemu_binaries_modified': False,
              'official_assembly_generates_runtime_disks': True,
              'guest_userdata_expected_to_change': True,
              'apps_tested': False, 'wechat_tested': False,
              'host_report': host_report,
              'source_sha256': {str(x.relative_to(spec_dir)): digest(x) for x in [
                  Path(__file__), spec_dir / 'tools/cuttlefish-tcg-qemu.py',
                  spec_dir / 'tools/fetch-cuttlefish-inputs.py', spec_dir / 'cuttlefish-inputs.json',
                  spec_dir / 'tools/cuttlefish_target_baselines.py',
                  spec_dir / 'tools/cuttlefish_owned_location.py',
                  spec_dir / 'tools/ci_owned_apks.py',
                  spec_dir / 'tools/inspect-cuttlefish-arm-host.py',
                  spec_dir / 'evidence/official-target-apk-readiness.json']}}
    report['original_baselines_requested'] = args.original_baselines
    report['software_hw_timeout_multiplier_requested'] = 50
    if args.original_baselines:
        report['scope'] = 'CUTTLEFISH_SYSTEM_AND_INSTALLED_ORIGINAL_STARTUP_NOT_CONTAINER_LOCATION_OR_VIVO'
    report['container_startups_requested'] = bool(args.container_apks)
    report['owned_location_requested'] = args.owned_location_check
    report['owned_location_passed'] = False
    report['owned_guest_location_tested'] = False
    if args.container_apks:
        report['scope'] = 'CUTTLEFISH_SYSTEM_AND_ORIGINAL_CONTAINER_STARTUP_NOT_LOCATION_OR_VIVO'
    runner = None
    start = time.monotonic()
    process_log = None
    environment = dict(os.environ)
    launch_record = out / 'qemu-launch.json'

    def command(argv, name, timeout=60, check=True, **kwargs):
        kwargs.setdefault('env', environment)
        try:
            result = subprocess.run(argv, capture_output=True, timeout=timeout, **kwargs)
        except subprocess.TimeoutExpired as error:
            (out / (name + '.txt')).write_bytes((error.stdout or b'') + (error.stderr or b'')
                                               + b'\nCOMMAND_TIMEOUT\n')
            raise
        (out / (name + '.txt')).write_bytes(result.stdout + result.stderr)
        if check and result.returncode:
            raise RuntimeError(name + ' failed with exit ' + str(result.returncode))
        return result

    try:
        if args.container_apks:
            from ci_owned_apks import verify
            own_apks = Path(args.container_apks).resolve()
            temporary = Path(os.environ['RUNNER_TEMP']).resolve()
            if own_apks == temporary or not own_apks.is_relative_to(temporary):
                raise RuntimeError('Own-build transport must stay in this runner temporary directory')
            own_manifest = verify(own_apks, spec_dir, report['research_commit'])
            report['own_apk_transport_manifest_sha256'] = digest(own_apks / 'manifest.json')
        if not host_report.get('eligible_for_boot_attempt'):
            raise RuntimeError('Actual host prerequisites did not pass')
        if min(host_report.get('cpu_count') or 0, host_report.get('affinity_cpu_count') or 0) < 4:
            raise RuntimeError('This large-app software attempt requires four measured host CPUs')
        report['requested_guest_cpus'] = 4
        actual_groups = set(os.getgroups()) | {os.getegid()}
        if os.getuid() != host_report['uid'] or os.getuid() == 0:
            raise RuntimeError('Run as the same ordinary host probe user, not root')
        report['host_package_groups_active'] = {name: grp.getgrnam(name).gr_gid in actual_groups
                                                for name in ['cvdnetwork', 'kvm']}
        if not all(report['host_package_groups_active'].values()):
            raise RuntimeError('Required real host package memberships are not active')
        firmware = next((x for x in [Path('/usr/share/qemu/efi-virtio.rom'),
                                    Path('/usr/lib/ipxe/qemu/efi-virtio.rom'),
                                    Path('/usr/share/ipxe/qemu/efi-virtio.rom')] if x.is_file()), None)
        if firmware is None:
            raise RuntimeError('Install the distribution ipxe-qemu firmware; efi-virtio.rom is missing')
        report['network_boot_rom'] = {'path': str(firmware.resolve()), 'sha256': digest(firmware)}
        if shutil.disk_usage(work).free < 40 * 1024**3:
            raise RuntimeError('At least 40 GiB free is required before downloading/extracting')
        downloads = work / 'downloads'
        # Stream progress to the job; errors must not include short-lived download URLs.
        command([sys.executable, str(spec_dir / 'tools/fetch-cuttlefish-inputs.py'),
                 '--output', str(downloads)], 'artifact-download', timeout=900)
        report['artifacts'] = spec['artifacts']
        project = next(x for x in ET.parse(downloads / 'manifest_16596636.xml').getroot().iter('project')
                       if x.get('path') == 'device/google/cuttlefish')
        if project.get('revision') != spec['cuttlefish_source_revision']:
            raise RuntimeError('Matching host source manifest revision changed')
        host = work / 'host'
        host.mkdir()
        with tarfile.open(downloads / 'cvd-host_package.tar.gz') as archive:
            archive.extractall(host, filter='data')
        for name, expected in spec['host_binary_sha256'].items():
            binary = host / name
            with binary.open('rb') as stream:
                header = stream.read(20)
            if digest(binary) != expected or header[:4] != b'\x7fELF' \
                    or int.from_bytes(header[18:20], 'little') != spec['host_elf_machine']:
                raise RuntimeError('Matching host binary identity/architecture rejected: ' + name)
        report['host_binaries_verified'] = True
        # Bionic's host tzcode requires all three roots. Use the actual timezone
        # database shipped in this host archive, not an empty placeholder.
        android_data = work / 'host-android-data'
        android_data.mkdir(mode=0o700)
        timezone_root = work / 'host-tzdata'
        timezone_file = timezone_root / 'etc/tz/tzdata'
        timezone_file.parent.mkdir(parents=True)
        shutil.copyfile(host / 'usr/share/zoneinfo/tzdata', timezone_file)
        environment.update(ANDROID_HOST_OUT=str(host), ANDROID_DATA=str(android_data),
                           ANDROID_ROOT=str(host), ANDROID_TZDATA_ROOT=str(timezone_root),
                           CUTTLEFISH_INSTANCE='1')
        report['host_timezone_sha256'] = digest(timezone_file)
        report['host_runtime_roots'] = {k: environment[k] for k in [
            'ANDROID_HOST_OUT', 'ANDROID_DATA', 'ANDROID_ROOT', 'ANDROID_TZDATA_ROOT']}
        command([str(host / 'bin/avbtool'), 'version'], 'host-avbtool-preflight')
        command([str(host / 'bin/adb'), 'version'], 'host-adb-preflight')
        report['host_tool_preflights_passed'] = True
        images = work / 'images'
        images.mkdir()
        with zipfile.ZipFile(downloads / spec['artifacts'][1]['name']) as archive:
            expected_files = {'android-info.txt', 'boot.img', 'bootloader', 'init_boot.img',
                              'userdata.img', 'vbmeta.img', 'vbmeta_system.img',
                              'vendor_boot.img', 'super.img'}
            if set(archive.namelist()) != expected_files or sum(
                    x.file_size for x in archive.infolist()) != spec['image_unpacked_bytes']:
                raise RuntimeError('Pinned image archive layout changed')
            archive.extractall(images)
        if (images / 'android-info.txt').read_text() != 'config=phone\ngfxstream=supported\n':
            raise RuntimeError('Unexpected guest configuration')
        helper = work / 'capability_query.py'
        with urllib.request.urlopen(spec['host_capability_helper']['url'], timeout=45) as response:
            helper.write_bytes(response.read(16 * 1024))
        if digest(helper) != spec['host_capability_helper']['sha256']:
            raise RuntimeError('Official host helper digest mismatch')
        helper_target = Path('/usr/lib/cuttlefish-common/bin/capability_query.py')
        if helper_target.exists() and digest(helper_target) != digest(helper):
            raise RuntimeError('Existing host helper differs; preserved')
        command(['sudo', '-n', 'install', '-D', '-m', '0755', str(helper), str(helper_target)],
                'install-official-helper')
        for interface in ['cvd-mtap-01', 'cvd-wtap-01', 'cvd-etap-01']:
            command(['sudo', '-n', 'ip', 'tuntap', 'add', 'dev', interface,
                     'mode', 'tap', 'user', str(os.getuid())], 'create-' + interface)
            command(['sudo', '-n', 'ip', 'link', 'set', interface, 'up'], 'up-' + interface)
        # The actual config_server source accepts an IPv4/broadcast on the mobile
        # TAP itself. Choose a free private /30, without changing existing routes.
        routes = json.loads(subprocess.check_output(
            ['ip', '-j', '-4', 'route', 'show', 'table', 'all'], env=environment))
        network = private_tap_network(routes)
        gateway = str(network.network_address + 1)
        command(['sudo', '-n', 'ip', 'address', 'add', gateway + '/30', 'broadcast',
                 str(network.broadcast_address), 'dev', 'cvd-mtap-01'], 'mobile-tap-ipv4')
        report['network_scope'] = 'Dedicated local IPv4/broadcast on TAP; no DHCP/NAT or Internet proof'
        report['mobile_tap_ipv4'] = {'gateway': gateway, 'prefix': 30,
                                     'broadcast': str(network.broadcast_address)}
        wrapper = work / 'qemu'
        wrapper.mkdir()
        wrapper_binary = wrapper / 'qemu-system-aarch64'
        shutil.copyfile(spec_dir / 'tools/cuttlefish-tcg-qemu.py', wrapper_binary)
        wrapper_binary.chmod(0o755)
        instance = work / 'instance'
        environment.update(ANDROID_PRODUCT_OUT=str(images),
                           CVD_TCG_LAUNCH_RECORD=str(launch_record))
        # Explicit paths replace the generic documentation's HOME override.
        flags = ['--system_image_dir=' + str(images), '--instance_dir=' + str(instance),
                 '--assembly_dir=' + str(work / 'assembly'), '--vm_manager=qemu_cli',
                 '--qemu_binary_dir=' + str(wrapper), '--gpu_mode=guest_swiftshader',
                 '--cpus=4', '--memory_mb=3072', '--start_webrtc=false', '--enable_audio=false',
                 '--start_gnss_proxy=false', '--guest_enforce_security=true',
                 '--report_anonymous_usage_stats=n', '--daemon=false', '--use_allocd=false',
                 '--vhost_net=false', '--restart_subprocesses=false',
                 # The upstream non-native TCG path sets this same multiplier.
                 # Our same-architecture adapter selects TCG after assembly, so
                 # request it explicitly through the supported bootconfig flag.
                 '--extra_bootconfig_args=androidboot.hw_timeout_multiplier=50']
        report['assembly_flags'] = flags
        files = '\n'.join(str(x) for x in sorted(images.iterdir())) + '\n'
        assembled = command([str(host / 'bin/assemble_cvd')] + flags, 'assemble',
                            input=files.encode(), env=environment, cwd=host, timeout=300)
        # Normal documented stages: assemble_cvd output is run_cvd input.
        process_log = (out / 'run-cvd.txt').open('wb')
        runner = subprocess.Popen([str(host / 'bin/run_cvd')], stdin=subprocess.PIPE,
                                  stdout=process_log, stderr=subprocess.STDOUT,
                                  env=environment, cwd=host, start_new_session=True)
        runner.stdin.write(assembled.stdout)
        runner.stdin.close()
        adb = host / 'bin/adb'
        serial = '127.0.0.1:6520'
        report.update(state='WAITING_FOR_BOOT', serial=serial)
        last_notice = last_adb = last_crash = last_monitor = last_console = 0
        deadline = time.monotonic() + args.boot_seconds

        def shell(arguments, timeout=12):
            return subprocess.run([str(adb), '-s', serial, 'shell'] + arguments,
                                  capture_output=True, timeout=timeout, env=environment)

        while time.monotonic() < deadline:
            if runner.poll() is not None:
                report.update(state='CVD_EXITED_BEFORE_BOOT', launcher_exit=runner.returncode)
                break
            if launch_record.is_file():
                launched = json.loads(launch_record.read_text())
                report['qemu_launch_requested'] = True
                qemu_process = Path('/proc') / str(launched['pid'])
                try:
                    state = (qemu_process / 'stat').read_text().rsplit(')', 1)[1].split()[0]
                    if state == 'Z':
                        report['state'] = 'QEMU_EXITED_BEFORE_BOOT'
                        break
                    binary = (qemu_process / 'exe').resolve(strict=True)
                    if binary == Path('/usr/bin/qemu-system-aarch64').resolve():
                        argv = (qemu_process / 'cmdline').read_bytes().split(b'\0')[:-1]
                        if argv != [launched['binary'].encode()] + [x.encode() for x in launched['effective_arguments']]:
                            raise RuntimeError('Live QEMU arguments differ from the owned launch record')
                        if 'qemu_live_process_verified' not in report:
                            if digest(binary) != host_report['qemu']['sha256']:
                                raise RuntimeError('Live QEMU executable differs from actual host probe')
                            report['qemu_live_process_verified'] = True
                except (FileNotFoundError, ProcessLookupError):
                    report['state'] = 'QEMU_EXITED_BEFORE_BOOT'
                    break
            text = (out / 'run-cvd.txt').read_bytes()[-1024 * 1024:]
            if b'Could not open device config' in text:
                report['state'] = 'HOST_CONFIG_SERVER_FAILED_BEFORE_BOOT'
                break
            now = time.monotonic()
            if now - last_console >= 30:
                for name in ['kernel.log', 'logcat']:
                    console = instance / 'instances/cvd-1/logs' / name
                    if console.is_file():
                        with console.open('rb') as stream:
                            stream.seek(0, os.SEEK_END)
                            stream.seek(max(0, stream.tell() - 1024 * 1024))
                            data = stream.read()
                        failure = boot_log_failure(data)
                        if failure:
                            report.update(state=failure, fatal_observed_in=name)
                            (out / 'boot-fatal-evidence.txt').write_bytes(data)
                            break
                last_console = now
                if report['state'] != 'WAITING_FOR_BOOT':
                    break
            if report.get('qemu_live_process_verified') and now - last_monitor >= 60:
                snapshot = qmp_boot_snapshot(instance / 'instances/cvd-1/internal/qemu_monitor.sock')
                snapshot['elapsed_seconds'] = round(now - start, 3)
                try:
                    stat = (qemu_process / 'stat').read_text().rsplit(')', 1)[1].split()
                    snapshot['qemu_cpu_ticks'] = {'user': int(stat[11]), 'system': int(stat[12]),
                                                 'clock_ticks_per_second': os.sysconf('SC_CLK_TCK')}
                except OSError as error:
                    snapshot['cpu_state_error'] = type(error).__name__
                with (out / 'qmp-boot-snapshots.jsonl').open('a') as stream:
                    stream.write(json.dumps(snapshot) + '\n')
                report['qmp_snapshot_count'] = report.get('qmp_snapshot_count', 0) + 1
                last_monitor = now
            if now - last_notice >= 30:
                print('Waiting for real Cuttlefish ARM64 boot:', round(now - start), flush=True)
                last_notice = now
            if now - last_adb >= 15:
                try:
                    command([str(adb), 'connect', serial], 'adb-connect', timeout=8, check=False)
                except subprocess.TimeoutExpired:
                    report['adb_connect_timeout_count'] = report.get('adb_connect_timeout_count', 0) + 1
                last_adb = now
            try:
                boot_property = shell(['getprop', 'sys.boot_completed'])
                if boot_property.returncode == 0 and 'early_hw_timeout_multiplier' not in report:
                    timeout_property = shell(['getprop', 'ro.hw_timeout_multiplier'])
                    if timeout_property.returncode == 0:
                        report['early_hw_timeout_multiplier'] = timeout_property.stdout.decode().strip()
                if boot_property.returncode == 0 and boot_property.stdout.strip() == b'1':
                    report['boot_completed'] = True
                    break
                if now - last_crash >= 30:
                    crash = subprocess.run([str(adb), '-s', serial, 'logcat', '-d', '-b', 'crash'],
                                           capture_output=True, timeout=12, env=environment).stdout
                    (out / 'platform-crash-log.txt').write_bytes(crash)
                    pids = sorted({int(x) for x in re.findall(
                        rb'pid:\s*(\d+),[^\n]*>>>\s*system_server\s*<<<', crash)})
                    report['system_server_crash_pids'] = pids
                    if len(pids) >= 2:
                        report['state'] = 'SYSTEM_SERVER_NATIVE_CRASH_LOOP'
                        break
                    last_crash = now
            except subprocess.TimeoutExpired:
                pass
            time.sleep(3)
        if report['boot_completed']:
            command([str(adb), '-s', serial, 'unroot'], 'adb-unroot', check=False)
            for _ in range(30):
                try:
                    if shell(['id', '-u']).stdout.strip() == b'2000':
                        break
                except subprocess.TimeoutExpired:
                    pass
                time.sleep(1)
            properties = {key: shell(['getprop', key]).stdout.decode().strip() for key in [
                'ro.build.version.sdk', 'ro.build.version.incremental', 'ro.build.fingerprint',
                'ro.product.name', 'ro.hardware', 'ro.product.cpu.abi', 'ro.product.cpu.abilist',
                'ro.dalvik.vm.native.bridge', 'ro.hw_timeout_multiplier', 'sys.boot_completed']}
            report.update(properties=properties, kernel_arch=shell(['uname', '-m']).stdout.decode().strip(),
                          shell_uid=shell(['id', '-u']).stdout.decode().strip(),
                          selinux=shell(['getenforce']).stdout.decode().strip(),
                          guest_cpu_online=shell(['cat', '/sys/devices/system/cpu/online']).stdout.decode().strip())
            launched = json.loads(launch_record.read_text())
            qemu_process = Path('/proc') / str(launched['pid'])
            actual_binary = (qemu_process / 'exe').resolve(strict=True)
            report['qemu'] = {'pid': launched['pid'], 'binary': str(actual_binary),
                              'sha256': digest(actual_binary),
                              'arguments_match_launch_record': (qemu_process / 'cmdline').read_bytes().split(b'\0')[:-1]
                                  == [launched['binary'].encode()] + [x.encode() for x in launched['effective_arguments']]}
            abis = properties['ro.product.cpu.abilist'].split(',')
            report['passed'] = properties['ro.build.version.sdk'] == '33' \
                and properties['ro.build.version.incremental'] == spec['build_id'] \
                and properties['ro.product.name'] == 'aosp_cf_arm64_phone' \
                and properties['ro.hardware'] == 'cutf_cvm' \
                and properties['ro.product.cpu.abi'] == 'arm64-v8a' \
                and 'arm64-v8a' in abis and not any('x86' in x for x in abis) \
                and properties['ro.dalvik.vm.native.bridge'] in ['', '0'] \
                and properties['ro.hw_timeout_multiplier'] == '50' \
                and report['kernel_arch'] == 'aarch64' and report['shell_uid'] == '2000' \
                and report['selinux'] == 'Enforcing' \
                and report['guest_cpu_online'] == '0-3' \
                and actual_binary == Path('/usr/bin/qemu-system-aarch64').resolve() \
                and report['qemu']['sha256'] == host_report['qemu']['sha256'] \
                and report['qemu']['arguments_match_launch_record']
            report['state'] = 'QUALIFIED_BOOT' if report['passed'] else 'BOOT_IDENTITY_REJECTED'
            report['system_boot_passed'] = report['passed']
            if report['passed'] and args.original_baselines:
                from cuttlefish_target_baselines import run_original_baselines
                container = None
                if args.container_apks:
                    from cuttlefish_target_baselines import run_container_control
                    container = {'scope': 'CVD_ARM64_CONTAINER_STARTUP_ONLY_NOT_LOCATION_OR_VIVO',
                        'results': {}, 'passed': False,
                        'own_apk_manifest_sha256': digest(own_apks / 'manifest.json'),
                        'own_apks': own_manifest['apks'], 'target_apks_uploaded_or_modified': False,
                        'accounts_used': False, 'location_business_tested': False}

                    def after_target(key, original):
                        container['results'][key] = run_container_control(work, spec_dir, report,
                            launched, environment, own_apks, key, original)
                        container['passed'] = set(container['results']) == {'wechat', 'douyin'} \
                            and all(x.get('passed') is True for x in container['results'].values())
                        folder = out / 'container-target-startups'
                        folder.mkdir(exist_ok=True)
                        (folder / 'summary.json').write_text(json.dumps(container, indent=2) + '\n')

                baseline = run_original_baselines(work, spec_dir, report, launched, environment,
                    after_target=after_target if args.container_apks else None,
                    ui_apks=own_apks if args.container_apks else None)
                report['original_baselines_passed'] = baseline['passed']
                report['apps_tested'] = any(x.get('launch_requested') for x in baseline['results'].values())
                report['wechat_tested'] = baseline['results']['wechat'].get('launch_requested', False)
                report['location_business_tested'] = False
                if args.container_apks:
                    # Each successful control has already been followed immediately.
                    report['container_startups_passed'] = container['passed']
                    if args.owned_location_check and baseline['passed'] and container['passed']:
                        from cuttlefish_owned_location import run_owned_location_control
                        expected_pid = container['results']['douyin']['environment_after_container']['system_pid']
                        owned = run_owned_location_control(work, spec_dir, report, launched,
                            environment, own_apks, expected_pid)
                        report['owned_location_passed'] = owned['passed']
                        report['owned_guest_location_tested'] = 'location' in owned.get('cases', {})
                    report['passed'] = baseline['passed'] and container['passed']
                    report['state'] = 'QUALIFIED_BOOT_AND_CONTAINER_UI' if report['passed'] \
                        else 'ORIGINAL_OR_CONTAINER_STARTUP_NOT_CONFIRMED'
                    if args.owned_location_check:
                        report['scope'] = 'CUTTLEFISH_SYSTEM_CONTAINER_UI_AND_OWNED_LOCATION_NOT_TARGET_LOCATION_OR_VIVO'
                        report['passed'] = report['passed'] and report['owned_location_passed']
                        report['state'] = 'QUALIFIED_BOOT_CONTAINER_UI_AND_OWNED_LOCATION' if report['passed'] \
                            else 'PREREQUISITE_OR_OWNED_CONTRACT_NOT_CONFIRMED'
                else:
                    report['passed'] = baseline['passed']
                    report['state'] = 'QUALIFIED_BOOT_AND_ORIGINAL_UI' if baseline['passed'] \
                        else 'ORIGINAL_BASELINE_NOT_CONFIRMED'
        elif report['state'] == 'WAITING_FOR_BOOT':
            report['state'] = 'BOOT_TIMEOUT'
    except Exception as error:
        report.update(state='PREPARATION_OR_PROBE_ERROR', passed=False,
                      error=type(error).__name__ + ': ' + str(error)[:1000])
    finally:
        if runner and runner.poll() is None:
            try:
                os.killpg(runner.pid, signal.SIGTERM)
                runner.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(runner.pid, signal.SIGKILL)
                runner.wait(timeout=10)
            except ProcessLookupError:
                pass
        if launch_record.is_file():
            record = json.loads(launch_record.read_text())
            pidfd = None
            try:
                pidfd = os.pidfd_open(record['pid'])
                if owned_qemu_active(record):
                    signal.pidfd_send_signal(pidfd, signal.SIGTERM)
                    stop_deadline = time.monotonic() + 5
                    while owned_qemu_active(record) and time.monotonic() < stop_deadline:
                        time.sleep(0.2)
                    if owned_qemu_active(record):
                        signal.pidfd_send_signal(pidfd, signal.SIGKILL)
                        time.sleep(0.5)
            except ProcessLookupError:
                pass
            finally:
                if pidfd is not None:
                    os.close(pidfd)
            report['qemu_process_stopped'] = not owned_qemu_active(record)
            if not report['qemu_process_stopped']:
                report.update(passed=False, cleanup_error='Owned QEMU process did not stop')
        if process_log:
            process_log.close()
        # Preserve only this instance's text logs; images and APKs are not uploaded.
        for path in (work / 'instance').rglob('*'):
            if path.is_file() and not path.is_symlink() and (path.suffix == '.log' or path.name == 'logcat'):
                destination = out / 'instance-logs' / path.relative_to(work / 'instance')
                destination.parent.mkdir(parents=True, exist_ok=True)
                with path.open('rb') as source:
                    source.seek(0, os.SEEK_END)
                    source.seek(max(0, source.tell() - 4 * 1024 * 1024))
                    destination.write_bytes(source.read())
        report['observed_seconds'] = round(time.monotonic() - start, 3)
        report['requested_stages_passed'] = report['passed'] and (not args.original_baselines
                                                                or report.get('original_baselines_passed') is True)
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({k: report[k] for k in ['state', 'passed', 'requested_stages_passed', 'boot_completed',
                                               'apps_tested', 'original_baselines_passed', 'observed_seconds',
                                               'error'] if k in report}, indent=2), flush=True)
    return 0 if report['requested_stages_passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
