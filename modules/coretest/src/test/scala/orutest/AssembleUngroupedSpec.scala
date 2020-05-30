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
import com.softwaremill.quicklens._

object AssembleUngroupedSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AssembleUngroupedSpec")(
      test("all non-nullable columns") {
        val dbRowsHList: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] = {
          Vector(
            (c1db, d1db, e1db),
            (c1db, d1db, e2db),
            (c2db, d2db, e3db),
          ).map(_.productElements)
        }

        val result =
          UngroupedAssembler.assembleUngrouped(Infallible.companyAssembler)(dbRowsHList).sequence

        assert(result)(equalTo(expectedCompanies))

      },
      test("nullable children columns") {
        val dbRows = expectedCompaniesWithSomeEmptyChildren.flatMap(companyToOptDbRows)
        val result =
          UngroupedAssembler.assembleUngrouped(Infallible.companyOptAssembler)(
            dbRows.map(_.productElements),
          )
        assert(result)(equalTo(expectedCompaniesWithSomeEmptyChildren))
      },
      test("Parent with multiple children") {
        val dbRows = expectedEnterprise.flatMap(enterpriseToDbRows)
        val result = UngroupedAssembler
          .assembleUngrouped(Infallible.enterpriseAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(result)(equalTo(expectedEnterprise))
      },
      test("Error when the root level db item conversion failed") {
        val dbRows =
          expectedCompanies
            .updated(0, expectedCompanies(0).copy(name = "errComp"))
            .flatMap(companyToDbRows)

        val result = UngroupedAssembler
          .assembleUngrouped(Fallible.companyAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(result)(isLeft(equalTo(Err("company errComp"))))
      },
      test("Error when the parent db item conversion failed") {
        val companiesWithBadDepartment: Vector[Company] =
          modify(expectedCompanies)(_.at(0).departments.at(0).name).setTo("errDep")
        val dbRows = companiesWithBadDepartment
          .flatMap(companyToDbRows)

        val result = UngroupedAssembler
          .assembleUngrouped(Fallible.companyAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(result)(isLeft(equalTo(Err("department errDep"))))
      },
      test("Error when the leaf db item conversion failed") {
        val companiesWithBadEmployee: Vector[Company] =
          modify(expectedCompanies)(_.at(0).departments.at(0).employees.at(0).name).setTo("errEmp")
        val dbRows = companiesWithBadEmployee
          .flatMap(companyToDbRows)

        val result = UngroupedAssembler
          .assembleUngrouped(Fallible.companyAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(result)(isLeft(equalTo(Err("employee errEmp"))))
      },
      test("Error when one of the children of a multi-children parent fails") {
        val enterpriseWithBadInvoice: Vector[Enterprise] =
          modify(expectedEnterprise)(_.at(0).invoices.at(0).amount).setTo(0)
        val dbRows = enterpriseWithBadInvoice
          .flatMap(enterpriseToDbRows)

        val result = UngroupedAssembler
          .assembleUngrouped(Fallible.enterpriseAssembler)(dbRows.map(_.productElements))
          .sequence

        assert(result)(isLeft(equalTo(Err("invoice 0"))))
      },
      testM("Property: Roundtrip conversion from List[Company] <=> Db rows") {
        check(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          val rows = original
            .flatMap(companyToDbRows)
            .map(_.productElements)
          val result =
            UngroupedAssembler.assembleUngrouped(Infallible.companyAssembler)(rows).sequence
          assert(result)(equalTo(original))
        }
      },
      testM("Property: (Shuffled) Roundtrip conversion from List[Company] <=> Db rows") {
        checkM(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                UngroupedAssembler.assembleUngrouped(Infallible.companyAssembler)(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      testM(
        "Property: Roundtrip conversion from List[Company] <=> Db rows (with potentially empty department/employee list)",
      ) {
        checkM(Gen.listOf(genCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToOptDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                UngroupedAssembler.assembleUngrouped(Infallible.companyOptAssembler)(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      testM("Property: Roundtrip for Parent with multiple children") {
        checkM(Gen.listOf(genNonEmptyEnterprise).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(enterpriseToDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                UngroupedAssembler.assembleUngrouped(Infallible.enterpriseAssembler)(rows).sequence
              assert(normalizeEnterprise(result))(equalTo(normalizeEnterprise(original)))
            }
        }
      },
    )

}
