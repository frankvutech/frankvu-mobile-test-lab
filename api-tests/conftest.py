"""Shared fixtures for the API automation suite.

Configuration (environment variables):
    API_BASE_URL   Base URL of the backend (default http://localhost:8080).

Every test starts from the seed: the autouse ``reset_database`` fixture calls
``POST /api/v1/test/reset`` first, so tests never depend on execution order.
These are LOCAL TEST CREDENTIALS ONLY; never point this suite at a production service.
"""
import os

import httpx
import pytest

BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080")

CUSTOMER = ("test@example.com", "Password123!")
QA = ("qa@example.com", "QaPassword123!")
ADMIN = ("admin@example.com", "Admin123!")
LOCKED = ("locked@example.com", "Locked123!")


@pytest.fixture(scope="session")
def client():
    with httpx.Client(base_url=BASE_URL, timeout=10.0) as session:
        try:
            session.get("/ready").raise_for_status()
        except httpx.HTTPError as error:
            pytest.exit(f"Backend not ready at {BASE_URL}: {error}. Start it first (see docs/api-testing.md).", returncode=2)
        yield session


@pytest.fixture(autouse=True)
def reset_database(client):
    response = client.post("/api/v1/test/reset")
    assert response.status_code == 204, "Test support must be enabled (TEST_SUPPORT_ENABLED=true)"


def login(client, credentials):
    email, password = credentials
    response = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    assert response.status_code == 200, response.text
    return response.json()["token"]


def bearer(token):
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def customer(client):
    """Auth headers for test@example.com (CUSTOMER)."""
    return bearer(login(client, CUSTOMER))


@pytest.fixture
def qa(client):
    """Auth headers for qa@example.com (CUSTOMER, starts with product_002 in the wishlist)."""
    return bearer(login(client, QA))


@pytest.fixture
def admin(client):
    """Auth headers for admin@example.com (ADMIN)."""
    return bearer(login(client, ADMIN))


def error_code(response):
    """The stable machine-readable code of an error response."""
    body = response.json()
    assert set(body["error"]) >= {"code", "message", "details"}, body
    return body["error"]["code"]
