package doobieroll

import cats.arrow.FunctionK

/** An atomic group of database columns that converts to a single entity (entity does not have children)
  *  The construction may or may not fail, represented by the error context F */
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
