package fi.roikonen.domain.wish.projection

import fi.roikonen.domain.wish.PrivateEvents.WishApproved
import fi.roikonen.domain.wish.Stream
import fi.roikonen.structure.StreamIdentifier

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.util.UUID

class WishSpec extends AnyFeatureSpec with GivenWhenThen {

  private def newProjection(id: UUID): Wish = Wish(id)

  Feature("Wish projection metadata") {

    Scenario("from returns the correct wish stream identifier") {
      Given("a wish projection with a known id")
      val id = UUID.randomUUID()
      val state = newProjection(id)

      When("from is accessed")
      val from = state.from

      Then("the stream identifier is based on the id and Wish stream type")
      val expected = StreamIdentifier(id.toString, Stream.Wish.value)
      assert(from == expected)
    }

    Scenario("default returns a projection with the same id and empty childId") {
      Given("a wish projection with some child id")
      val id = UUID.randomUUID()
      val state = Wish(id, childId = "child-123")

      When("default is called")
      val defaultState = state.default

      Then("the resulting projection has the same id but an empty childId")
      assert(defaultState.id == id)
      assert(defaultState.childId == "")
    }
  }

  Feature("Projecting WishApproved events") {

    Scenario("WishApproved sets the childId on the projection") {
      Given("a wish projection with no associated childId yet")
      val id = UUID.randomUUID()
      val state = newProjection(id)

      And("a WishApproved event for that wish and some child")
      val childId = "child-1"
      val event = WishApproved(
        wishId = id,
        childId = childId,
        wish = "Train set"
      )

      When("the event is projected")
      val newState = state.project(event)

      Then("the projection's childId is updated to the event's childId")
      assert(newState.childId == childId)

      And("the id remains the same")
      assert(newState.id == id)
    }
  }
}
