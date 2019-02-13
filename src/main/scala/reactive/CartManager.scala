import akka.actor._
import akka.actor.ActorRef
import akka.persistence.fsm.PersistentFSM
import scala.concurrent.duration._
import akka.event.LoggingReceive
import CartManager._
import akka.actor.ActorRef
import Cart._
import scala.concurrent.Await
import OrderManager.CartDataWithSender
import scala.reflect._

object CartManager {
  def apply(id: String, cartItemsTimerTime: FiniteDuration,
            checkoutTimerTime: FiniteDuration): CartManager =
    new CartManager(id, cartItemsTimerTime, checkoutTimerTime)

  case class AddItemToCart(item: Item, replyTo: ActorRef)

  case class RemoveItemFromCart(item: Item)

  case class StartCheckout()

  case class CancelCheckout()

  case class CloseCheckout()

  sealed trait State extends PersistentFSM.FSMState {
    override def identifier: String = this.getClass.getName
  }

  case object Empty extends State

  case object NonEmpty extends State

  case object InCheckout extends State

  case object CartItemsTimerExpired

  sealed trait CartManagerData {
    def cart: Cart

    def updateCart(newCart: Cart): CartManagerData

    def addCheckoutRefAndSender(checkoutRef: ActorRef, sender: ActorRef): CartManagerDataWithCheckout

    def removeCheckoutRef(): CartManagerData
  }

  final case class CartData(cart: Cart = Cart()) extends CartManagerData {

    override def updateCart(newCart: Cart): CartManagerData = {
      copy(cart = newCart)
    }

    override def addCheckoutRefAndSender(checkoutRef: ActorRef, sender: ActorRef): CartManagerDataWithCheckout = {
      CartManagerDataWithCheckout(cart, checkoutRef, sender)
    }

    override def removeCheckoutRef(): CartManagerData = this
  }

  case class CartManagerDataWithCheckout(cart: Cart, checkoutReg: ActorRef, senderRef: ActorRef) extends CartManagerData {
    def removeCheckoutRef(): CartData = {
      CartData(cart)
    }

    override def updateCart(newCart: Cart): CartManagerData = {
      copy(cart = newCart)
    }

    override def addCheckoutRefAndSender(checkoutRef: ActorRef, sender: ActorRef): CartManagerDataWithCheckout = {
      this
    }
  }


  sealed trait CartManagerEvent

  case class AddingItemEvent(item: Item) extends CartManagerEvent

  case class RemovingItemEvent(item: Item) extends CartManagerEvent

  case class AddingCheckoutEventWithSender(checkout: ActorRef, customer: ActorRef) extends CartManagerEvent

  case object FreeingCartEvent extends CartManagerEvent

  case object RemovingCheckoutEvent extends CartManagerEvent

}

object InfoMessages {

  sealed trait Event

  case object CartEmpty extends Event

  case object ItemAdded extends Event

  case object ItemRemoved extends Event

  case object ItemRemovedEmpty extends Event

  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  case object CheckoutCanceled extends Event

  case object GetShoppingCart extends Event

  case class SelectedDeliveryAndPayment(payment: ActorRef) extends Event

  case object CheckoutClosed extends Event

}


class CartManager(id: String, cartItemsTimerTime: FiniteDuration,
                  checkoutTimerTime: FiniteDuration)(
                   implicit val domainEventClassTag: ClassTag[CartManagerEvent]
                 ) extends PersistentFSM[State, CartManagerData, CartManagerEvent] {


  override def persistenceId = id


  startWith(CartManager.Empty, CartData())

  override def applyEvent(cartManagerEvent: CartManagerEvent, currentData: CartManagerData): CartManagerData = {
    cartManagerEvent match {
      case AddingItemEvent(item) => currentData.updateCart(currentData.cart.addItem(item))
      case RemovingItemEvent(item) => currentData.updateCart(currentData.cart.removeItem(item, item.count))
      case AddingCheckoutEventWithSender(checkout, sender) => currentData.addCheckoutRefAndSender(checkout, sender)
      case RemovingCheckoutEvent => currentData.removeCheckoutRef()
      case FreeingCartEvent => currentData.updateCart(Cart())
    }
  }

  when(CartManager.Empty) {
    case Event(AddItemToCart(newItem, replyTo), _) ⇒
      log.info("Event: add item {} to cart in state {}.", newItem, stateName)
      setTimer("cartTimer", CartItemsTimerExpired, cartItemsTimerTime)
      sender ! InfoMessages.ItemAdded
      replyTo ! OrderManager.Done
      goto(CartManager.NonEmpty) applying AddingItemEvent(newItem)
  }

  when(CartManager.NonEmpty) {
    case Event(CartManager.AddItemToCart(newItem, replyTo), _) ⇒
      log.info("Event: add item {} to cart in state {}.", newItem, stateName)
      setTimer("cartTimer", CartItemsTimerExpired, cartItemsTimerTime)
      sender ! InfoMessages.ItemAdded
      replyTo ! OrderManager.Done
      stay applying AddingItemEvent(newItem)

    case Event(CartManager.RemoveItemFromCart(item), cartData: CartData) if cartData.cart.removeItem(item, item.count).count == 0 =>
      sender ! InfoMessages.ItemRemoved
      goto(Empty) applying FreeingCartEvent
    case Event(CartManager.RemoveItemFromCart(item), cartData: CartData) =>
      sender ! InfoMessages.ItemRemoved
      stay applying RemovingItemEvent(item)

    case Event(CartManager.StartCheckout, _) =>
      var checkout = context.actorOf(Props(Checkout("Checkout", 1.second,
        3.second, self)), "checkout")
      print("Event: Checkout started.")
      sender ! InfoMessages.CheckoutStarted(checkout)
      goto(InCheckout) applying AddingCheckoutEventWithSender(checkout, sender)
    case Event(CartItemsTimerExpired, _) =>
      log.info("Cart Timer")
      goto(Empty) applying FreeingCartEvent
  }

  when(InCheckout) {
    case Event(Checkout.CancelCheckout, cartData: CartManager.CartManagerDataWithCheckout) =>
      log.info("CART: Checkout cancelled.")
      sender ! InfoMessages.CheckoutCanceled
      goto(NonEmpty) applying RemovingCheckoutEvent

    case Event(CartManager.CloseCheckout, _) =>
      goto(CartManager.Empty) applying FreeingCartEvent

    case Event(InfoMessages.CheckoutClosed, prevCartData: CartManager.CartManagerDataWithCheckout) =>
      log.info("CART: Checkout closed.")
      prevCartData.senderRef ! InfoMessages.CartEmpty
      goto(CartManager.Empty) applying FreeingCartEvent
  }

  whenUnhandled {
    case Event(InfoMessages.GetShoppingCart, _) ⇒
      sender ! stateData.cart
      stay
    case Event(e, s) ⇒
      log.info("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }
}
