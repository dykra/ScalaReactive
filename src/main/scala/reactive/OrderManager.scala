import java.net.URI
import akka.actor._
import akka.actor.{ActorRef, FSM}
import akka.event.LoggingReceive
import scala.concurrent.Await
import scala.concurrent.duration._
import OrderManager._

object OrderManager {
  def apply(id: String): OrderManager = new OrderManager(id)

  sealed trait State

  case object Uninitialized extends State

  case object Open extends State

  case object InCheckout extends State

  case object InPayment extends State

  case object Finished extends State

  sealed trait Command

  case class AddItem(id: Item) extends Command

  case class RemoveItem(id: Item) extends Command

  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String) extends Command

  case object Buy extends Command

  case object Pay extends Command

  sealed trait Ack

  case object Done extends Ack

  sealed trait Data

  case class Empty() extends Data

  case class CartData(cartRef: ActorRef) extends Data

  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef) extends Data
  case class CartDataWithEmptyCart(cartRef: ActorRef) extends Data

  case class InCheckoutData(checkoutRef: ActorRef) extends Data

  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends Data

  case class InPaymentData(paymentRef: ActorRef) extends Data

  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef) extends Data

}


class OrderManager(id: String) extends FSM[State, Data] {

  import OrderManager._
  import CartManager._

  startWith(OrderManager.Uninitialized, OrderManager.Empty())

  when(OrderManager.Uninitialized) {
    case Event(OrderManager.AddItem(newItem), cartData: OrderManager.Empty) =>
      var cart = context.actorOf(Props(CartManager(id, 8.seconds, 10.seconds)), "cart")
      cart ! CartManager.AddItemToCart(newItem, sender)
      goto(OrderManager.Open) using OrderManager.CartData(cart)
  }

  when(OrderManager.Open) {
    case Event(InfoMessages.ItemAdded, prevCartData: OrderManager.CartData ) =>
      log.info("ORDER MANAGER: Item Added")
      stay using OrderManager.CartData(prevCartData.cartRef)

    case Event(OrderManager.AddItem(newItem), prevCartData: OrderManager.CartData ) =>
      print("ORDER MANAGER: add iotem ")
      prevCartData.cartRef ! CartManager.AddItemToCart(newItem, sender)
      stay using OrderManager.CartData(prevCartData.cartRef)

//    case Event(OrderManager.AddItem(newItem), prevCartData: OrderManager.CartDataWithEmptyCart ) =>
//      prevCartData.cartRef ! CartManager.AddItemToCart(newItem)
//      stay using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)

    // tracimy starego sendera
//    case Event(OrderManager.AddItem(newItem), prevCartData: OrderManager.CartDataWithSender) =>
//      prevCartData.cartRef ! CartManager.AddItemToCart(newItem)
//      stay using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)

    case Event(OrderManager.RemoveItem(itemToRemove), prevCartData: OrderManager.CartData) =>
      prevCartData.cartRef ! CartManager.RemoveItemFromCart(itemToRemove)
      stay using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)

      // tracimy starego sendera
    case Event(OrderManager.RemoveItem(itemToRemove), prevCartData: OrderManager.CartDataWithSender) =>
      prevCartData.cartRef ! CartManager.RemoveItemFromCart(itemToRemove)
      stay using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)

    case Event(InfoMessages.ItemRemoved, prevCartData: OrderManager.CartDataWithSender) =>
      prevCartData.sender ! OrderManager.Done
      log.info("Item removed")
      stay using OrderManager.CartData(prevCartData.cartRef)

//    case Event(InfoMessages.ItemRemovedEmpty, prevCartData: OrderManager.CartDataWithSender) =>
//      prevCartData.sender ! OrderManager.Done
//      log.info("CART Empty")
//      stay using OrderManager.CartDataWithEmptyCart(prevCartData.cartRef)


    case Event(OrderManager.Buy, prevCartData: OrderManager.CartData) =>
      prevCartData.cartRef ! CartManager.StartCheckout
      goto(OrderManager.InCheckout) using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)
//
//    case Event(OrderManager.Buy, prevCartData: OrderManager.CartDataWithSender) =>
//      prevCartData.cartRef ! CartManager.StartCheckout
//      goto(OrderManager.InCheckout) using OrderManager.CartDataWithSender(prevCartData.cartRef, sender)
  }

  when(OrderManager.InCheckout) {
    case Event(InfoMessages.CheckoutStarted(chekoutRef), prevCartData: OrderManager.CartDataWithSender) =>
      prevCartData.sender ! OrderManager.Done
      log.info("Checkout started")
      stay using OrderManager.InCheckoutData(chekoutRef)
    case Event(OrderManager.SelectDeliveryAndPaymentMethod(delivery: String, payment: String), prevCheckoutData: InCheckoutData) =>
      prevCheckoutData.checkoutRef ! Checkout.SelectPaymentMethod
      prevCheckoutData.checkoutRef ! Checkout.ProcessPayment
      goto(OrderManager.InPayment) using OrderManager.InCheckoutDataWithSender(prevCheckoutData.checkoutRef, sender)
  }


  when(OrderManager.InPayment) {
    case Event(InfoMessages.SelectedDeliveryAndPayment(paymentActor), prevCheckoutData: OrderManager.InCheckoutDataWithSender) =>
      prevCheckoutData.sender ! OrderManager.Done
      stay using OrderManager.InPaymentData(paymentActor)

    case Event(OrderManager.Pay, prevCartData: InPaymentData) =>
      prevCartData.paymentRef ! Payment.DoPayment("revolut")
      log.info("ORDER MANAGER: PAY")
      goto(OrderManager.Finished) using OrderManager.InPaymentDataWithSender(prevCartData.paymentRef, sender)
  }
  when(OrderManager.Finished) {
    case Event(Payment.PaymentConfirmed, prevCartData: InPaymentDataWithSender) =>
      log.info("ORDER MANAGER: Payment Confirmed")
      prevCartData.sender ! OrderManager.Done
      stay using prevCartData
    case Event(InfoMessages.CheckoutClosed, prevCartData: OrderManager.InPaymentDataWithSender) =>
      log.info("ORDER MANAGER: Checkout closed")
      stay using OrderManager.Empty()
    case Event(InfoMessages.CartEmpty, prevData: OrderManager.Empty) =>
      log.info("Cart Empty. Life cycle is done.")
      stay
  }
    whenUnhandled{
      case Event(e, s) ⇒
        log.info("received unhandled request {} in state {}/{}", e, stateName, s)
        stay
    }
  initialize()
}

object OrderManagerAgregator {

  sealed trait State

  case object InitialState extends State

  sealed trait Data

  case class Empty() extends Data

  case class AgregatorData(orderManager: ActorRef) extends Data

  case class Init()

}

class OrderManagerAgregator extends FSM[OrderManagerAgregator.State, OrderManagerAgregator.Data] {

  startWith(OrderManagerAgregator.InitialState, OrderManagerAgregator.Empty())

  when(OrderManagerAgregator.InitialState) {
    case Event(OrderManagerAgregator.Init, prevData: OrderManagerAgregator.Empty) => {
      val orderManager = context.actorOf(Props(OrderManager("test2")), "orderManager1")
      orderManager ! OrderManager.AddItem(Item(URI.create("test2"), "test2", 1))
      print("Wyslano Add Item\n")
      stay using OrderManagerAgregator.AgregatorData(orderManager)
    }


    case Event(OrderManager.Done, data: OrderManagerAgregator.AgregatorData) =>
      log.info("DONE")
      data.orderManager ! OrderManager.Buy
      data.orderManager ! OrderManager.SelectDeliveryAndPaymentMethod("a","a")
      stay
  }
}

object OrderManagerApp extends App {
  val system = ActorSystem("OrderManagerFSM")
  val mainActor = system.actorOf(Props[OrderManagerAgregator], "orderManagerAgregator")
  mainActor ! OrderManagerAgregator.Init

  Await.result(system.whenTerminated, Duration.Inf)
}
