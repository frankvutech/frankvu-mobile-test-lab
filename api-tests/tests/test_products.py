import pytest

from conftest import error_code


def ids(response):
    return [item["id"] for item in response.json()["items"]]


@pytest.mark.smoke
def test_default_listing_is_first_page_by_id(client):
    response = client.get("/api/v1/products")
    assert response.status_code == 200
    body = response.json()
    assert (body["page"], body["size"], body["totalItems"], body["totalPages"]) == (1, 10, 20, 2)
    assert ids(response)[0] == "product_001"
    assert ids(response)[-1] == "product_010"


def test_product_detail(client):
    response = client.get("/api/v1/products/product_003")
    assert response.status_code == 200
    assert response.json() == {
        "id": "product_003",
        "name": "Product 003",
        "description": "Deterministic sample product number 003.",
        "category": "Garden",
        "priceCents": 1249,
        "stock": 10,
    }


def test_missing_product_is_404(client):
    response = client.get("/api/v1/products/product_999")
    assert response.status_code == 404
    assert error_code(response) == "PRODUCT_NOT_FOUND"


def test_search_is_case_insensitive(client):
    response = client.get("/api/v1/products", params={"q": "PRODUCT 01", "size": 50})
    assert ids(response) == [f"product_{n:03d}" for n in range(10, 20)]


def test_search_with_no_matches_returns_an_empty_page(client):
    body = client.get("/api/v1/products", params={"q": "zzz"}).json()
    assert body["items"] == []
    assert (body["totalItems"], body["totalPages"]) == (0, 0)


def test_category_filter(client):
    response = client.get("/api/v1/products", params={"category": "electronics"})
    assert ids(response) == ["product_002", "product_006", "product_010", "product_014", "product_018"]


def test_categories(client):
    assert client.get("/api/v1/categories").json() == ["Books", "Electronics", "Garden", "Toys"]


@pytest.mark.parametrize(
    ("sort", "first", "last"),
    [
        ("price_asc", "product_001", "product_020"),
        ("price_desc", "product_020", "product_001"),
        ("name_asc", "product_001", "product_020"),
        ("name_desc", "product_020", "product_001"),
    ],
)
def test_sorting(client, sort, first, last):
    result = ids(client.get("/api/v1/products", params={"sort": sort, "size": 50}))
    assert (result[0], result[-1]) == (first, last)


def test_combined_filter_sort_and_pagination(client):
    body = client.get("/api/v1/products", params={"category": "Books", "sort": "price_desc", "size": 2, "page": 2}).json()
    assert [item["id"] for item in body["items"]] == ["product_009", "product_005"]
    assert (body["totalItems"], body["totalPages"]) == (5, 3)


def test_pagination_boundaries(client):
    last_page = client.get("/api/v1/products", params={"size": 7, "page": 3}).json()
    assert [item["id"] for item in last_page["items"]] == ["product_015", "product_016", "product_017", "product_018", "product_019", "product_020"]
    beyond = client.get("/api/v1/products", params={"size": 7, "page": 4}).json()
    assert beyond["items"] == [] and beyond["totalPages"] == 3


@pytest.mark.parametrize(
    ("params", "field"),
    [({"page": 0}, "page"), ({"page": "x"}, "page"), ({"size": 0}, "size"), ({"size": 51}, "size"), ({"sort": "random"}, "sort")],
)
def test_invalid_query_parameters(client, params, field):
    response = client.get("/api/v1/products", params=params)
    assert response.status_code == 400
    assert error_code(response) == "VALIDATION_ERROR"
    assert response.json()["error"]["details"][0]["field"] == field
