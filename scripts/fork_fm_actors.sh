#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

fork_actor() {
  local src="$1"
  local dst="$2"
  mkdir -p "$(dirname "$dst")"
  rm -rf "$dst"
  rsync -a --exclude 'build' --exclude 'actor.wasm' "$ROOT_DIR/$src/" "$ROOT_DIR/$dst/"
  echo "forked: $src -> $dst"
}

fork_actor "60-apps/etzhayyim-project-apqc/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-0-financial-management-cvaeukqn" \
           "60-apps/etzhayyim-project-ma/appview/forks/apqc-9-0-financial-management"
fork_actor "60-apps/etzhayyim-project-apqc/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-1-2-cost-accounting-hs5myyk4" \
           "60-apps/etzhayyim-project-ma/appview/forks/apqc-9-1-2-cost-accounting"
fork_actor "60-apps/etzhayyim-project-apqc/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-svc-apqc-9-4-accounts-receivable-sq8qt88a" \
           "60-apps/etzhayyim-project-ma/appview/forks/apqc-9-4-accounts-receivable"
fork_actor "60-apps/etzhayyim-project-open-isco/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-1211-treasury-manager-7df4q796" \
           "60-apps/etzhayyim-project-ma/appview/forks/isco-1211-treasury-manager"
fork_actor "60-apps/etzhayyim-project-open-isco/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-financial-and-investment-advisers-hhddguqm" \
           "60-apps/etzhayyim-project-ma/appview/forks/isco-2412-financial-and-investment-advisers"
fork_actor "60-apps/etzhayyim-project-open-isco/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-psn-isco-2412-investment-analyst-vccbzmvf" \
           "60-apps/etzhayyim-project-ma/appview/forks/isco-2412-investment-analyst"
fork_actor "60-apps/etzhayyim-project-open-isic/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6430-qkt6zyvr" \
           "60-apps/etzhayyim-project-ma/appview/forks/isic-6430-trusts-funds"
fork_actor "60-apps/etzhayyim-project-open-isic/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-64-643-6431-sfmtdkzd" \
           "60-apps/etzhayyim-project-ma/appview/forks/isic-6431-mutual-funds"
fork_actor "60-apps/etzhayyim-project-open-isic/appview/etzhayyim-performer-sys-etzhayyim-actors-pba7d22f-org-isic-k-65-653-6530-rveryau2" \
           "60-apps/etzhayyim-project-ma/appview/forks/isic-6530-pension-funding"
