package fr.acinq.eclair.channel

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.testkit.TestProbe
import fr.acinq.bitcoin.{BinaryData, Crypto}
import fr.acinq.eclair._
import lightning.{locktime, update_add_htlc}
import lightning.locktime.Locktime.Blocks
import org.junit.runner.RunWith
import org.scalatest.Ignore
import org.scalatest.junit.JUnitRunner

import scala.concurrent.duration._

/**
  * Created by PM on 26/04/2016.
  */
@Ignore
@RunWith(classOf[JUnitRunner])
class NominalChannelSpec extends BaseChannelTestClass {

  test("open channel and reach normal state") { case (alice, bob, pipe) =>
    val monitorA = TestProbe()
    alice ! SubscribeTransitionCallBack(monitorA.ref)
    val CurrentState(_, OPEN_WAIT_FOR_OPEN_WITHANCHOR) = monitorA.expectMsgClass(classOf[CurrentState[_]])

    val monitorB = TestProbe()
    bob ! SubscribeTransitionCallBack(monitorB.ref)
    val CurrentState(_, OPEN_WAIT_FOR_OPEN_NOANCHOR) = monitorB.expectMsgClass(classOf[CurrentState[_]])

    pipe !(alice, bob) // this starts the communication between alice and bob

    within(30 seconds) {

      val Transition(_, OPEN_WAIT_FOR_OPEN_WITHANCHOR, OPEN_WAIT_FOR_COMMIT_SIG) = monitorA.expectMsgClass(classOf[Transition[_]])
      val Transition(_, OPEN_WAIT_FOR_OPEN_NOANCHOR, OPEN_WAIT_FOR_ANCHOR) = monitorB.expectMsgClass(classOf[Transition[_]])

      val Transition(_, OPEN_WAIT_FOR_COMMIT_SIG, OPEN_WAITING_OURANCHOR) = monitorA.expectMsgClass(classOf[Transition[_]])
      val Transition(_, OPEN_WAIT_FOR_ANCHOR, OPEN_WAITING_THEIRANCHOR) = monitorB.expectMsgClass(classOf[Transition[_]])

      val Transition(_, OPEN_WAITING_OURANCHOR, OPEN_WAIT_FOR_COMPLETE_OURANCHOR) = monitorA.expectMsgClass(5 seconds, classOf[Transition[_]])
      val Transition(_, OPEN_WAITING_THEIRANCHOR, OPEN_WAIT_FOR_COMPLETE_THEIRANCHOR) = monitorB.expectMsgClass(classOf[Transition[_]])

      val Transition(_, OPEN_WAIT_FOR_COMPLETE_OURANCHOR, NORMAL) = monitorA.expectMsgClass(classOf[Transition[_]])
      val Transition(_, OPEN_WAIT_FOR_COMPLETE_THEIRANCHOR, NORMAL) = monitorB.expectMsgClass(classOf[Transition[_]])
    }
  }

  test("create and fulfill HTLCs") { case (alice, bob, pipe) =>
    pipe !(alice, bob) // this starts the communication between alice and bob

    within(30 seconds) {

      awaitCond(alice.stateName == NORMAL)
      awaitCond(bob.stateName == NORMAL)

      val R: BinaryData = "0102030405060708010203040506070801020304050607080102030405060708"
      val H = Crypto.sha256(R)

      alice ! CMD_ADD_HTLC(60000000, H, locktime(Blocks(4)))
      Thread.sleep(100)

      alice.stateData match {
        case d: DATA_NORMAL =>
          val List(update_add_htlc(_, _, h, _, _)) = d.ourChanges.proposed
          assert(h == bin2sha256(H))
      }
      bob.stateData match {
        case d: DATA_NORMAL =>
          val List(update_add_htlc(_, _, h, _, _)) = d.theirChanges.proposed
          assert(h == bin2sha256(H))
      }

      alice ! CMD_SIGN
      Thread.sleep(100)

      alice.stateData match {
        case d: DATA_NORMAL =>
          val htlc = d.theirCommit.spec.htlcs_out.head
          assert(htlc.rHash == bin2sha256(H))
      }
      bob.stateData match {
        case d: DATA_NORMAL =>
          val htlc = d.ourCommit.spec.htlcs_in.head
          assert(htlc.rHash == bin2sha256(H))
      }

      bob ! CMD_FULFILL_HTLC(1, R)
      bob ! CMD_SIGN
      alice ! CMD_SIGN

      Thread.sleep(200)

      alice.stateData match {
        case d: DATA_NORMAL =>
          assert(d.ourCommit.spec.htlcs_out.isEmpty)
          assert(d.ourCommit.spec.amount_us_msat == d.ourCommit.spec.initial_amount_us_msat - 60000000)
          assert(d.ourCommit.spec.amount_them_msat == d.ourCommit.spec.initial_amount_them_msat + 60000000)
      }
      bob.stateData match {
        case d: DATA_NORMAL =>
          assert(d.ourCommit.spec.htlcs_in.isEmpty)
          assert(d.ourCommit.spec.amount_us_msat == d.ourCommit.spec.initial_amount_us_msat + 60000000)
          assert(d.ourCommit.spec.amount_them_msat == d.ourCommit.spec.initial_amount_them_msat - 60000000)
      }
    }
  }

  test("close channel starting with no HTLC") { case (alice, bob, pipe) =>
//    pipe !(alice, bob) // this starts the communication between alice and bob
//
//    within(30 seconds) {
//
//      awaitCond(alice.stateName == NORMAL)
//      awaitCond(bob.stateName == NORMAL)
//
//      alice ! CMD_CLOSE(None)
//
//      awaitCond(alice.stateName == CLOSING)
//      awaitCond(bob.stateName == CLOSING)
//    }
  }

}