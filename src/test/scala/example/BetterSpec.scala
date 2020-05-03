package example

import java.util.UUID

import zio.test.{DefaultRunnableSpec, ZSpec}
import zio.test.environment.TestEnvironment
import zio.test._
import shapeless.{test => _, _}
import TestData._
import example.model.db.{DbCompany, DbDepartment, DbEmployee}

object BetterSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[TestEnvironment, Any] = suite("BetterSpec") {
    test("go") {
      val res = dbRows.map(r => implicitly[Generic.Aux[Tuple3[DbCompany, DbDepartment, DbEmployee], DbCompany :: DbDepartment ::  DbEmployee :: HNil]].to(r))
      val result = Better.go(companyP)(res)

      pprint.pprintln(result)

      zio.test.assertCompletes
    }
  }

  import Better._
  import model._
  import model.db._
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
          employees = employees.toVector
        )
      )
  )

  val companyP: Parent1[Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] = departmentP.forParent(companyDef, (dbCompany: DbCompany, departments: Vector[Department]) => {
    Right(
      Company(
        id = dbCompany.id,
        name = dbCompany.name,
        departments = departments.toVector
      )
    )
  })

}
