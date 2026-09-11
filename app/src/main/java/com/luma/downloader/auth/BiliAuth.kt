package com.luma.downloader.auth

import com.luma.core.*
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

class LoginRejected:Exception("B站会话已失效，请重新登录")
data class QrTicket(val url:String,val key:String,val expiresAt:Long)
data class QrResult(val code:Long,val text:String,val account:Account?=null)
class BiliAuth(private val vault:SessionVault) {
    private val headers=mapOf("Referer" to "https://www.bilibili.com/")
    fun newQr(token:CancelToken):QrTicket {
        val d=Http(direct=true).json("https://passport.bilibili.com/x/passport-login/web/qrcode/generate",token,headers)
        require(d.n("code")==0L){"二维码申请失败，请检查网络"}
        val u=d.s("data","url");UrlPolicy.checkTransport(u);require(Platform.BILIBILI.owns(java.net.URI(u).host))
        val key=d.s("data","qrcode_key");require(key.isNotBlank()){"平台未返回有效二维码编号"}
        return QrTicket(u,key,System.currentTimeMillis()+180_000)
    }
    fun poll(ticket:QrTicket,token:CancelToken):QrResult {
        if(System.currentTimeMillis()>=ticket.expiresAt)return QrResult(86038L,"二维码已过期")
        val jar=MemoryCookies();val http=Http(jar,true)
        val r=http.json("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=${enc(ticket.key)}",token,headers)
        require(r.n("code")==0L){"二维码状态读取失败"}
        val d=r.at("data");val code=d.n("code")?:-1
        if(code!=0L)return QrResult(code,when(code){86101L->"等待使用 B站 App 扫码";86090L->"已扫码，请在 B站确认";86038L->"二维码已过期";else->"授权未完成，请重试"})
        // Confirmation URL may carry Set-Cookie only after following it. Never print/log the URL.
        if(jar.cookies().none{it.name=="SESSDATA"}) {
            val confirm=d.s("url")
            if(confirm.startsWith("https://")&&Platform.BILIBILI.owns(java.net.URI(confirm).host))http.text(confirm,token,headers)
        }
        require(jar.cookies().any{it.name=="SESSDATA"}){"平台未返回会话 Cookie"}
        val account=verify(Account(Platform.BILIBILI,jar.cookies(),d.s("refresh_token")),token)
        return QrResult(0,"登录成功",account)
    }
    fun verify(account:Account,token:CancelToken):Account {
        val jar=MemoryCookies(account.cookies);val r=Http(jar,true).json("https://api.bilibili.com/x/web-interface/nav",token,headers)
        if(r.n("code")==-101L||r.at("data","isLogin")==false)throw LoginRejected()
        require(r.n("code")==0L&&r.at("data","isLogin").truth()){"未能验证 B站身份，请稍后重试"}
        val d=r.at("data");val vip=when {d.n("vipStatus")==1L&&d.n("vipType")==2L->"年度大会员";d.n("vipStatus")==1L->"大会员";d.n("vipStatus")!=null->"非有效大会员";else->"未获取"}
        return account.copy(cookies=jar.cookies(),uid=d.n("mid").toString(),name=d.s("uname"),avatar=secureUrl(d.s("face")),vip=vip,checked=System.currentTimeMillis(),rejected=false)
    }
    @Synchronized fun maintain(token:CancelToken,autoRefresh:Boolean):Account? {
        var account=vault.get(Platform.BILIBILI)?:return null
        val revision=account.revision
        if(account.pendingConfirm.isNotBlank())account=confirm(account,token)
        val jar=MemoryCookies(account.cookies);val http=Http(jar,true)
        val csrf=jar.cookies().firstOrNull{it.name=="bili_jct"}?.value.orEmpty()
        if(autoRefresh&&csrf.isNotBlank()&&account.refreshToken.isNotBlank()) {
            val info=http.json("https://passport.bilibili.com/x/passport-login/web/cookie/info?csrf=${enc(csrf)}",token,headers)
            if(info.n("code")==-101L)throw LoginRejected()
            require(info.n("code")==0L){"会话续期检查未通过"}
            if(info.at("data","refresh").truth()) {
                val stamp=info.n("data","timestamp")?:System.currentTimeMillis()
                val correspond=correspondPath(stamp)
                val page=http.text("https://www.bilibili.com/correspond/1/$correspond",token,headers).body
                val refreshCsrf=Regex("<div[^>]*id=[\"']1-name[\"'][^>]*>([^<]+)</div>").find(page)?.groupValues?.get(1)?:error("未获取到续期校验值")
                val r=http.json("https://passport.bilibili.com/x/passport-login/web/cookie/refresh",token,headers,mapOf("csrf" to csrf,"refresh_csrf" to refreshCsrf,"source" to "main_web","refresh_token" to account.refreshToken))
                require(r.n("code")==0L&&r.s("data","refresh_token").isNotBlank()){"平台拒绝刷新会话"}
                val updated=account.copy(cookies=jar.cookies(),refreshToken=r.s("data","refresh_token"),pendingConfirm=account.refreshToken)
                // Persist new credentials BEFORE invalidating the old refresh token; journal supports crash recovery.
                check(vault.saveIfUnchanged(account,updated)){"账号已被切换，取消旧账号更新"}
                account=confirm(updated,token)
            }
        }
        val checked=verify(account,token)
        check(vault.saveIfUnchanged(account,checked)) { "账号已切换，未覆盖新会话" };return checked
    }
    private fun confirm(account:Account,token:CancelToken):Account {
        if(account.pendingConfirm.isBlank())return account
        val csrf=account.cookies.firstOrNull{it.name=="bili_jct"}?.value?:error("续期凭据缺少 csrf")
        val r=Http(MemoryCookies(account.cookies),true).json("https://passport.bilibili.com/x/passport-login/web/confirm/refresh",token,headers,mapOf("csrf" to csrf,"refresh_token" to account.pendingConfirm))
        // Keep journal on transient/unknown errors rather than discard the new token.
        require(r.n("code")==0L){"续期确认尚未完成；下次联网将重试"}
        return account.copy(pendingConfirm="").also {check(vault.saveIfUnchanged(account,it)){"账号已切换"}}
    }
    private fun correspondPath(timestamp:Long):String {
        val encoded="MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDLgd2OAkcGVtoE3ThUREbio0EgUc/prcajMKXvkCKFCWhJYJcLkcM2DKKcSeFpD/j6Boy538YXnR6VhcuUJOhH2x71nzPjfdTcqMz7djHum0qSZA0AyCBDABUqCrfNgCiJ00Ra7GmRj+YCK1NJEuewlb40JNrRuoEUXpabUzGB8QIDAQAB"
        val key=KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(encoded)))
        val cipher=Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key,OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT))
        return cipher.doFinal("refresh_$timestamp".toByteArray()).joinToString(""){"%02x".format(it)}
    }
}
