from conftest import error_code


def test_checkout_creates_a_deterministic_order(client, customer):
    client.post("/api/v1/cart/items", headers=customer, json={"productId": "product_001", "quantity": 2})
    client.post("/api/v1/cart/items", headers=customer, json={"productId": "product_002"})

    response = client.post("/api/v1/orders", headers=customer)
    assert response.status_code == 201
    order = response.json()
    assert order["id"] == 1
    assert order["orderNumber"] == "ORDER-0001"
    assert order["status"] == "PLACED"
    assert order["totalCents"] == 2 * 749 + 999
    assert [(item["productId"], item["quantity"]) for item in order["items"]] == [("product_001", 2), ("product_002", 1)]

    # The cart is emptied and stock is decremented.
    assert client.get("/api/v1/cart", headers=customer).json()["items"] == []
    assert client.get("/api/v1/products/product_001").json()["stock"] == 8


def test_order_history_and_detail(client, customer):
    for product in ("product_003", "product_004"):
        client.post("/api/v1/cart/items", headers=customer, json={"productId": product})
        client.post("/api/v1/orders", headers=customer)
    history = client.get("/api/v1/orders", headers=customer).json()
    assert [order["orderNumber"] for order in history] == ["ORDER-0001", "ORDER-0002"]
    detail = client.get("/api/v1/orders/2", headers=customer)
    assert detail.status_code == 200
    assert detail.json()["items"][0]["productId"] == "product_004"


def test_empty_cart_cannot_be_checked_out(client, customer):
    response = client.post("/api/v1/orders", headers=customer)
    assert response.status_code == 409
    assert error_code(response) == "CART_EMPTY"


def test_another_users_order_is_not_found(client, customer, qa):
    client.post("/api/v1/cart/items", headers=customer, json={"productId": "product_001"})
    client.post("/api/v1/orders", headers=customer)
    response = client.get("/api/v1/orders/1", headers=qa)
    assert response.status_code == 404
    assert error_code(response) == "ORDER_NOT_FOUND"
    assert client.get("/api/v1/orders", headers=qa).json() == []


def test_invalid_and_missing_order_ids(client, customer):
    assert error_code(client.get("/api/v1/orders/abc", headers=customer)) == "INVALID_ID"
    assert error_code(client.get("/api/v1/orders/999", headers=customer)) == "ORDER_NOT_FOUND"


def test_stock_is_rechecked_at_checkout(client, customer, qa):
    # Both users put the last unit of product_019 in their carts; only the first checkout succeeds.
    client.post("/api/v1/cart/items", headers=customer, json={"productId": "product_019"})
    client.post("/api/v1/cart/items", headers=qa, json={"productId": "product_019"})
    assert client.post("/api/v1/orders", headers=customer).status_code == 201
    second = client.post("/api/v1/orders", headers=qa)
    assert second.status_code == 409
    assert error_code(second) == "INSUFFICIENT_STOCK"
