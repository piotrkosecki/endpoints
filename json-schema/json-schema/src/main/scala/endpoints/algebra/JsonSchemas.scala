package endpoints.algebra

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

/**
  * An algebra interface for describing algebraic data types. Such descriptions
  * can be interpreted to produce a JSON schema of the data type, a JSON encoder,
  * a JSON decoder, etc.
  *
  * A description contains the fields of a case class and their type, and the
  * constructor names of a sealed trait.
  *
  * For instance, consider the following record type:
  *
  * {{{
  *   case class User(name: String, age: Int)
  * }}}
  *
  * Its description is the following:
  *
  * {{{
  *   object User {
  *     implicit val schema: JsonSchema[User] = (
  *       field[String]("name") zip
  *       field[Int]("age")
  *     ).invmap((User.apply _).tupled)(Function.unlift(User.unapply))
  *   }
  * }}}
  *
  * The description says that the record type has two fields, the first one has type `String` and is
  * named “name”, and the second one has type `Int` and name “age”.
  *
  * To describe sum types you have to explicitly “tag” each alternative:
  *
  * {{{
  *   sealed trait Shape
  *   case class Circle(radius: Double) extends Shape
  *   case class Rectangle(width: Double, height: Double) extends Shape
  *
  *   object Shape {
  *     implicit val schema: JsonSchema[Shape] = {
  *       val circleSchema = field[Double]("radius").invmap(Circle)(Function.unlift(Circle.unapply))
  *       val rectangleSchema = (
  *         field[Double]("width") zip
  *         field[Double]("height")
  *       ).invmap((Rectangle.apply _).tupled)(Function.unlift(Rectangle.unapply))
  *       (circleSchema.tagged("Circle") orElse rectangleSchema.tagged("Rectangle"))
  *         .invmap[Shape] {
  *           case Left(circle) => circle
  *           case Right(rect)  => rect
  *         } {
  *           c: Circle    => Left(c)
  *           r: Rectangle => Right(r)
  *         }
  *     }
  *   }
  * }}}
  */
trait JsonSchemas {

  /** The JSON schema of a type `A` */
  type JsonSchema[A]

  /** The JSON schema of a record type (case class) `A` */
  type Record[A] <: JsonSchema[A]

  /** A JSON schema containing the name of the type `A`.
    * Tagged schemas are useful to describe sum types (sealed traits).
    */
  type Tagged[A] <: JsonSchema[A]

  /** The JSON schema of a record with no fields */
  def emptyRecord: Record[Unit]

  /** The JSON schema of a record with a single field `name` of type `A` */
  def field[A](name: String, documentation: Option[String] = None)(implicit tpe: JsonSchema[A]): Record[A]

  /** The JSON schema of a record with a single optional field `name` of type `A` */
  def optField[A](name: String, documentation: Option[String] = None)(implicit tpe: JsonSchema[A]): Record[Option[A]]

  /** Tags a schema for type `A` with the given tag name */
  def taggedRecord[A](recordA: Record[A], tag: String): Tagged[A]

  /** The JSON schema of a coproduct made of the given alternative tagged records */
  def choiceTagged[A, B](taggedA: Tagged[A], taggedB: Tagged[B]): Tagged[Either[A, B]]

  /** The JSON schema of a record merging the fields of the two given records */
  def zipRecords[A, B](recordA: Record[A], recordB: Record[B]): Record[(A, B)]

  /** Transforms the type of the JSON schema */
  def invmapRecord[A, B](record: Record[A], f: A => B, g: B => A): Record[B]

  /** Transforms the type of the JSON schema */
  def invmapTagged[A, B](taggedA: Tagged[A], f: A => B, g: B => A): Tagged[B]

  /** Transforms the type of the JSON schema */
  def invmapJsonSchema[A, B](jsonSchema: JsonSchema[A], f: A => B, g: B => A): JsonSchema[B]

  /** Convenient infix operations */
  final implicit class RecordOps[A](recordA: Record[A]) {
    def zip[B](recordB: Record[B]): Record[(A, B)] = zipRecords(recordA, recordB)
    def invmap[B](f: A => B)(g: B => A): Record[B] = invmapRecord(recordA, f, g)
    def tagged(tag: String): Tagged[A] = taggedRecord(recordA, tag)
  }

  /** Convenient infix operations */
  final implicit class JsonSchemaOps[A](jsonSchema: JsonSchema[A]) {
    def invmap[B](f: A => B)(g: B => A): JsonSchema[B] = invmapJsonSchema(jsonSchema, f, g)
  }

  final implicit class TaggedOps[A](taggedA: Tagged[A]) {
    def orElse[B](taggedB: Tagged[B]): Tagged[Either[A, B]] = choiceTagged(taggedA, taggedB)
    def invmap[B](f: A => B)(g: B => A): Tagged[B] = invmapTagged(taggedA, f, g)
  }

  /** A JSON schema for type `String` */
  implicit def stringJsonSchema: JsonSchema[String]

  /** A JSON schema for type `Int` */
  implicit def intJsonSchema: JsonSchema[Int]

  /** A JSON schema for type `Long` */
  implicit def longJsonSchema: JsonSchema[Long]

  /** A JSON schema for type `BigDecimal` */
  implicit def bigdecimalJsonSchema: JsonSchema[BigDecimal]

  /** A JSON schema for type `Double` */
  implicit def doubleJsonSchema: JsonSchema[Double]

  /** A JSON schema for type `Boolean` */
  implicit def booleanJsonSchema: JsonSchema[Boolean]

  /** A JSON schema for sequences */
  implicit def arrayJsonSchema[C[X] <: Seq[X], A](implicit
    jsonSchema: JsonSchema[A],
    cbf: CanBuildFrom[_, A, C[A]]
  ): JsonSchema[C[A]]

}
