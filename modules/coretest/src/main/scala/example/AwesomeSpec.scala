package example

import cats.implicits._
import example.TestData._
import example.TestModelHelpers._
import example.model._
import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.{Atom, EE, Par, UngroupedAssembler}
import shapeless.{test => _, _}
import zio.test.Assertion._
import zio.test.environment.TestEnvironment
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import shapeless.syntax.std.tuple._

object AwesomeSpec extends DefaultRunnableSpec {
  import ExampleModelInstances._

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

  object ExampleModelInstances {
    import oru.syntax._

    val employeeAtom: Atom[Employee, DbEmployee :: HNil] =
      new Atom[Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): Either[EE, Employee] =
          Employee.fromDb(h.head)
      }

    val invoiceAtom: Atom[Invoice, DbInvoice :: HNil] =
      new Atom[Invoice, DbInvoice :: HNil] {
        override def construct(db: DbInvoice :: HNil): Either[EE, Invoice] = Invoice.fromDb(db.head)
      }

    val departmentPar: Par.Aux[Department, DbDepartment, Employee :: HNil] =
      Par.make((d: DbDepartment) => d.id, Department.fromDb)

    val companyPar: Par.Aux[Company, DbCompany, Department :: HNil] =
      Par.make((d: DbCompany) => d.id, Company.fromDb)

    val enterprisePar: Par.Aux[Enterprise, DbCompany, Department :: Invoice :: HNil] =
      Par.make2((d: DbCompany) => d.id, Enterprise.fromDb)

    val employeeAssembler: UngroupedAssembler[Employee, DbEmployee :: HNil] =
      employeeAtom.asUnordered
    val departmentAssembler
      : UngroupedParentAssembler[Department, DbDepartment :: DbEmployee :: HNil] =
      departmentPar.asUnordered(employeeAssembler)
    val companyAssembler
      : UngroupedParentAssembler[Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
      companyPar.asUnordered(departmentAssembler)
    val companyOptAssembler
      : UngroupedParentAssembler[Company, DbCompany :: Option[DbDepartment] :: Option[
        DbEmployee
      ] :: HNil] =
      companyPar.asUnordered(departmentPar.asUnordered(employeeAssembler.optional).optional)

    val invoiceAssembler: UngroupedAssembler[Invoice, DbInvoice :: HNil] = invoiceAtom.asUnordered

    val enterpriseAssembler: UngroupedAssembler.UngroupedParentAssembler[
      Enterprise,
      DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil
    ] = enterprisePar.asUnordered(
      departmentAssembler,
      invoiceAssembler
    )
  }

}
