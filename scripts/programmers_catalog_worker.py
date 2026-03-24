#!/usr/bin/env python3
"""
Fetches Programmers catalog data and writes a normalized JSON snapshot into
PROGRAMMERS_IMPORT_DIR for the backend file source to ingest.

Priority:
1. Crawl Programmers challenge pages and extract lesson data.
2. Fallback to a JSON feed URL if crawling yields no items.
3. Optionally trigger the backend admin ingest endpoint after writing.
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from urllib import error, parse, request


USER_AGENT = "ctps-programmers-catalog-worker/1.0"
DEFAULT_URL_TEMPLATE = "https://school.programmers.co.kr/learn/challenges?page={page}"


@dataclass
class CatalogItem:
    externalId: str
    title: str
    problemNumber: str
    difficulty: str
    tags: list[str]
    externalUrl: str
    recommendationReason: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Collect Programmers catalog items and write a normalized import JSON."
    )
    parser.add_argument("--import-dir", default=os.getenv("PROGRAMMERS_IMPORT_DIR", ""))
    parser.add_argument("--pages", type=int, default=int(os.getenv("PROGRAMMERS_CRAWLER_PAGES", "3")))
    parser.add_argument(
        "--url-template",
        default=os.getenv("PROGRAMMERS_CRAWLER_URL_TEMPLATE", DEFAULT_URL_TEMPLATE),
        help="Listing URL template. Use {page} placeholder.",
    )
    parser.add_argument(
        "--fallback-feed-url",
        default=os.getenv("PROGRAMMERS_FEED_URL", ""),
        help="Fallback JSON feed URL used only when crawl result is empty.",
    )
    parser.add_argument("--delay-ms", type=int, default=int(os.getenv("PROGRAMMERS_CRAWLER_DELAY_MS", "250")))
    parser.add_argument("--retention", type=int, default=int(os.getenv("PROGRAMMERS_CRAWLER_RETENTION", "10")))
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument(
        "--ingest-url",
        default=os.getenv("CTPS_PROGRAMMERS_INGEST_URL", ""),
        help="Optional admin ingest endpoint, for example http://localhost:8080/api/admin/external-search/programmers/ingest",
    )
    parser.add_argument("--admin-token", default=os.getenv("ADMIN_SECURITY_TOKEN", ""))
    return parser.parse_args()


def fetch_text(url: str) -> str:
    req = request.Request(url, headers={"User-Agent": USER_AGENT})
    with request.urlopen(req, timeout=20) as response:
        encoding = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(encoding, errors="replace")


def fetch_json(url: str) -> Any:
    return json.loads(fetch_text(url))


def normalize_difficulty(raw: Any) -> str:
    if raw is None:
        return "medium"

    text = str(raw).strip().lower()
    if text in {"1", "lv1", "level1", "easy"}:
        return "easy"
    if text in {"2", "3", "lv2", "lv3", "level2", "level3", "medium"}:
        return "medium"
    if text in {"4", "5", "lv4", "lv5", "level4", "level5", "hard"}:
        return "hard"

    if "easy" in text:
        return "easy"
    if "hard" in text:
        return "hard"
    if "medium" in text:
        return "medium"

    digits = re.findall(r"\d+", text)
    if digits:
        level = int(digits[0])
        if level <= 1:
            return "easy"
        if level <= 3:
            return "medium"
        return "hard"

    return "medium"


def strip_tags(value: str) -> str:
    without_tags = re.sub(r"<[^>]+>", " ", value)
    return html.unescape(re.sub(r"\s+", " ", without_tags)).strip()


def first_present(data: dict[str, Any], keys: Iterable[str]) -> Any:
    for key in keys:
        value = data.get(key)
        if value not in (None, "", [], {}):
            return value
    return None


def normalize_tags(raw: Any) -> list[str]:
    if raw is None:
        return []
    if isinstance(raw, str):
        return [raw.strip()] if raw.strip() else []
    if isinstance(raw, list):
        tags: list[str] = []
        for item in raw:
            if isinstance(item, str) and item.strip():
                tags.append(item.strip())
            elif isinstance(item, dict):
                tag = first_present(item, ("name", "label", "title", "value"))
                if isinstance(tag, str) and tag.strip():
                    tags.append(tag.strip())
        return list(dict.fromkeys(tags))
    return []


def normalize_candidate(candidate: dict[str, Any]) -> CatalogItem | None:
    identifier = first_present(candidate, ("externalId", "lessonId", "lesson_id", "problemId", "id"))
    title = first_present(candidate, ("title", "name", "lessonTitle"))
    if not identifier or not title:
        return None

    problem_number = str(first_present(candidate, ("problemNumber", "problem_number", "id", "lessonId", "lesson_id")) or identifier)
    external_url = first_present(candidate, ("externalUrl", "url", "link"))
    if isinstance(external_url, str) and external_url.startswith("/"):
        external_url = parse.urljoin("https://school.programmers.co.kr", external_url)
    if not external_url:
        external_url = f"https://school.programmers.co.kr/learn/courses/30/lessons/{identifier}"

    tags = normalize_tags(first_present(candidate, ("tags", "skills", "categories", "categoryNames")))
    recommendation_reason = first_present(
        candidate,
        ("recommendationReason", "recommendation_reason", "summary", "description"),
    )
    if not recommendation_reason:
        recommendation_reason = "프로그래머스에서 탐색 가능한 추천 문제입니다."

    return CatalogItem(
        externalId=f"programmers-{identifier}",
        title=str(title).strip(),
        problemNumber=problem_number,
        difficulty=normalize_difficulty(first_present(candidate, ("difficulty", "level", "difficultyLevel"))),
        tags=tags,
        externalUrl=str(external_url),
        recommendationReason=str(recommendation_reason).strip(),
    )


def walk_candidates(node: Any) -> Iterable[dict[str, Any]]:
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from walk_candidates(value)
    elif isinstance(node, list):
        for item in node:
            yield from walk_candidates(item)


def extract_next_data(html_text: str) -> Any | None:
    match = re.search(
        r'<script[^>]+id="__NEXT_DATA__"[^>]*>\s*(.*?)\s*</script>',
        html_text,
        re.DOTALL | re.IGNORECASE,
    )
    if not match:
        return None
    try:
        return json.loads(match.group(1))
    except json.JSONDecodeError:
        return None


def extract_anchor_fallback(html_text: str) -> list[CatalogItem]:
    items: list[CatalogItem] = []
    pattern = re.compile(
        r'<a[^>]+href="(?P<href>/learn/courses/\d+/lessons/(?P<id>\d+))"[^>]*>(?P<content>.*?)</a>',
        re.DOTALL | re.IGNORECASE,
    )
    for match in pattern.finditer(html_text):
        title = strip_tags(match.group("content"))
        if not title:
            continue
        items.append(
            CatalogItem(
                externalId=f"programmers-{match.group('id')}",
                title=title,
                problemNumber=match.group("id"),
                difficulty="medium",
                tags=[],
                externalUrl=parse.urljoin("https://school.programmers.co.kr", match.group("href")),
                recommendationReason="프로그래머스 공개 문제 목록에서 수집한 문제입니다.",
            )
        )
    return items


def crawl_programmers_catalog(url_template: str, pages: int, delay_ms: int) -> list[CatalogItem]:
    deduped: dict[str, CatalogItem] = {}

    for page in range(1, pages + 1):
        url = url_template.format(page=page)
        html_text = fetch_text(url)

        next_data = extract_next_data(html_text)
        if next_data is not None:
            for candidate in walk_candidates(next_data):
                item = normalize_candidate(candidate)
                if item:
                    deduped[item.externalId] = item

        if not deduped:
            for item in extract_anchor_fallback(html_text):
                deduped[item.externalId] = item

        if page < pages and delay_ms > 0:
            time.sleep(delay_ms / 1000)

    return list(deduped.values())


def load_fallback_feed(feed_url: str) -> list[CatalogItem]:
    if not feed_url:
        return []
    payload = fetch_json(feed_url)
    if not isinstance(payload, list):
        return []

    items: list[CatalogItem] = []
    for entry in payload:
        if not isinstance(entry, dict):
            continue
        item = normalize_candidate(entry)
        if item:
            items.append(item)
    return items


def write_snapshot(import_dir: str, items: list[CatalogItem], retention: int) -> Path:
    target_dir = Path(import_dir)
    target_dir.mkdir(parents=True, exist_ok=True)

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    target = target_dir / f"programmers-catalog-{timestamp}.json"
    temp = target_dir / f".programmers-catalog-{timestamp}.tmp"

    payload = [asdict(item) for item in items]
    temp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(target)

    snapshots = sorted(
        (path for path in target_dir.glob("programmers-catalog-*.json") if path.is_file()),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    for old_file in snapshots[retention:]:
        old_file.unlink(missing_ok=True)

    return target


def trigger_ingest(ingest_url: str, admin_token: str) -> None:
    if not ingest_url:
        return

    headers = {"User-Agent": USER_AGENT}
    if admin_token:
        headers["X-Admin-Token"] = admin_token

    req = request.Request(ingest_url, method="POST", headers=headers)
    with request.urlopen(req, timeout=20) as response:
        if response.status >= 400:
            raise RuntimeError(f"ingest trigger failed with status={response.status}")


def main() -> int:
    args = parse_args()
    if not args.import_dir and not args.dry_run:
        print("PROGRAMMERS_IMPORT_DIR 또는 --import-dir 설정이 필요합니다.", file=sys.stderr)
        return 1

    try:
        items = crawl_programmers_catalog(args.url_template, args.pages, args.delay_ms)
        source_name = "crawl"
        if not items:
            items = load_fallback_feed(args.fallback_feed_url)
            source_name = "fallback-feed"

        if not items:
            print("수집된 프로그래머스 카탈로그 항목이 없습니다.", file=sys.stderr)
            return 2

        if args.dry_run:
            print(json.dumps([asdict(item) for item in items[:5]], ensure_ascii=False, indent=2))
            print(f"dry-run source={source_name} count={len(items)}")
            return 0

        output_file = write_snapshot(args.import_dir, items, args.retention)
        trigger_ingest(args.ingest_url, args.admin_token)
        print(f"wrote {len(items)} items from {source_name} to {output_file}")
        return 0
    except error.HTTPError as http_error:
        print(f"http error: {http_error.code} {http_error.reason}", file=sys.stderr)
        return 3
    except Exception as exception:
        print(f"worker failed: {exception}", file=sys.stderr)
        return 4


if __name__ == "__main__":
    raise SystemExit(main())
