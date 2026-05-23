from datetime import datetime, timezone

import pytest

from stt_worker.validator import parse_job


def test_parse_job_valid():
    payload = {
        "jobId": "123",
        "taskId": "456",
        "audioUrl": "https://example.com/audio.m4a",
        "languageHint": "en-IN",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "correlationId": "corr-1",
    }
    job = parse_job(json_dumps(payload))
    assert job.jobId == "123"
    assert job.taskId == "456"


def test_parse_job_valid_s3_object_ref():
    payload = {
        "jobId": "123",
        "taskId": "456",
        "audioFileId": "789",
        "storageProvider": "AWS",
        "bucket": "oolshik-dev-bucket",
        "objectKey": "audio/u1/file.m4a",
        "region": "ap-south-1",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "correlationId": "corr-1",
    }
    job = parse_job(json_dumps(payload))
    assert job.bucket == "oolshik-dev-bucket"
    assert job.objectKey == "audio/u1/file.m4a"


def test_parse_job_invalid():
    payload = {"taskId": "456"}
    with pytest.raises(Exception):
        parse_job(json_dumps(payload))


def json_dumps(obj) -> str:
    import json

    return json.dumps(obj)
