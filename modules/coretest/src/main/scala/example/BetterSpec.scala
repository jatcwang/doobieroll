package example

import java.util.UUID

import cats.implicits._
import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.test.environment.TestEnvironment
import zio.test._
import zio.test.Assertion._
import shapeless.{test => _, _}
import TestData._
import TestModelHelpers._
import model._

object BetterSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Nothing] =
    suite("BetterSpec")(
      test("go") {
        val result = Better.assembleUnordered(companyP)(dbRowsHList).sequence

        val Right(companies) = result

        assert(normalizeCompanies(companies))(equalTo(expectedCompanies))

      },
      testM("con conversion works") {
        checkNM(50)(Gen.listOf(genNonEmptyCompany).map(_.toVector)) { original =>
          zio.random
            .shuffle(
              original.flatMap(companyToDbRows).toList,
            )
            .map(_.toVector)
            .map { rows =>
              val Right(result) = Better.assembleUnordered(companyP)(rows.map(dbRowsToHlist)).sequence
              assert(normalizeCompanies(result))(equalTo(normalizeCompanies(original)))
            }
        }
      }
    )

  import Better._
  import Atom._

  val companyDef: ParentDef[Company, UUID, DbCompany] =
    new ParentDef[Company, UUID, DbCompany] {
      override def name: String = "company"
      override def getId(dbs: DbCompany): UUID = dbs.id
    }

  val departmentDef: ParentDef[Department, UUID, DbDepartment] =
    new ParentDef[Department, UUID, DbDepartment] {
      override def name: String = "department"
      override def getId(dbs: DbDepartment): UUID = dbs.id
    }

  val employeeAtom: Atom[Employee, DbEmployee] = new Atom[Employee, DbEmployee] {
    override def name: String = "employee"
    override def construct(db: DbEmployee): Either[EE, Employee] =
      Right(
        Employee(
          db.id,
          db.name
        )
      )
  }

  val departmentP: Parent1[Department, DbDepartment :: DbEmployee :: HNil] = employeeAtom.forParent(
    departmentDef,
    (dbDepartment: DbDepartment, employees: Vector[Employee]) =>
      Right(
        Department(
          id = dbDepartment.id,
          name = dbDepartment.name,
          employees = employees
        )
      )
  )

  val companyP: Parent1[Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    departmentP.forParent(
      companyDef,
      (dbCompany: DbCompany, departments: Vector[Department]) => {
        Right(
          Company(
            id = dbCompany.id,
            name = dbCompany.name,
            departments = departments
          )
        )
      }
    )

}
