package example

import java.util.UUID

import cats.implicits._
import example.TestData._
import example.TestModelHelpers._
import example.model._
import shapeless.{test => _, _}
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}

object AwesomeSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("AwesomeSpec")(
      test("go") {
        val result = Awesome.assembleUnordered(dbRowsHList, companyMkVis).sequence

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
              val Right(result) = Awesome.assembleUnordered(rows, companyMkVis).sequence
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
              val Right(result) = Awesome.assembleUnordered(rows, optCompanyMkVis).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      }
    )

  import Awesome._
  import Better.{Atom, EE}

  val employeeAtom: Atom[Employee, DbEmployee :: HNil] = new Atom[Employee, DbEmployee :: HNil] {
    override def name: String = "employee"
    override def construct(h: DbEmployee :: HNil): Either[EE, Employee] = {
      val db = h.head
      Right(
        Employee(
          db.id,
          db.name
        )
      )
    }
  }

  val departmentIdAtom: IdAtom[Department, UUID, DbDepartment] =
    new IdAtom[Department, UUID, DbDepartment] {
      override def getId(adb: DbDepartment): UUID = adb.id
    }

  val companyIdAtom: IdAtom[Company, UUID, DbCompany] = new IdAtom[Company, UUID, DbCompany] {
    override def getId(adb: DbCompany): UUID = adb.id
  }

  val departmentMkVis: MkParVis[Department, DbDepartment :: DbEmployee :: HNil] =
    Awesome.mkVisParent(
      departmentIdAtom,
      Awesome.mkVisAtom(employeeAtom),
      constructWithChild = (dbDepartment: DbDepartment, employees: Vector[Employee]) =>
        Right(
          Department(
            id = dbDepartment.id,
            name = dbDepartment.name,
            employees = employees
          )
        )
    )

  val optDepartmentMkVis: MkParVis[Department, Option[DbDepartment] :: Option[DbEmployee] :: HNil] =
    Awesome
      .mkVisParent(
        departmentIdAtom,
        Awesome.mkVisAtom(employeeAtom).optional,
        constructWithChild = (dbDepartment: DbDepartment, employees: Vector[Employee]) =>
          Right(
            Department(
              id = dbDepartment.id,
              name = dbDepartment.name,
              employees = employees
            )
          )
      )
      .optional

  val companyMkVis: MkParVis[Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    Awesome.mkVisParent(
      idAtom = companyIdAtom,
      mkVisChild = departmentMkVis,
      constructWithChild = Company.fromDb
    )

  val optCompanyMkVis
    : MkParVis[Company, DbCompany :: Option[DbDepartment] :: Option[DbEmployee] :: HNil] = {
    Awesome.mkVisParent(
      idAtom = companyIdAtom,
      mkVisChild = optDepartmentMkVis,
      constructWithChild = Company.fromDb
    )
  }

}
