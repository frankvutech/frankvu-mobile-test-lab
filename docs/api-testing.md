# API testing

`api-tests/` is an HTTP automation suite for the backend, written with **pytest** and **httpx**. It
tests the API directly — no mobile app or UI driver is involved.

## Layout

```text
api-tests/
├── pytest.ini          markers, options
├── requirements.txt    httpx, pytest (pinned)
├── conftest.py         client, reset fixture, login helpers, error_code()
└── tests/
    ├── test_health.py              health, readiness, OpenAPI served
    ├── test_auth.py                login success/failure, validation, me, logout/revocation, register, password reset
    ├── test_authorization.py       missing/invalid tokens, customer denied on admin, admin product/order management
    ├── test_products.py            detail, search, category filter, sorting, pagination boundaries, invalid parameters
    ├── test_cart.py                add, update, remove, clear, per-user carts, quantity and stock rules
    ├── test_wishlist.py            list, add, remove, duplicates, errors
    ├── test_orders.py              checkout, history, detail, empty cart, isolation, stock re-check
    └── test_errors_and_reset.py    JSON 404, deterministic 500, reset restores the seed, repeatability
```

## Repeatability

The autouse fixture `reset_database` calls `POST /api/v1/test/reset` before **every** test, so each
test starts from the seed and no test depends on another or on execution order. (Verified by running
the whole suite in reverse order.) Tests use only the documented local test accounts and never a
production service.

Helpers keep tests readable:

```python
def test_customer_is_forbidden_on_admin_endpoints(client, customer):
    response = client.get("/api/v1/admin/orders", headers=customer)
    assert response.status_code == 403
    assert error_code(response) == "FORBIDDEN"
```

`customer`, `qa` and `admin` fixtures log in and return auth headers; `error_code(response)` also
asserts the error body has the standard shape.

## Running locally

1. Start the backend — either:
   ```bash
   make backend-run          # Gradle, H2 database, http://localhost:8080
   ```
   or
   ```bash
   docker compose up --build -d backend   # PostgreSQL + backend
   ```
2. Create the virtual environment once and run the suite:
   ```bash
   make api-test-setup
   make api-test             # or: cd api-tests && .venv/bin/pytest
   ```
3. Stop Docker services with `docker compose down` (add `-v` to drop the database volume).

Useful options:

```bash
cd api-tests
.venv/bin/pytest -m smoke                     # fast smoke subset
.venv/bin/pytest tests/test_cart.py -k stock  # one area
API_BASE_URL=http://localhost:9090 .venv/bin/pytest
.venv/bin/pytest --junitxml=reports/api-tests.xml
```

If the backend is not reachable, the suite stops immediately with a message instead of failing every test.

Reset data by hand at any time with `make api-reset`.

## In CI

`.github/workflows/backend.yml`:

1. `docker compose up --build --wait backend` — PostgreSQL and the backend, waiting for both health checks.
2. `curl /health` and `/ready`.
3. Backend ktlint, detekt, unit and integration tests (`./gradlew ktlintCheck detekt test`).
4. `pytest --junitxml=reports/api-tests.xml` against `http://localhost:8080`.
5. Backend logs on failure, reports uploaded, `docker compose down -v`.
