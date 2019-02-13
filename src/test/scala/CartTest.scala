import java.net.URI
import akka.actor._
import akka.testkit._
import scala.concurrent.duration._

import akka.actor.ActorSystem
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import Cart._

class CartTest extends TestKit(ActorSystem("CartTest"))
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "cart" should "be initialized empty" in {
    val cart = Cart()
    cart.items shouldBe Map.empty
  }


  "cart" should "add few items" in {
    var cart = Cart()
    val ballURI_1 = URI.create("ball_1")
    val ball_1: Item = Item(ballURI_1, "ball_1", 10, 5)

    val ball_2: Item = Item(URI.create("ball_2"), "ball_2", 7, 15)

    cart = cart.addItem(ball_1)

    cart.count shouldBe 5

    cart = cart.addItem(ball_2)

    cart.count shouldBe 20

    cart = cart.addItem(ball_1)

    cart.items(ballURI_1).count shouldBe 10

  }

  "cart" should "not remove not existing item" in {
    val cart = Cart()
    val ballURI_1 = URI.create("ball_1")
    val ball_1: Item = Item(ballURI_1, "ball_1", 10, 5)
    cart.removeItem(ball_1).count shouldBe 0
  }

  "cart" should "remove few items" in {
    var cart = Cart()
    val ballURI_1 = URI.create("ball_1")
    val ball_1: Item = Item(ballURI_1, "ball_1", 10, 5)
    cart = cart.addItem(ball_1)

    cart.count shouldBe 5

    val ball_2: Item = Item(ballURI_1, "ball_1", 10, 2)
    cart = cart.removeItem(ball_2, ball_2.count)
    cart.count shouldBe 3
  }

}