#!/usr/bin/env bash
# Runs ktlint (+ Compose rules) on the given files, with the versions Spotless uses (gradle/libs.versions.toml).
# Needs a JDK on PATH. The two jars (ktlint CLI from Maven Central, the self-contained Compose rules jar from GitHub releases) are downloaded once into ~/.cache/stayfocus-hooks.
set -euo pipefail
root="$(cd "$(dirname "$0")/../../.." && pwd)"
toml="$root/gradle/libs.versions.toml"
ver() { sed -n "s/^$1 *= *\"\(.*\)\"/\1/p" "$toml" | head -n1; }
ktlint_v="$(ver ktlint)"
compose_v="$(ver composeRulesKtlint)"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/stayfocus-hooks"
mkdir -p "$cache"
ktlint_jar="$cache/ktlint-cli-$ktlint_v-all.jar"
compose_jar="$cache/compose-rules-ktlint-$compose_v.jar"
m2=https://repo1.maven.org/maven2
gh_dl=https://github.com/mrmans0n/compose-rules/releases/download
[ -s "$ktlint_jar" ] || curl -fsSL "$m2/com/pinterest/ktlint/ktlint-cli/$ktlint_v/ktlint-cli-$ktlint_v-all.jar" -o "$ktlint_jar"
[ -s "$compose_jar" ] || curl -fsSL "$gh_dl/v$compose_v/ktlint-compose-$compose_v-all.jar" -o "$compose_jar"
exec java -jar "$ktlint_jar" -R "$compose_jar" --relative "$@"
