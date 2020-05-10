package example

import cats.implicits._
import example.Awesome.EE
import example.TestData._
import example.TestModelHelpers._
import example.model._
import oru.{Atom, Par}
import shapeless.{test => _, _}
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object AwesomeSpec extends DefaultRunnableSpec {
  import ExampleModelInstances._

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AwesomeSpec")(
      test("go") {
        val result = Awesome.assembleUnordered(dbRowsHList).sequence

        val Right(companies) = result

        assert(normalizeCompanies(companies))(equalTo(expectedCompanies))

      },
      testM("Property: Roundtrip conversion from List[Company] <=> Db rows") {
        checkM(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToDbRows).map(dbRowsToHlist).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) = Awesome.assembleUnordered(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      },
      testM("Property: Roundtrip conversion from List[Company] <=> Db rows (with potentially empty department/employee list)") {
        checkM(Gen.listOf(genCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToOptDbRows).map(dbRowsToOptHlist).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) = Awesome.assembleUnordered(rows).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      }
    )

  object ExampleModelInstances {
    implicit val employeeAtom: Atom[Employee, DbEmployee :: HNil] = new Atom[Employee, DbEmployee :: HNil] {
      override def construct(h: DbEmployee :: HNil): Either[EE, Employee] = Employee.fromDb(h.head)
    }

    implicit val departmentPar: Par.Aux[Department, DbDepartment, Employee] = Par.make((d: DbDepartment) => d.id, Department.fromDb)

    implicit val companyPar: Par.Aux[Company, DbCompany, Department] = Par.make((d: DbCompany) => d.id, Company.fromDb)
  }

}
