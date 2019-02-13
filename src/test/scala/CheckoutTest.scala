import java.net.URI
import akka.actor._
import akka.testkit._
import scala.concurrent.duration._
import Checkout._
import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CheckoutTest extends TestKit(ActorSystem("CheckoutTest"))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val cart = TestProbe()

  "Checkout" should "start with state sellecting delivery" in {
    val checkout = system.actorOf(Props(new Checkout("testCheckout1", 5.seconds, 5.seconds, cart.ref)))
    checkout ! GetStateName
    expectMsg(SelectingDelivery)
  }

  "Checkout" should "end with cancelled when cancelled" in {
    val checkout = system.actorOf(Props(new Checkout("testCheckout2", 5.seconds, 5.seconds, cart.ref)))
    checkout ! Checkout.CancelCheckout
    checkout ! GetStateName
    expectMsg(Cancelled)
  }


  "Checkout" should "persist checkout timers" in {

    val time = 1.seconds
    val checkout = system.actorOf(Props(new Checkout("testCheckout3", time, 2 *  time, cart.ref)))
    checkout ! SelectPaymentMethod

    checkout ! GetStateName
    expectMsg(SelectingPayment)

    Thread.sleep(2 * time.toMillis)

    checkout ! PoisonPill

    val checkout2 = system.actorOf(Props(new Checkout("testCheckout3", time, 2 *  time, cart.ref)))

    checkout2 ! GetStateName
    expectMsg(Cancelled)
  }

  "Checkout" should "persist payment timers" in {

    val time = 1.seconds
    val checkout = system.actorOf(Props(new Checkout("testCheckout4",  2 * time,  time, cart.ref)))
    checkout ! SelectPaymentMethod

    checkout ! GetStateName
    expectMsg(SelectingPayment)

    checkout ! ProcessPayment
    expectMsgClass(classOf[InfoMessages.SelectedDeliveryAndPayment])

    Thread.sleep(2 * time.toMillis)

    checkout ! PoisonPill

    val checkout2 = system.actorOf(Props(new Checkout("testCheckout4",  2 * time,  time, cart.ref)))

    checkout2 ! GetStateName
    expectMsgPF() {
      case InfoMessages.SelectedDeliveryAndPayment(payment) => payment ! Payment.DoPayment("")

    }
    expectMsg(Cancelled)
  }


  "Checkout" should "retrieve data and state" in {
    val time = 5.seconds
    var checkout = system.actorOf(Props(new Checkout("testCheckout5",  time,  time, cart.ref)))

    checkout ! SelectPaymentMethod

    checkout ! GetStateName
    expectMsg(SelectingPayment)

    checkout ! PoisonPill

    checkout = system.actorOf(Props(new Checkout("testCheckout5",  time,  time, cart.ref)))

    checkout ! ProcessPayment
    expectMsgClass(classOf[InfoMessages.SelectedDeliveryAndPayment])

    checkout ! GetStateName
    expectMsg(ProcessingPayment)
  }

}