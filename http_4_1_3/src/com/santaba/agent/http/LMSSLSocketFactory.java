package com.santaba.agent.http;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.scheme.LayeredSchemeSocketFactory;
import org.apache.http.conn.scheme.LayeredSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
 * Date: 7/23/15
 */
@SuppressWarnings("deprecation")
@ThreadSafe
public class LMSSLSocketFactory implements LayeredSchemeSocketFactory, LayeredSocketFactory {

    public static final String TLS   = "TLS";
    public static final String SSL   = "SSL";
    public static final String SSLV2 = "SSLv2";

    public static final X509HostnameVerifier ALLOW_ALL_HOSTNAME_VERIFIER
            = new AllowAllHostnameVerifier();

    public static final X509HostnameVerifier BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
            = new BrowserCompatHostnameVerifier();

    public static final X509HostnameVerifier STRICT_HOSTNAME_VERIFIER
            = new StrictHostnameVerifier();

    /**
     * Gets the default factory, which uses the default JVM settings for secure
     * connections.
     *
     * @return the default factory
     */
    public static LMSSLSocketFactory getSocketFactory() {
        return new LMSSLSocketFactory();
    }

    private final javax.net.ssl.SSLSocketFactory socketfactory;
    private final HostNameResolver nameResolver;
    // TODO: make final
    private volatile X509HostnameVerifier hostnameVerifier;

    private static SSLContext createSSLContext(
            String algorithm,
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore,
            final SecureRandom random,
            final TrustStrategy trustStrategy)
            throws NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException, KeyManagementException {
        if (algorithm == null) {
            algorithm = TLS;
        }
        KeyManagerFactory kmfactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmfactory.init(keystore, keystorePassword != null ? keystorePassword.toCharArray(): null);
        KeyManager[] keymanagers =  kmfactory.getKeyManagers();
        TrustManagerFactory tmfactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmfactory.init(truststore);
        TrustManager[] trustmanagers = tmfactory.getTrustManagers();
        if (trustmanagers != null && trustStrategy != null) {
            for (int i = 0; i < trustmanagers.length; i++) {
                TrustManager tm = trustmanagers[i];
                if (tm instanceof X509TrustManager) {
                    trustmanagers[i] = new TrustManagerDecorator(
                            (X509TrustManager) tm, trustStrategy);
                }
            }
        }

        SSLContext sslcontext = SSLContext.getInstance(algorithm);
        sslcontext.init(keymanagers, trustmanagers, random);
        return sslcontext;
    }

    private static SSLContext createDefaultSSLContext() {
        try {
            return createSSLContext(TLS, null, null, null, null, null);
        } catch (Exception ex) {
            throw new IllegalStateException("Failure initializing default SSL context", ex);
        }
    }

    /**
     * @deprecated Use {@link #LMSSLSocketFactory(String, KeyStore, String, KeyStore, SecureRandom, X509HostnameVerifier)}
     */
    @Deprecated
    public LMSSLSocketFactory(
            final String algorithm,
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore,
            final SecureRandom random,
            final HostNameResolver nameResolver)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(createSSLContext(
                        algorithm, keystore, keystorePassword, truststore, random, null),
                nameResolver);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            String algorithm,
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore,
            final SecureRandom random,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(createSSLContext(
                        algorithm, keystore, keystorePassword, truststore, random, null),
                hostnameVerifier);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            String algorithm,
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore,
            final SecureRandom random,
            final TrustStrategy trustStrategy,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(createSSLContext(
                        algorithm, keystore, keystorePassword, truststore, random, trustStrategy),
                hostnameVerifier);
    }

    public LMSSLSocketFactory(
            final KeyStore keystore,
            final String keystorePassword,
            final KeyStore truststore)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(TLS, keystore, keystorePassword, truststore, null, null, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(
            final KeyStore keystore,
            final String keystorePassword)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException{
        this(TLS, keystore, keystorePassword, null, null, null, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(
            final KeyStore truststore)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(TLS, null, null, truststore, null, null, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final TrustStrategy trustStrategy,
            final X509HostnameVerifier hostnameVerifier)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(TLS, null, null, null, null, trustStrategy, hostnameVerifier);
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final TrustStrategy trustStrategy)
            throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        this(TLS, null, null, null, null, trustStrategy, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    public LMSSLSocketFactory(final SSLContext sslContext) {
        this(sslContext, BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
    }

    /**
     * @deprecated Use {@link #LMSSLSocketFactory(SSLContext)}
     */
    @Deprecated
    public LMSSLSocketFactory(
            final SSLContext sslContext, final HostNameResolver nameResolver) {
        super();
        this.socketfactory = sslContext.getSocketFactory();
        this.hostnameVerifier = BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
        this.nameResolver = nameResolver;
    }

    /**
     * @since 4.1
     */
    public LMSSLSocketFactory(
            final SSLContext sslContext, final X509HostnameVerifier hostnameVerifier) {
        super();
        this.socketfactory = sslContext.getSocketFactory();
        this.hostnameVerifier = hostnameVerifier;
        this.nameResolver = null;
    }

    private LMSSLSocketFactory() {
        this(createDefaultSSLContext());
    }

    /**
     * @param params Optional parameters. Parameters passed to this method will have no effect.
     *               This method will create a unconnected instance of {@link Socket} class.
     * @since 4.1
     */
    public Socket createSocket(final HttpParams params) throws IOException {
        return createSocket();
    }

    @Deprecated
    public Socket createSocket() throws IOException {
        Metrics.getInstance().startStep(Metrics.STEP_3);
        try {
            return this.socketfactory.createSocket();
        }
        finally {
            Metrics.getInstance().finishStep(Metrics.STEP_3);
        }
    }

    /**
     * @since 4.1
     */
    public Socket connectSocket(
            final Socket socket,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (remoteAddress == null) {
            throw new IllegalArgumentException("Remote address may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        Socket sock = socket != null ? socket : new Socket();
        if (localAddress != null) {
            sock.setReuseAddress(HttpConnectionParams.getSoReuseaddr(params));
            sock.bind(localAddress);
        }

        int connTimeout = HttpConnectionParams.getConnectionTimeout(params);
        int soTimeout = HttpConnectionParams.getSoTimeout(params);

        try {
            sock.setSoTimeout(soTimeout);
            sock.connect(remoteAddress, connTimeout);
        } catch (SocketTimeoutException ex) {
            throw new ConnectTimeoutException("Connect to " + remoteAddress.getHostName() + "/"
                    + remoteAddress.getAddress() + " timed out");
        }
        SSLSocket sslsock;
        // Setup SSL layering if necessary
        if (sock instanceof SSLSocket) {
            sslsock = (SSLSocket) sock;
        } else {
            sslsock = (SSLSocket) this.socketfactory.createSocket(
                    sock, remoteAddress.getHostName(), remoteAddress.getPort(), true);
        }
        if (this.hostnameVerifier != null) {
            try {
                this.hostnameVerifier.verify(remoteAddress.getHostName(), sslsock);
                // verifyHostName() didn't blowup - good!
            } catch (IOException iox) {
                // close the socket before re-throwing the exception
                try { sslsock.close(); } catch (Exception x) { /*ignore*/ }
                throw iox;
            }
        }
        return sslsock;
    }


    /**
     * Checks whether a socket connection is secure.
     * This factory creates TLS/SSL socket connections
     * which, by default, are considered secure.
     * <br/>
     * Derived classes may override this method to perform
     * runtime checks, for example based on the cypher suite.
     *
     * @param sock      the connected socket
     *
     * @return  <code>true</code>
     *
     * @throws IllegalArgumentException if the argument is invalid
     */
    public boolean isSecure(final Socket sock) throws IllegalArgumentException {
        if (sock == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        // This instanceof check is in line with createSocket() above.
        if (!(sock instanceof SSLSocket)) {
            throw new IllegalArgumentException("Socket not created by this factory");
        }
        // This check is performed last since it calls the argument object.
        if (sock.isClosed()) {
            throw new IllegalArgumentException("Socket is closed");
        }
        return true;
    }

    /**
     * @since 4.1
     */
    public Socket createLayeredSocket(
            final Socket socket,
            final String host,
            final int port,
            final boolean autoClose) throws IOException, UnknownHostException {
        SSLSocket sslSocket = (SSLSocket) this.socketfactory.createSocket(
                socket,
                host,
                port,
                autoClose
        );
        if (this.hostnameVerifier != null) {
            this.hostnameVerifier.verify(host, sslSocket);
        }
        // verifyHostName() didn't blowup - good!
        return sslSocket;
    }

    @Deprecated
    public void setHostnameVerifier(X509HostnameVerifier hostnameVerifier) {
        if ( hostnameVerifier == null ) {
            throw new IllegalArgumentException("Hostname verifier may not be null");
        }
        this.hostnameVerifier = hostnameVerifier;
    }

    public X509HostnameVerifier getHostnameVerifier() {
        return this.hostnameVerifier;
    }

    /**
     * @deprecated Use {@link #connectSocket(Socket, InetSocketAddress, InetSocketAddress, HttpParams)}
     */
    @Deprecated
    public Socket connectSocket(
            final Socket socket,
            final String host, int port,
            final InetAddress localAddress, int localPort,
            final HttpParams params) throws IOException, UnknownHostException, ConnectTimeoutException {
        InetSocketAddress local = null;
        if (localAddress != null || localPort > 0) {
            // we need to bind explicitly
            if (localPort < 0) {
                localPort = 0; // indicates "any"
            }
            local = new InetSocketAddress(localAddress, localPort);
        }
        InetAddress remoteAddress;
        if (this.nameResolver != null) {
            remoteAddress = this.nameResolver.resolve(host);
        } else {
            remoteAddress = InetAddress.getByName(host);
        }
        InetSocketAddress remote = new InetSocketAddress(remoteAddress, port);
        return connectSocket(socket, remote, local, params);
    }

    /**
     * @deprecated Use {@link #createLayeredSocket(Socket, String, int, boolean)}
     */
    @Deprecated
    public Socket createSocket(
            final Socket socket,
            final String host, int port,
            boolean autoClose) throws IOException, UnknownHostException {
        return createLayeredSocket(socket, host, port, autoClose);
    }

}
