# etzhayyim-project-ma

Global M&A仲介サービスを **APQC（業務プロセス）**, **ISCO（職能アクター）**, **ISIC（産業分類）** で統合した actor-first プロジェクト。

## 対象スコープ（sales / marketing / matching 含む）

- Sell-side / Buy-side の案件創出（Sales Origination）
- セクター別キャンペーン運用（Marketing）
- 候補企業・買い手のスコアリングとマッチング（Matching）
- DD・Valuation・交渉・契約調整
- Closing と PMI（Post Merger Integration）

## Actorレイヤ

### 1) MA Core
- `org-ma-global-m-a-brokerage-orchestrator-v1`

### 2) APQC process actors
- `svc-apqc-3-2-2-ma-sales-origination-v1`
- `svc-apqc-3-1-1-ma-marketing-campaign-v1`
- `svc-apqc-2-6-4-ma-target-screening-v1`
- `svc-apqc-5-2-3-ma-buyer-matching-v1`

### 3) ISCO role actors
- `psn-isco-1221-ma-marketing-manager-v1`
- `psn-isco-3324-ma-trade-broker-v1`
- `psn-isco-2412-ma-investment-adviser-v1`

### 4) ISIC industry actors
- `org-isic-k-66-662-6619-ma-advisory-v1`
- `org-isic-m-70-702-7020-ma-integration-v1`

## アーキテクチャ原則

- 既存 `etzhayyim-project-apqc` / `etzhayyim-project-open-isco` / `etzhayyim-project-open-isic` の wasm actor 雛形を fork-style で再編。
- すべての actor は最低限 `/health`, `/api/mcp/tools`, `/api/mcp` を実装。
- `project.jsonld` を単一の業務DAGとして管理し、UIは同一モデルを可視化。

## UI

`ui/index.html` で以下を表示:
- End-to-end deal pipeline
- APQC / ISCO / ISIC / MA Core actor mapping
- Sales / Marketing / Matching / PMI 各ステージの担当 actor

## Pulumi / App デプロイ

Pulumi 側のデプロイレイヤは `projects/*/wasm/*/wadm/*.wadm.yaml` と `projects/*/wasm/*/k8s/http-routes.yaml` を自動検出して適用します。
本プロジェクトの各 actor には以下を追加済みです。

- `wadm/ma-mcp.wadm.yaml`
- `k8s/http-routes.yaml`
- `wit/world.wit`
- `kotodama.toml` の `[component]` 設定

実行例:

```bash
cd infra/pulumi
export PULUMI_CONFIG_PASSPHRASE='***'
etzhayyim_WASM_SKIP_AUTOBUILD=1 pulumi up --yes
```
