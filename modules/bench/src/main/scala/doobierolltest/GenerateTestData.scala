package doobierolltest

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import doobierolltest.TestDataHelpers._
import doobierolltest.model.Wrapper
import io.circe.syntax._
import zio._

object GenerateTestData extends App {

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    genNonEmptyCompany
      .map { c =>
        companyToDbRows(c).map { case (c, d, e) => Wrapper(c, d, e) }
      }
      .runCollectN(35)
      .map(_.flatten)
      .provideLayer(zio.test.environment.testEnvironment)
      .flatMap { c =>
        val bytes = c.asJson.spaces2.getBytes(StandardCharsets.UTF_8)
        zio.blocking.effectBlocking {
          Files.write(Paths.get("testdata.json"), bytes)
        }
      }
      .exitCode

}
