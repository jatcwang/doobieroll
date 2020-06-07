package doobierolltest

import cats.Id
import doobieroll.UngroupedAssembler.UngroupedParentAssembler
import doobieroll._
import doobierolltest.model._
import shapeless.{::, HNil}
import cats.implicits._

object TestDataInstances {

  object Infallible {

    val employeeLeaf: LeafDef[Id, Employee, DbEmployee :: HNil] =
      new LeafDef[Id, Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): Employee =
          Employee.fromDb(h.head)
      }

    val invoiceLeaf: LeafDef[Id, Invoice, DbInvoice :: HNil] =
      new LeafDef[Id, Invoice, DbInvoice :: HNil] {
        override def construct(db: DbInvoice :: HNil): Invoice = Invoice.fromDb(db.head)
      }

    val departmentParent: ParentDef.Aux[Id, Department, DbDepartment, Employee :: HNil] =
      InfallibleParentDef.make(
        (d: DbDepartment) => d.id,
        Department.fromDb,
      )

    val companyParent: ParentDef.Aux[Id, Company, DbCompany, Department :: HNil] =
      InfallibleParentDef.make((d: DbCompany) => d.id, Company.fromDb)

    val enterpriseParent: ParentDef.Aux[Id, Enterprise, DbCompany, Department :: Invoice :: HNil] =
      InfallibleParentDef.make2((d: DbCompany) => d.id, Enterprise.fromDb)

    val employeeAssembler: UngroupedAssembler[Id, Employee, DbEmployee :: HNil] =
      employeeLeaf.fdoobierollnordered
    val departmentAssembler
      : UngroupedParentAssembler[Id, Department, DbDepartment :: DbEmployee :: HNil] =
      departmentParent.forUnordered(employeeAssembler)
    val companyAssembler
      : UngroupedParentAssembler[Id, Company, DbCompany :: DbDepartment :: DbEmployee :: HNil] =
      companyParent.forUnordered(departmentAssembler)
    val companyOptAssembler
      : UngroupedParentAssembler[Id, Company, DbCompany :: Option[DbDepartment] :: Option[
        DbEmployee,
      ] :: HNil] =
      companyParent.forUnordered(departmentParent.forUnordered(employeeAssembler.optional).optional)

    val invoiceAssembler: UngroupedAssembler[Id, Invoice, DbInvoice :: HNil] =
      invoiceLeaf.fdoobierollnordered

    val enterpriseAssembler: UngroupedAssembler.UngroupedParentAssembler[
      Id,
      Enterprise,
      DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil,
    ] = enterpriseParent.fdoobierollnordered(
      departmentAssembler,
      invoiceAssembler,
    )
  }

  object Fallible {
    type ConvRes[+A] = Either[Err, A]

    val employeeLeaf: LeafDef[ConvRes, Employee, DbEmployee :: HNil] =
      new LeafDef[ConvRes, Employee, DbEmployee :: HNil] {
        override def construct(h: DbEmployee :: HNil): ConvRes[Employee] =
          Employee.fromDbFallible(h.head)
      }

    val invoiceLeaf: LeafDef[ConvRes, Invoice, DbInvoice :: HNil] =
      new LeafDef[ConvRes, Invoice, DbInvoice :: HNil] {
        override def construct(db: DbInvoice :: HNil): ConvRes[Invoice] =
          Invoice.fromDbFallible(db.head)
      }

    val departmentParent: ParentDef.Aux[ConvRes, Department, DbDepartment, Employee :: HNil] =
      ParentDef.make(
        (d: DbDepartment) => d.id,
        Department.fromDbFallible,
      )

    val companyParent: ParentDef.Aux[ConvRes, Company, DbCompany, Department :: HNil] =
      ParentDef.make((d: DbCompany) => d.id, Company.fromDbFallible)

    val enterpriseParent
      : ParentDef.Aux[ConvRes, Enterprise, DbCompany, Department :: Invoice :: HNil] =
      ParentDef.make2((d: DbCompany) => d.id, Enterprise.fromDbFallible)

    val employeeAssembler: UngroupedAssembler[ConvRes, Employee, DbEmployee :: HNil] =
      employeeLeaf.fdoobierollnordered
    val departmentAssembler
      : UngroupedParentAssembler[ConvRes, Department, DbDepartment :: DbEmployee :: HNil] =
      departmentParent.forUnordered(employeeAssembler)
    val companyAssembler: UngroupedParentAssembler[
      ConvRes,
      Company,
      DbCompany :: DbDepartment :: DbEmployee :: HNil,
    ] =
      companyParent.forUnordered(departmentAssembler)
    val companyOptAssembler
      : UngroupedParentAssembler[ConvRes, Company, DbCompany :: Option[DbDepartment] :: Option[
        DbEmployee,
      ] :: HNil] =
      companyParent.forUnordered(departmentParent.forUnordered(employeeAssembler.optional).optional)

    val invoiceAssembler: UngroupedAssembler[ConvRes, Invoice, DbInvoice :: HNil] =
      invoiceLeaf.fdoobierollnordered

    val enterpriseAssembler: UngroupedParentAssembler[
      ConvRes,
      Enterprise,
      DbCompany :: DbDepartment :: DbEmployee :: DbInvoice :: HNil,
    ] = enterpriseParent.fdoobierollnordered(
      departmentAssembler,
      invoiceAssembler,
    )
  }

}
