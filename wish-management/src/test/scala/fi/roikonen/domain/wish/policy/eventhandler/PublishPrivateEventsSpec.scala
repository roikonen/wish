package fi.roikonen.domain.wish.policy.eventhandler

import fi.roikonen.domain.wish.PrivateEvents._
import fi.roikonen.domain.wish.PublicEvent
import fi.roikonen.domain.wish.PublicEvent.PrivateEventPersisted
import fi.roikonen.domain.wish.Topic
import fi.roikonen.structure.{PrivateEvent, StreamIdentifier, TestEventBus}

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.util.UUID

import scala.concurrent.ExecutionContext.Implicits.global

class PublishPrivateEventsSpec extends AnyFeatureSpec with GivenWhenThen {

  Feature("Publishing all child private events as JSON to the PrivateEvents topic") {

    Scenario("ChildEvent is serialized and published") {
      Given("a handler and TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishPrivateEvents(bus)

      And("a WishApproved ChildEvent")
      val ce = WishApproved(
        wishId = UUID.randomUUID(),
        childId = "child-1",
        wish = "RC Car"
      )

      When("the event is processed")
      handler.when(ce)

      Then("a PrivateEventPersisted event is published to the PrivateEvents topic")
      val (_, pe, topic) =
        bus.lastPublished.getOrElse(fail("No event published"))

      assert(topic == Topic.PrivateEvents.value)

      And("the JSON inside PrivateEventPersisted contains the event data")
      val persisted = pe.asInstanceOf[PrivateEventPersisted]
      val json = persisted.json

      assert(json.contains("RC Car"))
      assert(json.contains("child-1"))
    }

    Scenario("Non-ChildEvent triggers no publish") {
      Given("a handler and TestEventBus")
      val bus = new TestEventBus[PublicEvent]
      val handler = PublishPrivateEvents(bus)

      And("a PrivateEvent that is not a ChildEvent")
      val customEvent = new PrivateEvent {
        override def consistencyStream: StreamIdentifier = StreamIdentifier("custom")
        override def projectionStreams: Set[StreamIdentifier] = Set.empty
      }

      When("the handler processes the event")
      handler.when(customEvent)

      Then("no event is published")
      assert(bus.published.isEmpty)
    }
  }
}
