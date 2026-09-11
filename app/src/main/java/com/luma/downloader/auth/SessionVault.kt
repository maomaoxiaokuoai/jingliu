package com.luma.downloader.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.util.Base64
import com.luma.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Account(val platform:Platform,val cookies:List<Cookie>,val refreshToken:String="",val pendingConfirm:String="",
    val uid:String="",val name:String="",val avatar:String="",val vip:String="未获取",val checked:Long=0,val rejected:Boolean=false,
    val revision:String=UUID.randomUUID().toString()) {
    val stateLabel: String get() {
        val local = SessionHealthPolicy.local(platform, cookies, rejected)
        if (local in setOf(SessionHealthCode.EXPIRED, SessionHealthCode.REJECTED,
                SessionHealthCode.MISSING, SessionHealthCode.MISSING_LOGIN_COOKIE)) return local.label
        return if (checked > 0) "会话已持久保存 · 待本次检查" else local.label
    }
    fun json():Map<String,Any?> = mapOf("platform" to platform.name,"cookies" to cookies.map{it.json()},"refreshToken" to refreshToken,"pendingConfirm" to pendingConfirm,"uid" to uid,"name" to name,"avatar" to avatar,"vip" to vip,"checked" to checked,"rejected" to rejected,"revision" to revision)
    companion object {fun from(v:Any?)=Account(Platform.valueOf(v.s("platform")),v.list("cookies").map{Cookie.from(it)},v.s("refreshToken"),v.s("pendingConfirm"),v.s("uid"),v.s("name"),v.s("avatar"),v.s("vip").ifBlank{"未获取"},v.n("checked")?:0,v.at("rejected").truth(),v.s("revision").ifBlank{UUID.randomUUID().toString()})}
}

/** No plaintext backup. AES key never leaves Android Keystore; ciphertext and IV are atomically replaced. */
class SessionVault internal constructor(
    private val file: File,
    private val keyProvider: () -> SecretKey
) {
    constructor(context: Context) : this(
        File(context.noBackupFilesDir, "sessions.aes-gcm"),
        { androidKey(context) }
    )
    private val state=MutableStateFlow<List<Account>>(emptyList())
    val accounts=state.asStateFlow()
    private var loaded=false
    private val epochs = mutableMapOf<Platform, Long>()
    @Synchronized fun epoch(p: Platform): Long { load(); return epochs[p] ?: 0L }
    @Synchronized fun load(){
        if(loaded)return
        if(file.exists()) {
            val envelope=Json.parse(file.readText());val iv=Base64.getDecoder().decode(envelope.s("iv"))
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keyProvider(),GCMParameterSpec(128,iv))
            val plain=cipher.doFinal(Base64.getDecoder().decode(envelope.s("data")))
            try {state.value=Json.parse(plain.toString(Charsets.UTF_8)).arr().map{Account.from(it)}}finally{plain.fill(0)}
        };loaded=true
    }
    @Synchronized fun get(p:Platform):Account?{load();return state.value.firstOrNull{it.platform==p}}
    @Synchronized fun save(account:Account,expectedRevision:String?=null):Boolean {
        load();if(expectedRevision!=null&&get(account.platform)?.revision!=expectedRevision)return false
        val next=(state.value.filterNot{it.platform==account.platform}+account).sortedBy{it.platform.ordinal}
        val previous = get(account.platform)
        persist(next)
        if (previous?.revision != account.revision || previous?.cookies != account.cookies) {
            epochs[account.platform] = (epochs[account.platform] ?: 0L) + 1L
        }
        state.value=next;return true
    }
    /** Full snapshot comparison prevents a late check from overwriting a refreshed/new/deleted account. */
    @Synchronized fun saveIfUnchanged(expected: Account?, next: Account): Boolean {
        load()
        if (get(next.platform) != expected) return false
        return save(next)
    }
    @Synchronized fun delete(p:Platform){load();val next=state.value.filterNot{it.platform==p};persist(next);state.value=next;epochs[p]=(epochs[p]?:0L)+1L}
    @Synchronized fun resetCorruptedVault(){if(file.exists()&&!file.delete())error("无法删除安全存储");state.value=emptyList();loaded=true;Platform.entries.forEach{epochs[it]=(epochs[it]?:0L)+1L}}
    fun jar(p:Platform):MemoryCookies=MemoryCookies(get(p)?.cookies.orEmpty())
    private fun persist(accounts:List<Account>){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keyProvider())
        val plain=Json.stringify(accounts.map{it.json()}).toByteArray()
        try {val data=cipher.doFinal(plain);atomicText(file,Json.stringify(mapOf("schema" to 1,"iv" to Base64.getEncoder().encodeToString(cipher.iv),"data" to Base64.getEncoder().encodeToString(data))))}finally{plain.fill(0)}
    }
    private companion object {
        fun androidKey(context: Context): SecretKey {
            val alias = "shiliu.session.v1"
            val file = File(context.noBackupFilesDir, "sessions.aes-gcm")
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(alias, null) as? SecretKey)?.let { return it }
            check(!file.exists()) { "安全密钥已丢失；需要清除本机凭据并重新登录" }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            }.generateKey()
        }
    }
}
