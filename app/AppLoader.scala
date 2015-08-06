import controllers.Assets
import play.api.ApplicationLoader.Context
import play.api._
import db.slick.{DbName, SlickComponents}
import router.Routes
import slick.driver.PostgresDriver

class AppLoader extends ApplicationLoader {
  def load(context: Context) = (new MyComponents(context)).application

  class MyComponents(context: Context) extends BuiltInComponentsFromContext(context) with SlickComponents {

    lazy val router = new Routes(
      httpErrorHandler,
      new _root_.controllers.Application(api.dbConfig[PostgresDriver](DbName("default"))),
      new Assets(httpErrorHandler),
      "/"
    )
  }
}
