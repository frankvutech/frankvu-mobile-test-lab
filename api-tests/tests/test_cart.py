import pytest

from conftest import error_code

ADD = "/api/v1/cart/items"


def test_new_cart_is_empty(client, customer):
    assert client.get("/api/v1/cart", headers=customer).json() == {"items": [], "totalQuantity": 0, "totalCents": 0}


def test_add_update_remove_and_clear(client, customer):
    added = client.post(ADD, headers=customer, json={"productId": "product_001", "quantity": 2})
    assert added.status_code == 201
    assert added.json()["totalCents"] == 2 * 749

    # Adding again increases the quantity.
    again = client.post(ADD, headers=customer, json={"productId": "product_001"}).json()
    assert again["items"][0]["quantity"] == 3

    client.post(ADD, headers=customer, json={"productId": "product_002"})
    updated = client.patch("/api/v1/cart/items/product_002", headers=customer, json={"quantity": 4}).json()
    assert updated["totalQuantity"] == 7
    assert updated["totalCents"] == 3 * 749 + 4 * 999

    removed = client.delete("/api/v1/cart/items/product_001", headers=customer).json()
    assert [line["product"]["id"] for line in removed["items"]] == ["product_002"]

    cleared = client.delete("/api/v1/cart", headers=customer)
    assert cleared.status_code == 200
    assert cleared.json()["items"] == []


def test_carts_are_per_user(client, customer, qa):
    client.post(ADD, headers=customer, json={"productId": "product_001"})
    assert client.get("/api/v1/cart", headers=qa).json()["items"] == []


@pytest.mark.parametrize("quantity", [0, 11, -1])
def test_quantity_bounds(client, customer, quantity):
    response = client.post(ADD, headers=customer, json={"productId": "product_001", "quantity": quantity})
    assert response.status_code == 400
    assert response.json()["error"]["details"][0]["field"] == "quantity"


def test_quantity_cap_applies_to_the_total(client, customer):
    client.post(ADD, headers=customer, json={"productId": "product_001", "quantity": 8})
    response = client.post(ADD, headers=customer, json={"productId": "product_001", "quantity": 3})
    assert response.status_code == 400


def test_sold_out_and_low_stock(client, customer):
    sold_out = client.post(ADD, headers=customer, json={"productId": "product_020"})
    assert sold_out.status_code == 409
    assert error_code(sold_out) == "INSUFFICIENT_STOCK"
    assert client.post(ADD, headers=customer, json={"productId": "product_019"}).status_code == 201
    second = client.post(ADD, headers=customer, json={"productId": "product_019"})
    assert error_code(second) == "INSUFFICIENT_STOCK"


def test_unknown_product_and_missing_line(client, customer):
    assert error_code(client.post(ADD, headers=customer, json={"productId": "product_999"})) == "PRODUCT_NOT_FOUND"
    assert error_code(client.patch("/api/v1/cart/items/product_005", headers=customer, json={"quantity": 2})) == "CART_ITEM_NOT_FOUND"
    assert error_code(client.delete("/api/v1/cart/items/product_005", headers=customer)) == "CART_ITEM_NOT_FOUND"


def test_malformed_body(client, customer):
    response = client.post(ADD, headers={**customer, "Content-Type": "application/json"}, content=b"{not json")
    assert response.status_code == 400
    assert error_code(response) == "MALFORMED_REQUEST"
