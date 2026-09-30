#!/usr/bin/env python3
"""Check converted SSO loot with real Minecraft codecs, outside the game server."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--libraries', type=Path, required=True, help='libraries directory from server_smoke.py run')
parser.add_argument('--java', default='/usr/lib/jvm/java-25-openjdk/bin/java')
parser.add_argument('--javac', default='javac')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = root / 'build' / 'qa-loot-codecs'
output.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(str(path.resolve()) for path in [
    *sorted(args.libraries.glob('*.jar')),
    root / 'libs/simple_smithing_overhaul-fabric-2.9.14+26.3.jar',
])
subprocess.run([args.javac, '--release', '25', '-cp', classpath, '-d', str(output),
                str(root / 'src/main/java/com/thenathe/ssopolymer/LootCompatibility.java'),
                str(root / 'qa/LootCodecChecks.java')], check=True)
result = subprocess.run([args.java, '-cp', str(output) + os.pathsep + classpath,
                         'com.thenathe.ssopolymer.LootCodecChecks'], text=True, capture_output=True)
(output / 'result.log').write_text(result.stdout + result.stderr)
print(result.stdout, end='')
print(result.stderr, end='')
raise SystemExit(result.returncode)
