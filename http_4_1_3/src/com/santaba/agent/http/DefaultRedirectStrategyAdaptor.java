package com.santaba.agent.http;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.protocol.HttpContext;

import java.net.URI;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
class DefaultRedirectStrategyAdaptor implements RedirectStrategy {

    private final org.apache.http.client.RedirectHandler handler;

    @Deprecated
    public DefaultRedirectStrategyAdaptor(final org.apache.http.client.RedirectHandler handler) {
        super();
        this.handler = handler;
    }

    public boolean isRedirected(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        return this.handler.isRedirectRequested(response, context);
    }

    public HttpUriRequest getRedirect(
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws ProtocolException {
        URI uri = this.handler.getLocationURI(response, context);
        String method = request.getRequestLine().getMethod();
        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            return new HttpHead(uri);
        } else {
            return new HttpGet(uri);
        }
    }

}
