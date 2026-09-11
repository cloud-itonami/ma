(ns matching.facts
  "Per-jurisdiction catalog of the rules that govern HOW a target may be
  shown to a prospective buyer -- the spec-basis table the Matching
  Governor checks every `:pairing/screen` and `:introduction/make`
  proposal against ('did the advisor cite an OFFICIAL public source for
  this jurisdiction's approach/confidentiality rules, or did it invent
  one?').

  The M&A analog of `brokerage.facts` in `cloud-itonami-isic-6612`. What
  is catalogued here is deliberately NARROW: not company law, not merger
  control, not tax -- only the rules that constrain the act this actor
  actually performs, which is disclosing that a particular seller is for
  sale to a particular buyer. That act is regulated in every jurisdiction
  below, and by a different instrument in each.

  Coverage is reported HONESTLY (see `coverage`): a jurisdiction not in
  this table has NO spec-basis, full stop. The advisor must not fabricate
  one, and `matching.governor` HARD-holds if it tries. Extending coverage
  is additive -- add one map, cite a real source, done. Never invent a
  jurisdiction's rules to make coverage look bigger.

  Seed values are drawn from each jurisdiction's own regulator or
  ministry (see `:provenance`). They are a STARTING catalog of four, not
  a survey of ~194 jurisdictions.")

(def catalog
  "iso3 -> approach/confidentiality rule map.

  `:required-evidence` is what must be on file before a named target may
  be disclosed to a named buyer in that jurisdiction. `:legal-basis` /
  `:owner-authority` / `:provenance` are the citation the governor
  requires before any pairing proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "中小企業庁 (SME Agency) / 金融庁 (Financial Services Agency)"
          :legal-basis "中小M&Aガイドライン (SME M&A Guidelines) + 金融商品取引法 第166条 (FIEA insider trading)"
          :national-spec "中小M&Aガイドライン 第2章「仲介者・FAの行動指針」(秘密保持・利益相反の開示)"
          :provenance "https://www.chusho.meti.go.jp/ https://www.fsa.go.jp/"
          :required-evidence ["秘密保持契約書 (executed NDA)"
                              "売却意向表明・仲介契約書 (sell-side mandate letter)"
                              "買い手本人確認記録 (buyer verification record)"
                              "開示先同意記録 (seller's consent to this counterparty)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Securities and Exchange Commission (SEC)"
          :legal-basis "Securities Act of 1933 §4(a)(2) + Regulation D Rule 502(c) (prohibition on general solicitation)"
          :national-spec "SEC Rule 502(c) -- a pre-existing substantive relationship is required before an offer may be made"
          :provenance "https://www.sec.gov/"
          :required-evidence ["Executed non-disclosure agreement"
                              "Sell-side engagement letter"
                              "Buyer verification record"
                              "Seller's written consent to approach this counterparty"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Financial Conduct Authority (FCA)"
          :legal-basis "Market Abuse Regulation (MAR) Article 11 (market soundings) as retained in UK law"
          :national-spec "FCA MAR 1 + UK MAR Art. 11 -- the market-sounding regime governs disclosing inside information to a potential buyer"
          :provenance "https://www.fca.org.uk/"
          :required-evidence ["Executed non-disclosure agreement"
                              "Sell-side engagement letter"
                              "Buyer verification record"
                              "Market sounding record / seller consent"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesanstalt für Finanzdienstleistungsaufsicht (BaFin)"
          :legal-basis "Marktmissbrauchsverordnung (MAR) Art. 11 i.V.m. Wertpapierhandelsgesetz (WpHG)"
          :national-spec "BaFin Emittentenleitfaden -- Marktsondierung (market sounding) Anforderungen"
          :provenance "https://www.bafin.de/"
          :required-evidence ["Vertraulichkeitsvereinbarung (executed NDA)"
                              "Verkäufermandat (sell-side engagement letter)"
                              "Käufer-Identifizierungsnachweis (buyer verification record)"
                              "Marktsondierungsprotokoll (market sounding record / seller consent)"]}})

(defn spec-basis
  "The jurisdiction's approach-rule map, or nil -- nil means NO
  spec-basis, and the governor must hold any proposal that tries to
  disclose a target on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never reports a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami/ma R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "A starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `matching.facts/catalog`, "
                 "never fabricate a jurisdiction's rules.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a coll of evidence strings) satisfy every evidence
  item listed for `iso3`? Missing spec-basis -> never satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
