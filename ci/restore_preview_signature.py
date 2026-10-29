#!/usr/bin/env python3
"""Restore an exact own APK from public signature/ZIP metadata, without a signing key."""
import argparse
import base64
import binascii
import hashlib
import json
from pathlib import Path


MAX_APK = 16 * 1024 * 1024
MAX_RECIPE = 1024 * 1024


def restore(base, recipe):
    if recipe.get('schema') != 'GOGOGO_PUBLIC_APK_SIGNATURE_RECIPE_V1':
        raise ValueError('Unsupported signature recipe')
    size = recipe.get('target_size')
    if type(size) is not int or not 0 < size <= MAX_APK or len(base) > MAX_APK:
        raise ValueError('APK size bound')
    if recipe.get('base_size') != len(base) or hashlib.sha256(base).hexdigest() != recipe.get('base_sha256'):
        raise ValueError('Wrong base APK')
    operations = recipe.get('operations')
    if not isinstance(operations, list) or not 1 <= len(operations) <= 2000:
        raise ValueError('Invalid operation count')
    output = bytearray()
    for operation in operations:
        if not isinstance(operation, dict):
            raise ValueError('Invalid operation')
        if set(operation) == {'copy'}:
            span = operation['copy']
            if not isinstance(span, list) or len(span) != 2:
                raise ValueError('Invalid copy range')
            offset, count = span
            if type(offset) is not int or type(count) is not int or offset < 0 or count <= 0 or offset + count > len(base):
                raise ValueError('Copy exceeds the verified base')
            output.extend(base[offset:offset + count])
        elif set(operation) == {'insert_base64'}:
            literal = operation['insert_base64']
            if not isinstance(literal, str) or len(literal) > MAX_RECIPE:
                raise ValueError('Invalid literal')
            try:
                output.extend(base64.b64decode(literal, validate=True))
            except (binascii.Error, ValueError) as error:
                raise ValueError('Invalid base64 literal') from error
        else:
            raise ValueError('Unsupported operation')
        if len(output) > size:
            raise ValueError('Output exceeds the pinned APK size')
    if len(output) != size or hashlib.sha256(output).hexdigest() != recipe.get('target_sha256'):
        raise ValueError('Restored APK does not match the signed candidate')
    return bytes(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True)
    parser.add_argument('--recipe', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    recipe = Path(args.recipe)
    base = Path(args.base)
    if recipe.stat().st_size > MAX_RECIPE or base.stat().st_size > MAX_APK:
        raise ValueError('Input size bound')
    result = restore(base.read_bytes(), json.loads(recipe.read_text()))
    with Path(args.output).open('xb') as stream:
        stream.write(result)
    print(json.dumps({'restored': True, 'bytes': len(result), 'sha256': hashlib.sha256(result).hexdigest(),
                      'private_key_used': False}))


if __name__ == '__main__':
    main()
