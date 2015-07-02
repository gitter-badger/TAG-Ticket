package controllers

import java.sql.ResultSet
import play.api.Play.current
import play.api._
import play.api.libs.json.Json.JsValueWrapper
import play.api.mvc._
import play.api.db._
import play.api.libs.json._

import scala.collection.mutable.ArrayBuffer


class Application extends Controller {

  def index = Action {
    Ok
  }


  def queryToJson(query: String, rsToJsRow: ResultSet => JsValue): JsArray = {
    var jsonBuffer = ArrayBuffer.empty[JsValue]
    val conn = DB.getConnection()
    try {
      val stmt = conn.createStatement
      val rs = stmt.executeQuery(query)
      while (rs.next()) {
        jsonBuffer += rsToJsRow(rs)
      }
      rs.close()
      stmt.close()
    }
    finally {
      conn.close()
    }
    JsArray(jsonBuffer)
  }


  def listAgents = Action {
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
  def listTickets = Action {
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
