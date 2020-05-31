package doobieroll

import shapeless.{Id => _, _}
import cats.Id
import cats.arrow.FunctionK

trait ParentDef[F[_], A, ADb] { self =>
  type Child <: HList
  type ChildVecs <: HList
  type Id

  def getId(adb: ADb): Id
  def constructWithChild(adb: ADb, children: ChildVecs): F[A]

  /** Modify the 'context' of the construction result.
    *  For example, you can turn a infallible ParentDef into one that return Either (but never fails).
    *  This allows you to reuse existing Parent definitions to work in a different context */
  final def mapK[G[_]](
    transform: FunctionK[F, G],
  ): ParentDef.AuxAll[G, A, ADb, Child, ChildVecs, Id] = new ParentDef[G, A, ADb] {
    type Child = self.Child
    type ChildVecs = self.ChildVecs
    type Id = self.Id

    override def getId(adb: ADb): Id = self.getId(adb)
    override def constructWithChild(adb: ADb, children: ChildVecs): G[A] =
      transform.apply(self.constructWithChild(adb, children))
  }
}

object ParentDef {
  type Aux[F[_], A, ADb, Child0 <: HList] = ParentDef[F, A, ADb] {
    type Child = Child0
  }

  type AuxAll[F[_], A, ADb, Child0 <: HList, ChildVecs0 <: HList, Id0] = ParentDef[F, A, ADb] {
    type Child = Child0
    type ChildVecs = ChildVecs0
    type Id = Id0
  }

  def make[F[_], A, ADb, Child0, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0]) => F[A],
  ): ParentDef.Aux[F, A, ADb, Child0 :: HNil] = {
    val getId0 = getId
    val constructWithChild0 = constructWithChild
    new ParentDef[F, A, ADb] {
      override type Child = Child0 :: HNil
      override type ChildVecs = Vector[Child0] :: HNil
      override type Id = Id0

      override def getId(adb: ADb): Id = getId0(adb)

      override def constructWithChild(adb: ADb, children: Vector[Child0] :: HNil): F[A] =
        constructWithChild0(adb, children.head)
    }
  }

  def make2[F[_], A, ADb, Child0, Child1, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1]) => F[A],
  ): ParentDef.Aux[F, A, ADb, Child0 :: Child1 :: HNil] = {
    val getId0 = getId
    val constructWithChild0 = constructWithChild
    new ParentDef[F, A, ADb] {
      override type Child = Child0 :: Child1 :: HNil
      override type ChildVecs = Vector[Child0] :: Vector[Child1] :: HNil
      override type Id = Id0

      override def getId(adb: ADb): Id = getId0(adb)

      override def constructWithChild(
        adb: ADb,
        children: Vector[Child0] :: Vector[Child1] :: HNil,
      ): F[A] = {
        val child0 :: child1 :: HNil = children
        constructWithChild0(adb, child0, child1)
      }
    }
  }
}

/** Definition of a parent type that cannot fail */
trait InfallibleParentDef[A, ADb] extends ParentDef[Id, A, ADb]

object InfallibleParentDef {
  type Aux[A, ADb, Child0 <: HList] = ParentDef[Id, A, ADb] {
    type Child = Child0
  }

  def make[A, ADb, Child0, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0]) => A,
  ): InfallibleParentDef.Aux[A, ADb, Child0 :: HNil] =
    ParentDef.make[Id, A, ADb, Child0, Id0](getId, constructWithChild)

  def make2[A, ADb, Child0, Child1, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1]) => A,
  ): InfallibleParentDef.Aux[A, ADb, Child0 :: Child1 :: HNil] =
    ParentDef.make2[Id, A, ADb, Child0, Child1, Id0](getId, constructWithChild)

}
