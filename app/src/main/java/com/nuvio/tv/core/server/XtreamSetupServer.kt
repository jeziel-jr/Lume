package com.nuvio.tv.core.server

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.nuvio.tv.data.xtream.XtreamCredentials
import fi.iki.elonen.NanoHTTPD
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class XtreamSetupServer(
    private val context: Context,
    private val defaultBaseUrl: String,
    private val onCredentialsProposed: (String, XtreamCredentials) -> Unit,
    port: Int,
) : NanoHTTPD(port) {
    enum class Status { VALIDATING, AWAITING_CONFIRMATION, APPLIED, REJECTED, INVALID, ERROR }

    private val gson = Gson()
    private val sessionKey = ByteArray(32).also(SecureRandom()::nextBytes)
    private val statuses = ConcurrentHashMap<String, Pair<Status, String?>>()

    val qrFragment: String = Base64.encodeToString(
        sessionKey,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    override fun serve(session: IHTTPSession): Response = when {
        session.method == Method.GET && session.uri == "/" -> html(XtreamSetupWebPage.html())
        session.method == Method.GET && session.uri == "/crypto-js.js" -> serveCryptoJs()
        session.method == Method.GET && session.uri == "/api/config" -> json(
            Response.Status.OK,
            mapOf("defaultBaseUrl" to defaultBaseUrl),
        )
        session.method == Method.POST && session.uri == "/api/credentials" -> receiveCredentials(session)
        session.method == Method.GET && session.uri.startsWith("/api/status/") -> {
            val id = session.uri.substringAfterLast('/')
            val state = statuses[id]
            json(
                Response.Status.OK,
                mapOf(
                    "status" to (state?.first?.name?.lowercase() ?: "not_found"),
                    "message" to state?.second,
                ),
            )
        }
        else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    fun updateStatus(id: String, status: Status, message: String? = null) {
        if (statuses.containsKey(id)) statuses[id] = status to message
    }

    private fun receiveCredentials(session: IHTTPSession): Response {
        val bodyFiles = HashMap<String, String>()
        return runCatching {
            session.parseBody(bodyFiles)
            val envelope = gson.fromJson(bodyFiles["postData"].orEmpty(), EncryptedEnvelope::class.java)
            val json = decrypt(envelope)
            val payload = gson.fromJson(json, CredentialPayload::class.java)
            val credentials = XtreamCredentials(
                baseUrl = payload.baseUrl.orEmpty(),
                username = payload.username.orEmpty(),
                password = payload.password.orEmpty(),
            ).normalized()
            require(credentials.isComplete)
            val id = UUID.randomUUID().toString()
            statuses.keys.forEach { oldId -> statuses[oldId] = Status.REJECTED to null }
            statuses[id] = Status.VALIDATING to null
            onCredentialsProposed(id, credentials)
            json(Response.Status.OK, mapOf("id" to id, "status" to "validating"))
        }.getOrElse {
            json(Response.Status.BAD_REQUEST, mapOf("error" to context.getString(com.nuvio.tv.R.string.xtream_setup_invalid_payload)))
        }
    }

    private fun decrypt(envelope: EncryptedEnvelope): String {
        val iv = Base64.decode(envelope.iv, Base64.DEFAULT)
        val payload = Base64.decode(envelope.payload, Base64.DEFAULT)
        val suppliedMac = Base64.decode(envelope.mac, Base64.DEFAULT)
        val expectedMac = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(sessionKey, "HmacSHA256"))
            doFinal(iv + payload)
        }
        require(MessageDigest.isEqual(suppliedMac, expectedMac))
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(sessionKey, "AES"),
            IvParameterSpec(iv),
        )
        return cipher.doFinal(payload).toString(Charsets.UTF_8)
    }

    private fun html(value: String): Response = newFixedLengthResponse(
        Response.Status.OK,
        "text/html; charset=utf-8",
        value,
    )

    private fun serveCryptoJs(): Response {
        val resource = javaClass.classLoader
            ?.getResourceAsStream("META-INF/resources/webjars/crypto-js/4.2.0/crypto-js.js")
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val bytes = resource.use { it.readBytes() }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/javascript; charset=utf-8",
            bytes.inputStream(),
            bytes.size.toLong(),
        )
    }

    private fun json(status: Response.Status, value: Any): Response = newFixedLengthResponse(
        status,
        "application/json; charset=utf-8",
        gson.toJson(value),
    )

    private data class EncryptedEnvelope(
        val iv: String = "",
        val payload: String = "",
        val mac: String = "",
    )
    private data class CredentialPayload(
        val baseUrl: String? = null,
        val username: String? = null,
        val password: String? = null,
    )

    companion object {
        fun startOnAvailablePort(
            context: Context,
            defaultBaseUrl: String,
            onCredentialsProposed: (String, XtreamCredentials) -> Unit,
            startPort: Int = 8110,
            maxAttempts: Int = 10,
        ): XtreamSetupServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    return XtreamSetupServer(context, defaultBaseUrl, onCredentialsProposed, port).also {
                        it.start(SOCKET_READ_TIMEOUT, false)
                    }
                } catch (_: Exception) {
                }
            }
            return null
        }
    }
}

private object XtreamSetupWebPage {
    fun html(): String = """
        <!doctype html>
        <html lang="pt-BR">
        <head>
          <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Lume — Configurar servidor</title>
          <style>
            :root{color-scheme:dark;--ink:#f7f0e4;--muted:#a9a094;--amber:#e0a64b;--line:#3e3931;--card:#1b1916;--field:#12110f}
            *{box-sizing:border-box}body{margin:0;color:var(--ink);font-family:Inter,ui-sans-serif,system-ui,-apple-system,sans-serif;min-height:100svh;display:grid;place-items:center;padding:22px;background:radial-gradient(circle at 50% -10%,#43331f 0,transparent 42%),linear-gradient(160deg,#0d0c0b,#151310 58%,#0d0c0b)}
            main{position:relative;overflow:hidden;width:min(100%,480px);background:linear-gradient(145deg,rgba(31,29,25,.98),rgba(23,21,18,.98));border:1px solid rgba(224,166,75,.2);border-radius:30px;padding:30px;box-shadow:0 30px 80px rgba(0,0,0,.48)}
            main:before{content:"";position:absolute;width:180px;height:180px;border-radius:50%;background:rgba(224,166,75,.07);right:-90px;top:-100px}.brand{display:flex;align-items:center;gap:11px;margin-bottom:28px}.mark{width:38px;height:38px;border-radius:12px;display:grid;place-items:center;background:var(--amber);color:#171108;font-weight:900;font-size:20px;box-shadow:0 8px 24px rgba(224,166,75,.2)}.wordmark{font-size:18px;font-weight:800;letter-spacing:.08em}.eyebrow{font-size:11px;color:var(--amber);text-transform:uppercase;letter-spacing:.16em;font-weight:800;margin-bottom:8px}
            h1{margin:0 0 9px;font-size:clamp(27px,8vw,34px);line-height:1.05;letter-spacing:-.035em}p{color:var(--muted);margin:0 0 26px;line-height:1.55;font-size:14px}
            label{display:block;font-size:12px;text-transform:uppercase;letter-spacing:.08em;font-weight:700;color:#d4cbbd;margin:17px 0 8px}.field-row{display:flex;gap:8px;align-items:stretch}input{width:100%;min-width:0;border:1px solid var(--line);background:var(--field);color:#fff;border-radius:14px;padding:14px 15px;font-size:16px;outline:none;transition:.2s border-color,.2s box-shadow,.2s background}input:focus{border-color:var(--amber);box-shadow:0 0 0 3px rgba(224,166,75,.12)}input:disabled{opacity:1;color:#b9b0a3;background:#24211d;cursor:not-allowed}
            .edit{width:50px;flex:0 0 50px;margin:0;padding:0;border-radius:14px;border:1px solid var(--line);background:#29251f;color:var(--amber);display:grid;place-items:center}.edit svg{width:20px;height:20px}.edit:active{transform:scale(.96)}
            .submit{width:100%;margin-top:27px;border:0;border-radius:999px;padding:16px;background:linear-gradient(135deg,#e6ad54,#c9852d);color:#181108;font-size:16px;font-weight:800;box-shadow:0 12px 30px rgba(201,133,45,.2)}button:disabled{opacity:.52}.security{display:flex;gap:8px;align-items:center;margin-top:15px;color:#827a70;font-size:11px;justify-content:center}.security svg{width:14px;height:14px;color:#9f968a}
            #status{min-height:22px;margin-top:19px;color:var(--amber);text-align:center;font-size:14px}.error{color:#ef8e86!important}.ok{color:#8ed0a5!important}@media(max-width:420px){main{padding:24px;border-radius:25px}.brand{margin-bottom:23px}}
          </style>
        </head>
        <body><main><div class="brand"><div class="mark">L</div><div class="wordmark">LUME</div></div><div class="eyebrow">Configuração segura</div><h1>Conecte sua conta</h1><p>Use os dados do seu provedor. Eles serão enviados somente para esta TV e você ainda confirmará tudo na tela.</p>
          <form id="form"><label>Servidor / DNS</label><div class="field-row"><input id="baseUrl" required disabled autocapitalize="none" spellcheck="false"><button id="editDns" class="edit" type="button" aria-label="Editar servidor"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg></button></div>
          <label>Usuário</label><input id="username" required autocapitalize="none" autocomplete="username" spellcheck="false">
          <label>Senha</label><input id="password" required type="password" autocomplete="current-password">
          <button class="submit" id="submit" type="submit">Conectar à TV</button></form><div class="security"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg><span>Envio protegido e válido apenas nesta sessão</span></div><div id="status"></div>
        </main><script src="/crypto-js.js"></script><script>
          const statusEl=document.getElementById('status'),submit=document.getElementById('submit'),baseUrl=document.getElementById('baseUrl'),username=document.getElementById('username'),password=document.getElementById('password'),editDns=document.getElementById('editDns');
          fetch('/api/config').then(r=>r.json()).then(v=>baseUrl.value=v.defaultBaseUrl||'');
          editDns.addEventListener('click',()=>{baseUrl.disabled=false;baseUrl.focus();baseUrl.select();editDns.style.display='none'});
          const key=()=>{let s=location.hash.slice(1).replace(/-/g,'+').replace(/_/g,'/');while(s.length%4)s+='=';return CryptoJS.enc.Base64.parse(s)};
          async function poll(id){const v=await fetch('/api/status/'+id).then(r=>r.json());
            if(v.status==='validating'){statusEl.textContent='Validando credenciais…';setTimeout(()=>poll(id),800)}
            else if(v.status==='awaiting_confirmation'){statusEl.textContent='Confirme os dados na TV…';setTimeout(()=>poll(id),800)}
            else if(v.status==='applied'){statusEl.className='ok';statusEl.textContent='Tudo pronto. Você pode voltar para a TV.'}
            else if(v.status==='invalid'||v.status==='error'){statusEl.className='error';statusEl.textContent=v.message||'Não foi possível validar os dados.';submit.disabled=false}
            else{statusEl.className='error';statusEl.textContent='A sessão expirou. Gere outro QR na TV.'}}
          document.getElementById('form').addEventListener('submit',async e=>{e.preventDefault();submit.disabled=true;statusEl.className='';statusEl.textContent='Enviando…';try{
            const secret=key(),iv=CryptoJS.lib.WordArray.random(16),plain=JSON.stringify({baseUrl:baseUrl.value,username:username.value,password:password.value});
            const payload=CryptoJS.AES.encrypt(plain,secret,{iv,mode:CryptoJS.mode.CBC,padding:CryptoJS.pad.Pkcs7}).ciphertext;
            const mac=CryptoJS.HmacSHA256(iv.clone().concat(payload),secret);
            const response=await fetch('/api/credentials',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({iv:CryptoJS.enc.Base64.stringify(iv),payload:CryptoJS.enc.Base64.stringify(payload),mac:CryptoJS.enc.Base64.stringify(mac)})});
            if(!response.ok)throw new Error();const value=await response.json();poll(value.id)
          }catch(_){statusEl.className='error';statusEl.textContent='Falha ao enviar. Confirme que o celular continua na mesma rede da TV.';submit.disabled=false}});
        </script></body></html>
    """.trimIndent()
}
