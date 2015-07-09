package controllers

import java.util
import java.util.Date

import play.api.Play.current
import play.api._
import play.api.libs.json.Json.JsValueWrapper
import play.api.mvc._
import play.api.db._
import play.twirl.api.Html
import play.api.libs.json._

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

  def logIn(user: String, password: String): String = {

    //either the password was changed or the user is not yet in memory
    //Lets get it from the db
    var conn: java.sql.Connection = DB.getConnection();
    var pstmt: java.sql.PreparedStatement = null;
    var rs: java.sql.ResultSet = null;
    var newCookie: String = "Not Allowed";
    try {
      var query: String = "Select access_level,last_name,first_name from agent where user_name = ? AND password = ?";
      pstmt = conn.prepareStatement(query);
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
      if (!conn.isClosed()) {
        conn.close();
      }
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

class AdminUser
(var userName: String,
 var password: String,
 var fullName: String,
 var accessLevel: String) {
  var lastLogin: util.Date = new Date();
  //if expired then forward to the login page
  var openCookies: util.HashSet[String] = new util.HashSet[String]();

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
    openCookies.add(newCookieVal);
    return newCookieVal;
  }

  def checkCookie(cookieVal: String): Boolean = {
    var newDate: util.Date = new util.Date();
    if (newDate.getTime() - lastLogin.getTime() > (30 * 60 * 1000)) {
      openCookies = new util.HashSet[String]();
      println("Clearing Cookies");
      return false;
    }
    if (openCookies.contains(cookieVal)) {
      touch();
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
      println("Pre Check: "+tokens(0)+" : "+tokens(1));
      if (UserRepository.isLoggedIn(tokens(0), tokens(1))) {
        block(request);
      }
      else {
        Future.successful(Results.Redirect("http://localhost:9000/simpleLogin.html"));
      }
    }
    catch {
      case e: Exception =>
        //if can't get cookie go to login
        println("MY ERROR:  "+e.getMessage());
        Future.successful(Results.Redirect("http://localhost:9000/simpleLogin.html"));
    }
  }
}

class Application extends Controller {

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
        val newCookie = UserRepository.logIn(user, pwd);
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

  def listAgents = Action {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    val conn = DB.getConnection()
    try {
      val stmt = conn.createStatement
      val rs = stmt.executeQuery("SELECT id, first_name, last_name, user_name from AGENT")
      while (rs.next()) {
        val json: JsValue = Json.obj(
          "id" -> rs.getInt("ID"),
          "firstName" -> rs.getString("first_name"),
          "lastName" -> rs.getString("last_name"),
          "userName" -> rs.getString("user_name")
        )
        jsonBuffer += json
      }
      rs.close()
      stmt.close()
    }
    finally {
      conn.close()
    }
    val j = JsArray(jsonBuffer)
    Ok(j)
  }


  def validateAgent() = TODO

  def addAgent() = TODO


  //TODO we want to take some parameters for this call
  // - Office location
  // - Agent ID for tickets assigned to that agent
  // - can these be
  def listTickets = Action {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    val conn = DB.getConnection()
    try {
      val stmt = conn.createStatement
      val rs = stmt.executeQuery("SELECT * from V_TICKET")
      while (rs.next()) {
        val json: JsValue = Json.obj(
          "ticket_id" -> rs.getInt("TICKET_ID"),
          "version" -> rs.getInt("VERSION"),
          "Action" -> rs.getString("ACTION"),
          "Description" -> rs.getString("DESCRIPTION"),
          "Status" -> rs.getString("STATUS"),
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
        jsonBuffer += json
      }
    } finally {
      conn.close()
    }
    val j = JsArray(jsonBuffer)
    Ok(j)
  }


  def findAccount = TODO

  def getAccount = TODO


  def getTicketTypes = Action {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    var conn = DB.getConnection()
    try {
      val stmt = conn.prepareStatement("SELECT id, name, category, description, wiki_link, notes from TICKET_ACTION")
      val rs = stmt.executeQuery()
      while (rs.next()) {
        val json: JsValue = Json.obj(
          "id" -> rs.getInt("id"),
          "Name" -> rs.getString("name"),
          "Category" -> rs.getString("category"),
          "Description" -> rs.getString("description"),
          "Wiki" -> rs.getString("wiki_link"),
          "Notes" -> rs.getString("notes")
        )
        jsonBuffer += json
      }
      rs.close()
      stmt.close()
    } finally {

      conn.close()
    }

    val j = JsArray(jsonBuffer)
    Ok(j)
  }


  def getTicketStatusTypes = Action {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    var conn = DB.getConnection()
    try {
      val stmt = conn.prepareStatement("SELECT id, status, description from TICKET_STATUS")
      val rs = stmt.executeQuery()
      while (rs.next()) {
        val json: JsValue = Json.obj(
          "id" -> rs.getInt("id"),
          "Status" -> rs.getString("status"),
          "Description" -> rs.getString("description")
        )
        jsonBuffer += json
      }
      rs.close()
      stmt.close()
    } finally {

      conn.close()
    }

    val j = JsArray(jsonBuffer)
    Ok(j)
  }


  def createTicket = TODO


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

//def is function action is request response function,ok means 200
