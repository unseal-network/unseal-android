#!/usr/bin/env python3

# Copyright (c) 2026 Element Creations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

"""Publish a signed Android App Bundle to a Google Play track."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

API_BASE = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
MAX_RELEASE_NOTES_CHARS = 500


@dataclass(frozen=True)
class PublishConfig:
    package_name: str
    bundle_path: Path
    track: str
    release_name: str
    status: str
    user_fraction: float | None
    changes_not_sent_for_review: bool
    changes_in_review_behavior: str
    release_notes_dir: Path | None
    release_notes_language: str
    dry_run: bool
    dry_run_version_code: str | None


class GooglePlayApi:
    def __init__(self, access_token: str) -> None:
        self.access_token = access_token

    def create_edit(self, package_name: str) -> str:
        response = self._json_request(
            "POST",
            f"{API_BASE}/applications/{quote(package_name)}/edits",
            body={},
        )
        edit_id = response.get("id")
        if not edit_id:
            raise RuntimeError(f"Create edit response did not include an id: {response}")
        return str(edit_id)

    def upload_bundle(self, package_name: str, edit_id: str, bundle_path: Path) -> str:
        query = urllib.parse.urlencode({"uploadType": "media"})
        url = f"{UPLOAD_BASE}/applications/{quote(package_name)}/edits/{quote(edit_id)}/bundles?{query}"
        response = self._binary_request("POST", url, bundle_path.read_bytes(), "application/octet-stream")
        version_code = response.get("versionCode")
        if version_code is None:
            raise RuntimeError(f"Bundle upload response did not include a versionCode: {response}")
        return str(version_code)

    def update_track(
        self,
        package_name: str,
        edit_id: str,
        track: str,
        release: dict[str, Any],
    ) -> dict[str, Any]:
        return self._json_request(
            "PUT",
            f"{API_BASE}/applications/{quote(package_name)}/edits/{quote(edit_id)}/tracks/{quote(track)}",
            body={"track": track, "releases": [release]},
        )

    def commit_edit(
        self,
        package_name: str,
        edit_id: str,
        changes_not_sent_for_review: bool,
        changes_in_review_behavior: str,
    ) -> dict[str, Any]:
        query = urllib.parse.urlencode(
            {
                "changesNotSentForReview": str(changes_not_sent_for_review).lower(),
                "changesInReviewBehavior": changes_in_review_behavior,
            }
        )
        return self._json_request(
            "POST",
            f"{API_BASE}/applications/{quote(package_name)}/edits/{quote(edit_id)}:commit?{query}",
            body=None,
        )

    def _json_request(self, method: str, url: str, body: dict[str, Any] | None) -> dict[str, Any]:
        data = None if body is None else json.dumps(body, separators=(",", ":")).encode("utf-8")
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        return self._request(method, url, data, headers)

    def _binary_request(self, method: str, url: str, data: bytes, content_type: str) -> dict[str, Any]:
        return self._request(
            method,
            url,
            data,
            {
                "Accept": "application/json",
                "Content-Type": content_type,
            },
        )

    def _request(self, method: str, url: str, data: bytes | None, headers: dict[str, str]) -> dict[str, Any]:
        request_headers = {
            **headers,
            "Authorization": f"Bearer {self.access_token}",
            "User-Agent": "unseal-android-github-actions-release",
        }
        request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            response_body = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Google Play API {method} {redact_url(url)} failed with HTTP {error.code}: {response_body}") from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"Google Play API {method} {redact_url(url)} failed: {error}") from error

        if not raw:
            return {}

        return json.loads(raw.decode("utf-8"))


def quote(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def redact_url(url: str) -> str:
    parsed = urllib.parse.urlsplit(url)
    return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))


def parse_args() -> PublishConfig:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--track", required=True)
    parser.add_argument("--release-name", required=True)
    parser.add_argument("--status", required=True, choices=("draft", "inProgress", "halted", "completed"))
    parser.add_argument("--user-fraction", type=float)
    parser.add_argument("--changes-not-sent-for-review", action="store_true")
    parser.add_argument(
        "--changes-in-review-behavior",
        default="ERROR_IF_IN_REVIEW",
        choices=(
            "CHANGES_IN_REVIEW_BEHAVIOR_TYPE_UNSPECIFIED",
            "CANCEL_IN_REVIEW_AND_SUBMIT",
            "ERROR_IF_IN_REVIEW",
        ),
    )
    parser.add_argument("--release-notes-dir", type=Path)
    parser.add_argument("--release-notes-language", default="en-US")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--dry-run-version-code")
    args = parser.parse_args()

    if not args.bundle.is_file():
        parser.error(f"--bundle must point to an existing AAB file: {args.bundle}")

    if args.user_fraction is not None:
        if args.status not in {"inProgress", "halted"}:
            parser.error("--user-fraction can only be set for inProgress or halted releases")
        if args.user_fraction <= 0 or args.user_fraction >= 1:
            parser.error("--user-fraction must be greater than 0 and less than 1")

    if args.release_notes_dir is not None and not args.release_notes_dir.is_dir():
        parser.error(f"--release-notes-dir must point to an existing directory: {args.release_notes_dir}")

    if args.dry_run and args.dry_run_version_code is None:
        parser.error("--dry-run-version-code is required with --dry-run")

    return PublishConfig(
        package_name=args.package_name,
        bundle_path=args.bundle,
        track=args.track,
        release_name=args.release_name,
        status=args.status,
        user_fraction=args.user_fraction,
        changes_not_sent_for_review=args.changes_not_sent_for_review,
        changes_in_review_behavior=args.changes_in_review_behavior,
        release_notes_dir=args.release_notes_dir,
        release_notes_language=args.release_notes_language,
        dry_run=args.dry_run,
        dry_run_version_code=args.dry_run_version_code,
    )


def build_release(config: PublishConfig, version_code: str) -> dict[str, Any]:
    release: dict[str, Any] = {
        "name": config.release_name,
        "versionCodes": [version_code],
        "status": config.status,
    }

    if config.user_fraction is not None:
        release["userFraction"] = config.user_fraction

    release_notes = read_release_notes(config.release_notes_dir, config.release_notes_language, version_code)
    if release_notes is not None:
        release["releaseNotes"] = [release_notes]

    return release


def read_release_notes(release_notes_dir: Path | None, language: str, version_code: str) -> dict[str, str] | None:
    if release_notes_dir is None:
        return None

    release_notes_file = release_notes_dir / f"{version_code}.txt"
    if not release_notes_file.is_file():
        print(f"Warning: no Google Play release notes found at {release_notes_file}; publishing without release notes.")
        return None

    text = release_notes_file.read_text(encoding="utf-8").strip()
    if len(text) > MAX_RELEASE_NOTES_CHARS:
        raise ValueError(
            f"Google Play release notes in {release_notes_file} are {len(text)} characters; "
            f"the limit is {MAX_RELEASE_NOTES_CHARS}."
        )

    return {
        "language": language,
        "text": text,
    }


def publish(config: PublishConfig, access_token: str) -> str:
    api = GooglePlayApi(access_token)
    edit_id = api.create_edit(config.package_name)
    print(f"Created Google Play edit {edit_id}.")

    version_code = api.upload_bundle(config.package_name, edit_id, config.bundle_path)
    print(f"Uploaded {config.bundle_path} with version code {version_code}.")

    release = build_release(config, version_code)
    api.update_track(config.package_name, edit_id, config.track, release)
    print(f"Updated Google Play track {config.track} with release {config.release_name}.")

    api.commit_edit(
        config.package_name,
        edit_id,
        config.changes_not_sent_for_review,
        config.changes_in_review_behavior,
    )
    print(f"Committed Google Play edit {edit_id}.")
    return version_code


def write_step_summary(config: PublishConfig, version_code: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary_path:
        return

    with open(summary_path, "a", encoding="utf-8") as summary:
        summary.write("## Google Play publish\n\n")
        summary.write(f"- Package: `{config.package_name}`\n")
        summary.write(f"- Track: `{config.track}`\n")
        summary.write(f"- Status: `{config.status}`\n")
        summary.write(f"- Version code: `{version_code}`\n")
        summary.write(f"- Release: `{config.release_name}`\n")


def main() -> int:
    config = parse_args()
    if config.dry_run:
        version_code = config.dry_run_version_code
        assert version_code is not None
        print(json.dumps(build_release(config, version_code), indent=2, sort_keys=True))
        return 0

    access_token = os.environ.get("GOOGLE_PLAY_ACCESS_TOKEN", "")
    if not access_token:
        print(
            "Missing GOOGLE_PLAY_ACCESS_TOKEN. Generate it with Workload Identity Federation and the "
            f"{ANDROID_PUBLISHER_SCOPE} scope.",
            file=sys.stderr,
        )
        return 1

    try:
        version_code = publish(config, access_token)
        write_step_summary(config, version_code)
    except (RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"Failed to publish to Google Play: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
