#!/usr/bin/env bash
# Prints, as a JSON array, the Gradle modules to build for the changes since <base>:
# every module with a changed file (markdown excluded), plus every module that depends
# on one of those, directly or transitively. Dependencies are read from each module's
# build.gradle project(':name') references, so a new module needs no change here.
#
# A change to the root build files or to these workflows builds every module.
# Any other change outside the module directories builds nothing.
#
# usage: affected-modules.sh [base-commit]
# With no base, or a base that is not in the checkout (a first push, a force push),
# every module is built.
set -euo pipefail

mapfile -t modules < <(sed -nE "s/^include '([^']+)'.*/\1/p" settings.gradle)

# module names are plain Gradle project names, so they need no JSON escaping
print() {
    local json=''
    for m in "$@"; do json+="${json:+,}\"$m\""; done
    echo "[$json]"
}

base=${1:-}
if [ -z "$base" ] || ! git cat-file -e "$base^{commit}" 2>/dev/null; then
    print "${modules[@]}"
    exit 0
fi

declare -A affected=()
while IFS= read -r file; do
    case "$file" in
        build.gradle|settings.gradle|gradle.properties|gradlew|gradlew.bat|gradle/*|\
        .github/scripts/*|.github/workflows/build-main.yml|.github/workflows/build-pr.yml|.github/workflows/build-modules.yml)
            print "${modules[@]}"
            exit 0
            ;;
        *.md)
            ;;
        */*)
            dir=${file%%/*}
            for m in "${modules[@]}"; do
                if [ "$m" = "$dir" ]; then affected[$m]=1; fi
            done
            ;;
    esac
done < <(git diff --name-only "$base" HEAD)

# add dependents until nothing changes
added=1
while [ $added -eq 1 ]; do
    added=0
    for m in "${modules[@]}"; do
        if [ -n "${affected[$m]:-}" ]; then continue; fi
        # strip // comments so a commented-out dependency does not count
        for dep in $(sed -E 's#//.*##' "$m/build.gradle" | grep -oE "project\([^)]*':[^']+'" | sed -E "s/.*':([^']+)'/\1/"); do
            if [ -n "${affected[$dep]:-}" ]; then
                affected[$m]=1
                added=1
                break
            fi
        done
    done
done

result=()
for m in "${modules[@]}"; do
    if [ -n "${affected[$m]:-}" ]; then result+=("$m"); fi
done
print "${result[@]}"
