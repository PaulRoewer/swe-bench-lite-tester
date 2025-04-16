# SWE-Bench Lite REST Service

This project provides a REST API wrapper for executing individual [SWE-Bench Lite](https://github.com/princeton-nlp/SWE-bench) test cases in a Dockerized environment. It acts as a bridge between AI-driven agents and the SWE-Bench evaluation harness by exposing a simple HTTP endpoint.

## Overview

The REST API allows you to send a test case with repository path and test lists, and the server handles the patch diff creation, test execution via SWE-Bench's `run_evaluation.py`, and report generation.

## REST API

### `POST /test`

**Request Body (JSON):**
```json
{
  "instance_id": "astropy__astropy-12907",
  "repoDir": "/repos/repo_1",
  "FAIL_TO_PASS": [
    "astropy/modeling/tests/test_separable.py::test_separable[compound_model6-result6]"
  ],
  "PASS_TO_PASS": [
    "astropy/modeling/tests/test_separable.py::test_coord_matrix"
  ]
}
```

**Fields:**
- `instance_id`: Unique identifier for this test instance.
- `repoDir`: Mounted directory inside the Docker container pointing to the cloned repo.
- `FAIL_TO_PASS`: List of test identifiers expected to fail before the patch but pass after.
- `PASS_TO_PASS`: List of test identifiers expected to pass both before and after the patch.

**Response (JSON):**
```json
{
  "output": "...",             // Log output of the SWE-Bench evaluation run
  "exitCode": 0,               // Exit code of the run
  "harnessOutput": "{...}"     // JSON report content as string
}
```

## Usage

### Start Server

Use the provided Dockerfile to build the service:
```bash
docker build -t swe-bench-lite-tester .
```

Run the service:
```bash
docker run -p 8080:8080 -v /var/run/docker.sock:/var/run/docker.sock -v /absolute/path/to/repos:/repos swe-bench-lite-tester
```

### Example Client Call
```python
import requests

test_payload = {
    "instance_id": "astropy__astropy-12907",
    "repoDir": "/repos/repo_1",
    "FAIL_TO_PASS": ["astropy/..."],
    "PASS_TO_PASS": ["astropy/..."]
}

response = requests.post("http://localhost:8080/test", json=test_payload)
print(response.json())
```

## License

MIT
