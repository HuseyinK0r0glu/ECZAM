#!/usr/bin/env python3
r"""Supplement the medications catalog with real, well-known US brand-name
drugs pulled live from the OpenFDA drug label API (https://open.fda.gov/) —
the CLAUDE.md-approved national-DB integration for the MVP.

This is a one-off/occasional supplement to CatalogSeedRunner's Stage A bulk
import (the 20k+-row Tip-Atlası Turkish dataset): a small, curated batch of
recognizable US brands, useful for demoing/testing against catalog data that
isn't Turkish-only. It only ever reads real data from OpenFDA — it never
fabricates a name, barcode, or leaflet section.

Usage:
    python3 backend/scripts/import_openfda_brands.py > medications.csv
    # then, with the db container up (docker compose up -d db):
    cat <<'SQL' | docker compose exec -T db psql -U eczam -d eczam
    \copy medications(name, generic_name, manufacturer, barcode, active_ingredient, leaflet_raw, leaflet_sections, leaflet_hash) FROM STDIN WITH (FORMAT csv, NULL '')
    SQL
    # (append the CSV content to that same stdin stream, or pipe both together)

Idempotency note: `barcode` is UNIQUE on the medications table but this
script does not check for existing rows itself (COPY has no ON CONFLICT) — if
re-running, first delete or filter out barcodes already present, e.g.:
    docker compose exec -T db psql -U eczam -d eczam -c \
        "SELECT barcode FROM medications WHERE barcode = ANY(ARRAY[...])"
"""
import csv
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# Real, well-known brand names to look up — NOT barcodes/NDCs (those are
# looked up live from OpenFDA below, never guessed).
BRANDS = [
    "Advil", "Tylenol", "Lipitor", "Zoloft", "Prozac", "Amoxicillin",
    "Metformin", "Lisinopril", "Synthroid", "Nexium", "Ventolin",
    "Zithromax", "Norvasc", "Crestor", "Xanax", "Amoxil",
    "Prilosec", "Zocor", "Cipro",
]

MIN_LEAFLET_SECTIONS = 2  # skip a result if fewer than this many real sections exist


def fetch(brand: str) -> dict | None:
    q = urllib.parse.quote(f'openfda.brand_name:"{brand}"')
    url = f"https://api.fda.gov/drug/label.json?search={q}&limit=3"
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as e:
        if e.code != 404:  # 404 just means "no matches for this brand"
            print(f"  fetch failed for {brand}: {e}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  fetch failed for {brand}: {e}", file=sys.stderr)
        return None


def first(v, default=None):
    return str(v[0]) if isinstance(v, list) and v else default


def main() -> None:
    rows = []
    seen_barcodes: set[str] = set()
    seen_names: set[str] = set()

    for brand in BRANDS:
        data = fetch(brand)
        time.sleep(0.3)  # be polite to the public API
        if not data or "results" not in data:
            continue
        for r in data["results"]:
            of = r.get("openfda", {}) or {}
            name = first(of.get("brand_name"))
            if not name or name.upper() in seen_names:
                continue
            ndc_list = of.get("product_ndc") or []
            if not ndc_list or ndc_list[0] in seen_barcodes:
                continue
            barcode = ndc_list[0]

            dosage = first(r.get("dosage_and_administration"))
            side_effects = first(r.get("adverse_reactions")) or first(r.get("warnings"))
            contraindications = first(r.get("contraindications")) or first(r.get("do_not_use"))
            storage = first(r.get("how_supplied_storage_and_handling")) or first(r.get("storage_and_handling"))
            interactions = first(r.get("drug_interactions"))

            if sum(1 for x in (dosage, side_effects, contraindications, storage, interactions) if x) < MIN_LEAFLET_SECTIONS:
                continue

            leaflet_sections = {
                "dosage": dosage,
                "side_effects": side_effects,
                "contraindications": contraindications,
                "storage": storage,
                "interactions": interactions,
                "missed_dose": dosage,  # OpenFDA labels have no distinct "missed dose" section
            }
            leaflet_raw = "\n\n".join(
                f"{k}: {v}"
                for k, v in (
                    ("dosage_and_administration", first(r.get("dosage_and_administration"))),
                    ("indications_and_usage", first(r.get("indications_and_usage"))),
                    ("adverse_reactions", first(r.get("adverse_reactions"))),
                    ("warnings", first(r.get("warnings"))),
                    ("contraindications", first(r.get("contraindications"))),
                    ("do_not_use", first(r.get("do_not_use"))),
                    ("drug_interactions", first(r.get("drug_interactions"))),
                    ("how_supplied_storage_and_handling", first(r.get("how_supplied_storage_and_handling"))),
                )
                if v
            )
            if not leaflet_raw:
                continue

            generic = first(of.get("generic_name"))
            seen_barcodes.add(barcode)
            seen_names.add(name.upper())
            rows.append([
                name[:255],
                (generic or "")[:255],
                (first(of.get("manufacturer_name")) or "")[:255],
                barcode[:100],
                (generic or "")[:512],
                leaflet_raw,
                json.dumps(leaflet_sections),
                hashlib.sha256(leaflet_raw.encode("utf-8")).hexdigest(),
            ])

    print(f"Collected {len(rows)} real, distinct medications with real leaflet content", file=sys.stderr)
    w = csv.writer(sys.stdout)
    w.writerows(rows)


if __name__ == "__main__":
    main()
