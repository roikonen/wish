package fi.roikonen.domain.wish.policy.gatekeeper

import fi.roikonen.domain.wish.Child
import fi.roikonen.domain.wish.PrivateEvents.BecameNaughty
import fi.roikonen.domain.wish.policy.gatekeeper.VerifyWish.checkIfNaughty
import fi.roikonen.domain.wish.projection.NaughtyList
import fi.roikonen.structure.{Gatekeeper, Journal, PrivateEvent, StreamIdentifier}

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.time.Instant
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*

class VerifyWishSpec extends AnyFeatureSpec with GivenWhenThen {

  // Use the global EC in tests (VerifyWish takes an implicit ExecutionContext)
  given ExecutionContext = ExecutionContext.global

  /** Very small in-memory Journal stub that only supports what VerifyWish / NaughtyList.rehydrate
    * actually needs.
    *
    *   - getOffset(stream) is always 0
    *   - read(stream, 0) returns BecameNaughty events constructed from naughtyChildren when the
    *     stream is the NaughtyList stream
    */
  class FakeJournal(naughtyChildren: Map[String, Instant]) extends Journal {

    override def append(
      events: Seq[PrivateEvent],
      cursor: Option[Journal.Cursor]
    ): Future[Unit] =
      Future.successful(()) // not used in these tests

    override def read(
      stream: StreamIdentifier,
      offset: Journal.Cursor
    ): Future[(Seq[PrivateEvent], Journal.Cursor)] = {
      val naughtyStream = NaughtyList().sourceStream
      if (stream == naughtyStream) {
        val events: Seq[PrivateEvent] =
          naughtyChildren.toSeq.map { case (id, until) =>
            BecameNaughty(id, until): PrivateEvent
          }
        Future.successful(events -> 0L)
      } else {
        Future.successful(Seq.empty[PrivateEvent] -> 0L)
      }
    }

    override def storeOffset(stream: StreamIdentifier, cursor: Journal.Cursor): Future[Unit] =
      Future.successful(())

    override def getOffset(stream: StreamIdentifier): Journal.Cursor = 0L
  }

  Feature("VerifyWish gatekeeper enriches MakeWish commands") {

    Scenario("Child is on the naughty list and makes a harmless wish") {
      Given("a child who is on the naughty list in the NaughtyList projection")
      val childId = "simo"
      val naughtyUntil = Instant.now().plusSeconds(3600)

      val journal = new FakeJournal(
        naughtyChildren = Map(childId -> naughtyUntil)
      )

      And("a MakeWish command with no prior naughty metadata and a harmless wish")
      val original = Child.MakeWish(
        wish = "Lego set",
        naughtyWish = None,
        onNaughtyListUntil = None
      )

      And("a VerifyWish gatekeeper for that child")
      val gatekeeper = new VerifyWish(childId, journal)

      When("the gatekeeper handles the command")
      val effect: Gatekeeper.Effect[Child.MakeWish] =
        Await.result(gatekeeper.handle(original), 3.seconds)

      Then("the effect contains a successful, enriched MakeWish command")
      assert(effect.effect.isRight)

      val enriched = effect.effect.toOption.get

      And("the naughtyWish flag is set to false for a harmless wish")
      assert(enriched.naughtyWish.contains(false))

      And("onNaughtyListUntil is populated from the NaughtyList projection")
      assert(enriched.onNaughtyListUntil.contains(naughtyUntil))

      And("the original wish text is preserved")
      assert(enriched.wish == original.wish)
    }

    Scenario("Child is not on the naughty list and makes a harmless wish") {
      Given("a child who is NOT on the naughty list")
      val childId = "child-456"
      val journal = new FakeJournal(
        naughtyChildren = Map.empty
      )

      And("a MakeWish command with a harmless wish")
      val original = Child.MakeWish(
        wish = "New bike",
        naughtyWish = None,
        onNaughtyListUntil = None
      )

      And("a VerifyWish gatekeeper for that child")
      val gatekeeper = new VerifyWish(childId, journal)

      When("the gatekeeper handles the command")
      val effect = Await.result(gatekeeper.handle(original), 3.seconds)

      Then("the effect is successful")
      assert(effect.effect.isRight)

      val enriched = effect.effect.toOption.get

      And("the naughtyWish flag is false")
      assert(enriched.naughtyWish.contains(false))

      And("onNaughtyListUntil is Instant.MIN (child not in NaughtyList)")
      assert(enriched.onNaughtyListUntil.contains(Instant.MIN))

      And("the wish text is preserved")
      assert(enriched.wish == original.wish)
    }

    Scenario("Child makes a clearly naughty wish") {
      Given("a child who is not on the naughty list")
      val childId = "child-789"
      val journal = new FakeJournal(Map.empty)

      And("a wish that contains a known naughty keyword")
      val original = Child.MakeWish(
        wish = "I want a flamethrower",
        naughtyWish = None,
        onNaughtyListUntil = None
      )

      And("a VerifyWish gatekeeper for that child")
      val gatekeeper = new VerifyWish(childId, journal)

      When("the gatekeeper handles the command")
      val effect = Await.result(gatekeeper.handle(original), 3.seconds)

      Then("the effect is successful")
      assert(effect.effect.isRight)

      val enriched = effect.effect.toOption.get

      And("the naughtyWish flag is detected as true")
      assert(enriched.naughtyWish.contains(true))

      And("the child is still not on the NaughtyList (Instant.MIN)")
      assert(enriched.onNaughtyListUntil.contains(Instant.MIN))

      And("the wish text is preserved")
      assert(enriched.wish == original.wish)
    }
  }

  Feature("checkIfNaughty keyword detection") {

    Scenario("Harmless wish does not contain naughty keywords") {
      Given("a harmless wish without any naughty keywords")
      val wish = "A book and a teddy bear"

      When("checkIfNaughty is invoked")
      val result = Await.result(checkIfNaughty(wish), 1.second)

      Then("the result is false")
      assert(!result)
    }

    Scenario("Wish containing a naughty keyword is detected") {
      Given("a wish with a naughty keyword in any casing")
      val wish = "Could I please have a Flamethrower?"

      When("checkIfNaughty is invoked")
      val result = Await.result(checkIfNaughty(wish), 1.second)

      Then("the result is true")
      assert(result)
    }
  }
}
