package com.santaba.agent.http;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.impl.execchain.MainClientExec;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
public class LMHttpClientBuilder extends HttpClientBuilder{

    public static LMHttpClientBuilder create() {
        return new LMHttpClientBuilder();
    }

    /**
     * Produces an instance of {@link ClientExecChain} to be used as a main exec.
     * <p>
     * Default implementation produces an instance of {@link MainClientExec}
     * </p>
     * <p>
     * For internal use.
     * </p>
     *
     * @since 4.4
     */
    @Override
    protected ClientExecChain createMainExec(
            final HttpRequestExecutor requestExec,
            final HttpClientConnectionManager connManager,
            final ConnectionReuseStrategy reuseStrategy,
            final ConnectionKeepAliveStrategy keepAliveStrategy,
            final HttpProcessor proxyHttpProcessor,
            final AuthenticationStrategy targetAuthStrategy,
            final AuthenticationStrategy proxyAuthStrategy,
            final UserTokenHandler userTokenHandler)
    {
        return new LMMainClientExec(
                requestExec,
                connManager,
                reuseStrategy,
                keepAliveStrategy,
                proxyHttpProcessor,
                targetAuthStrategy,
                proxyAuthStrategy,
                userTokenHandler);
    }
}
