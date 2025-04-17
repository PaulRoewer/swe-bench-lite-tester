# SWE-Bench-Lite API - Test Service

A lightweight REST API to evaluate [SWE-bench Lite](https://huggingface.co/datasets/princeton-nlp/SWE-bench_Lite) test cases in combination with automated code modifications (e.g. via agents like OpenManus).  
Built specifically for running `run_evaluation.py` inside Docker.

---

## 🌐 REST API Overview

### `POST /test`

Run a bug-fixing evaluation job for a given SWE-bench Lite instance.

#### Request Example

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

#### Request Fields

- `instance_id`: Unique ID for this run – shown in the final report file.
- `repoDir`: Path inside container to mounted code repo (e.g. `/repos/repo_1`).
- `FAIL_TO_PASS`: Failing tests expected to pass after patching.
- `PASS_TO_PASS`: Tests that should remain passing.

---

### Response

```json
{
  "exitCode": 0,
  "harnessOutput": "{...}",  // stringified JSON
  "error": null
}
```

- `exitCode`: Exit status of the Python evaluation process.
- `harnessOutput`: The raw JSON result of the test run.
- `error`: If anything failed internally.

---

## 🐳 Run via DockerHub

```bash
docker pull paulroewer/swe-bench-lite-tester:latest
docker run -p 8082:8080 \
  -v /path/to/your/repos:/repos \
  -v /var/run/docker.sock:/var/run/docker.sock \
  paulroewer/swe-bench-lite-tester
```

---

## 📜 License

This project uses [SWE-bench Lite](https://github.com/SWE-bench/SWE-bench/tree/main/swebench/harness). MIT
