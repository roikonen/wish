package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.PrivateEvents._
import fi.roikonen.domain.wish.{PublicEvent, Topic}
import fi.roikonen.structure.{PrivateEvent, StreamIdentifier, TestEventBus}

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.util.UUID

class PublishWishUpdatesSpec extends AnyFeatureSpec with GivenWhenThen {

  Feature("Publishing wish updates (approved and cancelled) to WishUpdates topic") {

    Scenario("Publishing a PublicEvent.WishApproved") {
      Given("a PublishWishUpdates handler with TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishWishUpdates(bus)

      And("a WishApproved ChildEvent")
      val id = UUID.randomUUID()
      val ce = WishApproved(id, "child-1", "A drone")

      When("the event is processed")
      handler.when(ce)

      Then("a PublicEvent.WishApproved is published to the WishUpdates topic")
      val (_, event, topic) =
        bus.lastPublished.getOrElse(fail("No event published"))

      assert(topic == Topic.WishUpdates.value)

      And("the event contains the same id, childId and wish text")
      val wa = event.asInstanceOf[PublicEvent.WishApproved]
      assert(wa.id == id)
      assert(wa.childId == "child-1")
      assert(wa.wish == "A drone")
    }

    Scenario("Publishing a PublicEvent.WishCancelled") {
      Given("a handler and TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishWishUpdates(bus)

      And("a WishCancelled ChildEvent")
      val id = UUID.randomUUID()
      val ce = WishCancelled(id, "child-1")

      When("the event is processed")
      handler.when(ce)

      Then("a PublicEvent.WishCancelled is published to the WishUpdates topic")
      val (_, event, topic) =
        bus.lastPublished.getOrElse(fail("No event published"))

      assert(topic == Topic.WishUpdates.value)

      And("the event contains the same wish id")
      val wc = event.asInstanceOf[PublicEvent.WishCancelled]
      assert(wc.id == id)
    }

    Scenario("Other ChildEvents produce no wish update") {
      Given("a handler and TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishWishUpdates(bus)

      And("a ChildEvent that is neither WishApproved nor WishCancelled")
      val ce = BecameNaughty("child-1", java.time.Instant.now())

      When("the event is processed")
      handler.when(ce)

      Then("no public event is published")
      assert(bus.published.isEmpty)
    }

    Scenario("Non-ChildEvent produces no output") {
      Given("a handler and TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishWishUpdates(bus)

      And("a PrivateEvent that is not a ChildEvent")
      val customEvent = new PrivateEvent {
        override def consistencyStream: StreamIdentifier = StreamIdentifier("custom")
        override def projectionStreams: Set[StreamIdentifier] = Set.empty
      }

      When("the event is processed")
      handler.when(customEvent)

      Then("no public event is published")
      assert(bus.published.isEmpty)
    }
  }
}
