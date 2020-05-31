package doobieroll.syntax

import cats.Id
import cats.arrow.FunctionK
import doobieroll.{LeafDef, ParentDef}

trait DefSyntax {
  implicit class ParentDefIdExtension[A, ADb](val parentDef: ParentDef[cats.Id, A, ADb]) {

    /** Convert an infallible defintion to one that may fail. You can use this when you need to compose
      *  an infallible definition with other fallible ones */
    def forEither[E]
      : ParentDef.AuxAll[Either[E, *], A, ADb, parentDef.Child, parentDef.ChildVecs, parentDef.Id] =
      parentDef.mapK(DefSyntax.idToEitherFunctionK[E])
  }

  implicit class LeafDefIdExtension[A, ADb](leafDef: LeafDef[cats.Id, A, ADb]) {

    /** Convert an infallible defintion to one that may fail. You can use this when you need to compose
      *  an infallible definition with other fallible ones */
    def forEither[E]: LeafDef[Either[E, *], A, ADb] = leafDef.mapK(DefSyntax.idToEitherFunctionK)
  }
}

object DefSyntax {
  private[syntax] implicit def idToEitherFunctionK[E]: FunctionK[cats.Id, Either[E, *]] =
    new FunctionK[cats.Id, Either[E, *]] {
      override def apply[A](value: Id[A]): Either[E, A] = Right(value)
    }
}
