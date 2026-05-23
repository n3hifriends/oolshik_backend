from __future__ import annotations

import os
from typing import Optional

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError, EndpointConnectionError

from stt_worker.audio.fetcher import FetchResult
from stt_worker.audio.http_fetcher import AudioDownloadError


class S3AudioFetcher:
    def fetch(
        self,
        bucket: str,
        object_key: str,
        dest_path: str,
        max_bytes: int,
        *,
        region: Optional[str] = None,
        endpoint: Optional[str] = None,
        path_style_access_enabled: bool = False,
    ) -> FetchResult:
        os.makedirs(os.path.dirname(dest_path), exist_ok=True)
        client_kwargs = {}
        if region:
            client_kwargs["region_name"] = region
        if endpoint:
            client_kwargs["endpoint_url"] = endpoint
        if path_style_access_enabled:
            client_kwargs["config"] = Config(s3={"addressing_style": "path"})

        client = boto3.client("s3", **client_kwargs)
        try:
            head = client.head_object(Bucket=bucket, Key=object_key)
            declared_size = head.get("ContentLength")
            if declared_size is not None and declared_size > max_bytes:
                raise AudioDownloadError(
                    "AUDIO_TOO_LARGE",
                    f"Audio exceeds {max_bytes} bytes",
                    False,
                )
            content_type = head.get("ContentType")
            with open(dest_path, "wb") as out:
                client.download_fileobj(bucket, object_key, out)
            size = os.path.getsize(dest_path)
            if size == 0:
                raise AudioDownloadError("DOWNLOAD_FAILED", "Empty audio", False)
            if size > max_bytes:
                raise AudioDownloadError(
                    "AUDIO_TOO_LARGE",
                    f"Audio exceeds {max_bytes} bytes",
                    False,
                )
            return FetchResult(path=dest_path, size_bytes=size, content_type=content_type)
        except ClientError as exc:
            code = exc.response.get("Error", {}).get("Code", "DOWNLOAD_FAILED")
            status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode", 0)
            retryable = 500 <= status <= 599
            raise AudioDownloadError("DOWNLOAD_FAILED", f"S3 {code}", retryable) from exc
        except EndpointConnectionError as exc:
            raise AudioDownloadError("DOWNLOAD_FAILED", str(exc), True) from exc
        except BotoCoreError as exc:
            raise AudioDownloadError("DOWNLOAD_FAILED", str(exc), True) from exc
