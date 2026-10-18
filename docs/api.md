# API reference

The machine-readable specification is `backend/src/main/resources/openapi/openapi.yaml`, served at
`GET /openapi/openapi.yaml` and browsable at `/docs` (Swagger UI) when the backend runs. This page
summarises it with examples.

Base URL: `http://localhost:8080`. All endpoints are JSON. Authenticated endpoints need
`Authorization: Bearer <token>` from `/api/v1/auth/login` or `/register`.

## Errors

Every error has the same shape and a stable `code` — assert on the code, not the message:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "details": [{ "field": "email", "message": "Email is not valid." }]
  }
}
```

| Status | Codes |
|---|---|
| 400 | `VALIDATION_ERROR` (with `details`), `MALFORMED_REQUEST` (unreadable JSON), `INVALID_ID`, `INVALID_RESET_CODE` |
| 401 | `UNAUTHORIZED` (no token), `INVALID_TOKEN` (bad signature, expired, revoked), `UNKNOWN_USER`, `INVALID_PASSWORD` |
| 403 | `FORBIDDEN` (not an admin), `ACCOUNT_LOCKED` |
| 404 | `PRODUCT_NOT_FOUND`, `CART_ITEM_NOT_FOUND`, `WISHLIST_ITEM_NOT_FOUND`, `ORDER_NOT_FOUND`, `UNKNOWN_USER`, `ROUTE_NOT_FOUND` |
| 409 | `EMAIL_TAKEN`, `INSUFFICIENT_STOCK`, `CART_EMPTY`, `ALREADY_IN_WISHLIST`, `PRODUCT_EXISTS`, `PRODUCT_IN_USE` |
| 500 | `INTERNAL_ERROR` (no internal details leaked) |

## Endpoints

| Method | Path | Auth | Success |
|---|---|---|---|
| GET | `/health` | – | 200 `{"status":"ok"}` |
| GET | `/ready` | – | 200 `{"status":"ready","checks":{"database":"up"}}` / 503 |
| POST | `/api/v1/auth/register` | – | 201 token + user (CUSTOMER) |
| POST | `/api/v1/auth/login` | – | 200 token + user |
| POST | `/api/v1/auth/password-reset` | – | 204 (code `123456`) |
| GET | `/api/v1/auth/me` | user | 200 user |
| POST | `/api/v1/auth/logout` | user | 204, token revoked |
| GET | `/api/v1/products?q=&category=&sort=&page=&size=` | – | 200 page |
| GET | `/api/v1/products/{id}` | – | 200 product |
| GET | `/api/v1/categories` | – | 200 `["Books","Electronics","Garden","Toys"]` |
| GET / DELETE | `/api/v1/cart` | user | 200 cart / cleared cart |
| POST | `/api/v1/cart/items` | user | 201 cart (adds to existing quantity) |
| PATCH / DELETE | `/api/v1/cart/items/{productId}` | user | 200 cart |
| GET | `/api/v1/wishlist` | user | 200 wishlist |
| POST | `/api/v1/wishlist/items` | user | 201 wishlist |
| DELETE | `/api/v1/wishlist/items/{productId}` | user | 200 wishlist |
| POST | `/api/v1/orders` | user | 201 order (checkout) |
| GET | `/api/v1/orders` / `/api/v1/orders/{id}` | user | 200 own orders / order |
| GET / POST | `/api/v1/admin/products` | admin | 200 page / 201 product |
| PUT / DELETE | `/api/v1/admin/products/{id}` | admin | 200 / 204 |
| GET | `/api/v1/admin/orders` | admin | 200 all orders |
| PATCH | `/api/v1/admin/orders/{id}` | admin | 200 order (`PLACED`, `SHIPPED`, `CANCELLED`) |
| POST | `/api/v1/test/reset` | – (test support) | 204, seed restored |
| GET | `/api/v1/test/server-error` | – (test support) | always 500 `INTERNAL_ERROR` |

### Products: search, filter, sort, paginate

- `q`: case-insensitive substring of the name. `category`: case-insensitive exact match.
- `sort`: `id` (default), `name_asc`, `name_desc`, `price_asc`, `price_desc` (ties by id).
- `page` ≥ 1 (default 1); `size` 1–50 (default 10). A page past the end returns `items: []` with the
  correct `totalItems`/`totalPages`; invalid values return `400 VALIDATION_ERROR`.

```bash
curl 'http://localhost:8080/api/v1/products?category=Books&sort=price_desc&size=2&page=2'
```
```json
{
  "items": [
    { "id": "product_009", "name": "Product 009", "description": "Deterministic sample product number 009.",
      "category": "Books", "priceCents": 2749, "stock": 10 },
    { "id": "product_005", "name": "Product 005", "description": "Deterministic sample product number 005.",
      "category": "Books", "priceCents": 1749, "stock": 10 }
  ],
  "page": 2, "size": 2, "totalItems": 5, "totalPages": 3
}
```

### Login and an authenticated call

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"Password123!"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
```
```json
{ "id": "user_customer", "email": "test@example.com", "displayName": "Test Customer", "role": "CUSTOMER" }
```

### Cart and checkout

```bash
curl -s -X POST http://localhost:8080/api/v1/cart/items -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"productId":"product_001","quantity":2}'
curl -s -X POST http://localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN"
```
```json
{ "id": 1, "orderNumber": "ORDER-0001", "userId": "user_customer", "status": "PLACED", "totalCents": 1498,
  "items": [{ "productId": "product_001", "name": "Product 001", "priceCents": 749, "quantity": 2 }] }
```

Rules: quantity per line 1–10 (`400`), never more than stock (`409 INSUFFICIENT_STOCK`; `product_020`
is sold out, `product_019` has one unit). Checkout re-checks stock (two carts holding the last unit:
the second checkout gets `409`), decrements stock and empties the cart; an empty cart is `409 CART_EMPTY`.

### Deterministic scenarios

| Scenario | Request |
|---|---|
| Validation error | `POST /api/v1/auth/login` with `{}` |
| Unauthorized | any `/api/v1/cart` call without a token |
| Forbidden | `GET /api/v1/admin/orders` as `test@example.com` |
| Missing resource | `GET /api/v1/products/product_999` |
| Conflict | add `product_020` to the cart; register `test@example.com` again |
| Empty results | `GET /api/v1/products?q=zzz` |
| Server error | `GET /api/v1/test/server-error` |
| Pagination boundary | `GET /api/v1/products?size=7&page=3` (6 items), `page=4` (none) |
| Locked account | login as `locked@example.com` → `403 ACCOUNT_LOCKED` |
