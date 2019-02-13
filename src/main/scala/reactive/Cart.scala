import java.net.URI

case class Item(id: URI, name: String, price: BigDecimal, count: Int = 1)

case class Cart(items: Map[URI, Item] = Map.empty) {

  def addItem(it: Item): Cart = {
    val currentCount = if (items contains it.id) items(it.id).count else 0
    copy(items = items.updated(it.id, it.copy(count = currentCount + it.count)))
  }


  def removeItem(item: Item, count: Int = 1): Cart = {
    val itemToSearchFor = items.get(item.id)
    itemToSearchFor match {
      case None => this
      case Some(foundItem) =>
        val foundCount = foundItem.count
        val countAfter = foundCount - count
        if (countAfter <= 0)
          copy(items = items - item.id)
        else
          copy(items = items.updated(item.id, foundItem.copy(count = countAfter)))
    }
  }

  def count: Int = items.values.map(_.count).sum

  def isEmpty: Boolean = count <= 0
}