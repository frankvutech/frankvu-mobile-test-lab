package com.mobiletestplayground.core.domain

import com.mobiletestplayground.core.domain.access.AccessDecision
import com.mobiletestplayground.core.domain.access.AccessLevel
import com.mobiletestplayground.core.domain.access.AccessPolicy
import com.mobiletestplayground.core.domain.catalog.PriceFormatter
import com.mobiletestplayground.core.domain.deeplink.DeepLink
import com.mobiletestplayground.core.domain.deeplink.DeepLinkParser
import com.mobiletestplayground.core.domain.list.DynamicListQuery
import com.mobiletestplayground.core.domain.list.PagedItems
import com.mobiletestplayground.core.domain.list.SortOrder
import com.mobiletestplayground.core.domain.misc.DateMath
import com.mobiletestplayground.core.domain.misc.GestureType
import com.mobiletestplayground.core.domain.misc.PermissionStatus
import com.mobiletestplayground.core.domain.misc.PermissionStatusResolver
import com.mobiletestplayground.core.domain.misc.SwipeClassifier
import com.mobiletestplayground.core.domain.misc.ZoomRules
import com.mobiletestplayground.core.domain.model.DynamicItem
import com.mobiletestplayground.core.domain.model.Role
import com.mobiletestplayground.core.domain.model.Session
import com.mobiletestplayground.core.domain.model.User
import com.mobiletestplayground.core.domain.network.HttpErrorMapper
import com.mobiletestplayground.core.domain.network.NetworkError
import com.mobiletestplayground.core.domain.seed.SeedData
import com.mobiletestplayground.core.domain.validation.EmailValidator
import com.mobiletestplayground.core.domain.validation.PasswordPolicy
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessPolicyTest {
    private val customer = Session(User("c", "c@x.io", "C", Role.CUSTOMER), rememberMe = false)
    private val admin = Session(User("a", "a@x.io", "A", Role.ADMIN), rememberMe = false)

    @Test
    fun `unauthenticated users must log in for protected screens`() {
        assertEquals(AccessDecision.RequiresLogin, AccessPolicy.decide(AccessLevel.AUTHENTICATED, null))
        assertEquals(AccessDecision.RequiresLogin, AccessPolicy.decide(AccessLevel.ADMIN, null))
        assertEquals(AccessDecision.Granted, AccessPolicy.decide(AccessLevel.PUBLIC, null))
    }

    @Test
    fun `customers are denied admin screens and admins are granted`() {
        assertEquals(AccessDecision.Denied, AccessPolicy.decide(AccessLevel.ADMIN, customer))
        assertEquals(AccessDecision.Granted, AccessPolicy.decide(AccessLevel.ADMIN, admin))
        assertEquals(AccessDecision.Granted, AccessPolicy.decide(AccessLevel.AUTHENTICATED, customer))
    }

    @Test
    fun `disabling authentication in test mode opens customer screens but never admin screens`() {
        assertEquals(AccessDecision.Granted, AccessPolicy.decide(AccessLevel.AUTHENTICATED, null, authenticationRequired = false))
        assertEquals(AccessDecision.RequiresLogin, AccessPolicy.decide(AccessLevel.ADMIN, null, authenticationRequired = false))
    }
}

class ValidatorsTest {
    @Test
    fun `email rule`() {
        listOf("test@example.com", "a.b+c@sub.domain.org", " qa@example.com ").forEach { assertTrue(EmailValidator.isValid(it), it) }
        listOf("", "plain", "a@b", "a@b.c", "a b@c.de", "@example.com", "a@.com").forEach { assertFalse(EmailValidator.isValid(it), it) }
    }

    @Test
    fun `password policy reports the first violation`() {
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.firstViolation("Ab1!"))
        assertEquals(PasswordPolicy.Violation.TOO_LONG, PasswordPolicy.firstViolation("A1!" + "a".repeat(62)))
        assertEquals(PasswordPolicy.Violation.MISSING_UPPERCASE, PasswordPolicy.firstViolation("password1!"))
        assertEquals(PasswordPolicy.Violation.MISSING_DIGIT, PasswordPolicy.firstViolation("Password!"))
        assertEquals(PasswordPolicy.Violation.MISSING_SPECIAL, PasswordPolicy.firstViolation("Password1"))
        assertNull(PasswordPolicy.firstViolation("Password123!"))
    }

    @Test
    fun `every seeded password satisfies the policy`() {
        SeedData.accounts.forEach { assertNull(PasswordPolicy.firstViolation(it.password), it.user.email) }
    }
}

class DeepLinkParserTest {
    @Test
    fun `supported links`() {
        assertEquals(DeepLink.Home, DeepLinkParser.parse("mtp://playground"))
        assertEquals(DeepLink.Home, DeepLinkParser.parse("mtp://playground/home"))
        assertEquals(DeepLink.Scenario("forms"), DeepLinkParser.parse("mtp://playground/scenario/forms"))
        assertEquals(DeepLink.Product("product_003"), DeepLinkParser.parse("mtp://playground/product/product_003?ref=x"))
        assertEquals(DeepLink.Admin, DeepLinkParser.parse("mtp://playground/admin"))
        assertEquals(DeepLink.Detail(3), DeepLinkParser.parse("mtp://playground/detail/3"))
        assertEquals(DeepLink.TestMode, DeepLinkParser.parse("mtp://playground/test-mode/"))
    }

    @Test
    fun `unsupported links are rejected`() {
        listOf(
            "https://playground/home",
            "mtp://other/home",
            "mtp://playground/unknown",
            "mtp://playground/scenario/../admin",
            "mtp://playground/detail/0",
            "mtp://playground/detail/abc",
            "mtp://playground/a/b/c",
        ).forEach { assertNull(DeepLinkParser.parse(it), it) }
    }
}

class ListRulesTest {
    private val items = SeedData.dynamicItems + DynamicItem(6, "apricot")

    @Test
    fun `search is case insensitive and sort is stable`() {
        assertEquals(listOf("Apple", "apricot"), DynamicListQuery.apply(items, "AP", SortOrder.BY_ID).map { it.title })
        assertEquals(listOf("apricot", "Apple"), DynamicListQuery.apply(items, "ap", SortOrder.TITLE_DESCENDING).map { it.title })
        assertEquals("Elderberry", DynamicListQuery.apply(items, "", SortOrder.TITLE_DESCENDING).first().title)
        assertEquals(emptyList(), DynamicListQuery.apply(items, "zzz", SortOrder.BY_ID))
    }

    @Test
    fun `paging is deterministic and ends`() {
        val first = PagedItems.page(0)
        assertEquals("Row 001", first.items.first().title)
        assertEquals(20, first.items.size)
        assertTrue(first.hasMore)
        val last = PagedItems.page(4)
        assertEquals("Row 100", last.items.last().title)
        assertFalse(last.hasMore)
        assertTrue(PagedItems.page(5).items.isEmpty())
    }
}

class MiscRulesTest {
    @Test
    fun `http status mapping`() {
        assertEquals(NetworkError.BadRequest, HttpErrorMapper.map(400))
        assertEquals(NetworkError.Unauthorized, HttpErrorMapper.map(401))
        assertEquals(NetworkError.Forbidden, HttpErrorMapper.map(403))
        assertEquals(NetworkError.NotFound, HttpErrorMapper.map(404))
        assertEquals(NetworkError.Server(503), HttpErrorMapper.map(503))
        assertEquals(NetworkError.UnexpectedStatus(418), HttpErrorMapper.map(418))
    }

    @Test
    fun `permission states`() {
        assertEquals(PermissionStatus.GRANTED, PermissionStatusResolver.resolve(true, false, true))
        assertEquals(PermissionStatus.NOT_REQUESTED, PermissionStatusResolver.resolve(false, false, false))
        assertEquals(PermissionStatus.DENIED, PermissionStatusResolver.resolve(false, true, true))
        assertEquals(PermissionStatus.PERMANENTLY_DENIED, PermissionStatusResolver.resolve(false, false, true))
    }

    @Test
    fun `date math uses the fixed reference date`() {
        assertEquals(17, DateMath.daysFromReference(LocalDate.of(2025, 2, 1)))
        assertEquals(-14, DateMath.daysFromReference(LocalDate.of(2025, 1, 1)))
        assertEquals(25, DateMath.ageOn(LocalDate.of(2000, 1, 15)))
        assertEquals(25, DateMath.ageOn(LocalDate.of(2000, 1, 16), LocalDate.of(2025, 1, 16)))
        assertEquals(23, DateMath.ageOn(LocalDate.of(2001, 1, 16)))
    }

    @Test
    fun `zoom is clamped and formatted`() {
        assertEquals("1.0x", ZoomRules.label(0.2f))
        assertEquals("2.0x", ZoomRules.label(2f))
        assertEquals("4.0x", ZoomRules.label(9f))
    }

    @Test
    fun `swipes are classified by dominant axis`() {
        assertEquals(GestureType.SWIPE_LEFT, SwipeClassifier.classify(-200f, 30f, 50f))
        assertEquals(GestureType.SWIPE_DOWN, SwipeClassifier.classify(10f, 120f, 50f))
        assertNull(SwipeClassifier.classify(10f, 10f, 50f))
    }

    @Test
    fun `seed data is stable`() {
        assertEquals("Product 003", SeedData.products[2].name)
        assertEquals("product_003", SeedData.products[2].id)
        assertEquals("12.49", PriceFormatter.format(1249))
        assertEquals(30, SeedData.staticListItems.size)
        assertEquals("Item 030", SeedData.staticListItems.last())
    }
}
