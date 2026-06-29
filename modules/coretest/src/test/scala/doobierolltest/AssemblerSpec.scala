package doobierolltest

import cats.syntax.all._
import doobierolltest.TestData._
import doobierolltest.TestDataInstances._
import doobierolltest.TestDataHelpers._
import doobierolltest.model._
import shapeless.{test => _, _}
import zio.Random
import zio.test.Assertion._
import zio.test._
import com.softwaremill.quicklens._

object AssemblerSpec extends ZIOSpecDefault {

  import shapeless.syntax.std.tuple._

  override def spec =
    suite("AssemblerSpec")(
      test("all non-nullable columns") {
        val dbRowsHList: Vector[DbCompany :: DbDepartment :: DbEmployee :: HNil] =
          Vector(
            (c1db, d1db, e1db),
            (c1db, d1db, e2db),
            (c2db, d2db, e3db),
          ).map(_.productElements)

        val result = Infallible.companyAssembler.assemble(dbRowsHList).sequence

        assert(result)(equalTo(expectedCompanies))

      },
      test("nullable children columns") {
        val dbRows = expectedCompaniesWithSomeEmptyChildren.flatMap(companyToOptDbRows)
        val result =
          Infallible.companyOptAssembler.assemble(dbRows.map(_.productElements))
        assert(result)(equalTo(expectedCompaniesWithSomeEmptyChildren))
      },
      test("Parent with multiple children") {
        val dbRows = expectedEnterprise.flatMap(enterpriseToDbRows)
        val result = Infallible.enterpriseAssembler.assemble(dbRows.map(_.productElements)).sequence

        assert(result)(equalTo(expectedEnterprise))
      },
      test("Error when the root level db item conversion failed") {
        val dbRows =
          expectedCompanies
            .updated(0, expectedCompanies(0).copy(name = "errComp"))
            .flatMap(companyToDbRows)

        val result = Fallible.companyAssembler.assemble(dbRows.map(_.productElements)).sequence

        assert(result)(isLeft(equalTo(Err("company errComp"))))
      },
      test("Error when the parent db item conversion failed") {
        val companiesWithBadDepartment: Vector[Company] =
          modify(expectedCompanies)(_.at(0).departments.at(0).name).setTo("errDep")
        val dbRows = companiesWithBadDepartment
          .flatMap(companyToDbRows)

        val result = Fallible.companyAssembler.assemble(dbRows.map(_.productElements)).sequence

        assert(result)(isLeft(equalTo(Err("department errDep"))))
      },
      test("Error when the leaf db item conversion failed") {
        val companiesWithBadEmployee: Vector[Company] =
          modify(expectedCompanies)(_.at(0).departments.at(0).employees.at(0).name).setTo("errEmp")
        val dbRows = companiesWithBadEmployee
          .flatMap(companyToDbRows)

        val result = Fallible.companyAssembler.assemble(dbRows.map(_.productElements)).sequence

        assert(result)(isLeft(equalTo(Err("employee errEmp"))))
      },
      test("Error when one of the children of a multi-children parent fails") {
        val enterpriseWithBadInvoice: Vector[Enterprise] =
          modify(expectedEnterprise)(_.at(0).invoices.at(0).amount).setTo(0)
        val dbRows = enterpriseWithBadInvoice
          .flatMap(enterpriseToDbRows)

        val result = Fallible.enterpriseAssembler.assemble(dbRows.map(_.productElements)).sequence

        assert(result)(isLeft(equalTo(Err("invoice 0"))))
      },
      test("Property: Roundtrip conversion from List[Company] <=> Db rows") {
        check(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          val rows = original
            .flatMap(companyToDbRows)
            .map(_.productElements)
          val result =
            Infallible.companyAssembler.assemble(rows).sequence
          assert(result)(equalTo(original))
        }
      },
      test("Property: (Shuffled) Roundtrip conversion from List[Company] <=> Db rows") {
        check(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          Random
            .shuffle(
              original.flatMap(companyToDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                Infallible.companyAssembler.assemble(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      test(
        "Property: Roundtrip conversion from List[Company] <=> Db rows (with potentially empty department/employee list)",
      ) {
        check(Gen.listOf(genCompany).map(_.toVector)) { original =>
          Random
            .shuffle(
              original.flatMap(companyToOptDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                Infallible.companyOptAssembler.assemble(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      test("Property: Roundtrip for Parent with multiple children") {
        check(Gen.listOf(genNonEmptyEnterprise).map(_.toVector)) { original =>
          Random
            .shuffle(
              original.flatMap(enterpriseToDbRows).map(_.productElements).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val result =
                Infallible.enterpriseAssembler.assemble(rows).sequence
              assert(normalizeEnterprise(result))(equalTo(normalizeEnterprise(original)))
            }
        }
      },
    ) @@ TestAspect.timeoutWarning(zio.Duration.fromSeconds(300))

}
