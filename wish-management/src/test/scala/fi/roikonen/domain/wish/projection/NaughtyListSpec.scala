package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.PrivateEvents.BecameNaughty
import fi.roikonen.structure.StreamIdentifier

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.time.Instant

class NaughtyListSpec extends AnyFeatureSpec with GivenWhenThen {

  private def emptyList: NaughtyList = NaughtyList(children = Map.empty)

  Feature("NaughtyList projection metadata") {

    Scenario("from returns the naughtiness stream identifier") {
      Given("an empty naughty list projection")
      val state = emptyList

      When("from is accessed")
      val from = state.sourceStream

      Then("the stream identifier represents the global naughtiness stream")
      val expected = StreamIdentifier("naughtiness")
      assert(from == expected)
    }

    Scenario("default returns an empty list") {
      Given("a naughty list projection with some entries")
      val state = NaughtyList(
        children = Map(
          "child-1" -> Instant.parse("2025-12-24T23:59:59Z")
        )
      )

      When("default is called")
      val defaultState = state.initialState

      Then("the resulting naughty list is empty")
      assert(defaultState.children.isEmpty)
    }
  }

  Feature("Projecting BecameNaughty events") {

    Scenario("BecameNaughty adds a child to the naughty list") {
      Given("an empty naughty list")
      val state = emptyList

      And("a BecameNaughty event for some child")
      val childId = "child-1"
      val until = Instant.parse("2025-12-24T23:59:59Z")
      val event = BecameNaughty(childId, until)

      When("the event is projected")
      val newState = state.project(event)

      Then("the child appears in the naughty list with the given expiration time")
      assert(newState.children.contains(childId))
      assert(newState.children(childId) == until)
    }

    Scenario("BecameNaughty overwrites an existing child's naughty until timestamp") {
      Given("a naughty list where a child already exists")
      val childId = "child-1"
      val oldUntil = Instant.parse("2025-12-01T00:00:00Z")
      val state = NaughtyList(
        children = Map(childId -> oldUntil)
      )

      And("a BecameNaughty event extends their naughty period")
      val newUntil = Instant.parse("2025-12-31T23:59:59Z")
      val event = BecameNaughty(childId, newUntil)

      When("the event is projected")
      val newState = state.project(event)

      Then("the child's naughty-until timestamp is updated")
      assert(newState.children(childId) == newUntil)
    }
  }
}
