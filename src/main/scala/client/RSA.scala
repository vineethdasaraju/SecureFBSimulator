package client

import java.security._
import java.security.spec.X509EncodedKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{SecretKey, Cipher}

import org.apache.commons.codec.binary.Base64


trait RSA {

  def getKeyPair : KeyPair = {
    KeyPairGenerator.getInstance("RSA").generateKeyPair()
  }

  def RsaToString(pubkey: PublicKey) : String = {
    encodeBASE64(pubkey.getEncoded)
  }

  def StringToPubRsa(pubkeyStr : String) : PublicKey = {
    var key_bytes : Array[Byte] = decodeBASE64(pubkeyStr)
    val spec : X509EncodedKeySpec = new X509EncodedKeySpec(key_bytes)
    val keyfactory : KeyFactory = KeyFactory.getInstance("RSA")
    val publickey: PublicKey = keyfactory.generatePublic(spec)
    publickey
  }

  def encryptRSAaESkey (s_key: SecretKey, pubKey : PublicKey) : String = {
    val cipher : Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
    val cipherText : Array[Byte] = cipher.doFinal(s_key.getEncoded)
    return encodeBASE64(cipherText)
  }

  def decryptRSAaESkey (cipherText : String, priKey : PrivateKey) : SecretKey = {
    val cipher : Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE, priKey)
    val key = new SecretKeySpec( cipher.doFinal(decodeBASE64(cipherText)),"AES")
    return key
  }


  def encryptRSA (plainText : String, pubKey : PublicKey) : String = {
    val cipher : Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
    val cipherText : Array[Byte] = cipher.doFinal(plainText.getBytes("UTF8"))
    return encodeBASE64(cipherText)
  }

  def decryptRSA (cipherText : String, priKey : PrivateKey) : String = {
    val cipher : Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    cipher.init(Cipher.DECRYPT_MODE, priKey)
    val plaintext: Array[Byte] = cipher.doFinal(decodeBASE64(cipherText))
    return new String(plaintext, "UTF8")
  }

  def getPublicKey(bytes : Array[Byte]) : PublicKey = {
      KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes))
  }

  def encodeBASE64(bytes: Array[Byte]): String = {
    return Base64.encodeBase64String(bytes)
  }

  def decodeBASE64(text:String) : Array[Byte] = {
    return Base64.decodeBase64(text)
  }

}
