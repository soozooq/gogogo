#!/usr/bin/env python3
"""Explicit TCG adapter for the pinned CVD host's same-architecture KVM flags."""
import json
import os
from pathlib import Path
import platform
import sys


def software_arguments(arguments):
    result = list(arguments)
    # Replace the original selector. Appending TCG can leave KVM/HVF enabled.
    if any(x in result for x in ['-enable-kvm', '-enable-hvf', '-accel']):
        raise ValueError('Unexpected additional accelerator selector')
    if result.count('-machine') != 1 or result.count('-cpu') != 1:
        raise ValueError('Expected one machine and one CPU from the pinned manager')
    machine = result.index('-machine') + 1
    cpu = result.index('-cpu') + 1
    if result[machine] != 'virt,accel=kvm,gic-version=3,usb=off,dump-guest-core=off' \
            or result[cpu] != 'host':
        raise ValueError('Pinned same-architecture CVD arguments have changed')
    result[machine] = 'virt,accel=tcg,gic-version=2,mte=on,usb=off,dump-guest-core=off'
    result[cpu] = 'max'
    return result


def main():
    if os.environ.get('GITHUB_ACTIONS') != 'true' or platform.system() != 'Linux' \
            or platform.machine() not in ['aarch64', 'arm64']:
        raise SystemExit('This adapter is limited to the dedicated native ARM Linux CI host')
    binary = '/usr/bin/qemu-system-aarch64'
    arguments = sys.argv[1:]
    if arguments in [['-version'], ['--version']]:
        os.execv(binary, [binary] + arguments)
    effective = software_arguments(arguments)
    path = Path(os.environ['CVD_TCG_LAUNCH_RECORD']).resolve()
    temporary = Path(os.environ['RUNNER_TEMP']).resolve()
    if not path.is_relative_to(temporary):
        raise SystemExit('Launch record must stay in this runner temporary directory')
    pending = path.with_name(path.name + '.pending')
    with pending.open('x') as stream:
        json.dump({'pid': os.getpid(), 'binary': binary, 'original_arguments': arguments,
                   'effective_arguments': effective, 'accelerator': 'tcg',
                   'adapter_modifies_guest_images': False}, stream, indent=2)
    # Publish only the completed record. The boot watcher must not read a
    # half-written JSON file, and an existing launch record must not be replaced.
    os.link(pending, path)
    pending.unlink()
    os.execv(binary, [binary] + effective)


if __name__ == '__main__':
    main()
