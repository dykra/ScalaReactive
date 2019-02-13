import akka.actor._
import akka.persistence.fsm.PersistentFSM
import akka.event.LoggingReceive
import scala.concurrent.Await
import scala.concurrent.duration._
import Checkout._
import OrderManager.CartDataWithSender
import scala.reflect.ClassTag
import akka.persistence.fsm.PersistentFSM

object Checkout {
  def apply(id: String, checkoutTimerTime: FiniteDuration,
            paymentTimerTime: FiniteDuration, cartRef: ActorRef): Checkout =
    new Checkout(id, checkoutTimerTime, paymentTimerTime, cartRef)

  case class StartCheckout()

  case class SelectPaymentMethod()

  case class ProcessPayment()

  case class CancelCheckout()

  case class Pay()

  case object GetStateName

  sealed trait CheckoutState extends PersistentFSM.FSMState {
    override def identifier: String = getClass.getName
  }

  case object SelectingDelivery extends CheckoutState

  case object SelectingPayment extends CheckoutState

  case object ProcessingPayment extends CheckoutState

  case object Cancelled extends CheckoutState

  case object Closed extends CheckoutState

  case object CheckoutTimerExpired

  case object PaymentTimerExpired

  sealed trait Data

  case class Empty() extends Data

  case class CheckoutDataWithCartRef(cartRef: ActorRef) extends Data

  case class CheckoutDataWithCartAndSenderRef(cartRef: ActorRef, senderRef: ActorRef) extends Data

  sealed trait Event

  case class SelectingDeliveryEvent(sender: ActorRef) extends Event

  case class SelectingPaymentEvent(sender: ActorRef, replyTo: ActorRef, payment: ActorRef) extends Event

  case object CleanCheckoutData extends Event
}

class Checkout(id: String, checkoutTimerTime: FiniteDuration,
               paymentTimerTime: FiniteDuration, initCartRef: ActorRef)(
                implicit val domainEventClassTag: ClassTag[Event]
              ) extends PersistentFSM[CheckoutState, Data, Event] {

  import Checkout._
  import Payment._

  override def persistenceId = id

  override def applyEvent(event: Checkout.Event, currentData: Checkout.Data): Checkout.Data = {
    event match {
      case CleanCheckoutData => CheckoutDataWithCartRef(initCartRef)
      case SelectingPaymentEvent(sender: ActorRef, replyTo: ActorRef, payment: ActorRef) => {
        replyTo ! InfoMessages.SelectedDeliveryAndPayment(payment)
        CheckoutDataWithCartAndSenderRef(initCartRef, sender)
      }
      case SelectingDeliveryEvent(sender: ActorRef) => CheckoutDataWithCartAndSenderRef(initCartRef, sender)
    }
  }


  startWith(Checkout.SelectingDelivery, CheckoutDataWithCartRef(initCartRef))
  setTimer("checkoutTimer", CheckoutTimerExpired, checkoutTimerTime)

  when(Checkout.SelectingDelivery) {
    case Event(CancelCheckout, checkoutData: CheckoutDataWithCartRef) =>
      log.info("Cancelled checkout")
      goto(Checkout.Cancelled) applying CleanCheckoutData

    case Event(SelectPaymentMethod, checkoutData: CheckoutDataWithCartRef) =>
      log.info("Delivery method selected. Going to state with selecting payment method.")
      goto(Checkout.SelectingPayment) applying SelectingDeliveryEvent(sender)
    case Event(CheckoutTimerExpired, checkoutData: CheckoutDataWithCartRef) =>
      log.info("Checkout expired in state {}.", stateName)
      goto(Checkout.Cancelled) applying CleanCheckoutData
  }

  when(Checkout.SelectingPayment) {
    case Event(CheckoutTimerExpired, checkoutData: CheckoutDataWithCartAndSenderRef) =>
      log.info("Checkout expired in state {}.", stateName)
      goto(Checkout.Cancelled) applying CleanCheckoutData
    case Event(ProcessPayment, checkoutData: CheckoutDataWithCartAndSenderRef) =>
      log.info("Payment is selected. Going to state processing payment.")
      setTimer("paymentTimer", PaymentTimerExpired, paymentTimerTime)
      var payment = context.actorOf(Props(Payment(self)), "payment")
      goto(Checkout.ProcessingPayment) applying SelectingPaymentEvent(checkoutData.senderRef, sender, payment)
  }

  when(Checkout.ProcessingPayment) {
    case Event(PaymentTimerExpired, _) =>
      log.info("Payment timer expired")
      goto(Checkout.Cancelled) applying CleanCheckoutData
    case Event(Payment.PaymentReceived, checkoutData: CheckoutDataWithCartAndSenderRef) =>
      log.info("CHECKOUT: payment received")
      checkoutData.cartRef ! InfoMessages.CheckoutClosed
      checkoutData.senderRef ! InfoMessages.CheckoutClosed
      goto(Checkout.Closed) applying CleanCheckoutData
  }

  when(Checkout.Closed) {
    case Event(PaymentTimerExpired, _) =>
      stop()
  }

  when(Checkout.Cancelled) {
    case Event(CheckoutTimerExpired, _) =>
      log.info("Checkout expired in state {}.", stateName)
      stop()
  }

  whenUnhandled {
    case Event(Checkout.GetStateName, _) =>
      sender ! stateName
      stay
    case Event(e, s) ⇒
      log.info("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }
}