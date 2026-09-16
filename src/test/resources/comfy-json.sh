#!/bin/sh
#
# Test double that emits a fixed JSON document regardless of arguments —
# backs the `generateJson` success path (`comfy generate <model> --json`).
#
cat > /dev/null 2>/dev/null
printf '{"code":0,"msg":"ok","data":[{"url":"https://example/asset.png"}]}'
