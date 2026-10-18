import pytest


@pytest.mark.smoke
def test_health_is_ok(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


@pytest.mark.smoke
def test_ready_reports_database(client):
    response = client.get("/ready")
    assert response.status_code == 200
    assert response.json() == {"status": "ready", "checks": {"database": "up"}}


def test_openapi_specification_is_served(client):
    response = client.get("/openapi/openapi.yaml")
    assert response.status_code == 200
    assert response.text.startswith("openapi: 3.0.3")
    assert "/api/v1/products" in response.text
