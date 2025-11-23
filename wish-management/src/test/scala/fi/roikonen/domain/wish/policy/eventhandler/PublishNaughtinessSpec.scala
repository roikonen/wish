package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.PrivateEvents._
import fi.roikonen.domain.wish.PublicEvent
import fi.roikonen.domain.wish.PublicEvent.NaughtinessDetected
import fi.roikonen.domain.wish.Topic
import fi.roikonen.structure.TestEventBus

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.time.Instant
import java.util.UUID

class PublishNaughtinessSpec extends AnyFeatureSpec with GivenWhenThen {

  Feature("Publishing naughtiness events") {

    Scenario("Publishing NaughtinessDetected when a naughty wish is rejected") {
      Given("a PublishNaughtiness event handler and a TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishNaughtiness(bus)

      And("a WishRejected event with NaughtyWish reason")
      val event = WishRejected(
        wishId = UUID.randomUUID(),
        childId = "child-1",
        wish = "Burn everything",
        reason = RejectionReason.NaughtyWish,
        rejectionTime = Instant.parse("2025-01-01T12:00:00Z")
      )

      When("the handler processes the event")
      handler.when(event)

      Then("a NaughtinessDetected public event is published to the naughtiness topic")
      val (_, publishedEvent, topic) =
        bus.lastPublished.getOrElse(fail("No event published"))

      assert(topic == Topic.Naughtiness.value)

      And("the published event contains correct childId, wish and rejectionTime")
      val nd = publishedEvent.asInstanceOf[NaughtinessDetected]
      assert(nd.id == "child-1")
      assert(nd.naughtyWish == "Burn everything")
      assert(nd.rejectionTime == Instant.parse("2025-01-01T12:00:00Z"))
    }

    Scenario("No public event is published when reason is not NaughtyWish") {
      Given("a handler and test bus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishNaughtiness(bus)

      And("a WishRejected for a non-naughty reason")
      val event = WishRejected(
        wishId = UUID.randomUUID(),
        childId = "child-1",
        wish = "Extra wish",
        reason = RejectionReason.TooManyWishes,
        rejectionTime = Instant.now()
      )

      When("the handler processes the event")
      handler.when(event)

      Then("no event is published")
      assert(bus.published.isEmpty)
    }

    Scenario("Non-WishRejected event produces no output") {
      Given("a handler and test bus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishNaughtiness(bus)

      And("a non-rejected event, e.g. WishApproved")
      val event = WishApproved(
        wishId = UUID.randomUUID(),
        childId = "child-1",
        wish = "Lego"
      )

      When("the handler processes the event")
      handler.when(event)

      Then("no event is published")
      assert(bus.published.isEmpty)
    }
  }
}
