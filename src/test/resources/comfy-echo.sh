#!/bin/sh
#
# Test double for the `comfy` CLI: prints every argument verbatim, joined by
# single spaces on one line, and exits with 0 — the same output contract as
# /bin/echo but portable across GNU/BSD coreutils.
#
cat > /dev/null 2>/dev/null
printf '%s\n' "$*"
