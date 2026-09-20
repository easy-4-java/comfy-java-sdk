#!/bin/sh
set -eu

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

git fetch --no-tags origin   feature/1.0.x:refs/remotes/origin/feature/1.0.x   feature/2.0.x:refs/remotes/origin/feature/2.0.x   feature/3.0.x:refs/remotes/origin/feature/3.0.x

extract_branch() {
  ref="$1"
  dir="$2"
  mkdir -p "$TMP/$dir"
  git archive --format=tar "$ref" src/main/java src/test/java src/test/resources     | tar -xf - -C "$TMP/$dir"

  find "$TMP/$dir" -type f -name '*.java' -print | while IFS= read -r file; do
    sed       -e 's/tools\.jackson/com.fasterxml.jackson/g'       -e 's/\.properties()/\.fields()/g'       "$file" > "$file.norm"
    mv "$file.norm" "$file"
  done
}

extract_branch origin/feature/1.0.x b1
extract_branch origin/feature/2.0.x b2
extract_branch origin/feature/3.0.x b3

diff -ru "$TMP/b1/src" "$TMP/b2/src"
diff -ru "$TMP/b2/src" "$TMP/b3/src"

echo "three-branch source/test/fixture parity: OK"
