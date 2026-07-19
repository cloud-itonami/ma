const pipeline = [
  ['Sales', 'Mandate origination and outreach'],
  ['Marketing', 'Sector campaign and teaser distribution'],
  ['Screening', 'Target fit and risk qualification'],
  ['Matching', 'Buyer-seller shortlist optimization'],
  ['Diligence', 'Financial/legal/operational checks'],
  ['Valuation', 'Pricing, structure, and scenario analysis'],
  ['Negotiation', 'Term sheet and final SPA negotiation'],
  ['Closing', 'Execution and transaction close'],
  ['PMI', 'Post-merger integration execution'],
];

const actors = [
  ['MA Core', 'org-ma-global-m-a-brokerage-orchestrator-v1', 'Sales / Closing / PMI orchestration'],
  ['APQC', 'svc-apqc-3-2-2-ma-sales-origination-v1', 'Sales funnel workflow'],
  ['APQC', 'svc-apqc-3-1-1-ma-marketing-campaign-v1', 'Marketing campaign workflow'],
  ['APQC', 'svc-apqc-2-6-4-ma-target-screening-v1', 'Screening + diligence coordination'],
  ['APQC', 'svc-apqc-5-2-3-ma-buyer-matching-v1', 'Buyer matching process'],
  ['ISCO', 'psn-isco-1221-ma-marketing-manager-v1', 'Marketing strategy owner'],
  ['ISCO', 'psn-isco-3324-ma-trade-broker-v1', 'Matching + negotiation intermediary'],
  ['ISCO', 'psn-isco-2412-ma-investment-adviser-v1', 'Valuation + structuring adviser'],
  ['ISIC', 'org-isic-k-66-662-6619-ma-advisory-v1', 'M&A execution support'],
  ['ISIC', 'org-isic-m-70-702-7020-ma-integration-v1', 'PMI and transformation support'],
];

const stageOwner = [
  ['Sales', 'MA Core + APQC Sales'],
  ['Marketing', 'APQC Marketing + ISCO 1221'],
  ['Matching', 'APQC Matching + ISCO 3324'],
  ['PMI', 'MA Core + ISIC 7020'],
];

document.getElementById('pipeline').innerHTML = pipeline
  .map(([stage, desc]) => `<li><strong>${stage}</strong>: ${desc}</li>`)
  .join('');

document.getElementById('actors').innerHTML = actors
  .map(([framework, actor, role]) => `<tr><td>${framework}</td><td>${actor}</td><td>${role}</td></tr>`)
  .join('');

document.getElementById('stage-owner').innerHTML = stageOwner
  .map(([stage, owner]) => `<tr><td>${stage}</td><td>${owner}</td></tr>`)
  .join('');
