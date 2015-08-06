package controllers

import java.sql.ResultSet
import java.util
import java.util.Date

import _root_.slick.backend
import _root_.slick.driver.{PostgresDriver}
import _root_.slick.jdbc.GetResult
import play.api.Play.current
import play.api.libs.json._
import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


object UserRepository {
  var users: java.util.HashMap[String, AdminUser] = new java.util.HashMap[String, AdminUser]();

  def isLoggedIn(user: String, cookieVal: String): Boolean = {
    println("Checking Logged In"+user+"  "+cookieVal);
    if (users.containsKey(user)) {
      var theUser: AdminUser = users.get(user);
      println("Has User")
      if (theUser.checkCookie(cookieVal)) {
        return true;
      }
    }
    return false;
  };

  def logIn(user: String, password: String)(dbConfig: backend.DatabaseConfig[PostgresDriver]): String = {

    //either the password was changed or the user is not yet in memory
    //Lets get it from the db
    var sess = dbConfig.db.createSession()
    var pstmt: java.sql.PreparedStatement = null;
    var rs: java.sql.ResultSet = null;
    var newCookie: String = "Not Allowed";
    try {
      var query: String = "Select access_level,last_name,first_name from agent where user_name = ? AND password = ?";
      pstmt = sess.conn.prepareStatement(query);
      pstmt.setString(1, user);
      pstmt.setString(2, password);
      rs = pstmt.executeQuery();
      if (rs.next()) {
      //  println("Yeah have something");
        if (users.containsKey(user)) {
          var oldUser: AdminUser = users.get(user);
          oldUser.password = password;
          oldUser.accessLevel = rs.getString("access_level");
          oldUser.fullName = rs.getString("first_name") + " " + rs.getString("last_name");
          newCookie = oldUser.addCookie();
        }
        else {
          var newUser: AdminUser = new AdminUser(user, password, rs.getString("first_name") + " " + rs.getString("last_name"), rs.getString("access_level"));
          users.put(user, newUser);
          newCookie = newUser.addCookie();
        }
      }
    }
    finally {
      sess.close()
      if (pstmt != null && !pstmt.isClosed()) {
        pstmt.close();
      }
      if (rs != null && !rs.isClosed()) {
        rs.close();
      }

    }
    return user + ":" + newCookie;
  }

}

class cookieItem{
  var lastLogin: util.Date = new Date();
  var token:String = null;
}

class AdminUser
(var userName: String,
 var password: String,
 var fullName: String,
 var accessLevel: String) {
  var lastLogin: util.Date = new Date();
  //if expired then forward to the login page
  var openCookies: util.HashMap[String,cookieItem] = new util.HashMap[String,cookieItem]();

  //etc.

  def getPassword(): String = {
    return password;
  }

  def touch(): Unit = {
    lastLogin = new util.Date();
  }

  def addCookie(): String = {
    //generate Cookie
    touch();
    var newCookieVal: String = (new util.Date()).toGMTString() + userName;
    newCookieVal = newCookieVal.replace(" ","");
    //TODO:need to encrypt it
    val newCookie:cookieItem = new cookieItem();
    openCookies.put(newCookieVal,newCookie);
    return newCookieVal;
  }

  def checkCookie(cookieVal: String): Boolean = {
    var newDate: util.Date = new util.Date();

    if (openCookies.containsKey(cookieVal)) {
      val myCookie:cookieItem = openCookies.get(cookieVal);
      if(newDate.getTime() - myCookie.lastLogin.getTime() > (30 * 60 * 1000))
      {
        openCookies.remove(cookieVal);
        println("removing cookie "+cookieVal);
        return false;
      }
      else
      {
        myCookie.lastLogin = new util.Date();
      }
      return true;
    }
    return false;
  }
}

object LoggedInAction extends ActionBuilder[Request] {
  def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
    try {
      var cookieVal: String = request.cookies("userCookie").value;
      var tokens: Array[String] = cookieVal.split(":", 2);
     // println("Pre Check: "+tokens(0)+" : "+tokens(1));
      if (UserRepository.isLoggedIn(tokens(0), tokens(1))) {
        block(request);
      }
      else {
        if (request.accepts("application/json"))
        {
          Future.successful(Results.Ok("{\"action\":\"redirect\",\"location\":\"http://localhost:9000/simpleLogin.html?block=notAllowed\"}"));
        }
        else
        {
          Future.successful(Results.Redirect("http://localhost:9000/simpleLogin.html?block=notAllowed&url="+java.net.URLEncoder.encode(request.uri,"utf-8")));
        }
      }
    }
    catch {
      case e: Exception =>
        //if can't get cookie go to login
        println("MY ERROR:  "+e.getMessage()+" "+request.contentType+" "+(request.contentType+"").contains("json"));
        if(request.accepts("application/json"))
        {
          Future.successful(Results.Ok("{\"action\":\"redirect\",\"location\":\"http://localhost:9000/simpleLogin.html?block=notAllowed\"}"));
        }
        else
        {
          Future.successful(Results.Redirect("http://localhost:9000/simpleLogin.html?block=notAllowed&url="+java.net.URLEncoder.encode(request.uri,"utf-8")));
        }
    }
  }
}


class Application(dbConfig: backend.DatabaseConfig[PostgresDriver]) extends Controller {

  def index = Action {
    Ok
  }

  def test1 = Action {
    Ok("hello world");
  };

  def test2 = LoggedInAction {
    Ok("hello world");
  };

  def loginFunction = Action {
    request =>
      request.body.asJson.map { json =>
        val user = (json \ "username").as[String];
        val pwd = (json \ "password").as[String];
        println(user + "  "+pwd);
        val newCookie = UserRepository.logIn(user, pwd)(dbConfig)
        println(newCookie);
        if (newCookie.contains("Not Allowed")) {
          Ok(Json.obj(
            "success" -> false,
            "error" -> "Not Allowed"

          ));
        }
        else {
          Ok(Json.obj(
            "success" -> true,
            "error" -> "",
            "redirect" -> "http://localhost:9000/test2"
          )).withCookies(Cookie("userCookie", newCookie));
        }
      }.getOrElse {
        Ok(Json.obj(
          "success" -> false,
          "error" -> "Invalid Input"

        ));
      }
  }

  def queryToJson(query: String, rsToJsRow: ResultSet => JsValue): JsArray = {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    val sess = dbConfig.db.createSession()
    try {
      val stmt = sess.conn.createStatement
      val rs = stmt.executeQuery(query)
      while (rs.next()) {
        jsonBuffer += rsToJsRow(rs)
      }
      rs.close()
      stmt.close()
    }
    finally {
      sess.close()
    }
    JsArray(jsonBuffer)
  }


  def listAgents = LoggedInAction {
    val query = "SELECT id, first_name, last_name, user_name from AGENT"
    val json = queryToJson(query, (rs: ResultSet) =>
      Json.obj(
        "id" -> rs.getInt("ID"),
        "firstName" -> rs.getString("first_name"),
        "lastName" -> rs.getString("last_name"),
        "userName" -> rs.getString("user_name")
      )
    )
    Ok(json)
  }


  //TODO we want to take some parameters for this call
  // - Office location
  // - Agent ID for tickets assigned to that agent
  // - can these be
  def listTickets = LoggedInAction {

    val query = "SELECT * from V_TICKET"
    val json = queryToJson(query, (rs: ResultSet)=>
      Json.obj(
        "ticket_id" -> rs.getInt("TICKET_ID"),
        "version" -> rs.getInt("VERSION"),
        "Action" -> rs.getString("ACTION"),
        "Description" -> rs.getString("DESCRIPTION"),
        "Status"  -> rs.getString("STATUS"),
        "Account_id" -> rs.getInt("ACCOUNT_ID"),
        "Person" -> rs.getInt("PERSON_ID"),
        "First Name" -> rs.getString("FIRST_NAME"),
        "Last Name" -> rs.getString("LAST_NAME"),
        "Device_id" -> rs.getInt("PERSON_DEVICE_ID"),
        "Device" -> rs.getString("DEVICE"),
        "Office_id" -> rs.getInt("OFFICE_LOCATION_ID"),
        "Office" -> rs.getString("LOCATION"),
        "Created By" -> rs.getString("CREATED_BY"),
        "Assigned To" -> rs.getString("ASSIGNED_TO"),
        "Notes" -> rs.getString("NOTES")
      )
    )
    Ok(json)

  }


  def getTicketTypes = Action {

    val query = "SELECT id, name, category, description, wiki_link, notes from TICKET_ACTION"
    val json = queryToJson(query, (rs: ResultSet) =>
      Json.obj(
        "id" -> rs.getInt("id"),
        "Name" -> rs.getString("name"),
        "Category" -> rs.getString("category"),
        "Description" -> rs.getString("description"),
        "Wiki" -> rs.getString("wiki_link"),
        "Notes" -> rs.getString("notes")
      )
    )
    Ok(json)

  }


  def getTicketStatusTypes = Action {
    val query = "SELECT id, status, description from TICKET_STATUS"
    val json = queryToJson(query, (rs: ResultSet) =>
      Json.obj(
        "id" -> rs.getInt("id"),
        "Status" -> rs.getString("status"),
        "Description" -> rs.getString("description")
      )
    )
    Ok(json)
  }

  def validateAgent() = TODO

  def addAgent() = TODO

  def findAccount = TODO

  def createTicket = TODO

  def getAccount = TODO

  def updateTicket = TODO

  def createAccount = TODO


   /*
	- account details
	- new person / update / remove person
	- new device / remove device
	- new filter / remove filter
     */
	 def updateAccount = TODO
}
