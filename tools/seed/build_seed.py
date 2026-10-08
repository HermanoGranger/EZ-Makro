#!/usr/bin/env python3
"""Builds the committed seed asset `core/data/src/main/assets/seed/food_seed_v1.json`.

Run:
    python3 tools/seed/build_seed.py            # print report + write JSON
    python3 tools/seed/build_seed.py --check    # report only, no write

Pipeline: BLS workbook -> selection rules (selection.py) -> validated JSON.
Nutrient values are rounded to 2 decimals as decided; missing values ("-")
become null.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from bls_extract import extract
from selection import CATEGORIES, HANDPICKED, PREP_MARKERS, display_name, is_vegan

REPO = Path(__file__).resolve().parents[2]
ASSET = REPO / "core/data/src/main/assets/seed/food_seed_v1.json"
SEED_VERSION = 1
SOURCE = {
    "name": "Bundeslebensmittelschlüssel (BLS), Version 4.0",
    "publisher": "Max Rubner-Institut",
    "license": "CC BY 4.0",
    "citation": (
        "Max Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), "
        "Version 4.0 — Deutsche Nährstoffdatenbank. Karlsruhe."
    ),
    "doi": "10.25826/Data20251217-134202-0",
}


def _has_marker(name: str, keyword: str) -> bool:
    lowered = name.lower()
    return any(m in lowered and m not in keyword for m in PREP_MARKERS)


def _number(raw: str) -> float | None:
    raw = raw.strip()
    if not raw or raw == "-":
        return None
    try:
        return round(float(raw), 2)
    except ValueError:
        return None


def select(
    foods: list[dict[str, str]],
) -> tuple[list[dict], list[str], list[str]]:
    """Returns (selected foods with category, warnings, errors)."""
    by_code = {f["code"]: f for f in foods}
    selected: dict[str, tuple[str, dict[str, str]]] = {}
    warnings: list[str] = []
    errors: list[str] = []

    # 1) handpicked codes are claimed first and never overridden
    for label, codes in HANDPICKED.items():
        for code in codes:
            food = by_code.get(code)
            if food is None:
                errors.append(f"{label}: Code {code} fehlt in BLS 4.0")
                continue
            if not is_vegan(food["name"]):
                errors.append(f"{label}: {code} '{food['name']}' ist nicht vegan")
                continue
            if code in selected:
                errors.append(f"{label}: {code} doppelt vergeben")
                continue
            selected[code] = (label, food)

    # 2) keyword budget: best (shortest, unprepared) candidate per keyword
    for label, groups, keywords in CATEGORIES:
        pool = [f for f in foods if f["code"][0] in groups and is_vegan(f["name"])]
        for keyword, max_claims in keywords:
            hits = [f for f in pool if keyword in f["name"].lower()]
            if not hits:
                warnings.append(f"{label}: kein Treffer für '{keyword}'")
                continue
            fresh = sorted(
                (f for f in hits if f["code"] not in selected),
                key=lambda f: (_has_marker(f["name"], keyword), len(f["name"]), f["name"]),
            )
            for food in fresh[:max_claims]:
                selected[food["code"]] = (label, food)

    return (
        [dict(food, category=label) for label, food in selected.values()],
        warnings,
        errors,
    )


def validate(foods: list[dict]) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    seen_names: dict[str, str] = {}
    for food in foods:
        fid = food["id"]
        if fid in seen:
            errors.append(f"duplicate id {fid}")
        seen.add(fid)
        if not food["name"].strip():
            errors.append(f"{fid}: empty name")
        if food["name"] in seen_names:
            errors.append(f"{fid}: Name doppelt zu {seen_names[food['name']]}")
        seen_names[food["name"]] = fid
        if food["kcal"] is None:
            errors.append(f"{fid}: missing kcal")
        if food["barcode"] is not None:
            errors.append(f"{fid}: seeds must not carry a barcode")
        for key in ("proteinG", "fatG", "carbsG", "fiberG"):
            value = food[key]
            if value is not None and value < 0:
                errors.append(f"{fid}: negative {key}")
    return errors


def to_seed_food(food: dict) -> dict:
    return {
        "id": f"bls:{food['code']}",
        "name": display_name(food["code"], food["name"]),
        "barcode": None,
        "kcal": _number(food["kcal"]),
        "proteinG": _number(food["protein_g"]),
        "fatG": _number(food["fat_g"]),
        "carbsG": _number(food["carbs_g"]),
        "fiberG": _number(food["fiber_g"]),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="report only")
    parser.add_argument("--list", action="store_true", help="print every selection")
    args = parser.parse_args()

    foods, warnings, errors = select(extract())
    seed_foods = [to_seed_food(f) for f in foods]
    errors += validate(seed_foods)

    current: dict = {}
    if ASSET.exists():
        current = json.loads(ASSET.read_text(encoding="utf-8"))

    counts: dict[str, int] = {}
    for food in foods:
        counts[food["category"]] = counts.get(food["category"], 0) + 1
    order = list(dict.fromkeys(list(HANDPICKED) + [label for label, _g, _k in CATEGORIES]))
    for label in order:
        print(f"{counts.get(label, 0):>4}  {label}")
    print(f"{len(seed_foods):>4}  Gesamt")
    if args.list:
        current_label = None
        for food in foods:
            if food["category"] != current_label:
                current_label = food["category"]
                print(f"\n== {current_label}")
            nutr = (
                f"{food['kcal']:>5} kcal  P {food['protein_g']}  F {food['fat_g']}"
                f"  KH {food['carbs_g']}  E {food['fiber_g']}"
            )
            print(f"  {food['code']}  {display_name(food['code'], food['name'])}  | {nutr}")
        print()
    if warnings:
        print("\nWarnungen:")
        for warning in warnings:
            print(f"  ! {warning}")
    if errors:
        print("\nFEHLER:")
        for error in errors:
            print(f"  x {error}")
        sys.exit(1)

    added = [f["id"] for f in seed_foods if f["id"] not in {g["id"] for g in current.get("foods", [])}]
    removed = [g["id"] for g in current.get("foods", []) if g["id"] not in {f["id"] for f in seed_foods}]
    if current:
        print(f"\ngegenüber Asset: +{len(added)} / -{len(removed)}")
        for fid in added[:20]:
            print(f"  + {fid}")
        for fid in removed[:20]:
            print(f"  - {fid}")

    if args.check:
        return

    document = {
        "seedVersion": SEED_VERSION,
        "source": SOURCE,
        "foods": seed_foods,
    }
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    ASSET.write_text(
        json.dumps(document, ensure_ascii=False, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    print(f"\n{ASSET.relative_to(REPO)} geschrieben ({len(seed_foods)} Foods)")


if __name__ == "__main__":
    main()
