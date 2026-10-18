import pytest

from conftest import error_code

PROTECTED = [
    ("GET", "/api/v1/auth/me"),
    ("GET", "/api/v1/cart"),
    ("GET", "/api/v1/wishlist"),
    ("GET", "/api/v1/orders"),
    ("POST", "/api/v1/orders"),
    ("GET", "/api/v1/admin/orders"),
]

ADMIN_ONLY = [
    ("GET", "/api/v1/admin/products", None),
    ("GET", "/api/v1/admin/orders", None),
    ("POST", "/api/v1/admin/products", {"id": "product_x", "name": "X", "description": "", "category": "Toys", "priceCents": 100, "stock": 1}),
    ("DELETE", "/api/v1/admin/products/product_001", None),
    ("PATCH", "/api/v1/admin/orders/1", {"status": "SHIPPED"}),
]


@pytest.mark.parametrize(("method", "path"), PROTECTED)
def test_missing_token_is_401(client, method, path):
    response = client.request(method, path)
    assert response.status_code == 401
    assert error_code(response) == "UNAUTHORIZED"


@pytest.mark.parametrize(
    "header",
    ["Bearer not-a-jwt", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyX2FkbWluIn0.invalid-signature", "Basic dGVzdDp0ZXN0"],
    ids=["garbage", "bad-signature", "wrong-scheme"],
)
def test_invalid_token_is_401(client, header):
    response = client.get("/api/v1/auth/me", headers={"Authorization": header})
    assert response.status_code == 401
    assert error_code(response) == "INVALID_TOKEN"


@pytest.mark.parametrize(("method", "path", "body"), ADMIN_ONLY)
def test_customer_is_forbidden_on_admin_endpoints(client, customer, method, path, body):
    response = client.request(method, path, headers=customer, json=body)
    assert response.status_code == 403
    assert error_code(response) == "FORBIDDEN"


def test_admin_can_manage_products(client, admin):
    new_product = {"id": "product_new", "name": "New Product", "description": "Created by a test.", "category": "Toys", "priceCents": 999, "stock": 3}
    assert client.post("/api/v1/admin/products", headers=admin, json=new_product).status_code == 201
    assert client.get("/api/v1/products/product_new").json()["name"] == "New Product"

    duplicate = client.post("/api/v1/admin/products", headers=admin, json=new_product)
    assert duplicate.status_code == 409
    assert error_code(duplicate) == "PRODUCT_EXISTS"

    updated = client.put("/api/v1/admin/products/product_new", headers=admin, json={**new_product, "priceCents": 1299})
    assert updated.status_code == 200
    assert updated.json()["priceCents"] == 1299

    assert client.delete("/api/v1/admin/products/product_new", headers=admin).status_code == 204
    assert client.get("/api/v1/products/product_new").status_code == 404


def test_admin_cannot_delete_a_product_in_a_wishlist(client, admin):
    # qa@example.com's seeded wishlist contains product_002.
    response = client.delete("/api/v1/admin/products/product_002", headers=admin)
    assert response.status_code == 409
    assert error_code(response) == "PRODUCT_IN_USE"


def test_admin_product_validation(client, admin):
    response = client.post(
        "/api/v1/admin/products",
        headers=admin,
        json={"id": "Bad Id", "name": "", "description": "", "category": "", "priceCents": 0, "stock": -1},
    )
    assert response.status_code == 400
    fields = {detail["field"] for detail in response.json()["error"]["details"]}
    assert fields == {"id", "name", "category", "priceCents", "stock"}


def test_admin_sees_every_order_and_can_ship_one(client, customer, admin):
    client.post("/api/v1/cart/items", headers=customer, json={"productId": "product_001"})
    order = client.post("/api/v1/orders", headers=customer).json()

    all_orders = client.get("/api/v1/admin/orders", headers=admin).json()
    assert [o["orderNumber"] for o in all_orders] == ["ORDER-0001"]

    shipped = client.patch(f"/api/v1/admin/orders/{order['id']}", headers=admin, json={"status": "SHIPPED"})
    assert shipped.status_code == 200
    assert shipped.json()["status"] == "SHIPPED"
    assert client.get(f"/api/v1/orders/{order['id']}", headers=customer).json()["status"] == "SHIPPED"

    invalid = client.patch(f"/api/v1/admin/orders/{order['id']}", headers=admin, json={"status": "LOST"})
    assert invalid.status_code == 400
