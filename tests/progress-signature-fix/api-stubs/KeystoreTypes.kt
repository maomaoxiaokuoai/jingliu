// Compile-only Android Keystore declarations. No emulated AndroidKeyStore provider.
package android.security.keystore
object KeyProperties {
 const val KEY_ALGORITHM_AES="AES";const val PURPOSE_ENCRYPT=1;const val PURPOSE_DECRYPT=2
 const val BLOCK_MODE_GCM="GCM";const val ENCRYPTION_PADDING_NONE="NoPadding"
}
class KeyGenParameterSpec:java.security.spec.AlgorithmParameterSpec {
 class Builder(alias:String,purpose:Int) {
  fun setBlockModes(vararg modes:String)=this
  fun setEncryptionPaddings(vararg padding:String)=this
  fun setKeySize(bits:Int)=this
  fun build()=KeyGenParameterSpec()
 }
}
