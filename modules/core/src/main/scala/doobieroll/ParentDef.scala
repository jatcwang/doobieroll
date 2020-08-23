package doobieroll

import shapeless.{Id => _, _}
import cats.arrow.FunctionK

/**
  * Definition of a type with one or more children fields, converting from a DB column group ADb,
  * which may fail in the context of F.
  * @tparam F The validation context. For example Either[MyDbConversionError, ?] or Validated[MyDbConversionError, ?]
  * @tparam A The domain type
  * @tparam ADb The database column group type
  */
trait ParentDef[F[_], A, ADb] { self =>
  type Child <: HList
  type ChildVecs <: HList
  type Id

  def getId(adb: ADb): Id
  def constructWithChild(adb: ADb, children: ChildVecs): F[A]

  /** Modify the 'context' of the construction result.
    *  For example, you can turn a infallible ParentDef into one that return Either (but never fails).
    *  This allows you to reuse existing Parent definitions to work in a different context
    */
  final def mapK[G[_]](
    transform: FunctionK[F, G],
  ): ParentDef.AuxAll[G, A, ADb, Child, ChildVecs, Id] =
    new ParentDef[G, A, ADb] {
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

  def make[A, ADb, Child0, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0]) => A,
  ): ParentDef.Aux[cats.Id, A, ADb, Child0 :: HNil] =
    makeF[cats.Id, A, ADb, Child0, Id0](getId, constructWithChild)

  def make2[A, ADb, Child0, Child1, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1]) => A,
  ): ParentDef.Aux[cats.Id, A, ADb, Child0 :: Child1 :: HNil] =
    makeF2[cats.Id, A, ADb, Child0, Child1, Id0](getId, constructWithChild)

  def make3[A, ADb, Child0, Child1, Child2, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1], Vector[Child2]) => A,
  ): ParentDef.Aux[cats.Id, A, ADb, Child0 :: Child1 :: Child2 :: HNil] =
    makeF3[cats.Id, A, ADb, Child0, Child1, Child2, Id0](getId, constructWithChild)

  def make4[A, ADb, Child0, Child1, Child2, Child3, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1], Vector[Child2], Vector[Child3]) => A,
  ): ParentDef.Aux[cats.Id, A, ADb, Child0 :: Child1 :: Child2 :: Child3 :: HNil] =
    makeF4[cats.Id, A, ADb, Child0, Child1, Child2, Child3, Id0](getId, constructWithChild)

  def make5[A, ADb, Child0, Child1, Child2, Child3, Child4, Id0](
    getId: ADb => Id0,
    constructWithChild: (
      ADb,
      Vector[Child0],
      Vector[Child1],
      Vector[Child2],
      Vector[Child3],
      Vector[Child4],
    ) => A,
  ): ParentDef.Aux[cats.Id, A, ADb, Child0 :: Child1 :: Child2 :: Child3 :: Child4 :: HNil] =
    makeF5[cats.Id, A, ADb, Child0, Child1, Child2, Child3, Child4, Id0](getId, constructWithChild)

  def makeF[F[_], A, ADb, Child0, Id0](
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

  def makeF2[F[_], A, ADb, Child0, Child1, Id0](
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

  def makeF3[F[_], A, ADb, Child0, Child1, Child2, Id0](
    getId: ADb => Id0,
    constructWithChild: (ADb, Vector[Child0], Vector[Child1], Vector[Child2]) => F[A],
  ): ParentDef.Aux[F, A, ADb, Child0 :: Child1 :: Child2 :: HNil] = {
    val getId0 = getId
    val constructWithChild0 = constructWithChild
    new ParentDef[F, A, ADb] {
      override type Child = Child0 :: Child1 :: Child2 :: HNil
      override type ChildVecs = Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: HNil
      override type Id = Id0

      override def getId(adb: ADb): Id = getId0(adb)

      override def constructWithChild(
        adb: ADb,
        children: Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: HNil,
      ): F[A] = {
        val child0 :: child1 :: child2 :: HNil = children
        constructWithChild0(adb, child0, child1, child2)
      }
    }
  }

  def makeF4[F[_], A, ADb, Child0, Child1, Child2, Child3, Id0](
    getId: ADb => Id0,
    constructWithChild: (
      ADb,
      Vector[Child0],
      Vector[Child1],
      Vector[Child2],
      Vector[Child3],
    ) => F[A],
  ): ParentDef.Aux[F, A, ADb, Child0 :: Child1 :: Child2 :: Child3 :: HNil] = {
    val getId0 = getId
    val constructWithChild0 = constructWithChild
    new ParentDef[F, A, ADb] {
      override type Child = Child0 :: Child1 :: Child2 :: Child3 :: HNil
      override type ChildVecs =
        Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: Vector[Child3] :: HNil
      override type Id = Id0

      override def getId(adb: ADb): Id = getId0(adb)

      override def constructWithChild(
        adb: ADb,
        children: Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: Vector[Child3] :: HNil,
      ): F[A] = {
        val child0 :: child1 :: child2 :: child3 :: HNil = children
        constructWithChild0(adb, child0, child1, child2, child3)
      }
    }
  }

  def makeF5[F[_], A, ADb, Child0, Child1, Child2, Child3, Child4, Id0](
    getId: ADb => Id0,
    constructWithChild: (
      ADb,
      Vector[Child0],
      Vector[Child1],
      Vector[Child2],
      Vector[Child3],
      Vector[Child4],
    ) => F[A],
  ): ParentDef.Aux[F, A, ADb, Child0 :: Child1 :: Child2 :: Child3 :: Child4 :: HNil] = {
    val getId0 = getId
    val constructWithChild0 = constructWithChild
    new ParentDef[F, A, ADb] {
      override type Child = Child0 :: Child1 :: Child2 :: Child3 :: Child4 :: HNil
      override type ChildVecs =
        Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: Vector[Child3] :: Vector[
          Child4,
        ] :: HNil
      override type Id = Id0

      override def getId(adb: ADb): Id = getId0(adb)

      override def constructWithChild(
        adb: ADb,
        children: Vector[Child0] :: Vector[Child1] :: Vector[Child2] :: Vector[Child3] :: Vector[
          Child4,
        ] :: HNil,
      ): F[A] = {
        val child0 :: child1 :: child2 :: child3 :: child4 :: HNil = children
        constructWithChild0(adb, child0, child1, child2, child3, child4)
      }
    }
  }
}
