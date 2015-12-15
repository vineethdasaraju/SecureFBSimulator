package server

import java.security.PublicKey
import java.util

import akka.actor._
import akka.io.IO
import akka.routing.RoundRobinRouter
import com.google.gson.JsonObject
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._
import spray.json._
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

case class start()
case class getStats()
case class printStats()
case class postPhotoUpdate(query: String)
case class PoststatusUpdate(query: String)
case class GetTimeline(timelineType: Int, query: String)
case class getPublicKeys(userRef: ActorRef, query: String)
case class updatePublicKey(userRef: ActorRef, query: String)
case class postPhotoRequest(userRef: ActorRef, query: String)
case class statusUpdateRequest(userRef: ActorRef, query: String)
case class TimelineRequest(userRef: ActorRef, timelineType: Int, query: String)

case class UserConfig(category: Int, count: Int, Friends: Array[Int], statusUpdateInterval: Int, timelineInterval: Int, profileInterval: Int)
case class FBConfig(serverIP: String, serverPort: Int, nOfUsers: Int, scale: Double, majorEvent: Int, statsInterval: Int, users: Array[UserConfig])
case class statusUpdate(userId: Int, tags: List[Int], content: String, timestamp: Long)
case class Photo(userId: Int, tags: List[Int], content: String, timestamp: Long)
case class PKey(userId: Int, pkey: String)
case class PKeyRequest(userId: Int, friends: Array[Int])
case class Timeline(userId:Int, timelineType: Int, statusUpdatesList: List[statusUpdate])

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val statusUpdateFormat = jsonFormat4(statusUpdate)
  implicit val timelineFormat = jsonFormat3(Timeline)
  implicit val userConfigFormat = jsonFormat6(UserConfig)
  implicit val FBConfigFormat = jsonFormat7(FBConfig)
  implicit val photoFormat = jsonFormat4(Photo)
  implicit val pKeyFormat = jsonFormat2(PKey)
  implicit val pKeyRequestFormat = jsonFormat2(PKeyRequest)
}

import server.MyJsonProtocol._

class Friend {
  var count: Int = _
  var start: Int = _
}

class UserInfo {
  var name: String = _
  var id: Int = _
  var myFriends = new Array[Friend](7)
  // photographs
  var home_photoAlbum = new ListBuffer[Photo]()
  var user_photoAlbum = new ListBuffer[Photo]()
  var tags_photoAlbum = new ListBuffer[Photo]()
  // status updates
  var home_timeline = new ListBuffer[statusUpdate]()
  var user_timeline = new ListBuffer[statusUpdate]()
  var tags_timeline = new ListBuffer[statusUpdate]()
  // public Key
  var publicKey: String = _
}

class FBStats (var Requests: Int, var statusUpdates: Int, var Timeline: Int, var photoPosts: Int) {
  var nOfRequests: Int = Requests
  var nOfstatusUpdateRequests: Int = statusUpdates
  var nOfTimelineRequests: Int = Timeline
  var noOfPhotoPostRequests: Int = photoPosts
  def copy = {
    new FBStats(nOfRequests, nOfstatusUpdateRequests, nOfTimelineRequests, noOfPhotoPostRequests)
  }
}

class Tracker(StatsInterval: Int) extends Actor {
  var startTime: Long = _
  var endTime: Long = _
  var scheduler: Cancellable = _
  var duration = new FiniteDuration(StatsInterval, MILLISECONDS)
  var future: Future[FBStats] = _
  var prevStats: FBStats = new FBStats(0,0,0,0)
  var timeCount: Int = _

  startTime = System.currentTimeMillis
  runTracker()
  def runTracker() {
    scheduler = context.system.scheduler.scheduleOnce(duration, self, printStats())
  }

  def receive = {
    case printStats() =>
      timeCount += 1
      val currStats = Server.stats.copy
      println("Time interval (1 seconds): " + timeCount +
        ", Total requests in this interval: " + (currStats.nOfRequests - prevStats.nOfRequests) +
        ", Total statusUpdates in this interval: " + (currStats.nOfstatusUpdateRequests - prevStats.nOfstatusUpdateRequests) +
        ", Total photoPosts in this interval: " + (currStats.noOfPhotoPostRequests - prevStats.noOfPhotoPostRequests) +
        ", Total timeline accesses in this interval: " + (currStats.nOfTimelineRequests - prevStats.nOfTimelineRequests))

      prevStats.nOfRequests  = currStats.nOfRequests
      prevStats.nOfstatusUpdateRequests = currStats.nOfstatusUpdateRequests
      prevStats.nOfTimelineRequests = currStats.nOfTimelineRequests
      if((System.currentTimeMillis - startTime).millis.toMinutes >= (5 minutes).toMinutes) {
        println("Shutting down the system.")
        context.system.shutdown()
      }
      runTracker()
  }
}

class statusUpdateService(userDatabase: Array[UserInfo], userRef: ActorRef) extends Actor {
  def receive = {
    case PoststatusUpdate(query) =>
      val statusUpdate = query.parseJson.convertTo[statusUpdate]
      userDatabase(statusUpdate.userId).user_timeline.+=:(statusUpdate)
      if(!statusUpdate.tags.isEmpty) {
        // Add the statusUpdate in tags_timeline of the mentioned users
        val itr = statusUpdate.tags.iterator
        while(itr.hasNext) {
          userDatabase(itr.next).tags_timeline.+=:(statusUpdate)
        }
      }
      for (cat <- 1 to 7) {
        val count = userDatabase(statusUpdate.userId).myFriends(cat-1).count
        val start = userDatabase(statusUpdate.userId).myFriends(cat-1).start
        if (count > 0) {
          for (i <- start to start+count) {
            userDatabase(i).home_timeline.+=:(statusUpdate)
          }
        }
      }
      userRef ! HttpResponse(status = 200, entity = "OK")
      Server.stats.nOfRequests += 1
      Server.stats.nOfstatusUpdateRequests += 1
      context.stop(self)
  }
}

class statusUpdateServer(userDatabase: Array[UserInfo]) extends Actor {
  def receive = {
    case statusUpdateRequest(userRef, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      val service = context.system.actorOf(Props(new statusUpdateService(userDatabase, userRef)))
      service ! PoststatusUpdate(query)
  }
}

class statusUpdateEngine(userDatabase: Array[UserInfo]) extends Actor {
  var statusUpdateServerRef: ActorRef = _

  initialize()

  def initialize() {
    val serverCount = Math.max(1, (userDatabase.length/1000000).toInt)
    statusUpdateServerRef = context.system.actorOf(Props(new statusUpdateServer(userDatabase)).withRouter(RoundRobinRouter(serverCount)), name = "statusUpdateServer")
  }

  def receive = {
    case statusUpdateRequest(userRef, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      val service = context.system.actorOf(
        Props(new statusUpdateService(userDatabase, userRef)))
      statusUpdateServerRef ! statusUpdateRequest(userRef, query)
  }
}

class photoPostService(userDatabase: Array[UserInfo], userRef: ActorRef) extends Actor {
  def receive = {
    case postPhotoUpdate(query) =>
      val photo = query.parseJson.convertTo[Photo]
      userDatabase(photo.userId).user_photoAlbum.+=:(photo)
      if(!photo.tags.isEmpty) {
        // Add the statusUpdate in tags_timeline of the mentioned users
        val itr = photo.tags.iterator
        while(itr.hasNext) {
          userDatabase(itr.next).tags_photoAlbum.+=:(photo)
        }
      }
      for (cat <- 1 to 7) {
        val count = userDatabase(photo.userId).myFriends(cat-1).count
        val start = userDatabase(photo.userId).myFriends(cat-1).start
        if (count > 0) {
          for (i <- start to start+count) {
            userDatabase(i).home_photoAlbum.+=:(photo)
          }
        }
      }
      userRef ! HttpResponse(status = 200, entity = "OK")
      Server.stats.nOfRequests += 1
      Server.stats.noOfPhotoPostRequests += 1
      context.stop(self)
  }
}

class photoPostServer(userDatabase: Array[UserInfo]) extends Actor {
  def receive = {
    case postPhotoRequest(userRef, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      val service = context.system.actorOf(Props(new photoPostService(userDatabase, userRef)))
      service ! postPhotoUpdate(query)
  }
}

class photoPostEngine(userDatabase: Array[UserInfo]) extends Actor {
  var photoPostServerRef: ActorRef = _

  initialize()

  def initialize() {
    val serverCount = Math.max(1, (userDatabase.length/1000000).toInt)
    photoPostServerRef = context.system.actorOf(Props(new photoPostServer(userDatabase)).withRouter(RoundRobinRouter(serverCount)), name = "photoPostServer")
  }

  def receive = {
    case postPhotoRequest(userRef, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      val service = context.system.actorOf(Props(new photoPostService(userDatabase, userRef)))
      photoPostServerRef ! postPhotoRequest(userRef, query)
  }
}


class PublicKeyUpdateEngine(userDatabase: Array[UserInfo]) extends Actor {
  // update public keys in server

  def receive = {
    case updatePublicKey(userRef, query) =>
      println("Request Received")
      val pubKey = query.parseJson.convertTo[PKey]
      userDatabase(pubKey.userId).publicKey = pubKey.pkey
      userRef ! HttpResponse(status = 200, entity = "OK")
      Server.stats.nOfRequests += 1
//      context.stop(self)
  }
}

class GetPublicKeysEngine(userDatabase: Array[UserInfo]) extends Actor {
  def receive = {
    case getPublicKeys(sender, query) =>
      val pkrequest = query.parseJson.convertTo[PKeyRequest]
      val returnPKeys = new ListBuffer[PKey]
      for(i <- pkrequest.friends){
        if(userDatabase(i).publicKey == null) sender ! HttpResponse(status = 404, entity = "Resource for " + i + " is null.")
        else returnPKeys += new PKey(i,userDatabase(i).publicKey)
      }
      val retVal : JsonObject = new JsonObject
      for(i <- returnPKeys){
        retVal.addProperty(Integer.toString(i.userId), i.pkey)
      }
      sender ! HttpResponse(status = 200, entity = retVal.toString())
  }
}

class TimelineService(userDatabase: Array[UserInfo], userRef: ActorRef) extends Actor {
  def receive = {
    case GetTimeline(timelineType, query) =>
      var statusUpdatesList: List[statusUpdate] = List[statusUpdate]()
      val userId = query.parseJson.convertTo[Int]
      // Timeline type: 0 - Home timeline
      //                1 - tags timeline
      //                2 - User timeline

      if (timelineType == 0) {
        statusUpdatesList = userDatabase(userId).home_timeline.take(20).toList
      } else if (timelineType == 1) {
        statusUpdatesList = userDatabase(userId).tags_timeline.take(20).toList
      } else {
        statusUpdatesList = userDatabase(userId).user_timeline.take(20).toList
      }
      userRef ! HttpResponse(status = 200,
        entity = Timeline(userId, timelineType, statusUpdatesList).toJson.toString)
      Server.stats.nOfRequests += 1
      Server.stats.nOfTimelineRequests += 1
      context.stop(self)
  }
}

class TimelineServer(userDatabase: Array[UserInfo]) extends Actor {
  def receive = {
    case TimelineRequest(userRef, timelineType, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      val service = context.system.actorOf(Props(new TimelineService(userDatabase, userRef)))
      service ! GetTimeline(timelineType, query)
  }
}

class TimelineEngine(userDatabase: Array[UserInfo]) extends Actor {
  var timelineServerRef: ActorRef = _

  initialize()

  def initialize() {
    val serverCount = Math.max(1, (userDatabase.length/1000000).toInt)
    timelineServerRef = context.system.actorOf(Props(new TimelineServer(userDatabase)).withRouter(RoundRobinRouter(serverCount)),
      name = "TimelineServer")
  }

  def receive = {
    case TimelineRequest(userRef, timelineType, query) =>
      //println("Creating service for user#" + id + ". Ref: " + client)
      timelineServerRef ! TimelineRequest(userRef, timelineType, query)
  }
}

class FBServer(userDatabase: Array[UserInfo], config: FBConfig) extends Actor {
  var statusUpdateEngine: ActorRef = _
  var photoPostEngine: ActorRef = _
  var timelineEngine: ActorRef = _
  var updatePublicKeyEngine: ActorRef = _
  var getPublicKeysEngine: ActorRef = _

  InitializeServer()

  def InitializeServer() {
    statusUpdateEngine = context.system.actorOf(Props(new statusUpdateEngine(userDatabase)), name = "statusUpdateEngine")
    photoPostEngine = context.system.actorOf(Props(new photoPostEngine(userDatabase)), name = "photoPostEngine")
    timelineEngine = context.system.actorOf(Props(new TimelineEngine(userDatabase)), name = "TimelineEngine")
    updatePublicKeyEngine = context.system.actorOf(Props(new PublicKeyUpdateEngine(userDatabase)), name = "PublicKeyUpdateEngine")
    getPublicKeysEngine = context.system.actorOf(Props(new GetPublicKeysEngine(userDatabase)), name = "GetPublicKeysEngine")
  }

  def receive = {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(POST, Uri.Path("/updatePublicKey"), _, entity: HttpEntity.NonEmpty, _) =>
      updatePublicKeyEngine ! updatePublicKey(sender, entity.asString)

      //TODO: Implement getPublicKeys API
    case HttpRequest(POST, Uri.Path("/getPublicKeys"), _, entity: HttpEntity.NonEmpty, _) =>
      getPublicKeysEngine ! getPublicKeys(sender, entity.asString)

    case HttpRequest(POST, Uri.Path("/statusUpdate"), _, entity: HttpEntity.NonEmpty, _) =>
      //println("Received statusUpdate request")
      statusUpdateEngine ! statusUpdateRequest(sender, entity.asString)

    case HttpRequest(POST, Uri.Path("/postPhoto"), _, entity: HttpEntity.NonEmpty, _) =>
      //println("Received statusUpdate request")
      photoPostEngine ! postPhotoRequest(sender, entity.asString)

    case HttpRequest(GET, Uri.Path("/homeTimeline"), _, entity: HttpEntity.NonEmpty, _) =>
      //println("Received home timeline request")
      timelineEngine ! TimelineRequest(sender, 0, entity.asString)

    case HttpRequest(GET, Uri.Path("/tagsTimeline"), _, entity: HttpEntity.NonEmpty, _) =>
      //println("Received tags timeline request")
      timelineEngine ! TimelineRequest(sender, 1, entity.asString)

    case HttpRequest(GET, Uri.Path("/userTimeline"), _, entity: HttpEntity.NonEmpty, _) =>
      //println("Received user timeline request")
      timelineEngine ! TimelineRequest(sender, 2, entity.asString)

    case unknown: HttpRequest =>
      sender ! HttpResponse(status = 404, entity = s"$unknown: Sorry, this request cannot be processed.")
  }
}

object Server extends App {
  var stats: FBStats = new FBStats(0,0,0,0)
  val config_str = "{\n\t\"serverIP\":\"localhost\",\n\t\"serverPort\":5642,\n\t\"nOfUsers\":1000000000,\n\t\"scale\":0.00005,\n\t\"majorEvent\":40000,\n\t\"statsInterval\":1000,\n\t\"users\":[{\n    \t\t\"category\":1,\n    \t\t\"count\":1000,\n    \t\t\"Friends\":[10,10,10,250000,250000,250000,250000],\n    \t\t\"statusUpdateInterval\":1800,\n    \t\t\"timelineInterval\":86400,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":2,\n    \t\t\"count\":100000,\n    \t\t\"Friends\":[0,10,10,25000,25000,25000,25000],\n    \t\t\"statusUpdateInterval\":3600,\n    \t\t\"timelineInterval\":10000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":3,\n    \t\t\"count\":1000000,\n    \t\t\"Friends\":[0,0,10,2500,2500,2500,2500],\n    \t\t\"statusUpdateInterval\":10800,\n    \t\t\"timelineInterval\":40000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":4,\n    \t\t\"count\":10000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":18000,\n    \t\t\"timelineInterval\":50000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":5,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":86400,\n    \t\t\"timelineInterval\":200000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":6,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":2592000,\n    \t\t\"timelineInterval\":20000,\n    \t\t\"profileInterval\":72000\n\t\t},\n\t\t{\n\t\t\t\"category\":7,\n    \t\t\"count\":100000000,\n    \t\t\"Friends\":[0,0,0,25,25,25,25],\n    \t\t\"statusUpdateInterval\":31104000,\n    \t\t\"timelineInterval\":20000,\n    \t\t\"profileInterval\":360000\n\t\t}\n\t]\n}"
  val config_json = config_str.parseJson
  val config = config_json.convertTo[FBConfig]
  println("No. of users: " + config.nOfUsers)
  println("Scale: " + config.scale)


  var userDatabase: Array[UserInfo] =
    new Array[UserInfo]((config.nOfUsers.toDouble * config.scale).toInt)

  println("Database size: " + userDatabase.length + " users")

  for (nodeCount <- 0 until (config.nOfUsers * config.scale).toInt) {
    userDatabase(nodeCount) = new UserInfo()
  }

  var user_end = new Array[Int](8)

  user_end(0) = 0

  // Create Friends for each user
  for (category <- 1 to 7) {
    user_end(category) = user_end(category-1) +
      (config.users(category-1).count * config.scale).toInt
    for(nodeCount <- user_end(category-1) to user_end(category)-1) {
      for (cat <- 1 to 7) {
        userDatabase(nodeCount).myFriends(cat-1) = new Friend()
        userDatabase(nodeCount).myFriends(cat-1).count =
          Math.min((config.users(cat-1).count * config.scale).toInt,
            config.users(category-1).Friends(cat-1))
        if (userDatabase(nodeCount).myFriends(cat-1).count > 0) {
          val range = (config.users(cat-1).count * config.scale).toInt -
            userDatabase(nodeCount).myFriends(cat-1).count
          if (range == 0)
            userDatabase(nodeCount).myFriends(cat-1).start = user_end(cat-1)
          else
            userDatabase(nodeCount).myFriends(cat-1).start =
              user_end(cat-1) + Random.nextInt(range)
        }
      }
    }
  }

  val serverConfig = ConfigFactory.parseString(
    """spray.can {
           server{
             pipelining-limit = 128
    	   }
      }""")
  implicit val system = ActorSystem("FBServerSystem", ConfigFactory.load(serverConfig))
  //println(system.logConfiguration)
  val handler = system.actorOf(Props(new FBServer(userDatabase, config)), name = "handler")
  IO(Http) ! Http.Bind(handler, interface = config.serverIP, port = config.serverPort)
  println("Server started!")
  system.actorOf(Props(new Tracker(config.statsInterval)), name = "tracker")
}