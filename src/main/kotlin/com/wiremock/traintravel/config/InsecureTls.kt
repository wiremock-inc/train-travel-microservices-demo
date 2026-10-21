package com.wiremock.traintravel.config

import java.net.Socket
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Makes every HTTPS client in the JVM accept any certificate and hostname, so downstream mocks
 * can be served with self-signed certificates. Demo only - never do this in production.
 *
 * Must run before any client is built: the JDK HttpClient, HttpURLConnection (SOAP) and the
 * JWT issuer/JWKS lookups all take the default SSLContext.
 */
object InsecureTls {

    fun trustAllCertificates() {
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(TrustAll), null)
        }
        SSLContext.setDefault(context)
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
    }

    // Extended, so JSSE delegates hostname checks here too instead of wrapping it with its own
    private object TrustAll : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {}
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
