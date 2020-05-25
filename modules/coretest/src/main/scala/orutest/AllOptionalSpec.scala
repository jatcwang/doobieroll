package orutest

import oru.hlist.AllOptional
import shapeless.test.illTyped
import shapeless._

// These are all compile time
object AllOptionalSpec {

  // Items that are not Option will be wrapped in option
  implicitly[AllOptional.Aux[Int :: HNil, Option[Int] :: HNil]]
  implicitly[AllOptional.Aux[Int :: Double :: HNil, Option[Int] :: Option[Double] :: HNil]]

  // Items that are already wrapped in option remains unchanged
  implicitly[AllOptional.Aux[Option[Int] :: HNil, Option[Int] :: HNil]]
  implicitly[AllOptional.Aux[Option[Int] :: Double :: HNil, Option[Int] :: Option[Double] :: HNil]]
  implicitly[AllOptional.Aux[Int :: Double :: HNil, Option[Int] :: Option[Double] :: HNil]]

  illTyped("""val x = implicitly[AllOptional.Aux[Int :: Double :: HNil, Option[Int] :: Double :: HNil]]""")

}
