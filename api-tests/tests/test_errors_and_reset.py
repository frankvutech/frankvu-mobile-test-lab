from conftest import CUSTOMER, bearer, error_code, login


def test_unknown_route_is_a_json_404(client):
    response = client.get("/api/v1/nope")
    assert response.status_code == 404
    assert error_code(response) == "ROUTE_NOT_FOUND"


def test_deterministic_server_error(client):
    response = client.get("/api/v1/test/server-error")
    assert response.status_code == 500
    assert error_code(response) == "INTERNAL_ERROR"
    assert "Deterministic" not in response.text  # internal details are not leaked


def test_reset_restores_the_seed(client):
    headers = bearer(login(client, CUSTOMER))
    client.post("/api/v1/auth/register", json={"displayName": "Temp", "email": "temp@example.com", "password": "Password123!"})
    client.post("/api/v1/cart/items", headers=headers, json={"productId": "product_001", "quantity": 5})
    client.post("/api/v1/orders", headers=headers)

    assert client.post("/api/v1/test/reset").status_code == 204

    assert client.get("/api/v1/products/product_001").json()["stock"] == 10
    assert client.post("/api/v1/auth/login", json={"email": "temp@example.com", "password": "Password123!"}).status_code == 401
    fresh = bearer(login(client, CUSTOMER))
    assert client.get("/api/v1/orders", headers=fresh).json() == []
    client.post("/api/v1/cart/items", headers=fresh, json={"productId": "product_002"})
    assert client.post("/api/v1/orders", headers=fresh).json()["orderNumber"] == "ORDER-0001"


def test_repeatability_same_flow_same_result(client):
    """Running the same flow twice from a reset yields identical responses."""
    results = []
    for _ in range(2):
        client.post("/api/v1/test/reset")
        headers = bearer(login(client, CUSTOMER))
        client.post("/api/v1/cart/items", headers=headers, json={"productId": "product_007", "quantity": 3})
        order = client.post("/api/v1/orders", headers=headers).json()
        results.append((order["id"], order["orderNumber"], order["totalCents"]))
    assert results[0] == results[1] == (1, "ORDER-0001", 3 * (499 + 7 * 250))
