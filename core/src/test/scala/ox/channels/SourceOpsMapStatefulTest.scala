package ox.channels

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import ox.*

class SourceOpsMapStatefulTest extends AnyFlatSpec with Matchers {

  behavior of "Source.mapStateful"

  it should "zip with index" in supervised {
    val c = Source.fromValues("a", "b", "c")

    val s = c.mapStateful(() => 0)((index, element) => (index + 1, (element, index)))

    s.toList shouldBe List(("a", 0), ("b", 1), ("c", 2))
  }

  it should "calculate a running total" in supervised {
    val c = Source.fromValues(1, 2, 3, 4, 5)

    val s = c.mapStateful(() => 0)((sum, element) => (sum + element, sum), Some.apply)

    s.toList shouldBe List(0, 1, 3, 6, 10, 15)
  }

  it should "be able to emit different values than incoming ones" in supervised {
    val c = Source.fromValues(1, 2, 3, 4, 5)

    val s = c.mapStateful(() => 0)((sum, element) => (sum + element, sum.toString), n => Some(n.toString))

    s.toList shouldBe List("0", "1", "3", "6", "10", "15")
  }

  it should "propagate errors in the mapping function" in supervised {
    // given
    given StageCapacity = StageCapacity(0) // so that the error isn't created too early
    val c = Source.fromValues("a", "b", "c")

    // when
    val s = c.mapStateful(() => 0) { (index, element) =>
      if (index < 2) (index + 1, element)
      else throw new RuntimeException("boom")
    }

    // then
    s.receive() shouldBe "a"
    s.receive() shouldBe "b"
    s.receiveOrClosed() should matchPattern {
      case ChannelClosed.Error(reason) if reason.getMessage == "boom" =>
    }
  }

  it should "propagate errors in the completion callback" in supervised {
    // given
    given StageCapacity = StageCapacity(0)
    val c = Source.fromValues("a", "b", "c")

    // when
    val s = c.mapStateful(() => 0)((index, element) => (index + 1, element), _ => throw new RuntimeException("boom"))

    // then
    s.receive() shouldBe "a"
    s.receive() shouldBe "b"
    s.receive() shouldBe "c"
    s.receiveOrClosed() should matchPattern {
      case ChannelClosed.Error(reason) if reason.getMessage == "boom" =>
    }
  }
}
