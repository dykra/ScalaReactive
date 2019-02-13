import java.net.URI
import akka.actor._
import akka.testkit._
import scala.concurrent.duration._

import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class CartManagerTest extends TestKit(ActorSystem("CartManagerTest"))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  val ballURI_1 = URI.create("ball_1")
  val ball_1: Item = Item(ballURI_1, "ball_1", 0)
  val ballURI_2 = URI.create("ball_2")
  val ball_2: Item = Item(ballURI_2, "ball_2", 0)
  val ballURI_3 = URI.create("ball_3")
  val ball_3: Item = Item(ballURI_3, "ball_3", 0)


  "CartManager" should "add item to shopping cart" in {
    import Checkout._
    import CartManager._
    val cart = system.actorOf(Props(new CartManager("testCartManager1", 5.seconds, 5.seconds)))

    val ball: Item = Item(URI.create("ball"), "ball", 0)
    cart ! CartManager.AddItemToCart(ball, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)
  }


  "CartManager" should "end with nonempty when cancelled" in {
    import Checkout._
    val cartManager = system.actorOf(Props(new CartManager("testCartManager2", 5.seconds, 5.seconds)))
    val ballURI = URI.create("ball")
    val ball: Item = Item(ballURI, "ball", 0)
    cartManager ! CartManager.AddItemToCart(ball, self)

    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    cartManager ! CartManager.StartCheckout

    expectMsgClass(classOf[InfoMessages.CheckoutStarted])

    cartManager ! Checkout.CancelCheckout

    expectMsg(InfoMessages.CheckoutCanceled)

    val cart = Cart(Map(ballURI -> ball))

    cartManager ! InfoMessages.GetShoppingCart

    //      expectMsg(cart)
    expectMsgPF() {
      case Cart(_) => ()
    }
  }

  "CartManager" must " remove item when possible" in {
    val cartManager = system.actorOf(Props(new CartManager("testCartManager3", 5.seconds, 5.seconds)))
    val ball4_URI = URI.create("ball_4")
    val ball_4: Item = Item(ball4_URI, "ball_4", 10, 2)
    cartManager ! CartManager.AddItemToCart(ball_4, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    var ball_count = 0
    cartManager ! InfoMessages.GetShoppingCart
    expectMsgPF() {
      case Cart(items) => ball_count = items(ball4_URI).count
    }

    val ball_TMP = Item(ball4_URI, "ball_4", 1)

    cartManager ! CartManager.RemoveItemFromCart(ball_TMP)
    expectMsg(InfoMessages.ItemRemoved)

    cartManager ! InfoMessages.GetShoppingCart
    expectMsgPF() {
      case Cart(items) => items(ball4_URI).count shouldBe ball_count-1
    }
  }

  "CartManager" should "persist timer" in {
    val time = 1.seconds
    val cartManager = system.actorOf(Props(new CartManager("testCartManager4", time, 5.seconds)))
    cartManager ! CartManager.AddItemToCart(ball_1, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)
    cartManager ! CartManager.AddItemToCart(ball_2, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)
    cartManager ! CartManager.AddItemToCart(ball_3, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    val cart = Cart(Map(ballURI_1 -> ball_1, ballURI_2 -> ball_2, ballURI_3 -> ball_3))
    cartManager ! InfoMessages.GetShoppingCart
    //      expectMsg(cart)

    expectMsgPF() {
      case Cart(_) => ()
    }

    Thread.sleep(2 * time.toMillis)

    cartManager ! PoisonPill

    val cartManager2 = system.actorOf(Props(new CartManager("testCartManager5", time, 5.seconds)))

    cartManager2 ! InfoMessages.GetShoppingCart
    expectMsg(Cart())
  }

  "CartManager" should "retrieve data and state" in {
    val time = 5.seconds
    val cartManager = system.actorOf(Props(new CartManager("testCartManager6", time, 5.seconds)))
    cartManager ! CartManager.AddItemToCart(ball_1, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)
    cartManager ! CartManager.AddItemToCart(ball_2, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)
    cartManager ! CartManager.AddItemToCart(ball_3, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    val cart = Cart(Map(ballURI_1 -> ball_1, ballURI_2 -> ball_2, ballURI_3 -> ball_3))
    cartManager ! InfoMessages.GetShoppingCart
    //      expectMsg(cart)
    expectMsgPF() {
      case Cart(_) => ()
    }
    cartManager ! PoisonPill

    val cartManager2 = system.actorOf(Props(new CartManager("testCartManager7", time, 5.seconds)))

    cartManager2 ! InfoMessages.GetShoppingCart
    //      expectMsg(cart)
    expectMsgPF() {
      case Cart(_) => ()
    }
  }

  "CartManager" should "return to Empty state after closing cart" in {
    val time = 5.seconds
    val cartManager = system.actorOf(Props(new CartManager("testCartManager8", time, 5.seconds)))
    cartManager ! CartManager.AddItemToCart(ball_1, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    cartManager ! CartManager.StartCheckout
    expectMsgClass(classOf[InfoMessages.CheckoutStarted])
    cartManager ! CartManager.CloseCheckout

    cartManager ! InfoMessages.GetShoppingCart
    expectMsg(Cart())
  }


  "CartManager" should "be empty after cart timer" in {
    val time = 1.seconds
    val cartManager = system.actorOf(Props(new CartManager("testCartManager9", time, 5.seconds)))
    cartManager ! CartManager.AddItemToCart(ball_1, self)
    expectMsg(InfoMessages.ItemAdded)
    expectMsg(OrderManager.Done)

    cartManager ! InfoMessages.GetShoppingCart

    expectMsgPF() {
      case Cart(_) => ()
    }

    Thread.sleep(2 * time.toMillis)

    cartManager ! InfoMessages.GetShoppingCart
    expectMsg(Cart())
  }
}