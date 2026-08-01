#!/usr/bin/env python3
"""Create deterministic, PII-free test fixtures from local Picnic captures."""

import argparse
import hashlib
import json
import re
from pathlib import Path

PRODUCT_ID = re.compile(r"(?<![A-Za-z0-9])s\d+(?![A-Za-z0-9])")
UUID = re.compile(r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
LONG_HEX = re.compile(r"[0-9a-fA-F]{16,}")
EMAIL = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")
JWT = re.compile(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+")
PHONE = re.compile(r"(?<!\w)\+?31[\s-]?\d(?:[\s-]?\d){8}(?!\w)")
POSTCODE = re.compile(r"\b\d{4}\s?[A-Za-z]{2}\b")

DROP_SUBTREES = {
    "action",
    "analytics",
    "analytics_context_data",
    "analyticsmetadata",
    "callback",
    "mutation",
    "onaction",
    "onlongpress",
    "onpress",
    "script",
}

SENSITIVE_KEY_PARTS = {
    "address",
    "auth",
    "customer",
    "device",
    "email",
    "firstname",
    "lastname",
    "phone",
    "postcode",
    "session",
    "token",
    "userid",
    "user_id",
}

STRUCTURAL_IDS = {
    "accordion-list",
    "alternatives-container",
    "description",
    "product-details-page-root-main-container",
    "product-page-allergies",
    "product-page-highlights",
    "product-page-image-gallery-main-image-container",
    "search-page-root-content",
}


class Pseudonyms:
    def __init__(self, main_product_id: str):
        self.main_product_id = main_product_id
        self.product_ids = {main_product_id: "s9000001"}
        self.identifiers = {}

    def product(self, value: str) -> str:
        if value not in self.product_ids:
            self.product_ids[value] = f"s9{len(self.product_ids) + 1:06d}"
        return self.product_ids[value]

    def identifier(self, value: str, prefix: str) -> str:
        key = (prefix, value)
        if key not in self.identifiers:
            digest = hashlib.sha256(value.encode()).hexdigest()[:12]
            self.identifiers[key] = f"{prefix}-{digest}"
        return self.identifiers[key]


def normalized_key(value: str) -> str:
    return re.sub(r"[^a-z0-9_]", "", value.lower())


def sensitive_key(value: str) -> bool:
    key = normalized_key(value)
    return any(part in key for part in SENSITIVE_KEY_PARTS)


def sanitize_string(value: str, key: str, pseudonyms: Pseudonyms) -> str:
    if value in STRUCTURAL_IDS:
        return value
    if JWT.search(value):
        return JWT.sub("<redacted-token>", value)
    value = EMAIL.sub("fixture@example.test", value)
    value = PHONE.sub("+31000000000", value)
    value = POSTCODE.sub("0000 ZZ", value)
    value = PRODUCT_ID.sub(lambda match: pseudonyms.product(match.group()), value)
    value = UUID.sub(lambda match: pseudonyms.identifier(match.group(), "uuid"), value)
    prefix = "image" if "image" in key or key in {"id", "source"} else "hash"
    value = LONG_HEX.sub(lambda match: pseudonyms.identifier(match.group(), prefix), value)
    return value


def sanitize(value, pseudonyms: Pseudonyms, key: str = ""):
    if isinstance(value, dict):
        result = {}
        product_id = value.get("id")
        for child_key, child_value in value.items():
            normalized = normalized_key(child_key)
            if normalized in DROP_SUBTREES or sensitive_key(child_key):
                continue
            result[child_key] = sanitize(child_value, pseudonyms, normalized)
        if isinstance(product_id, str) and PRODUCT_ID.fullmatch(product_id) and "name" in result:
            result["name"] = f"Fixture product {pseudonyms.product(product_id)}"
        return result
    if isinstance(value, list):
        return [sanitize(item, pseudonyms, key) for item in value]
    if isinstance(value, str):
        return sanitize_string(value, key, pseudonyms)
    return value


def product_count(value) -> int:
    if isinstance(value, dict):
        count = 1 if isinstance(value.get("sellingUnit"), dict) else 0
        return count + sum(product_count(child) for child in value.values())
    if isinstance(value, list):
        return sum(product_count(child) for child in value)
    return 0


def select_search_response(flows):
    candidates = [
        flow["resp_body"]
        for flow in flows
        if flow.get("path") == "/api/15/pages/search-page-root-content"
        and flow.get("status") == 200
        and isinstance(flow.get("resp_body"), dict)
    ]
    return max(candidates, key=product_count)


def select_product_variants(flows, primary):
    variants = []
    seen = {canonical_json(primary)}
    for flow in flows:
        if (
            flow.get("path") != "/api/15/pages/product-details-page-root"
            or flow.get("status") != 200
            or not isinstance(flow.get("resp_body"), dict)
        ):
            continue
        product_id = flow.get("query", {}).get("id")
        if not isinstance(product_id, str) or not PRODUCT_ID.fullmatch(product_id):
            continue
        sanitized = sanitize(flow["resp_body"], Pseudonyms(product_id))
        fingerprint = canonical_json(sanitized)
        if fingerprint not in seen:
            seen.add(fingerprint)
            variants.append(sanitized)
    return variants


def canonical_json(value) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def minimize_search_response(value):
    selling_units = []

    def collect(node):
        if isinstance(node, dict):
            if isinstance(node.get("sellingUnit"), dict):
                selling_units.append({"sellingUnit": node["sellingUnit"]})
            for child in node.values():
                collect(child)
        elif isinstance(node, list):
            for child in node:
                collect(child)

    collect(value)
    return {
        "id": value.get("id", "search-page-root-content"),
        "type": value.get("type", "BLOCK"),
        "children": selling_units,
    }


def write_fixture(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--product-input", type=Path, required=True)
    parser.add_argument("--flows-input", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--main-product-id", default="s1004201")
    args = parser.parse_args()

    product = json.loads(args.product_input.read_text(encoding="utf-8"))
    flows = json.loads(args.flows_input.read_text(encoding="utf-8"))
    search = select_search_response(flows)

    for stale_variant in args.output_dir.glob("product-details-app-1.239.3-variant-*.sanitized.json"):
        stale_variant.unlink()

    sanitized_product = sanitize(product, Pseudonyms(args.main_product_id))
    write_fixture(
        args.output_dir / "product-details-app-1.239.3.sanitized.json",
        sanitized_product,
    )
    for index, variant in enumerate(select_product_variants(flows, sanitized_product), start=1):
        write_fixture(
            args.output_dir / f"product-details-app-1.239.3-variant-{index:02d}.sanitized.json",
            variant,
        )
    write_fixture(
        args.output_dir / "search-app-1.239.3.sanitized.json",
        minimize_search_response(sanitize(search, Pseudonyms("__no_primary_product__"))),
    )


if __name__ == "__main__":
    main()
