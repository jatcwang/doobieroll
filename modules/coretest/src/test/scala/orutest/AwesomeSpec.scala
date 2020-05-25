package orutest

import cats.implicits._
import oru.UngroupedAssembler
import orutest.TestData._
import orutest.TestDataInstances._
import orutest.TestModelHelpers._
import orutest.model._
import shapeless.syntax.std.tuple._
import shapeless.{test => _, _}
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object AwesomeSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AwesomeSpec")(
      test("all non-nullable columns") {
        val dbRowsHList: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] = {
          Vector(
            (c1db, d1db, e1db),
            (c1db, d1db, e2db),
            (c2db, d2db, e3db),
          ).map(_.productElements)
        }

        val result = UngroupedAssembler.assembleUngrouped(companyAssembler)(dbRowsHList).sequence

        val Right(companies) = result

        assert(companies)(equalTo(expectedCompanies))

      },
      test("nullable children columns") {
        val dbRows = expectedCompaniesWithSomeEmptyChildren.flatMap(companyToOptDbRows)
        val result =
          UngroupedAssembler.assembleUngrouped(companyOptAssembler)(dbRows.map(_.productElements))
        val Right(companies) = result.sequence
        assert(companies)(equalTo(expectedCompaniesWithSomeEmptyChildren))
      },
      test("Parent with multiple children") {
        val dbRows = expectedEnterprise.flatMap(enterpriseToDbRows)
        val Right(enterprises) = UngroupedAssembler
          .assembleUngrouped(enterpriseAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(enterprises)(equalTo(expectedEnterprise))
      },
      testM("Property: Roundtrip conversion from List[Company] <=> Db rows") {
        checkM(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) =
                UngroupedAssembler.assembleUngrouped(companyAssembler)(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      testM(
        "Property: Roundtrip conversion from List[Company] <=> Db rows (with potentially empty department/employee list)"
      ) {
        checkM(Gen.listOf(genCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToOptDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) =
                UngroupedAssembler.assembleUngrouped(companyOptAssembler)(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      testM("Property: Roundtrip for Parent with multiple children") {
        checkM(Gen.listOf(genNonEmptyEnterprise).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(enterpriseToDbRows).map(_.productElements).toList
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) =
                UngroupedAssembler.assembleUngrouped(enterpriseAssembler)(rows).sequence
              assert(normalizeEnterprise(result))(equalTo(normalizeEnterprise(original)))
            }
        }
      },
    )

}
