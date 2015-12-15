package client

/**
  * Created by vineeth on 12/15/15.
  */
object Security extends App with AES with RSA{

  val plainText = "Hello World1213221"
  val AESkey = generateAESKey
  val ivector = "foo"
  println(AESkey.getEncoded.toString)
  println("alice: " + plainText)
  val enc = encryptAES(AESkey, plainText, ivector)
  println(new String(enc))
  val text = decryptAES(enc, AESkey, ivector)
  println("bob: " + text)

  val KP = getKeyPair
  println(KP.getPublic.toString)
  println(KP.getPrivate.toString)
  val s : String = encryptRSA(plainText, KP.getPublic)
  println(s)

  println(decryptRSA(s, KP.getPrivate))

}
