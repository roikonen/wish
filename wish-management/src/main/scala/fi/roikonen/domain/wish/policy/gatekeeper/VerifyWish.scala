package fi.roikonen.domain.wish.policy.gatekeeper

import fi.roikonen.domain.wish.Child
import fi.roikonen.domain.wish.policy.gatekeeper.VerifyWish.checkIfNaughty
import fi.roikonen.domain.wish.projection.NaughtyList
import fi.roikonen.structure.{Gatekeeper, Journal}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class VerifyWish(childId: String, journal: Journal)(implicit ec: ExecutionContext)
    extends Gatekeeper[Child.MakeWish] {
  override def handle(command: Child.MakeWish): Future[Gatekeeper.Effect[Child.MakeWish]] = {
    for {
      isOnNaughtyList <- NaughtyList()
        .rehydrate(journal)
        .map((list, _) => list.children.getOrElse(childId, Instant.MIN))
      isNaughtyWish <- checkIfNaughty(command.wish)
    } yield Gatekeeper.Effect[Child.MakeWish](
      Right(
        command.copy(
          naughtyWish = Option(isNaughtyWish),
          onNaughtyListUntil = Option(isOnNaughtyList)
        )
      )
    )
  }
}

object VerifyWish {

  // This mimics an external system.
  def checkIfNaughty(wish: String): Future[Boolean] =
    Future.successful(naughtyKeywords.toSeq.exists(wish.toLowerCase().contains(_)))

  val naughtyKeywords: Set[String] =
    Set(
      "flamethrower",
      "chainsaw",
      "bazooka",
      "grenade",
      "rocketlauncher",
      "dynamite",
      "crossbow",
      "slingshot",
      "machete",
      "blowtorch",
      "laserbeam",
      "cannon",
      "harpoon",
      "landmine",
      "spear",
      "shuriken",
      "nunchucks",
      "warhammer",
      "lockpick",
      "crowbar",
      "taser",
      "stunbaton",
      "poison",
      "beartrap",
      "explosive",
      "fireworks",
      "napalm",
      "anvil",
      "wreckingball",
      "jackhammer",
      "batteringram",
      "bbgun",
      "airsoftgun",
      "paintballgun",
      "shotgun",
      "rifle",
      "pistol",
      "railgun",
      "boomerang",
      "whip",
      "trident",
      "caltrops",
      "smokebomb",
      "thermite",
      "flashbang",
      "tank",
      "missile",
      "sledgehammer",
      "riotshield",
      "spycam",
      "tripwire",
      "mousetrap",
      "stungun",
      "laserpointer",
      "grapplinghook",
      "airhorn",
      "megaphone",
      "vodka",
      "whiskey",
      "tequila",
      "rum",
      "gin",
      "beer",
      "wine",
      "cigarettes",
      "cigar",
      "vape",
      "lotteryticket",
      "pokerchips",
      "blackjackset",
      "casinokit",
      "firecracker"
    )
}
