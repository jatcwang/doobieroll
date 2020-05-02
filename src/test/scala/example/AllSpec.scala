package example

import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.test.environment.TestEnvironment
import zio.test._

object AllSpec extends DefaultRunnableSpec {
  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AllSpec")(
      HelloSpec.spec,
      TableColumnsSpec.spec,
    )
}
