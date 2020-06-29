package doobieroll

import cats.arrow.FunctionK

/**
  * Definition of a domain type with no children, converting from a database columns group type [[ADb]]
  * The construction may or may not fail, represented by the error context [[F]]
  * @tparam F The validation context. For example Either[MyDbConversionError, ?] or Validated[MyDbConversionError, ?]
  * @tparam A The domain type
  * @tparam ADb The database column group type
  */
trait LeafDef[F[_], A, ADb] { self =>
  def construct(db: ADb): F[A]

  final def mapK[G[_]](transform: FunctionK[F, G]): LeafDef[G, A, ADb] =
    LeafDef.instance[G, A, ADb](adb => transform(self.construct(adb)))

}

object LeafDef {
  def instance[F[_], A, ADb](f: ADb => F[A]): LeafDef[F, A, ADb] = new LeafDef[F, A, ADb] {
    override def construct(db: ADb): F[A] = f(db)
  }
}

/** A LeafDef where the construction cannot fail */
trait InfallibleLeafDef[A, ADb] extends LeafDef[cats.Id, A, ADb]
