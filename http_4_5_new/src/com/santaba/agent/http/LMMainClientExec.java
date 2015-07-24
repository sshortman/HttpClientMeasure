package com.santaba.agent.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthState;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.BasicRouteDirector;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRouteDirector;
import org.apache.http.conn.routing.RouteTracker;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.auth.HttpAuthenticator;
import org.apache.http.impl.conn.ConnectionShutdownException;
import org.apache.http.impl.execchain.MainClientExec;
import org.apache.http.impl.execchain.RequestAbortedException;
import org.apache.http.impl.execchain.TunnelRefusedException;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.Args;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
public class LMMainClientExec extends MainClientExec {

    private final Log log = LogFactory.getLog(getClass());

    private final HttpRequestExecutor requestExecutor;
    private final HttpClientConnectionManager connManager;
    private final ConnectionReuseStrategy reuseStrategy;
    private final ConnectionKeepAliveStrategy keepAliveStrategy;
    private final HttpProcessor proxyHttpProcessor;
    private final AuthenticationStrategy targetAuthStrategy;
    private final AuthenticationStrategy proxyAuthStrategy;
    private final HttpAuthenticator authenticator;
    private final UserTokenHandler userTokenHandler;
    private final HttpRouteDirector routeDirector;
    public LMMainClientExec(HttpRequestExecutor requestExecutor, HttpClientConnectionManager connManager, ConnectionReuseStrategy reuseStrategy, ConnectionKeepAliveStrategy keepAliveStrategy, HttpProcessor proxyHttpProcessor, AuthenticationStrategy targetAuthStrategy, AuthenticationStrategy proxyAuthStrategy, UserTokenHandler userTokenHandler) {
        super(requestExecutor, connManager, reuseStrategy, keepAliveStrategy, proxyHttpProcessor, targetAuthStrategy, proxyAuthStrategy, userTokenHandler);
        Args.notNull(requestExecutor, "HTTP request executor");
        Args.notNull(connManager, "Client connection manager");
        Args.notNull(reuseStrategy, "Connection reuse strategy");
        Args.notNull(keepAliveStrategy, "Connection keep alive strategy");
        Args.notNull(proxyHttpProcessor, "Proxy HTTP processor");
        Args.notNull(targetAuthStrategy, "Target authentication strategy");
        Args.notNull(proxyAuthStrategy, "Proxy authentication strategy");
        Args.notNull(userTokenHandler, "User token handler");
        this.authenticator      = new HttpAuthenticator();
        this.routeDirector      = new BasicRouteDirector();
        this.requestExecutor    = requestExecutor;
        this.connManager        = connManager;
        this.reuseStrategy      = reuseStrategy;
        this.keepAliveStrategy  = keepAliveStrategy;
        this.proxyHttpProcessor = proxyHttpProcessor;
        this.targetAuthStrategy = targetAuthStrategy;
        this.proxyAuthStrategy  = proxyAuthStrategy;
        this.userTokenHandler   = userTokenHandler;
    }

    @Override
    public CloseableHttpResponse execute(
            final HttpRoute route,
            final HttpRequestWrapper request,
            final HttpClientContext context,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        Metrics.getInstance().startStep(Metrics.STEP_EXECUTE_ALL);
        try {

            Args.notNull(route, "HTTP route");
            Args.notNull(request, "HTTP request");
            Args.notNull(context, "HTTP context");


            final HttpClientConnection managedConn;
            final RequestConfig config = context.getRequestConfig();
            AuthState targetAuthState = context.getTargetAuthState();
            AuthState proxyAuthState = context.getProxyAuthState();
            Object userToken = context.getUserToken();


            Metrics.getInstance().startStep(Metrics.STEP_1);
            try {


                if (targetAuthState == null) {
                    targetAuthState = new AuthState();
                    context.setAttribute(HttpClientContext.TARGET_AUTH_STATE, targetAuthState);
                }

                if (proxyAuthState == null) {
                    proxyAuthState = new AuthState();
                    context.setAttribute(HttpClientContext.PROXY_AUTH_STATE, proxyAuthState);
                }

                if (request instanceof HttpEntityEnclosingRequest) {
                    RequestEntityProxy.enhance((HttpEntityEnclosingRequest) request);
                }


                final ConnectionRequest connRequest = connManager.requestConnection(route, userToken);
                if (execAware != null) {
                    if (execAware.isAborted()) {
                        connRequest.cancel();
                        throw new RequestAbortedException("Request aborted");
                    }
                    else {
                        execAware.setCancellable(connRequest);
                    }
                }


                try {
                    final int timeout = config.getConnectionRequestTimeout();
                    managedConn = connRequest.get(timeout > 0 ? timeout : 0, TimeUnit.MILLISECONDS);
                }
                catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RequestAbortedException("Request aborted", interrupted);
                }
                catch (final ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause == null) {
                        cause = ex;
                    }
                    throw new RequestAbortedException("Request execution failed", cause);
                }
            }
            finally {
                Metrics.getInstance().finishStep(Metrics.STEP_1);
            }

            context.setAttribute(HttpCoreContext.HTTP_CONNECTION, managedConn);

            if (config.isStaleConnectionCheckEnabled()) {
                // validate connection
                if (managedConn.isOpen()) {
                    this.log.debug("Stale connection check");
                    if (managedConn.isStale()) {
                        this.log.debug("Stale connection detected");
                        managedConn.close();
                    }
                }
            }

            final ConnectionHolder connHolder = new ConnectionHolder(this.log, this.connManager, managedConn);
            try {
                if (execAware != null) {
                    execAware.setCancellable(connHolder);
                }

                HttpResponse response;
                for (int execCount = 1; ; execCount++) {

                    Metrics.getInstance().startStep(Metrics.STEP_2);
                    try {

                        if (execCount > 1 && !RequestEntityProxy.isRepeatable(request)) {
                            throw new NonRepeatableRequestException("Cannot retry request " +
                                    "with a non-repeatable request entity.");
                        }

                        if (execAware != null && execAware.isAborted()) {
                            throw new RequestAbortedException("Request aborted");
                        }

                        if (!managedConn.isOpen()) {
                            this.log.debug("Opening connection " + route);
                            try {
                                establishRoute(proxyAuthState, managedConn, route, request, context);
                            }
                            catch (final TunnelRefusedException ex) {
                                if (this.log.isDebugEnabled()) {
                                    this.log.debug(ex.getMessage());
                                }
                                response = ex.getResponse();
                                break;
                            }
                        }
                        final int timeout = config.getSocketTimeout();
                        if (timeout >= 0) {
                            managedConn.setSocketTimeout(timeout);
                        }

                        if (execAware != null && execAware.isAborted()) {
                            throw new RequestAbortedException("Request aborted");
                        }

                        if (this.log.isDebugEnabled()) {
                            this.log.debug("Executing request " + request.getRequestLine());
                        }

                        if (!request.containsHeader(AUTH.WWW_AUTH_RESP)) {
                            if (this.log.isDebugEnabled()) {
                                this.log.debug("Target auth state: " + targetAuthState.getState());
                            }
                            this.authenticator.generateAuthResponse(request, targetAuthState, context);
                        }
                        if (!request.containsHeader(AUTH.PROXY_AUTH_RESP) && !route.isTunnelled()) {
                            if (this.log.isDebugEnabled()) {
                                this.log.debug("Proxy auth state: " + proxyAuthState.getState());
                            }
                            this.authenticator.generateAuthResponse(request, proxyAuthState, context);
                        }
                    }
                    finally {
                        Metrics.getInstance().finishStep(Metrics.STEP_2);
                    }

                    Metrics.getInstance().startStep(Metrics.STEP_5);
                    try {
                        response = requestExecutor.execute(request, managedConn, context);


                        // The connection is in or can be brought to a re-usable state.
                        if (reuseStrategy.keepAlive(response, context)) {
                            // Set the idle duration of this connection
                            final long duration = keepAliveStrategy.getKeepAliveDuration(response, context);
                            if (this.log.isDebugEnabled()) {
                                final String s;
                                if (duration > 0) {
                                    s = "for " + duration + " " + TimeUnit.MILLISECONDS;
                                }
                                else {
                                    s = "indefinitely";
                                }
                                this.log.debug("Connection can be kept alive " + s);
                            }
                            connHolder.setValidFor(duration, TimeUnit.MILLISECONDS);
                            connHolder.markReusable();
                        }
                        else {
                            connHolder.markNonReusable();
                        }

                        if (needAuthentication(
                                targetAuthState, proxyAuthState, route, response, context)) {
                            // Make sure the response body is fully consumed, if present
                            final HttpEntity entity = response.getEntity();
                            if (connHolder.isReusable()) {
                                EntityUtils.consume(entity);
                            }
                            else {
                                managedConn.close();
                                if (proxyAuthState.getState() == AuthProtocolState.SUCCESS
                                        && proxyAuthState.getAuthScheme() != null
                                        && proxyAuthState.getAuthScheme().isConnectionBased()) {
                                    this.log.debug("Resetting proxy auth state");
                                    proxyAuthState.reset();
                                }
                                if (targetAuthState.getState() == AuthProtocolState.SUCCESS
                                        && targetAuthState.getAuthScheme() != null
                                        && targetAuthState.getAuthScheme().isConnectionBased()) {
                                    this.log.debug("Resetting target auth state");
                                    targetAuthState.reset();
                                }
                            }
                            // discard previous auth headers
                            final HttpRequest original = request.getOriginal();
                            if (!original.containsHeader(AUTH.WWW_AUTH_RESP)) {
                                request.removeHeaders(AUTH.WWW_AUTH_RESP);
                            }
                            if (!original.containsHeader(AUTH.PROXY_AUTH_RESP)) {
                                request.removeHeaders(AUTH.PROXY_AUTH_RESP);
                            }
                        }
                        else {
                            break;
                        }
                    }
                    finally {
                        Metrics.getInstance().finishStep(Metrics.STEP_5);
                    }
                }

                Metrics.getInstance().startStep(Metrics.STEP_6);
                try {
                    if (userToken == null) {
                        userToken = userTokenHandler.getUserToken(context);
                        context.setAttribute(HttpClientContext.USER_TOKEN, userToken);
                    }
                    if (userToken != null) {
                        connHolder.setState(userToken);
                    }

                    // check for entity, release connection if possible
                    final HttpEntity entity = response.getEntity();
                    if (entity == null || !entity.isStreaming()) {
                        // connection not needed and (assumed to be) in re-usable state
                        connHolder.releaseConnection();
                        return new HttpResponseProxy(response, null);
                    }
                    else {
                        return new HttpResponseProxy(response, connHolder);
                    }
                }
                finally {
                    Metrics.getInstance().finishStep(Metrics.STEP_6);
                }
            }
            catch (final ConnectionShutdownException ex) {
                final InterruptedIOException ioex = new InterruptedIOException(
                        "Connection has been shut down");
                ioex.initCause(ex);
                throw ioex;
            }
            catch (final HttpException ex) {
                connHolder.abortConnection();
                throw ex;
            }
            catch (final IOException ex) {
                connHolder.abortConnection();
                throw ex;
            }
            catch (final RuntimeException ex) {
                connHolder.abortConnection();
                throw ex;
            }
        }
        finally {
            Metrics.getInstance().finishStep(Metrics.STEP_EXECUTE_ALL);
        }
    }

    private boolean needAuthentication(
            final AuthState targetAuthState,
            final AuthState proxyAuthState,
            final HttpRoute route,
            final HttpResponse response,
            final HttpClientContext context) {
        final RequestConfig config = context.getRequestConfig();
        if (config.isAuthenticationEnabled()) {
            HttpHost target = context.getTargetHost();
            if (target == null) {
                target = route.getTargetHost();
            }
            if (target.getPort() < 0) {
                target = new HttpHost(
                        target.getHostName(),
                        route.getTargetHost().getPort(),
                        target.getSchemeName());
            }
            final boolean targetAuthRequested = this.authenticator.isAuthenticationRequested(
                    target, response, this.targetAuthStrategy, targetAuthState, context);

            HttpHost proxy = route.getProxyHost();
            // if proxy is not set use target host instead
            if (proxy == null) {
                proxy = route.getTargetHost();
            }
            final boolean proxyAuthRequested = this.authenticator.isAuthenticationRequested(
                    proxy, response, this.proxyAuthStrategy, proxyAuthState, context);

            if (targetAuthRequested) {
                return this.authenticator.handleAuthChallenge(target, response,
                        this.targetAuthStrategy, targetAuthState, context);
            }
            if (proxyAuthRequested) {
                return this.authenticator.handleAuthChallenge(proxy, response,
                        this.proxyAuthStrategy, proxyAuthState, context);
            }
        }
        return false;
    }

    /**
     * Establishes the target route.
     */
    void establishRoute(
            final AuthState proxyAuthState,
            final HttpClientConnection managedConn,
            final HttpRoute route,
            final HttpRequest request,
            final HttpClientContext context) throws HttpException, IOException {
        final RequestConfig config = context.getRequestConfig();
        final int timeout = config.getConnectTimeout();
        final RouteTracker tracker = new RouteTracker(route);
        int step;
        do {
            final HttpRoute fact = tracker.toRoute();
            step = this.routeDirector.nextStep(route, fact);

            switch (step) {

                case HttpRouteDirector.CONNECT_TARGET:
                    this.connManager.connect(
                            managedConn,
                            route,
                            timeout > 0 ? timeout : 0,
                            context);
                    tracker.connectTarget(route.isSecure());
                    break;
                case HttpRouteDirector.CONNECT_PROXY:
                    this.connManager.connect(
                            managedConn,
                            route,
                            timeout > 0 ? timeout : 0,
                            context);
                    final HttpHost proxy  = route.getProxyHost();
                    tracker.connectProxy(proxy, false);
                    break;
                case HttpRouteDirector.TUNNEL_TARGET: {
                    final boolean secure = createTunnelToTarget(
                            proxyAuthState, managedConn, route, request, context);
                    this.log.debug("Tunnel to target created.");
                    tracker.tunnelTarget(secure);
                }   break;

                case HttpRouteDirector.TUNNEL_PROXY: {
                    // The most simple example for this case is a proxy chain
                    // of two proxies, where P1 must be tunnelled to P2.
                    // route: Source -> P1 -> P2 -> Target (3 hops)
                    // fact:  Source -> P1 -> Target       (2 hops)
                    final int hop = fact.getHopCount()-1; // the hop to establish
                    final boolean secure = createTunnelToProxy(route, hop, context);
                    this.log.debug("Tunnel to proxy created.");
                    tracker.tunnelProxy(route.getHopTarget(hop), secure);
                }   break;

                case HttpRouteDirector.LAYER_PROTOCOL:
                    this.connManager.upgrade(managedConn, route, context);
                    tracker.layerProtocol(route.isSecure());
                    break;

                case HttpRouteDirector.UNREACHABLE:
                    throw new HttpException("Unable to establish route: " +
                            "planned = " + route + "; current = " + fact);
                case HttpRouteDirector.COMPLETE:
                    this.connManager.routeComplete(managedConn, route, context);
                    break;
                default:
                    throw new IllegalStateException("Unknown step indicator "
                            + step + " from RouteDirector.");
            }

        } while (step > HttpRouteDirector.COMPLETE);
    }

    /**
     * Creates a tunnel to an intermediate proxy.
     * This method is <i>not</i> implemented in this class.
     * It just throws an exception here.
     */
    private boolean createTunnelToProxy(
            final HttpRoute route,
            final int hop,
            final HttpClientContext context) throws HttpException {

        // Have a look at createTunnelToTarget and replicate the parts
        // you need in a custom derived class. If your proxies don't require
        // authentication, it is not too hard. But for the stock version of
        // HttpClient, we cannot make such simplifying assumptions and would
        // have to include proxy authentication code. The HttpComponents team
        // is currently not in a position to support rarely used code of this
        // complexity. Feel free to submit patches that refactor the code in
        // createTunnelToTarget to facilitate re-use for proxy tunnelling.

        throw new HttpException("Proxy chains are not supported.");
    }

    /**
     * Creates a tunnel to the target server.
     * The connection must be established to the (last) proxy.
     * A CONNECT request for tunnelling through the proxy will
     * be created and sent, the response received and checked.
     * This method does <i>not</i> update the connection with
     * information about the tunnel, that is left to the caller.
     */
    private boolean createTunnelToTarget(
            final AuthState proxyAuthState,
            final HttpClientConnection managedConn,
            final HttpRoute route,
            final HttpRequest request,
            final HttpClientContext context) throws HttpException, IOException {

        final RequestConfig config = context.getRequestConfig();
        final int timeout = config.getConnectTimeout();

        final HttpHost target = route.getTargetHost();
        final HttpHost proxy = route.getProxyHost();
        HttpResponse response = null;

        final String authority = target.toHostString();
        final HttpRequest connect = new BasicHttpRequest("CONNECT", authority, request.getProtocolVersion());

        this.requestExecutor.preProcess(connect, this.proxyHttpProcessor, context);

        while (response == null) {
            if (!managedConn.isOpen()) {
                this.connManager.connect(
                        managedConn,
                        route,
                        timeout > 0 ? timeout : 0,
                        context);
            }

            connect.removeHeaders(AUTH.PROXY_AUTH_RESP);
            this.authenticator.generateAuthResponse(connect, proxyAuthState, context);

            response = this.requestExecutor.execute(connect, managedConn, context);

            final int status = response.getStatusLine().getStatusCode();
            if (status < 200) {
                throw new HttpException("Unexpected response to CONNECT request: " +
                        response.getStatusLine());
            }

            if (config.isAuthenticationEnabled()) {
                if (this.authenticator.isAuthenticationRequested(proxy, response,
                        this.proxyAuthStrategy, proxyAuthState, context)) {
                    if (this.authenticator.handleAuthChallenge(proxy, response,
                            this.proxyAuthStrategy, proxyAuthState, context)) {
                        // Retry request
                        if (this.reuseStrategy.keepAlive(response, context)) {
                            this.log.debug("Connection kept alive");
                            // Consume response content
                            final HttpEntity entity = response.getEntity();
                            EntityUtils.consume(entity);
                        } else {
                            managedConn.close();
                        }
                        response = null;
                    }
                }
            }
        }

        final int status = response.getStatusLine().getStatusCode();

        if (status > 299) {

            // Buffer response content
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                response.setEntity(new BufferedHttpEntity(entity));
            }

            managedConn.close();
            throw new TunnelRefusedException("CONNECT refused by proxy: " +
                    response.getStatusLine(), response);
        }

        // How to decide on security of the tunnelled connection?
        // The socket factory knows only about the segment to the proxy.
        // Even if that is secure, the hop to the target may be insecure.
        // Leave it to derived classes, consider insecure by default here.
        return false;
    }
}
