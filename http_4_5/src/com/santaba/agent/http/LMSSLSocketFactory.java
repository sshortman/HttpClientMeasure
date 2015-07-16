package com.santaba.agent.http;

import org.apache.http.HttpHost;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpInetSocketAddress;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.LayeredSchemeSocketFactory;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.scheme.SchemeLayeredSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLInitializationException;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;
import org.apache.http.util.TextUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/14/15
 */
@ThreadSafe
@Deprecated
public class LMSSLSocketFactory implements LayeredConnectionSocketFactory, SchemeLayeredSocketFactory,
        LayeredSchemeSocketFactory, LayeredSocketFactory {

    public static final String TLS   = "TLS";
    public static final String SSL   = "SSL";
    public static final String SSLV2 = "SSLv2";

    public static final X509HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER
            = new AllowAllHostnameVerifier();

    public static final X509HostnameVerifier BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
            = new BrowserCompatHostnameVerifier();

    public static final X509HostnameVerifier STRICT_HOSTNAME_VERIFIER
            = new StrictHostnameVerifier();

    public int handShakeCount = 0;
    public long handShakeTime = 0;
    public int handShakeStatus = 100; //200 means OK

    /**
     * Obtains default SSL socket factory with an SSL context based on the standard JSSE
     * trust material ({@code cacerts} file in the security properties directory).
     * System properties are not taken into consideration.
     *
     * @return default SSL socket factory
     */
    public static LMSSLSocketFactory getSocketFactory() throws SSLInitializationException {
        return new LMSSLSocketFactory(
                SSLContexts.createDefault(),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    private static String[] split(final String s) {
        if (TextUtils.isBlank(s)) {
            return null;
        }
        return s.split(" *, *");
    }

    /**
     * Obtains default SSL socket factory with an SSL context based on system properties
     * as described in
     * <a href="http://docs.oracle.com/javase/1.5.0/docs/guide/security/jsse/JSSERefGuide.html">
     * "JavaTM Secure Socket Extension (JSSE) Reference Guide for the JavaTM 2 Platform
     * Standard Edition 5</a>
     *
     * @return default system SSL socket factory
     */
    public static LMSSLSocketFactory getSystemSocketFactory() throws SSLInitializationException {
        return new LMSSLSocketFactory(
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault(),
                split(System.getProperty("https.protocols")),
                split(System.getProperty("https.cipherSuites")),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    private final javax.net.ssl.SSLSocketFactory socketfactory;
    private final HostNameResolver nameResolver;
    // TODO: make final
    private volatile X509HostnameVerifier hostnameVerifier;
    private final String[] supportedProtocols;
    private final String[] supportedCipherSuites;

    public LMSSLSocketFactory(
            final String algorithm,
            final KeyStore keystore,
            final String keyPassword,
            final KeyStore truststore,
            final SecureRandom random,
            final HostNameResolver nameResolver)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .useProtocol(algorithm)
                        .setSecureRandom(random)
                        .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                        .loadTrustMaterial(truststore)
                        .build(),
                nameResolver);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final String algorithm,
            final KeyStore keystore,
            final String keyPassword,
            final KeyStore truststore,
            final SecureRandom random,
            final TrustStrategy trustStrategy,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .useProtocol(algorithm)
                        .setSecureRandom(random)
                        .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                        .loadTrustMaterial(truststore, trustStrategy)
                        .build(),
                hostnameVerifier);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final String algorithm,
            final KeyStore keystore,
            final String keyPassword,
            final KeyStore truststore,
            final SecureRandom random,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .useProtocol(algorithm)
                        .setSecureRandom(random)
                        .loadKeyMaterial(keystore, keyPassword != null ? keyPassword.toCharArray() : null)
                        .loadTrustMaterial(truststore)
                        .build(),
                hostnameVerifier);
    }

    public LMSSLSocketFactory(
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .loadKeyMaterial(keystore, keystorePassword != null ? keystorePassword.toCharArray() : null)
                        .loadTrustMaterial(truststore)
                        .build(),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(
            final KeyStore keystore,
            final String keystorePassword)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException{
        this(SSLContexts.custom()
                        .loadKeyMaterial(keystore, keystorePassword != null ? keystorePassword.toCharArray() : null)
                        .build(),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(
            final KeyStore truststore)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .loadTrustMaterial(truststore)
                        .build(),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final TrustStrategy trustStrategy,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .loadTrustMaterial(null, trustStrategy)
                        .build(),
                hostnameVerifier);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final TrustStrategy trustStrategy)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(SSLContexts.custom()
                        .loadTrustMaterial(null, trustStrategy)
                        .build(),
                BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(final SSLContext sslContext) {
        this(sslContext, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(
            final SSLContext sslContext, final HostNameResolver nameResolver) {
        super();
        this.socketfactory = sslContext.getSocketFactory();
        this.hostnameVerifier = BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        this.nameResolver = nameResolver;
        this.supportedProtocols = null;
        this.supportedCipherSuites = null;
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final SSLContext sslContext, final X509HostnameVerifier hostnameVerifier) {
        this(Args.notNull(sslContext, "SSL context").getSocketFactory(),
                null, null, hostnameVerifier);
    }

    /**
     * @since 4.3
     */
    public LMSSLSocketFactory(
            final SSLContext sslContext,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final X509HostnameVerifier hostnameVerifier) {
        this(Args.notNull(sslContext, "SSL context").getSocketFactory(),
                supportedProtocols, supportedCipherSuites, hostnameVerifier);
    }

    /**
     * @since 4.2
     */
    public LMSSLSocketFactory(
            final javax.net.ssl.SSLSocketFactory socketfactory,
            final X509HostnameVerifier hostnameVerifier) {
        this(socketfactory, null, null, hostnameVerifier);
    }

    /**
     * @since 4.3
     */
    public LMSSLSocketFactory(
            final javax.net.ssl.SSLSocketFactory socketfactory,
            final String[] supportedProtocols,
            final String[] supportedCipherSuites,
            final X509HostnameVerifier hostnameVerifier) {
        this.socketfactory = Args.notNull(socketfactory, "SSL socket factory");
        this.supportedProtocols = supportedProtocols;
        this.supportedCipherSuites = supportedCipherSuites;
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier : BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        this.nameResolver = null;
    }

    /**
     * @param params Optional parameters. Parameters passed to this method will have no effect.
     *               This method will create a unconnected instance of {@link Socket} class.
     * @since 4.1
     */
    public Socket createSocket(final HttpParams params) throws IOException {
        return createSocket((HttpContext) null);
    }

    public Socket createSocket() throws IOException {
        return createSocket((HttpContext) null);
    }

    /**
     * @since 4.1
     */
    public Socket connectSocket(
            final Socket socket,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        Args.notNull(remoteAddress, "Remote address");
        Args.notNull(params, "HTTP parameters");
        final HttpHost host;
        if (remoteAddress instanceof HttpInetSocketAddress) {
            host = ((HttpInetSocketAddress) remoteAddress).getHttpHost();
        } else {
            host = new HttpHost(remoteAddress.getHostName(), remoteAddress.getPort(), "https");
        }
        final int socketTimeout = HttpConnectionParams.getSoTimeout(params);
        final int connectTimeout = HttpConnectionParams.getConnectionTimeout(params);
        socket.setSoTimeout(socketTimeout);
        return connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, null);
    }

    /**
     * Checks whether a socket connection is secure.
     * This factory creates TLS/SSL socket connections
     * which, by default, are considered secure.
     * <p>
     * Derived classes may override this method to perform
     * runtime checks, for example based on the cypher suite.
     * </p>
     *
     * @param sock      the connected socket
     *
     * @return  {@code true}
     *
     * @throws IllegalArgumentException if the argument is invalid
     */
    public boolean isSecure(final Socket sock) throws IllegalArgumentException {
        Args.notNull(sock, "Socket");
        Asserts.check(sock instanceof SSLSocket, "Socket not created by this factory");
        Asserts.check(!sock.isClosed(), "Socket is closed");
        return true;
    }

    /**
     * @since 4.2
     */
    public Socket createLayeredSocket(
            final Socket socket,
            final String host,
            final int port,
            final HttpParams params) throws IOException, UnknownHostException {
        return createLayeredSocket(socket, host, port, (HttpContext) null);
    }

    public Socket createLayeredSocket(
            final Socket socket,
            final String host,
            final int port,
            final boolean autoClose) throws IOException, UnknownHostException {
        return createLayeredSocket(socket, host, port, (HttpContext) null);
    }

    public void setHostnameVerifier(final X509HostnameVerifier hostnameVerifier) {
        Args.notNull(hostnameVerifier, "Hostname verifier");
        this.hostnameVerifier = hostnameVerifier;
    }

    public X509HostnameVerifier getHostnameVerifier() {
        return this.hostnameVerifier;
    }

    public Socket connectSocket(
            final Socket socket,
            final String host, final int port,
            final InetAddress local, final int localPort,
            final HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        final InetAddress remote;
        if (this.nameResolver != null) {
            remote = this.nameResolver.resolve(host);
        } else {
            remote = InetAddress.getByName(host);
        }
        InetSocketAddress localAddress = null;
        if (local != null || localPort > 0) {
            localAddress = new InetSocketAddress(local, localPort > 0 ? localPort : 0);
        }
        final InetSocketAddress remoteAddress = new HttpInetSocketAddress(
                new HttpHost(host, port), remote, port);
        return connectSocket(socket, remoteAddress, localAddress, params);
    }

    public Socket createSocket(
            final Socket socket,
            final String host, final int port,
            final boolean autoClose) throws IOException, UnknownHostException {
        return createLayeredSocket(socket, host, port, autoClose);
    }

    /**
     * Performs any custom initialization for a newly created SSLSocket
     * (before the SSL handshake happens).
     *
     * The default implementation is a no-op, but could be overridden to, e.g.,
     * call {@link SSLSocket#setEnabledCipherSuites(java.lang.String[])}.
     * @throws IOException (only if overridden)
     *
     * @since 4.2
     */
    protected void prepareSocket(final SSLSocket socket) throws IOException {
    }

    private void internalPrepareSocket(final SSLSocket socket) throws IOException {
        if (supportedProtocols != null) {
            socket.setEnabledProtocols(supportedProtocols);
        }
        if (supportedCipherSuites != null) {
            socket.setEnabledCipherSuites(supportedCipherSuites);
        }
        prepareSocket(socket);
    }

    public Socket createSocket(final HttpContext context) throws IOException {
        final SSLSocket sock = (SSLSocket) this.socketfactory.createSocket();
        internalPrepareSocket(sock);
        return sock;
    }

    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        Args.notNull(host, "HTTP host");
        Args.notNull(remoteAddress, "Remote address");
        final Socket sock = socket != null ? socket : createSocket(context);
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        try {
            sock.connect(remoteAddress, connectTimeout);
        } catch (final IOException ex) {
            try {
                sock.close();
            } catch (final IOException ignore) {
            }
            throw ex;
        }
        // Setup SSL layering if necessary
        if (sock instanceof SSLSocket) {
            final SSLSocket sslsock = (SSLSocket) sock;
            handShake(sslsock, host.getHostName());
            return sock;
        } else {
            return createLayeredSocket(sock, host.getHostName(), remoteAddress.getPort(), context);
        }
    }

    public Socket createLayeredSocket(
            final Socket socket,
            final String target,
            final int port,
            final HttpContext context) throws IOException {
        final SSLSocket sslsock = (SSLSocket) this.socketfactory.createSocket(
                socket,
                target,
                port,
                true);
        internalPrepareSocket(sslsock);
        handShake(sslsock, target);
        return sslsock;
    }

    private void handShake(SSLSocket socket, String target) throws IOException {
        boolean shakeSuccess = false;
        long startEpoch = System.currentTimeMillis();
        try {
            socket.startHandshake();
            verifyHostname(socket, target);
            shakeSuccess = true;
        }
        finally {
            long endEpoch = System.currentTimeMillis();
            handShakeCount++;
            handShakeTime += endEpoch - startEpoch;
            handShakeStatus = shakeSuccess ? 200 : 100;
        }
    }

    private void verifyHostname(final SSLSocket sslsock, final String hostname) throws IOException {
        try {
            this.hostnameVerifier.verify(hostname, sslsock);
            // verifyHostName() didn't blowup - good!
        } catch (final IOException iox) {
            // close the socket before re-throwing the exception
            try { sslsock.close(); } catch (final Exception x) { /*ignore*/ }
            throw iox;
        }
    }

}

