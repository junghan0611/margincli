#!/usr/bin/env bash
# abductcli — 에이전트 친화적 마진 분석 CLI
set -euo pipefail
cd "$(dirname "$0")"

case "${1:-help}" in
  build)
    echo "Building abductcli uber-jar..."
    clj -T:build uber
    ;;
  test)
    clj -M:test
    ;;
  *)
    clj -M:run "$@"
    ;;
esac
