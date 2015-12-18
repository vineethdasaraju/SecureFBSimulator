package client

import com.google.gson.JsonObject

import spray.json._

/**
  * Created by vineeth on 12/15/15.
  */
object Security extends App with AES with RSA{

//  val retVal : JsonObject = new JsonObject
//  retVal.addProperty("testing", "SecretKey")
//  val result = retVal.toString
//  var jsonresult = result.parseJson
//  var finalresult = jsonresult.asInstanceOf[JsObject].getFields("testing")(0).toString
//
//
//
//  val plainText = "Hello World1213221"
//  val AESkey = generateAESKey
//  val ivector = "foo"
//  println(AESkey.getEncoded.toString)
//  println("alice: " + plainText)
//  val enc =  encryptAES(AESkey, plainText, ivector)
//  println(new String(enc))
//  val text = decryptAES(enc, AESkey, ivector)
//  println("bob: " + text)
//
//  val KP = getKeyPair
//  println(KP.getPublic.toString)
//  println(KP.getPrivate.toString)
//  val s : String = encryptRSA(plainText, KP.getPublic)
//  println(s)
//
//  println(decryptRSA(s, KP.getPrivate))

  for(i <- 1 to 10000000){
    val AESkey = generateAESKey
    println(encodeBASE64(AESkey.getEncoded))
  }

}
