import akka.actor.{ActorRef, FSM}
import scala.concurrent.Await
import scala.concurrent.duration._
import Payment._

object Payment {
  def apply(checkoutRef: ActorRef): Payment = new Payment(checkoutRef)

  case class DoPayment(payment: String)

  sealed trait State

  case object InPayment extends State

  sealed trait Data

  case class PaymentData(checkoutRef: ActorRef) extends Data


  sealed trait Event
  case object PaymentReceived extends Event
  case object PaymentConfirmed extends Event

}

class Payment(checkoutRef: ActorRef) extends FSM[State, Data] {

  startWith(Payment.InPayment, Payment.PaymentData(checkoutRef))

  when(Payment.InPayment) {
    case Event(Payment.DoPayment(paymentAttrib), prevPaymentData: PaymentData) =>
      sender ! Payment.PaymentConfirmed
      log.info("PAYMENT: Payment is confirmed.")
      prevPaymentData.checkoutRef ! Payment.PaymentReceived
      stay using prevPaymentData
  }

}