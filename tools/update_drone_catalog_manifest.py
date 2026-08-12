#!/usr/bin/env python3
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_CATALOG_URL = (
    "https://www.kwos.org/appoggio/droni/DroneSkyCheck/catalog/drone_technical_catalog.json"
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Update the KWOS drone catalog manifest checksum and timestamp."
    )
    parser.add_argument(
        "--catalog",
        default="kwos/catalog/drone_technical_catalog.json",
        help="Catalog JSON path.",
    )
    parser.add_argument(
        "--manifest",
        default="kwos/catalog/drone_catalog_manifest.json",
        help="Manifest JSON path.",
    )
    parser.add_argument(
        "--catalog-version",
        type=int,
        default=None,
        help="Set catalogVersion explicitly. If omitted, keep the catalog value.",
    )
    args = parser.parse_args()

    catalog_path = Path(args.catalog)
    manifest_path = Path(args.manifest)
    catalog_text = catalog_path.read_text(encoding="utf-8")
    catalog = json.loads(catalog_text)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}

    schema_version = int(catalog.get("schemaVersion") or catalog.get("version") or 0)
    catalog_version = args.catalog_version or int(catalog.get("catalogVersion") or 0)
    if schema_version <= 0:
        raise SystemExit("Catalog schemaVersion/version is missing or invalid.")
    if catalog_version <= 0:
        raise SystemExit("Catalog catalogVersion is missing or invalid.")

    manifest.update(
        {
            "schemaVersion": schema_version,
            "catalogVersion": catalog_version,
            "updatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
            "catalogUrl": manifest.get("catalogUrl") or DEFAULT_CATALOG_URL,
            "sha256": hashlib.sha256(catalog_text.encode("utf-8")).hexdigest(),
        }
    )
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
