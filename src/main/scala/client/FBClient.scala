package client

import java.security.PublicKey

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import server.DS
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._
import spray.json._

import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.Breaks

case class statusUpdateActivity()

case class timelineActivity()

case class securedTimelineActivity()

case class profileActivity()

case class pKey(userId: Int, pKey: String)

case class getGroupTimeLine(groupID: Int)

case class groupMembersList(groupID: Int, members: ListBuffer[pKey])

case class postMessageToGroup(groupID: Int, message: String)

case class addMemberToGroup(uID: Int, groupID: Int)

case class groupTimeline(groupID: Int, listBuffer: ListBuffer[messageWithEncryptedAES])

case class statusUpdate(userId: Int, tags: List[Int], content: String, timestamp: Long)

case class SecurestatusUpdate(userId: Int, content: String, RSAencryptedAESkey: String, timestamp: Long)

case class messagesObject(userID: Int, messages: ListBuffer[privateMessageWithEncryptedAES])

case class Timeline(userId: Int, timelineType: Int, statusUpdatesList: List[statusUpdate])

case class SecureTimeline(userId: Int, statusUpdatesList: List[SecurestatusUpdate])

case class addUserToGroup(groupID: Int, userID: Int)

case class UserConfig(category: Int, count: Int, Friends: Array[Int], statusUpdateInterval: Int, timelineInterval: Int, profileInterval: Int, groups: ListBuffer[Int])

case class FBConfig(serverIP: String, serverPort: Int, nOfUsers: Int, scale: Double, majorEvent: Int, statsInterval: Int, users: Array[UserConfig])

case class encryptedAES(userId: Int, RSAencryptedAESkey: String)

case class messageWithEncryptedAES(userId: Int, AESencryptedMessage: String, keys: ListBuffer[encryptedAES], timestamp: Long)

case class friendsList(userId: Int, friends: ListBuffer[pKey])

case class postMessageObject(groupID: Int, message: messageWithEncryptedAES, timestamp: Long)

case class securedPostStatusUpdate(message: String)

case class getPrivateMessages(userID: Int)

case class privateMessageWithEncryptedAES(senderId: Int, AESencryptedMessage: String, encAES: encryptedAES, timestamp: Long)

case class sendPrivateMessage(userID: Int, friendID: Int, message: String)

case class getFriendsList()

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val pKeyFormat = jsonFormat2(pKey)
  implicit val FBConfigFormat = jsonFormat7(FBConfig)
  implicit val timelineFormat = jsonFormat3(Timeline)
  implicit val userConfigFormat = jsonFormat7(UserConfig)
  implicit val EncryptedAESFormat = jsonFormat2(encryptedAES)
  implicit val SecureTimelineFormat = jsonFormat2(SecureTimeline)
  implicit val userstatusUpdateFormat = jsonFormat4(statusUpdate)
  implicit val SecurestatusUpdateFormat = jsonFormat4(SecurestatusUpdate)
  implicit val MessageWithEncryptedAESFormat = jsonFormat4(messageWithEncryptedAES)
  implicit val addUserToGroupFormat = jsonFormat2(addUserToGroup)
  implicit val groupTimelineFormat = jsonFormat2(groupTimeline)
}

import client.MyJsonProtocol._

class User(id: Int, server: String, myConfig: UserConfig, nOfUsers: Int, eventTime: Int)(implicit system: ActorSystem) extends Actor with AES with RSA with DS {
  implicit val timeout = Timeout(60 seconds)
  val AESkey = generateAESKey
  val RSAKeyPair = getKeyPair
  var scheduler: Cancellable = _
  var statusUpdateCount: Int = _
  var friendlist = None: Option[friendsList]

  if(!Authenticate()) {
    println("failed to Authenticate")
    context.stop(self)
  }

  sendPublicKeyToServer

  waitForstatusUpdateActivity(Random.nextInt(myConfig.statusUpdateInterval))
  waitForTimelineActivity(Random.nextInt(myConfig.timelineInterval))
  waitForProfileActivity(Random.nextInt(myConfig.profileInterval))

  def sendPublicKeyToServer = {
    val pKeyRequestUri = s"http://$server/updatePublicKey"
    val pKeyJSON = new pKey(id, RSAKeyPair.getPublic.toString).toJson
    val future = IO(Http).ask(HttpRequest(POST, Uri(pKeyRequestUri)).withEntity(HttpEntity(pKeyJSON.toString))).mapTo[HttpResponse]
    val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
    println(response.entity.toString)
  }


  def Authenticate() : Boolean = {
    var future = IO(Http).ask(HttpRequest(GET, Uri(s"http://$server/getSecuredRandomInteger")).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
    var response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
    val jsonSecuredInt = response.entity.asString.parseJson
    val  securedInt = jsonSecuredInt.convertTo[Int]

    future = IO(Http).ask(HttpRequest(GET, Uri(s"http://$server/getSecuredRandomInteger")).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
    response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
    if(response.status.intValue != 200)
      false
    else
      true
  }

  def receive = {

    case statusUpdateActivity() =>
      poststatusUpdate(null)
      waitForstatusUpdateActivity(myConfig.statusUpdateInterval)

    case securedPostStatusUpdate(message: String) =>{
        self!getFriendsList
        statusUpdateCount += 1
        val timestamp = System.currentTimeMillis()
        var messageWithEncryptedAES = new messageWithEncryptedAES(id, "", ListBuffer[encryptedAES](), timestamp)
        val aESkey = generateAESKey
        messageWithEncryptedAES.AESencryptedMessage += encryptAESbase64(aESkey, message)
        for (friend <- friendlist.get.friends) {
          messageWithEncryptedAES.keys += new encryptedAES(friend.userId, encryptRSAaESkey(aESkey, StringToPubRsa(friend.pKey)))
        }
        val jsonstatusUpdate = messageWithEncryptedAES.toJson
        val future = IO(Http).ask(HttpRequest(POST, Uri(s"http://$server/SecureStatusUpdate")).withEntity(HttpEntity(jsonstatusUpdate.toString))).mapTo[HttpResponse]
        val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      }

    case getFriendsList =>{
        friendlist match {
          case None => {
            val future = IO(Http).ask(HttpRequest(GET, Uri(s"http://$server/getFriendsList")).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
            val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
            val jsonFriendsList = response.entity.asString.parseJson
            friendlist = Some(jsonFriendsList.convertTo[friendsList])
          }
        }
      }

    case profileActivity() =>
      val future = IO(Http).ask(HttpRequest(GET, Uri(s"http://$server/userTimeline")).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      waitForProfileActivity(myConfig.profileInterval)

    case timelineActivity() =>
      var pick = Random.nextInt(10)
      var timelineType = 0
      var requestUri = s"http://$server/homeTimeline"
      if (pick % 3 == 0) {
        timelineType = 1
        requestUri = s"http://$server/tagsTimeline"
      }
      val future = IO(Http).ask(HttpRequest(GET, Uri(requestUri)).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      val jsonTimeline = response.entity.asString.parseJson
      val timeline = jsonTimeline.convertTo[Timeline]
      if (Random.nextBoolean && (timeline.statusUpdatesList.length > 0)) {
        val statusUpdateNum = Random.nextInt(timeline.statusUpdatesList.length)
        val restatusUpdate = timeline.statusUpdatesList(statusUpdateNum)
        poststatusUpdate(restatusUpdate)
      }
      waitForTimelineActivity(myConfig.timelineInterval)

    case securedTimelineActivity() =>
      var requestUri = s"http://$server/securedTimeline"
      val future = IO(Http).ask(HttpRequest(GET, Uri(requestUri)).withEntity(HttpEntity(id.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      val jsonTimeline = response.entity.asString.parseJson
      val securetimeline = jsonTimeline.convertTo[SecureTimeline]
      for (securepost <- securetimeline.statusUpdatesList) {
        println(decryptAESbase64(securepost.content, decryptRSAaESkey(securepost.RSAencryptedAESkey, RSAKeyPair.getPrivate)))
      }
      waitForTimelineActivity(myConfig.timelineInterval)

    case postMessageToGroup(groupID, message) =>
      var requestURI = s"http://$server/postMessageToGroup"
      val timestamp = System.currentTimeMillis()
      var messageWithEncryptedAES = new messageWithEncryptedAES(id, "", ListBuffer[encryptedAES](), timestamp)
      val aESkey = generateAESKey
      messageWithEncryptedAES.AESencryptedMessage += encryptAESbase64(aESkey, message)
      for (friend <- friendlist.get.friends) {
        messageWithEncryptedAES.keys += new encryptedAES(friend.userId, encryptRSAaESkey(aESkey, StringToPubRsa(friend.pKey)))
      }
      val jsonstatusUpdate = new postMessageObject(groupID, messageWithEncryptedAES, timestamp).toJson
      val future = IO(Http).ask(HttpRequest(POST, Uri(requestURI)).withEntity(HttpEntity(jsonstatusUpdate.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]

    case getGroupTimeLine(groupID: Int) =>
      val requestURI = s"http://$server/getGroupTimeLine"
      val future = IO(Http).ask(HttpRequest(GET, Uri(requestURI)).withEntity(HttpEntity(groupID.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      val jsonTimeline = response.entity.asString.parseJson
      val timeline = jsonTimeline.convertTo[Timeline]
      if (Random.nextBoolean && (timeline.statusUpdatesList.length > 0)) {
        val statusUpdateNum = Random.nextInt(timeline.statusUpdatesList.length)
        val restatusUpdate = timeline.statusUpdatesList(statusUpdateNum)
        poststatusUpdate(restatusUpdate)
      }
      waitForTimelineActivity(myConfig.timelineInterval)

    case sendPrivateMessage(userID: Int, friendID: Int, message: String) => {
      val requestURI = s"http://$server/sendPrivateMessage"
//      privateMessageWithEncryptedAES(senderId: Int, AESencryptedMessage: String, encAES: encryptedAES, timestamp: Long)
      val timestamp = System.currentTimeMillis()
      val aESkey = generateAESKey
      val encryptedMsg = encryptAESbase64(aESkey, message)
      var privateMessageWithEncryptedAES = new privateMessageWithEncryptedAES(id, encryptedMsg, new encryptedAES(friendID, encryptRSAaESkey(aESkey, getPublicKey(friendID))), timestamp)
      val future = IO(Http).ask(HttpRequest(POST, Uri(requestURI)).withEntity(HttpEntity(privateMessageWithEncryptedAES.toJson.toString()))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
    }

    case getPrivateMessages(userID: Int) => {
      val requestURI = s"http://$server/requestPrivateMessages"
      val future = IO(Http).ask(HttpRequest(GET, Uri(requestURI)).withEntity(HttpEntity(userID.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
      val jsonTimeline = response.entity.asString.parseJson
      val secureMessages = jsonTimeline.convertTo[messagesObject]
      for (msg <- secureMessages.messages) {
        val mess = decryptAESbase64(msg.AESencryptedMessage, decryptRSAaESkey(msg.encAES.RSAencryptedAESkey, RSAKeyPair.getPrivate))
        println("Message: " + mess + "received from " + msg.senderId)
      }
    }

    case addMemberToGroup(groupID, uID) =>
      var requestURI = s"http://$server/addMemberToGroup"
      val request = new addUserToGroup(groupID, uID)
      val future = IO(Http).ask(HttpRequest(POST, Uri(requestURI)).withEntity(HttpEntity(request.toJson.toString))).mapTo[HttpResponse]
      val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
  }

  def getPublicKey(userId : Int):PublicKey={
    var pubKey = ""
    val loop = new Breaks
    loop.breakable(
      for(pkey <- (friendlist.get.friends)){
        if(pkey.userId == userId){
          pubKey = pkey.pKey
          loop.break()
        }
      }
    )
    StringToPubRsa(pubKey)
  }

  def waitForstatusUpdateActivity(waitTime: Int) {
    scheduler = context.system.scheduler.scheduleOnce((new FiniteDuration(waitTime, MILLISECONDS)), self, statusUpdateActivity())
  }

  def waitForTimelineActivity(waitTime: Int) {
    scheduler = context.system.scheduler.scheduleOnce((new FiniteDuration(waitTime, MILLISECONDS)), self, timelineActivity())
  }

  def waitForProfileActivity(waitTime: Int) {
    scheduler = context.system.scheduler.scheduleOnce((new FiniteDuration(waitTime, MILLISECONDS)), self, profileActivity())
  }

  def poststatusUpdate(statusUpdate: statusUpdate) {
    statusUpdateCount += 1
    val timestamp = System.currentTimeMillis()
    val isTag = Random.nextBoolean()
    var tags = List[Int]()
    var content: String = null
    if (statusUpdate == null) {
      if (isTag) {
        val Tag = Random.nextInt(nOfUsers)
        tags = tags.::(Tag)
        content = "User#" + id + " Tagged user#" + Tag + " and his statusUpdate count is " + statusUpdateCount
      } else {
        content = "User#" + id + " posted a statusUpdate and his statusUpdate count is " + statusUpdateCount
      }
    } else {
      // RestatusUpdate
      content = statusUpdate.content
    }
    val jsonstatusUpdate = new statusUpdate(id, tags, content, timestamp).toJson
    val future = IO(Http).ask(HttpRequest(POST, Uri(s"http://$server/statusUpdate")).withEntity(HttpEntity(jsonstatusUpdate.toString))).mapTo[HttpResponse]
    val response = Await.result(future, timeout.duration).asInstanceOf[HttpResponse]
  }
}

object Client extends App {
  val config_str = "{\n\t\"serverIP\":\"localhost\",\n\t\"serverPort\":5642,\n\t\"nOfUsers\":1000000000,\n\t\"scale\":0.00005,\n\t\"majorEvent\":40000,\n\t\"statsInterval\":1000,\n\t\"users\":[{\n    \t\t\"category\":1,\n    \t\t\"count\":1000,\n    \t\t\"Friends\":[10,10,10,250000,250000,250000,250000],\n    \t\t\"statusUpdateInterval\":1800,\n    \t\t\"timelineInterval\":86400,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":2,\n    \t\t\"count\":100000,\n    \t\t\"Friends\":[0,10,10,25000,25000,25000,25000],\n    \t\t\"statusUpdateInterval\":3600,\n    \t\t\"timelineInterval\":10000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":3,\n    \t\t\"count\":1000000,\n    \t\t\"Friends\":[0,0,10,2500,2500,2500,2500],\n    \t\t\"statusUpdateInterval\":10800,\n    \t\t\"timelineInterval\":40000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":4,\n    \t\t\"count\":10000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":18000,\n    \t\t\"timelineInterval\":50000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":5,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":86400,\n    \t\t\"timelineInterval\":200000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":6,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":2592000,\n    \t\t\"timelineInterval\":20000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":7,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":31104000,\n    \t\t\"timelineInterval\":20000,\n    \t\t\"profileInterval\":360000\n\t\t}\n\t]\n}"
  val config_json = config_str.parseJson
  val config = config_json.convertTo[FBConfig]

  println("No. of users: " + config.nOfUsers)
  println("Scale: " + config.scale)
  println("No. of users scaled down to: " + (config.nOfUsers.toDouble * config.scale).toInt)

  implicit val system = ActorSystem("FBClientSimulator")

  val server = config.serverIP + ":" + config.serverPort.toString()
  var user_end = new Array[Int](8)
  user_end(0) = 0
  for (category <- 1 to 7) {
    user_end(category) = user_end(category - 1) + (config.users(category - 1).count * config.scale).toInt

    for (nodeCount <- user_end(category - 1) to user_end(category) - 1) {
      var eventTime = 0
      if (Random.nextBoolean) eventTime = config.majorEvent
      system.actorOf(Props(new User(nodeCount, server, config.users(category - 1), (config.nOfUsers.toDouble * config.scale).toInt, eventTime)), name = "user" + nodeCount.toString)
    }
  }

  println("All users created.")
}



