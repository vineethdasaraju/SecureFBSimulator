package server

import java.security._
import java.security.spec.X509EncodedKeySpec
//import java.util.Base64
import javax.xml.bind.DatatypeConverter

trait DS {

  def getSecuredRandomInt(): Int = {
    var random = SecureRandom.getInstance("SHA1PRNG")
    random.nextInt()
  }

  def VerifyHash(publicKey: String, data: String, hash_verify : String): Boolean = {
    //Instantiate the cipher
    //val decoder = Base64.getUrlDecoder()
    //val encoder = Base64.getUrlEncoder()
    val data_array : Array[Byte] = data.getBytes("utf-8")
    val hash_array : Array[Byte] = DatatypeConverter.parseBase64Binary(hash_verify)
    //var str : String = new String(data_array,"utf-8")
    //println("str" +str)
    var key_bytes : Array[Byte] = DatatypeConverter.parseBase64Binary(publicKey)
    val spec : X509EncodedKeySpec = new X509EncodedKeySpec(key_bytes)
    val keyfactory : KeyFactory = KeyFactory.getInstance("RSA")
    val publickey: PublicKey = keyfactory.generatePublic(spec)
    //println("public key" +publickey)
    var sig : Signature = Signature.getInstance("MD5WithRSA")
    sig.initVerify(publickey)
    sig.update(data_array)
    sig.verify(hash_array)

  }
}
