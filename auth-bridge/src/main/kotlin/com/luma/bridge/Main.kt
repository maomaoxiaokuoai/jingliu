package com.luma.bridge

import com.luma.core.*
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val env=System.getenv();val base=QrProtocol.bridgeBase(env["PUBLIC_BASE_URL"].orEmpty())
    val keys=QrCapability.bridged.associateWith{p->Credentials(env[p.name+"_CLIENT_KEY"].orEmpty(),env[p.name+"_CLIENT_SECRET"].orEmpty())}
    val engine=BridgeEngine(base,Providers(keys))
    val server=createBridgeServer(engine,env["PORT"]?.toIntOrNull()?:8787)
    val cleanup=Executors.newSingleThreadScheduledExecutor()
    cleanup.scheduleAtFixedRate({engine.cleanup()},30,30,TimeUnit.SECONDS)
    Runtime.getRuntime().addShutdownHook(Thread{server.stop(0);cleanup.shutdownNow();(server.executor as java.util.concurrent.ExecutorService).shutdownNow()})
    server.start();println("Jingliu authorization bridge listening on loopback. No platform secrets are logged.")
}
/** Localhost only; production TLS/rate limiting belongs to the developer's reverse proxy. */
fun createBridgeServer(engine:BridgeEngine,port:Int):HttpServer {
    val server=HttpServer.create(InetSocketAddress("127.0.0.1",port),32)
    server.executor=Executors.newFixedThreadPool(8)
    server.createContext("/api/qr/"){exchange->
        try {
            require(exchange.requestMethod=="POST")
            require(exchange.requestHeaders.getFirst("Content-Type").orEmpty().startsWith("application/json"))
            val body=exchange.requestBody.use{input->val out=ByteArrayOutputStream();val b=ByteArray(4096);while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=16384);out.write(b,0,n)};Json.parse(out.toString("UTF-8"))}
            val data=when(exchange.requestURI.path) {
                "/api/qr/start"->engine.start(Platform.valueOf(body.s("platform")),body.s("nonce"))
                "/api/qr/poll"->engine.poll(body.s("session"),body.s("proof"),body.s("nonce"))
                "/api/qr/cancel"->engine.cancel(body.s("session"),body.s("proof"),body.s("nonce"))
                else->error("Unknown route")
            }
            exchange.reply(200,Json.stringify(data),"application/json")
        }catch(_:ProviderUnavailable){exchange.reply(503,"{\"error\":\"platform_not_configured\"}","application/json")}
        catch(_:Exception){exchange.reply(400,"{\"error\":\"authorization_request_failed\"}","application/json")}
    }
    server.createContext("/callback/"){e->
        try {
            require(e.requestMethod=="GET")
            val p=Platform.valueOf(e.requestURI.path.substringAfterLast('/'))
            if(p==Platform.TIKTOK) {
                // A landing page is not proof of authorization. Only validated QR polling can
                // accept TikTok's code/client_ticket/state and finish the associated session.
                e.reply(200,"<!doctype html><meta charset=utf-8><title>镜流授权</title><p>请回到镜流等待官方二维码状态确认。</p>","text/html")
                return@createContext
            }
            val done=engine.callback(p,QrProtocol.query(e.requestURI.toString()))
            e.reply(if(done)200 else 400,"<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>镜流授权</title><p>"+
                (if(done)"平台身份已经确认，请回到镜流。" else "本次授权未完成、已取消或已过期，请回到镜流重新申请。")+"</p>","text/html")
        }catch(_:Exception){e.reply(400,"Authorization was not completed. Return to the app.","text/plain")}
    }
    return server
}
private fun HttpExchange.reply(status:Int,body:String,type:String) {
    responseHeaders.set("Content-Type","$type; charset=utf-8");responseHeaders.set("Cache-Control","no-store")
    responseHeaders.set("Referrer-Policy","no-referrer");responseHeaders.set("X-Content-Type-Options","nosniff")
    responseHeaders.set("Content-Security-Policy","default-src 'none'; frame-ancestors 'none'")
    val bytes=body.toByteArray();sendResponseHeaders(status,bytes.size.toLong());responseBody.use{it.write(bytes)};close()
}
