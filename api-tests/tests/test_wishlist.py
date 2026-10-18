from conftest import error_code


def product_ids(response):
    return [item["id"] for item in response.json()["items"]]


def test_seeded_wishlist(client, qa):
    assert product_ids(client.get("/api/v1/wishlist", headers=qa)) == ["product_002"]


def test_add_and_remove(client, customer):
    assert product_ids(client.get("/api/v1/wishlist", headers=customer)) == []
    added = client.post("/api/v1/wishlist/items", headers=customer, json={"productId": "product_005"})
    assert added.status_code == 201
    assert product_ids(added) == ["product_005"]
    removed = client.delete("/api/v1/wishlist/items/product_005", headers=customer)
    assert removed.status_code == 200
    assert product_ids(removed) == []


def test_duplicate_is_a_conflict(client, qa):
    response = client.post("/api/v1/wishlist/items", headers=qa, json={"productId": "product_002"})
    assert response.status_code == 409
    assert error_code(response) == "ALREADY_IN_WISHLIST"


def test_errors(client, customer):
    assert error_code(client.post("/api/v1/wishlist/items", headers=customer, json={})) == "VALIDATION_ERROR"
    assert error_code(client.post("/api/v1/wishlist/items", headers=customer, json={"productId": "product_999"})) == "PRODUCT_NOT_FOUND"
    assert error_code(client.delete("/api/v1/wishlist/items/product_001", headers=customer)) == "WISHLIST_ITEM_NOT_FOUND"
