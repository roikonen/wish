package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.PrivateEvents._
import fi.roikonen.domain.wish.Stream
import fi.roikonen.structure.StreamIdentifier

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.time.Instant
import java.util.UUID

class ChildSpec extends AnyFeatureSpec with GivenWhenThen {

  private def emptyState(childId: String = "simo"): Child =
    Child(
      id = childId,
      wishes = Map.empty,
      onNaughtyListUntil = Instant.MIN,
      bannedUntil = Instant.MIN
    )

  Feature("Child projection metadata") {

    Scenario("from returns the correct stream identifier") {
      Given("a child projection with a known id")
      val childId = "child-42"
      val state   = emptyState(childId)

      When("from is accessed")
      val from = state.from

      Then("the stream identifier is based on the child id and Child stream type")
      val expected = StreamIdentifier(childId, Stream.Child.value)
      assert(from == expected)
    }

    Scenario("default returns an empty state with the same id") {
      Given("a child projection with some non-empty state")
      val childId = "child-99"
      val state = Child(
        id = childId,
        wishes = Map(UUID.randomUUID() -> "Something"),
        onNaughtyListUntil = Instant.now(),
        bannedUntil = Instant.now()
      )

      When("default is called")
      val defaultState = state.default

      Then("the resulting state has the same id but no wishes or bans")
      assert(defaultState.id == childId)
      assert(defaultState.wishes.isEmpty)
      assert(defaultState.onNaughtyListUntil == Instant.MIN)
      assert(defaultState.bannedUntil == Instant.MIN)
    }
  }

  Feature("Projecting WishApproved events") {

    Scenario("WishApproved adds the wish to the state") {
      Given("an empty child state")
      val childId = "child-1"
      val state   = emptyState(childId)

      And("a WishApproved event for that child")
      val wishId     = UUID.randomUUID()
      val wishText   = "New bike"
      val event      = WishApproved(wishId, childId, wishText)

      When("the event is projected")
      val newState = state.project(event)

      Then("the wish appears in the state's wishes map")
      assert(newState.wishes.contains(wishId))
      assert(newState.wishes(wishId) == wishText)

      And("the other fields remain unchanged")
      assert(newState.onNaughtyListUntil == state.onNaughtyListUntil)
      assert(newState.bannedUntil == state.bannedUntil)
    }
  }

  Feature("Projecting WishRejected events") {

    Scenario("WishRejected with NaughtyWish sets bannedUntil") {
      Given("a child state without a ban")
      val childId = "child-1"
      val state   = emptyState(childId)

      And("a WishRejected event with reason NaughtyWish and a known rejection time")
      val rejectionTime = Instant.parse("2025-01-01T12:00:00Z")
      val event = WishRejected(
        wishId = UUID.randomUUID(),
        childId = childId,
        wish = "Do something naughty",
        reason = RejectionReason.NaughtyWish,
        rejectionTime = rejectionTime
      )

      When("the event is projected")
      val newState = state.project(event)

      Then("bannedUntil is set to rejectionTime plus the configured ban duration (1 minute)")
      val expectedBanUntil = rejectionTime.plusSeconds(60) // 1.minute
      assert(newState.bannedUntil == expectedBanUntil)

      And("wishes and naughty list remain unchanged")
      assert(newState.wishes == state.wishes)
      assert(newState.onNaughtyListUntil == state.onNaughtyListUntil)
    }

    Scenario("WishRejected with other reasons leaves the state unchanged") {
      Given("a child state with some existing data")
      val childId = "child-1"
      val state = emptyState(childId).copy(
        bannedUntil = Instant.parse("2025-01-01T00:00:00Z"),
        onNaughtyListUntil = Instant.parse("2025-01-02T00:00:00Z")
      )

      And("a WishRejected event with reason TooManyWishes")
      val event = WishRejected(
        wishId = UUID.randomUUID(),
        childId = childId,
        wish = "One wish too many",
        reason = RejectionReason.TooManyWishes,
        rejectionTime = Instant.parse("2025-01-03T12:00:00Z")
      )

      When("the event is projected")
      val newState = state.project(event)

      Then("the state remains unchanged")
      assert(newState == state)
    }
  }

  Feature("Projecting WishCancelled events") {

    Scenario("WishCancelled removes the wish from the state") {
      Given("a child state with an existing wish")
      val childId = "child-1"
      val wishId  = UUID.randomUUID()
      val state = emptyState(childId).copy(
        wishes = Map(wishId -> "RC car")
      )

      And("a WishCancelled event for that wish")
      val event = WishCancelled(wishId, childId)

      When("the event is projected")
      val newState = state.project(event)

      Then("the wish is removed from the state's wishes map")
      assert(!newState.wishes.contains(wishId))
      assert(newState.wishes.isEmpty)
    }
  }

  Feature("Projecting WishFulfilled events") {

    Scenario("WishFulfilled removes the wish from the state") {
      Given("a child state with an existing wish")
      val childId = "child-1"
      val wishId  = UUID.randomUUID()
      val state = emptyState(childId).copy(
        wishes = Map(wishId -> "Drone")
      )

      And("a WishFulfilled event for that wish")
      val event = WishFulfilled(wishId, childId)

      When("the event is projected")
      val newState = state.project(event)

      Then("the wish is removed from the state's wishes map")
      assert(!newState.wishes.contains(wishId))
      assert(newState.wishes.isEmpty)
    }
  }

  Feature("Projecting BecameNaughty events") {

    Scenario("BecameNaughty updates onNaughtyListUntil") {
      Given("a child state with no naughty flag")
      val childId = "child-1"
      val state   = emptyState(childId)

      And("a BecameNaughty event with a future timestamp")
      val until = Instant.parse("2025-12-24T23:59:59Z")
      val event = BecameNaughty(childId, until)

      When("the event is projected")
      val newState = state.project(event)

      Then("onNaughtyListUntil is updated to the event's timestamp")
      assert(newState.onNaughtyListUntil == until)

      And("wishes and bannedUntil remain unchanged")
      assert(newState.wishes == state.wishes)
      assert(newState.bannedUntil == state.bannedUntil)
    }
  }
}
