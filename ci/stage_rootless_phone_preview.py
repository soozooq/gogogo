#!/usr/bin/env python3
"""Stage only the two owned phone APKs; never publishes a release or handles a private key."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile


SOURCE = 'c8052d318bed3cf9c0e37ddc864abea4cc74c816'
MAIN_CERT = '6eec4ddd865b85482bd5a97ef4ceaa14dded5c3a6cdcbffd4eccb78c54eff081'
HOST_CERT = '25bfd6e22d3a73813eee8f71734ad1916ccd0ae1bb80b83c44e947ec2355fa7b'
HOST_SHA = '4d1f072585c69e446bfb72f5cefa57ee6a221f557f5a44ed1e903576b1470b95'
PROFILE_SHA = '67f15d335ae34ac7245dec166806773eaf83f772df0ae2b5fe7c7a9851c41ed4'


def staging_spec(config=None):
    """Keep the historical package reproducible; newer packages use frozen pins."""
    if config is None:
        return {'runtime_commit': SOURCE, 'profile_sha256': PROFILE_SHA,
                'metadata_profile_path': 'docs/build-profiles/main-preview-2.4.35.init.gradle',
                'host_ci_non_signature_entries': 415, 'qualification_run_id': 40595256036,
                'expected_apks': {
                    'main': {'file': 'Tianwaitian-2.4.35-container-preview-arm64.apk',
                             'package': 'com.soozooq.gogogo.test', 'version_name': '2.4.35-container-preview',
                             'version_code': 2400103, 'certificate_sha256': MAIN_CERT},
                    'host': {'file': 'Tianwaitian-Original-Preview-c8052d31.apk',
                             'package': 'com.soozooq.tianwaitian.containerresearch', 'version_name': '0.4-preview',
                             'version_code': 4, 'certificate_sha256': HOST_CERT, 'sha256': HOST_SHA}}}
    profile = config['metadata_profile_path']
    if set(config['expected_apks']) != {'main', 'host'} \
            or config['expected_apks']['main']['package'] != 'com.soozooq.gogogo.test' \
            or config['expected_apks']['host']['package'] != 'com.soozooq.tianwaitian.containerresearch':
        raise ValueError('Only the two owned update packages are allowed')
    version = config['expected_apks']['main']['version_name']
    if not re.fullmatch(r'2\.4\.[0-9]+-container-preview', version) \
            or profile != 'docs/build-profiles/main-preview-' + version.removesuffix('-container-preview') + '.init.gradle' \
            or type(config['host_ci_non_signature_entries']) is not int \
            or not 1 <= config['host_ci_non_signature_entries'] <= 10000:
        raise ValueError('Frozen profile or host entry count differs')
    if not re.fullmatch('[0-9a-f]{40}', config['runtime_commit']) \
            or not re.fullmatch('[0-9a-f]{64}', config['profile_sha256']) \
            or config['expected_apks']['main']['certificate_sha256'] != MAIN_CERT \
            or config['expected_apks']['host']['certificate_sha256'] != HOST_CERT:
        raise ValueError('Frozen source, profile or update certificate differs')
    return config


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def non_signature_entries(path):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError('Corrupt APK ZIP')
        return {n: archive.read(n) for n in archive.namelist()
                if not re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', n, re.I)}


def inspect(path, sdk, package, version, code, certificate):
    ns = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.fromstring(subprocess.check_output([
        str(sdk / 'cmdline-tools/latest/bin/apkanalyzer'), 'manifest', 'print', str(path)]))
    if manifest.get('package') != package or manifest.get(ns + 'versionName') != version \
            or manifest.get(ns + 'versionCode') != str(code):
        raise ValueError('APK package/version differs')
    signing = subprocess.check_output([str(sdk / 'build-tools/33.0.2/apksigner'),
        'verify', '--print-certs', str(path)], text=True)
    actual = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', signing)
    if actual is None or actual[1] != certificate:
        raise ValueError('APK signing certificate differs')
    subprocess.run([str(sdk / 'build-tools/33.0.2/zipalign'), '-c', '-p', '4', str(path)], check=True)
    if package == 'com.soozooq.gogogo.test':
        with zipfile.ZipFile(path) as archive:
            abis = {name.split('/')[1] for name in archive.namelist()
                    if re.fullmatch(r'lib/[^/]+/[^/]+\.so', name)}
        if abis != {'arm64-v8a'}:
            raise ValueError('Phone main must contain only ARM64 native libraries; rendering APKs cannot be shipped')
    return {'package': package, 'version_name': version, 'version_code': code,
            'bytes': path.stat().st_size, 'sha256': digest(path), 'certificate_sha256': certificate,
            'signature_and_alignment_verified': True,
            'debuggable': manifest.find('application').get(ns + 'debuggable') == 'true'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', required=True)
    parser.add_argument('--host', required=True)
    parser.add_argument('--base-host', required=True)
    parser.add_argument('--sdk', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--delivery-run-id', required=True)
    parser.add_argument('--config', help='Frozen policy for a newer preview; omitted preserves historical 2.4.35')
    args = parser.parse_args()
    config = staging_spec(json.loads(Path(args.config).read_text()) if args.config else None)
    source_commit = config['runtime_commit']
    expected = config['expected_apks']
    source, sdk, output = Path(args.source).resolve(), Path(args.sdk).resolve(), Path(args.output).resolve()
    revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != source_commit or subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).strip():
        raise ValueError('Main build must use the clean fixed source checkout')
    profile = source / config['metadata_profile_path']
    if digest(profile) != config['profile_sha256']:
        raise ValueError('Main metadata profile differs')
    candidates = list((source / 'app/build/outputs/apk/debug').glob('*' + expected['main']['version_name'] + '*arm64-v8a*debug.apk'))
    if len(candidates) != 1 or digest(Path(args.host)) != expected['host']['sha256']:
        raise ValueError('Unexpected main output or restored host')
    host_entries = non_signature_entries(Path(args.host))
    if len(host_entries) != config['host_ci_non_signature_entries'] or host_entries != non_signature_entries(Path(args.base_host)):
        raise ValueError('Restored host must retain every CI non-signature entry')
    counts = {name: 0 for name in ['tests', 'failures', 'errors', 'skipped']}
    for path in (source / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        for name in counts:
            counts[name] += int(suite.get(name, '0'))
    severe = sum(n.get('severity') in ['Error', 'Fatal'] for n in ET.parse(
        source / 'app/build/reports/lint-results-debug.xml').iter('issue'))
    if counts != {'tests': 447, 'failures': 0, 'errors': 0, 'skipped': 0} or severe:
        raise ValueError('Main checks did not pass')
    output.mkdir(parents=True, exist_ok=False)
    names = {role: value['file'] for role, value in expected.items()}
    if set(names) != {'main', 'host'} or any(Path(name).name != name or not name.endswith('.apk') for name in names.values()):
        raise ValueError('Only two flat owned APK filenames are allowed')
    shutil.copyfile(candidates[0], output / names['main'])
    shutil.copyfile(args.host, output / names['host'])
    apks = {role: inspect(output / names[role], sdk, pin['package'], pin['version_name'],
                         pin['version_code'], pin['certificate_sha256']) for role, pin in expected.items()}
    for role in apks:
        apks[role]['file'] = names[role]
    # Cloud fixture signing can change the main APK hash; record the actual bytes built here.
    receipt = {'schema': 'GOGOGO_CI_PHONE_PREVIEW_STAGING_V1', 'source_commit': source_commit,
        'delivery_run_id': int(args.delivery_run_id), 'apks': apks, 'metadata_profile_sha256': PROFILE_SHA,
        'main_checks': {'junit': counts, 'lint_error_fatal_count': severe},
        'host_original_ci_run': config['qualification_run_id'],
        'host_ci_non_signature_entries_equal': config['host_ci_non_signature_entries'],
        'private_key_used_or_included': False, 'original_target_apks_included': False,
        'native_gate': 'NOT_CHECKED_BY_THIS_DELIVERY_JOB', 'phone_and_target_business': 'NOT_VERIFIED',
        'published': False}
    (output / 'candidate-manifest.json').write_text(json.dumps(receipt, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(apks[k]['sha256'] + '  ' + names[k] + '\n' for k in ['main', 'host']))
    (output / 'PHONE-README.txt').write_text(
        '天外天 ' + expected['main']['version_name'] + ' · 原版应用预览候选\n\n'
        '请从项目入口提供的已核验下载使用。本文件为打包说明，不单独表示云端或真机验收通过。\n\n'
        '使用流程：更新两份APK → 地图选点 → 首次导入原版并授权精确定位 → 确认并打开。\n'
        '当前支持微信8.0.79／3200、抖音40.8.0／400801完整原版；需自行登录，原应用保留。\n'
        '结束时点击“停止预览”。正常使用遇到问题，点击“复制反馈”，只选择已经看到的结果即可，无需逐项实验。\n'
        '反馈汇总己方状态，不含坐标，不自动上传；该状态不单独证明目标应用采用了位置。\n\n'
        '位置仍由选点合成。微信发送／实时共享、抖音无线及海外定位、vivo效果尚未验收。\n'
        '地图使用MapLibre／OpenFreeMap，源码及实际界面截图见项目入口。\n\n'
        '源码：https://github.com/soozooq/gogogo/tree/' + source_commit + '\n'
        '许可、对应来源与可替换库材料随预览APK保留于assets/research-notices。\n')
    shutil.copyfile(profile, output / profile.name)
    if {p.name for p in output.glob('*.apk')} != set(names.values()):
        raise ValueError('Only the two owned APKs may be staged')
    print(json.dumps({'staged': True, 'delivery_run_id': receipt['delivery_run_id'], 'apks': apks,
                      'published': False}))


if __name__ == '__main__':
    main()
