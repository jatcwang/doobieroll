package example

import cats.implicits._
import example.TestData._
import example.TestModelHelpers._
import example.model._
import oru.{Atom, EE, Par, UngroupedAssembler}
import shapeless.{test => _, _}
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object AwesomeSpec extends DefaultRunnableSpec {
  import ExampleModelInstances._

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AwesomeSpec")(
      test("all non-nullable columns") {
        val result = UngroupedAssembler.assembleUngrouped(dbRowsHList).sequence

        val Right(companies) = result

        assert(normalizeCompanies(companies))(equalTo(expectedCompanies))

      },
      test("nullable children columns") {
        val dbRows = expectedCompaniesWithSomeEmptyChildren.flatMap(companyToOptDbRows)
        val result = UngroupedAssembler.assembleUngrouped(dbRows.map(dbRowToOptHlist))
        val Right(companies) = result.sequence
        assert(normalizeCompanies(companies))(equalTo(expectedCompaniesWithSomeEmptyChildren))
      },
      testM("Property: Roundtrip conversion from List[Company] <=> Db rows") {
        checkM(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToDbRows).map(dbRowToHlist).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) = UngroupedAssembler.assembleUngrouped(rows).sequence
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
              original.flatMap(companyToOptDbRows).map(dbRowToOptHlist).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) = UngroupedAssembler.assembleUngrouped(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      }
    )

  object ExampleModelInstances {
    implicit val employeeAtom: Atom[Employee, DbEmployee :: HNil] =
      new Atom[Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): Either[EE, Employee] =
          Employee.fromDb(h.head)
      }

    implicit val departmentPar: Par.Aux[Department, DbDepartment, Employee] =
      Par.make((d: DbDepartment) => d.id, Department.fromDb)

    implicit val companyPar: Par.Aux[Company, DbCompany, Department] =
      Par.make((d: DbCompany) => d.id, Company.fromDb)
  }

}
