#!/usr/bin/env python3
"""Extracts the nutrient columns EZMakro needs from the BLS 4.0 workbook.

Stdlib only (zipfile + xml.etree) — no openpyxl, so the script runs on a bare
Python 3. The BLS workbook is a single sheet: one row of column headers
followed by one row per food.

Columns (located by header prefix, not by fixed index):
    BLS Code | Lebensmittelbezeichnung | ENERCC kcal | PROT625 protein
    FAT fat | CHO carbs | FIBT fibre   (all per 100 g)
"""

from __future__ import annotations

import csv
import io
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
CACHE = Path(__file__).resolve().parent / "cache"
XLSX = CACHE / "BLS_4_0_2025_DE" / "BLS_4_0_Daten_2025_DE.xlsx"
ZIP_URL = "https://blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip"
CSV_OUT = CACHE / "bls_foods.csv"

# header prefix -> output field
NUTRIENT_HEADERS = {
    "ENERCC ": "kcal",
    "PROT625 ": "protein_g",
    "FAT ": "fat_g",
    "CHO ": "carbs_g",
    "FIBT ": "fiber_g",
}
NAME_HEADER = "Lebensmittelbezeichnung"
CODE_HEADER = "BLS Code"


def _column_letters(ref: str) -> str:
    return re.match(r"[A-Z]+", ref).group(0)


def _load_shared_strings(z: zipfile.ZipFile) -> list[str]:
    strings: list[str] = []
    with z.open("xl/sharedStrings.xml") as f:
        for _event, el in ET.iterparse(f, events=("end",)):
            if el.tag == f"{NS}si":
                strings.append("".join(t.text or "" for t in el.iter(f"{NS}t")))
                el.clear()
    return strings


def _cell_value(cell: ET.Element, shared: list[str]) -> str:
    v = cell.find(f"{NS}v")
    if v is None or v.text is None:
        return ""
    if cell.get("t") == "s":
        return shared[int(v.text)]
    return v.text


def extract(xlsx: Path = XLSX) -> list[dict[str, str]]:
    """Returns one dict per food: code, name, kcal, protein_g, fat_g, carbs_g, fiber_g."""
    if not xlsx.exists():
        sys.exit(f"missing {xlsx} — run fetch_bls.py first")

    z = zipfile.ZipFile(xlsx)
    shared = _load_shared_strings(z)

    rows = z.open("xl/worksheets/sheet1.xml")
    header: dict[str, str] = {}  # column letter -> header text
    wanted: dict[str, str] = {}  # column letter -> field name
    foods: list[dict[str, str]] = []

    for _event, el in ET.iterparse(rows, events=("end",)):
        if el.tag != f"{NS}row":
            continue
        cells = {
            _column_letters(c.get("r")): _cell_value(c, shared)
            for c in el
            if c.tag == f"{NS}c"
        }
        if not header:  # first row = header
            header = cells
            for col, text in cells.items():
                if text == CODE_HEADER:
                    wanted[col] = "code"
                elif text == NAME_HEADER:
                    wanted[col] = "name"
                elif text.endswith(("Datenherkunft", "Referenz")):
                    continue  # the two metadata columns of each nutrient triplet
                else:
                    for prefix, field in NUTRIENT_HEADERS.items():
                        if text.startswith(prefix):
                            wanted[col] = field
            missing = {"code", "name", *NUTRIENT_HEADERS.values()} - set(wanted.values())
            if missing:
                sys.exit(f"header row does not contain: {sorted(missing)}")
        else:
            food = {field: cells.get(col, "").strip() for col, field in wanted.items()}
            if food.get("code"):
                foods.append(food)
        el.clear()

    return foods


def to_csv(foods: list[dict[str, str]], out: Path = CSV_OUT) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    fields = ["code", "name", "kcal", "protein_g", "fat_g", "carbs_g", "fiber_g"]
    with out.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(foods)


def main() -> None:
    foods = extract()
    to_csv(foods)
    print(f"{len(foods)} foods -> {CSV_OUT}")


if __name__ == "__main__":
    main()
