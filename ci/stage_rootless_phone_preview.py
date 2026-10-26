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


SOURCE = 'ff12e8d3cf2dde2884333bd78515727d17c284b3'
MAIN_CERT = '6eec4ddd865b85482bd5a97ef4ceaa14dded5c3a6cdcbffd4eccb78c54eff081'
HOST_CERT = '25bfd6e22d3a73813eee8f71734ad1916ccd0ae1bb80b83c44e947ec2355fa7b'
HOST_SHA = '2308c0bac2400f0da6ea781a098ee2c9cfd245a089c0f2837c53ab89a8f9f3f1'
PROFILE_SHA = '00ee9ae07b12d9c7acc42c9b2952982d7ae439aeb9e174ccf0ba7395d3d27ca8'


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
    args = parser.parse_args()
    source, sdk, output = Path(args.source).resolve(), Path(args.sdk).resolve(), Path(args.output).resolve()
    revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != SOURCE or subprocess.check_output(['git', '-C', str(source), 'status', '--porcelain'], text=True).strip():
        raise ValueError('Main build must use the clean fixed source checkout')
    profile = source / 'docs/build-profiles/main-preview-version.init.gradle'
    if digest(profile) != PROFILE_SHA:
        raise ValueError('Main metadata profile differs')
    candidates = list((source / 'app/build/outputs/apk/debug').glob('*2.4.32-container-preview*arm64-v8a*debug.apk'))
    if len(candidates) != 1 or digest(Path(args.host)) != HOST_SHA:
        raise ValueError('Unexpected main output or restored host')
    host_entries = non_signature_entries(Path(args.host))
    if len(host_entries) != 415 or host_entries != non_signature_entries(Path(args.base_host)):
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
    names = {'main': 'Tianwaitian-2.4.32-container-preview-arm64.apk',
             'host': 'Tianwaitian-Original-Preview-ff12e8d3.apk'}
    shutil.copyfile(candidates[0], output / names['main'])
    shutil.copyfile(args.host, output / names['host'])
    apks = {'main': inspect(output / names['main'], sdk, 'com.soozooq.gogogo.test',
                           '2.4.32-container-preview', 2400100, MAIN_CERT),
            'host': inspect(output / names['host'], sdk, 'com.soozooq.tianwaitian.containerresearch',
                           '0.1-research', 1, HOST_CERT)}
    for role in apks:
        apks[role]['file'] = names[role]
    # Cloud fixture signing can change the main APK hash; record the actual bytes built here.
    receipt = {'schema': 'GOGOGO_CI_PHONE_PREVIEW_STAGING_V1', 'source_commit': SOURCE,
        'delivery_run_id': int(args.delivery_run_id), 'apks': apks, 'metadata_profile_sha256': PROFILE_SHA,
        'main_checks': {'junit': counts, 'lint_error_fatal_count': severe},
        'host_original_ci_run': 40292596352, 'host_ci_non_signature_entries_equal': 415,
        'private_key_used_or_included': False, 'original_target_apks_included': False,
        'native_gate': 'NOT_CHECKED_BY_THIS_DELIVERY_JOB', 'phone_and_target_business': 'NOT_VERIFIED',
        'published': False}
    (output / 'candidate-manifest.json').write_text(json.dumps(receipt, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(''.join(apks[k]['sha256'] + '  ' + names[k] + '\n' for k in ['main', 'host']))
    (output / 'PHONE-README.txt').write_text(
        '天外天 2.4.32 · 原版应用预览候选\n\n'
        '本打包任务只准备交付草稿，不判断原生、vivo或目标定位业务成功。\n'
        '微信发送／实时共享、抖音无线及海外定位尚未验收。\n\n'
        '候选发布后的流程：安装两份APK → 地图选点 → 首次导入原版并授权精确定位 → 确认并打开。\n'
        '当前支持微信8.0.79／3200、抖音40.8.0／400801完整原版；需自行登录，原应用保留。\n'
        '需要反馈时一键复制反馈，无需逐项实验。主页使用免腾讯Key的MapLibre／OpenFreeMap；联网地图待实机结果。\n\n'
        '源码：https://github.com/soozooq/gogogo/tree/' + SOURCE + '\n'
        '许可、对应来源与可替换库材料随预览APK保留于assets/research-notices。\n')
    shutil.copyfile(profile, output / profile.name)
    if {p.name for p in output.glob('*.apk')} != set(names.values()):
        raise ValueError('Only the two owned APKs may be staged')
    print(json.dumps({'staged': True, 'delivery_run_id': receipt['delivery_run_id'], 'apks': apks,
                      'published': False}))


if __name__ == '__main__':
    main()
