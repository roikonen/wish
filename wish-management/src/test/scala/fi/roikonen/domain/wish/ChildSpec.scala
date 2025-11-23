package fi.roikonen.domain.wish

import fi.roikonen.domain.wish.{Child => ChildCommands}
import fi.roikonen.domain.wish.projection.{Child => ChildState}
import fi.roikonen.domain.wish.PrivateEvents.*
import fi.roikonen.structure.Command.CommandEffect

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.GivenWhenThen

import java.time.Instant
import java.util.UUID

class ChildSpec extends AnyFeatureSpec with GivenWhenThen {

  private def emptyState(childId: String = "simo"): ChildState =
    ChildState(
      id = childId,
      wishes = Map.empty,
      onNaughtyListUntil = Instant.MIN,
      bannedUntil = Instant.MIN
    )

  Feature("Making wishes") {

    Scenario("Child is banned due to earlier naughty wishes") {
      Given("a child who is still banned because of naughty wishes")
      val now = Instant.now()
      val state = emptyState().copy(
        bannedUntil = now.plusSeconds(60)
      )

      And("a new normal wish")
      val cmd = ChildCommands.MakeWish(
        wish = "A new bike",
        naughtyWish = Some(false),
        onNaughtyListUntil = None
      )

      When("the child tries to make a wish")
      val effect: CommandEffect = ChildCommands.makeWishHandler.handle(state, cmd)

      Then("no events are produced")
      assert(effect.events.isEmpty)

      And("the command response indicates the child is banned")
      assert(effect.commandResponse.code == 400)
      assert(
        effect.commandResponse.error.exists(
          _.startsWith("You are banned due to naughty wishes until")
        )
      )
    }

    Scenario("Child already has 10 wishes") {
      Given("a child with 10 existing wishes")
      val wishes = (1 to 10).map(_ => UUID.randomUUID() -> "Some wish").toMap
      val state = emptyState().copy(wishes = wishes)

      And("an additional wish is made")
      val cmd = ChildCommands.MakeWish(
        wish = "One extra wish",
        naughtyWish = Some(false),
        onNaughtyListUntil = None
      )

      When("the child tries to make the 11th wish")
      val effect: CommandEffect = ChildCommands.makeWishHandler.handle(state, cmd)

      Then("the wish is rejected due to too many wishes")
      assert(effect.commandResponse.code == 400)
      assert(effect.commandResponse.error.contains("Can't have more than ten wishes"))

      And("a WishRejected event with reason TooManyWishes is emitted")
      val Seq(event) = effect.events.collect { case e: WishRejected => e }
      assert(event.reason == RejectionReason.TooManyWishes)
      assert(event.childId == state.id)
      assert(event.wish == cmd.wish)
    }

    Scenario("Child is on the naughty list") {
      Given("a child who is on the naughty list")
      val now = Instant.now()
      val state = emptyState().copy(
        onNaughtyListUntil = now.plusSeconds(60)
      )

      And("the child makes a normal wish")
      val cmd = ChildCommands.MakeWish(
        wish = "A puppy",
        naughtyWish = Some(false),
        onNaughtyListUntil = None
      )

      When("the wish is handled")
      val effect: CommandEffect = ChildCommands.makeWishHandler.handle(state, cmd)

      Then("the wish is rejected due to being on the naughty list")
      assert(effect.commandResponse.code == 400)
      assert(
        effect.commandResponse.error.exists(
          _.startsWith("You naughty child! You can't wish until")
        )
      )

      And("a WishRejected event with reason ChildInNaughtyList is emitted")
      val Seq(event) = effect.events.collect { case e: WishRejected => e }
      assert(event.reason == RejectionReason.ChildInNaughtyList)
    }

    Scenario("Child submits an explicitly naughty wish") {
      Given("a child with clean state")
      val state = emptyState()

      And("a clearly naughty wish")
      val cmd = ChildCommands.MakeWish(
        wish = "Flamethrower",
        naughtyWish = Some(true),
        onNaughtyListUntil = None
      )

      When("the naughty wish is handled")
      val effect: CommandEffect = ChildCommands.makeWishHandler.handle(state, cmd)

      Then("the wish is rejected as naughty")
      assert(effect.commandResponse.code == 400)
      assert(effect.commandResponse.error.contains("Your wish was naughty, shame on you"))

      And("a WishRejected event with reason NaughtyWish is emitted")
      val Seq(event) = effect.events.collect { case e: WishRejected => e }
      assert(event.reason == RejectionReason.NaughtyWish)
      assert(event.childId == state.id)
      assert(event.wish == cmd.wish)
    }

    Scenario("Valid wish is approved") {
      Given("a child with no bans and fewer than 10 wishes")
      val state = emptyState()

      And("a non-naughty wish")
      val cmd = ChildCommands.MakeWish(
        wish = "Lego set",
        naughtyWish = Some(false),
        onNaughtyListUntil = None
      )

      When("the wish is handled")
      val effect: CommandEffect = ChildCommands.makeWishHandler.handle(state, cmd)

      Then("the wish is approved")
      // adjust if your default success code is always 200
      assert(effect.commandResponse.code == 200 || effect.commandResponse.code == 0)

      And("a WishApproved event is emitted for this child and wish text")
      val Seq(event) = effect.events.collect { case e: WishApproved => e }
      assert(event.childId == state.id)
      assert(event.wish == cmd.wish)
    }
  }

  Feature("Cancelling wishes") {

    Scenario("Cancelling an existing wish") {
      Given("a child with an existing wish")
      val wishId = UUID.randomUUID()
      val state = emptyState().copy(
        wishes = Map(wishId -> "Toy car")
      )

      And("a cancel command for that wish")
      val cmd = ChildCommands.CancelWish(wishId)

      When("the cancel command is handled")
      val effect: CommandEffect = ChildCommands.cancelWishHandler.handle(state, cmd)

      Then("the cancel succeeds")
      assert(effect.commandResponse.code == 200 || effect.commandResponse.code == 0)

      And("a WishCancelled event is emitted")
      assert(effect.events.size == 1)
      val event = effect.events.head
      assert(event.isInstanceOf[WishCancelled])

      val wc = event.asInstanceOf[WishCancelled]
      assert(wc.wishId == wishId)
      assert(wc.childId == state.id)
    }

    Scenario("Cancelling a non-existent wish") {
      Given("a child with no wishes")
      val state = emptyState().copy(
        wishes = Map.empty
      )

      And("a cancel command targeting a missing wish")
      val cmd = ChildCommands.CancelWish(UUID.randomUUID())

      When("the cancel command is handled")
      val effect: CommandEffect = ChildCommands.cancelWishHandler.handle(state, cmd)

      Then("no events are emitted")
      assert(effect.events.isEmpty)

      And("a 404 response is returned")
      assert(effect.commandResponse.code == 404)
      assert(effect.commandResponse.error.contains("Wish not found"))
    }
  }

  Feature("Marking a child naughty") {

    Scenario("Rejecting a past onNaughtyListUntil value") {
      Given("a child with clean state")
      val state = emptyState()

      And("a mark-naughty command with a past timestamp")
      val past = Instant.now().minusSeconds(60)
      val cmd = ChildCommands.MarkNaughty(past)

      When("the command is handled")
      val effect: CommandEffect = ChildCommands.markNaughtyHandler.handle(state, cmd)

      Then("no events are emitted")
      assert(effect.events.isEmpty)

      And("the response indicates the date has already expired")
      assert(effect.commandResponse.code == 400)
      assert(
        effect.commandResponse.error.exists(
          _.startsWith("The given date has already expired")
        )
      )
    }

    Scenario("Extending naughty period cancels all current wishes") {
      Given("a child who is already naughty but for a shorter period and has wishes")
      val wish1 = UUID.randomUUID()
      val wish2 = UUID.randomUUID()

      val state = emptyState().copy(
        wishes = Map(
          wish1 -> "RC car",
          wish2 -> "Drone"
        ),
        onNaughtyListUntil = Instant.now().plusSeconds(60)
      )

      And("a mark-naughty command that extends the naughty period")
      val newUntil = Instant.now().plusSeconds(3600)
      val cmd = ChildCommands.MarkNaughty(newUntil)

      When("the command is handled")
      val effect: CommandEffect = ChildCommands.markNaughtyHandler.handle(state, cmd)

      Then("the child becomes naughty until the new timestamp and all wishes are cancelled")
      assert(effect.commandResponse.code == 200)
      assert(
        effect.commandResponse.error.contains(
          s"Child ${state.id} marked naughty until $newUntil"
        )
      )

      And("a BecameNaughty event is emitted first")
      assert(effect.events.nonEmpty)
      val first = effect.events.head
      assert(first.isInstanceOf[BecameNaughty])

      val became = first.asInstanceOf[BecameNaughty]
      assert(became.childId == state.id)
      assert(became.onNaughtyListUntil == newUntil)

      And("WishCancelled is emitted for each active wish")
      val cancelledIds =
        effect.events.collect { case e: WishCancelled => e.wishId }.toSet

      assert(cancelledIds == Set(wish1, wish2))
    }

    Scenario("New naughty period does not extend existing one") {
      Given("a child who is already naughty for a long period")
      val now = Instant.now()
      val state = emptyState().copy(
        onNaughtyListUntil = now.plusSeconds(3600)
      )

      And("a command that would shorten the naughty period")
      val shorter = now.plusSeconds(60)
      val cmd = ChildCommands.MarkNaughty(shorter)

      When("the command is handled")
      val effect: CommandEffect = ChildCommands.markNaughtyHandler.handle(state, cmd)

      Then("no events are emitted")
      assert(effect.events.isEmpty)

      And("the response indicates the child was already on the naughty list")
      assert(effect.commandResponse.code == 400)
      assert(effect.commandResponse.error.contains("Child was already on the naughty list"))
    }
  }
}
