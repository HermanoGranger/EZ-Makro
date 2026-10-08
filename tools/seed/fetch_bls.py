#!/usr/bin/env python3
"""Downloads the BLS 4.0 archive (CC BY 4.0, Max Rubner-Institut) into cache/.

The archive and its extracted files are build inputs only — they are never
committed (see tools/seed/cache/.gitignore).
"""

from __future__ import annotations

import urllib.request
import zipfile
from pathlib import Path

from bls_extract import CACHE, XLSX, ZIP_URL

ZIP_OUT = CACHE / "BLS_4_0_2025_DE.zip"


def main() -> None:
    CACHE.mkdir(parents=True, exist_ok=True)
    if not ZIP_OUT.exists():
        print(f"downloading {ZIP_URL}")
        urllib.request.urlretrieve(ZIP_URL, ZIP_OUT)
    if not XLSX.exists():
        with zipfile.ZipFile(ZIP_OUT) as z:
            z.extractall(CACHE)
    print(f"ready: {XLSX}")


if __name__ == "__main__":
    main()
