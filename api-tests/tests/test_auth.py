import pytest

from conftest import ADMIN, CUSTOMER, LOCKED, bearer, error_code, login


@pytest.mark.smoke
def test_login_success_returns_token_and_user(client):
    response = client.post("/api/v1/auth/login", json={"email": "test@example.com", "password": "Password123!"})
    assert response.status_code == 200
    body = response.json()
    assert body["tokenType"] == "Bearer"
    assert body["token"].count(".") == 2  # a JWT
    assert body["user"] == {"id": "user_customer", "email": "test@example.com", "displayName": "Test Customer", "role": "CUSTOMER"}


def test_login_is_case_insensitive_for_email(client):
    response = client.post("/api/v1/auth/login", json={"email": "  TEST@Example.com ", "password": "Password123!"})
    assert response.status_code == 200


@pytest.mark.parametrize(
    ("payload", "status", "code"),
    [
        ({"email": "test@example.com", "password": "wrong"}, 401, "INVALID_PASSWORD"),
        ({"email": "nobody@example.com", "password": "Password123!"}, 401, "UNKNOWN_USER"),
        ({"email": LOCKED[0], "password": LOCKED[1]}, 403, "ACCOUNT_LOCKED"),
        ({"email": "", "password": ""}, 400, "VALIDATION_ERROR"),
        ({"email": "not-an-email", "password": "x"}, 400, "VALIDATION_ERROR"),
    ],
    ids=["wrong-password", "unknown-user", "locked", "empty", "invalid-email"],
)
def test_login_failures(client, payload, status, code):
    response = client.post("/api/v1/auth/login", json=payload)
    assert response.status_code == status
    assert error_code(response) == code


def test_validation_errors_list_every_field(client):
    response = client.post("/api/v1/auth/login", json={})
    fields = {detail["field"] for detail in response.json()["error"]["details"]}
    assert fields == {"email", "password"}


def test_me_returns_the_current_user(client, customer):
    response = client.get("/api/v1/auth/me", headers=customer)
    assert response.status_code == 200
    assert response.json()["email"] == "test@example.com"


def test_logout_revokes_the_token(client):
    headers = bearer(login(client, CUSTOMER))
    assert client.post("/api/v1/auth/logout", headers=headers).status_code == 204
    response = client.get("/api/v1/auth/me", headers=headers)
    assert response.status_code == 401
    assert error_code(response) == "INVALID_TOKEN"


def test_logout_revokes_only_that_token(client):
    first = bearer(login(client, ADMIN))
    second = bearer(login(client, ADMIN))
    client.post("/api/v1/auth/logout", headers=first)
    assert client.get("/api/v1/auth/me", headers=second).status_code == 200


def test_register_then_login(client):
    response = client.post(
        "/api/v1/auth/register",
        json={"displayName": "Ada Lovelace", "email": "Ada@Example.com", "password": "Analytical1!"},
    )
    assert response.status_code == 201
    assert response.json()["user"]["role"] == "CUSTOMER"
    assert response.json()["user"]["email"] == "ada@example.com"
    assert client.post("/api/v1/auth/login", json={"email": "ada@example.com", "password": "Analytical1!"}).status_code == 200


def test_register_duplicate_email_is_a_conflict(client):
    response = client.post(
        "/api/v1/auth/register",
        json={"displayName": "Copy", "email": "test@example.com", "password": "Password123!"},
    )
    assert response.status_code == 409
    assert error_code(response) == "EMAIL_TAKEN"


@pytest.mark.parametrize(
    ("password", "message"),
    [
        ("Ab1!", "Password must be at least 8 characters."),
        ("password1!", "Password needs an uppercase letter."),
        ("Password!", "Password needs a digit."),
        ("Password1", "Password needs a special character."),
    ],
)
def test_register_password_policy(client, password, message):
    response = client.post("/api/v1/auth/register", json={"displayName": "Ann", "email": "ann@example.com", "password": password})
    assert response.status_code == 400
    assert {"field": "password", "message": message} in response.json()["error"]["details"]


def test_password_reset_with_the_fixed_test_code(client):
    reset = "/api/v1/auth/password-reset"
    wrong = client.post(reset, json={"email": "test@example.com", "code": "000000", "newPassword": "NewPassword1!"})
    assert wrong.status_code == 400
    assert error_code(wrong) == "INVALID_RESET_CODE"
    unknown = client.post(reset, json={"email": "ghost@example.com", "code": "123456", "newPassword": "NewPassword1!"})
    assert unknown.status_code == 404
    weak = client.post(reset, json={"email": "test@example.com", "code": "123456", "newPassword": "weak"})
    assert error_code(weak) == "VALIDATION_ERROR"

    assert client.post(reset, json={"email": "test@example.com", "code": "123456", "newPassword": "NewPassword1!"}).status_code == 204
    assert client.post("/api/v1/auth/login", json={"email": "test@example.com", "password": "Password123!"}).status_code == 401
    assert client.post("/api/v1/auth/login", json={"email": "test@example.com", "password": "NewPassword1!"}).status_code == 200
