#!/usr/bin/env python3
"""One dedicated native ARM CI instance. System boot only; never an app result."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
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


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--workdir', required=True)
    p.add_argument('--host-report', required=True)
    args = p.parse_args()
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
              'boot_completed': False, 'guest_images_modified': False,
              'apps_tested': False, 'wechat_tested': False,
              'host_report': host_report,
              'source_sha256': {str(x.relative_to(spec_dir)): digest(x) for x in [
                  Path(__file__), spec_dir / 'tools/cuttlefish-tcg-qemu.py',
                  spec_dir / 'tools/fetch-cuttlefish-inputs.py', spec_dir / 'cuttlefish-inputs.json']}}
    runner = None
    start = time.monotonic()
    process_log = None

    def command(argv, name, timeout=60, check=True, **kwargs):
        result = subprocess.run(argv, capture_output=True, timeout=timeout, **kwargs)
        (out / (name + '.txt')).write_bytes(result.stdout + result.stderr)
        if check and result.returncode:
            raise RuntimeError(name + ' failed with exit ' + str(result.returncode))
        return result

    try:
        if not host_report.get('eligible_for_boot_attempt'):
            raise RuntimeError('Actual host prerequisites did not pass')
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
        report['network_scope'] = 'Dedicated TAP devices up; no DHCP/NAT configured or Internet proof'
        wrapper = work / 'qemu'
        wrapper.mkdir()
        wrapper_binary = wrapper / 'qemu-system-aarch64'
        shutil.copyfile(spec_dir / 'tools/cuttlefish-tcg-qemu.py', wrapper_binary)
        wrapper_binary.chmod(0o755)
        launch_record = out / 'qemu-launch.json'
        instance = work / 'instance'
        environment = dict(os.environ, ANDROID_HOST_OUT=str(host),
                           ANDROID_PRODUCT_OUT=str(images),
                           CVD_TCG_LAUNCH_RECORD=str(launch_record),
                           CUTTLEFISH_INSTANCE='1')
        # Explicit paths replace the generic documentation's HOME override.
        flags = ['--system_image_dir=' + str(images), '--instance_dir=' + str(instance),
                 '--assembly_dir=' + str(work / 'assembly'), '--vm_manager=qemu_cli',
                 '--qemu_binary_dir=' + str(wrapper), '--gpu_mode=guest_swiftshader',
                 '--cpus=2', '--memory_mb=3072', '--start_webrtc=false', '--enable_audio=false',
                 '--start_gnss_proxy=false', '--guest_enforce_security=true',
                 '--report_anonymous_usage_stats=n', '--daemon=false', '--use_allocd=false',
                 '--vhost_net=false', '--restart_subprocesses=false']
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
        last_notice = last_adb = last_crash = 0
        deadline = time.monotonic() + 1200

        def shell(arguments, timeout=12):
            return subprocess.run([str(adb), '-s', serial, 'shell'] + arguments,
                                  capture_output=True, timeout=timeout)

        while time.monotonic() < deadline:
            if runner.poll() is not None:
                report.update(state='CVD_EXITED_BEFORE_BOOT', launcher_exit=runner.returncode)
                break
            now = time.monotonic()
            if now - last_notice >= 30:
                print('Waiting for real Cuttlefish ARM64 boot:', round(now - start), flush=True)
                last_notice = now
            if now - last_adb >= 15:
                command([str(adb), 'connect', serial], 'adb-connect', check=False)
                last_adb = now
            try:
                if shell(['getprop', 'sys.boot_completed']).stdout.strip() == b'1':
                    report['boot_completed'] = True
                    break
                if now - last_crash >= 30:
                    crash = subprocess.run([str(adb), '-s', serial, 'logcat', '-d', '-b', 'crash'],
                                           capture_output=True, timeout=12).stdout
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
                'ro.dalvik.vm.native.bridge', 'sys.boot_completed']}
            report.update(properties=properties, kernel_arch=shell(['uname', '-m']).stdout.decode().strip(),
                          shell_uid=shell(['id', '-u']).stdout.decode().strip(),
                          selinux=shell(['getenforce']).stdout.decode().strip())
            launched = json.loads(launch_record.read_text())
            qemu_process = Path('/proc') / str(launched['pid'])
            actual_binary = (qemu_process / 'exe').resolve(strict=True)
            report['qemu'] = {'pid': launched['pid'], 'binary': str(actual_binary),
                              'sha256': digest(actual_binary),
                              'arguments_match_launch_record': (qemu_process / 'cmdline').read_bytes().split(b'\0')[:-1]
                                  == [str(actual_binary).encode()] + [x.encode() for x in launched['effective_arguments']]}
            abis = properties['ro.product.cpu.abilist'].split(',')
            report['passed'] = properties['ro.build.version.sdk'] == '33' \
                and properties['ro.build.version.incremental'] == spec['build_id'] \
                and properties['ro.product.name'] == 'aosp_cf_arm64_phone' \
                and properties['ro.hardware'] == 'cutf_cvm' \
                and properties['ro.product.cpu.abi'] == 'arm64-v8a' \
                and 'arm64-v8a' in abis and not any('x86' in x for x in abis) \
                and properties['ro.dalvik.vm.native.bridge'] in ['', '0'] \
                and report['kernel_arch'] == 'aarch64' and report['shell_uid'] == '2000' \
                and report['selinux'] == 'Enforcing' \
                and actual_binary == Path('/usr/bin/qemu-system-aarch64').resolve() \
                and report['qemu']['sha256'] == host_report['qemu']['sha256'] \
                and report['qemu']['arguments_match_launch_record']
            report['state'] = 'QUALIFIED_BOOT' if report['passed'] else 'BOOT_IDENTITY_REJECTED'
        elif report['state'] == 'WAITING_FOR_BOOT':
            report['state'] = 'BOOT_TIMEOUT'
    except Exception as error:
        report.update(state='PREPARATION_OR_PROBE_ERROR', error=type(error).__name__ + ': ' + str(error)[:1000])
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
        (out / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({k: report[k] for k in ['state', 'passed', 'boot_completed', 'apps_tested', 'observed_seconds']}, indent=2), flush=True)
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
