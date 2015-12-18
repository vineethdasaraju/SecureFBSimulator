package client

import java.security._
import java.util
import javax.crypto.spec.IvParameterSpec
import javax.crypto.{Cipher, KeyGenerator, SecretKey}

import javax.xml.bind.DatatypeConverter

import org.apache.commons.codec.binary.Base64

trait AES {

  val ALGORITHM = "AES/CBC/PKCS5Padding"
  val CHARSET = "UTF-8"


  def encryptAESbase64(key: SecretKey, plainText: String): String = {
    //Instantiate the cipher
    val cipher = Cipher.getInstance(ALGORITHM)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val encryptedTextBytes = cipher.doFinal(plainText.getBytes(CHARSET))
    var encypted_text : String = Base64.encodeBase64String(encryptedTextBytes)
    return encypted_text
  }

  def decryptAESbase64(encryptedText: String, key: SecretKey): String = {
    //Instantiate the cipher
    val cipher = Cipher.getInstance(ALGORITHM)
    cipher.init(Cipher.DECRYPT_MODE, key)

    val decryptedTextBytes = cipher.doFinal(Base64.decodeBase64(encryptedText))
    new String(decryptedTextBytes)
  }

  def encryptAES(key: SecretKey, plainText: String, ivector : String): Array[Byte] = {
    //Instantiate the cipher
    val x = ivector.getBytes("UTF-8")
    val iv:IvParameterSpec = new IvParameterSpec(util.Arrays.copyOfRange(x, 0, 16))
    val cipher = Cipher.getInstance(ALGORITHM)
    cipher.init(Cipher.ENCRYPT_MODE, key, iv)
    val encryptedTextBytes = cipher.doFinal(plainText.getBytes(CHARSET))
    encryptedTextBytes
  }

  def decryptAES(encryptedText: Array[Byte], key: SecretKey, ivector:String): String = {
    //Instantiate the cipher
    val x = ivector.getBytes("UTF-8")
    val iv:IvParameterSpec = new IvParameterSpec(util.Arrays.copyOfRange(x, 0, 16))
    val cipher = Cipher.getInstance(ALGORITHM)
    cipher.init(Cipher.DECRYPT_MODE, key, iv)

    val decryptedTextBytes = cipher.doFinal(encryptedText)
    new String(decryptedTextBytes)
  }

  def generateAESKey: SecretKey = {
    val rand = new SecureRandom();
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(rand)
    keyGen.generateKey()
  }
}
