package orutest

import cats.Id
import oru.UngroupedAssembler.UngroupedParentAssembler
import oru.syntax._
import oru.{InfallibleParentDef, LeafDef, ParentDef, UngroupedAssembler}
import orutest.model._
import shapeless.{::, HNil}

object TestDataInstances {

  val employeeAtom: LeafDef[Id, Employee, DbEmployee :: HNil] =
    new LeafDef[Id, Employee, DbEmployee :: HNil] {
      override def construct(h: DbEmployee :: HNil): Employee =
        Employee.fromDb(h.head)
    }

  val invoiceAtom: LeafDef[Id, Invoice, DbInvoice :: HNil] =
    new LeafDef[Id, Invoice, DbInvoice :: HNil] {
      override def construct(db: DbInvoice :: HNil): Invoice = Invoice.fromDb(db.head)
    }

  val departmentPar: ParentDef.Aux[Id, Department, DbDepartment, Employee :: HNil] =
    InfallibleParentDef.make(
      (d: DbDepartment) => d.id,
      Department.fromDb,
    )

  val companyPar: ParentDef.Aux[Id, Company, DbCompany, Department :: HNil] =
    InfallibleParentDef.make((d: DbCompany) => d.id, Company.fromDb)

  val enterprisePar: ParentDef.Aux[Id, Enterprise, DbCompany, Department :: Invoice :: HNil] =
    InfallibleParentDef.make2((d: DbCompany) => d.id, Enterprise.fromDb)

  val employeeAssembler: UngroupedAssembler[Id, Employee, DbEmployee :: HNil] =
    employeeAtom.asUnordered
  val departmentAssembler
    : UngroupedParentAssembler[Id, Department, DbDepartment :: DbEmployee :: HNil] =
    departmentPar.asUnordered(employeeAssembler)
  val companyAssembler
    : UngroupedParentAssembler[Id, Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
    companyPar.asUnordered(departmentAssembler)
  val companyOptAssembler
    : UngroupedParentAssembler[Id, Company, DbCompany :: Option[DbDepartment] :: Option[
      DbEmployee,
    ] :: HNil] =
    companyPar.asUnordered(departmentPar.asUnordered(employeeAssembler.optional).optional)

  val invoiceAssembler: UngroupedAssembler[Id, Invoice, DbInvoice :: HNil] = invoiceAtom.asUnordered

  val enterpriseAssembler: UngroupedAssembler.UngroupedParentAssembler[
    Id,
    Enterprise,
    DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil,
  ] = enterprisePar.asUnordered(
    departmentAssembler,
    invoiceAssembler,
  )

}
