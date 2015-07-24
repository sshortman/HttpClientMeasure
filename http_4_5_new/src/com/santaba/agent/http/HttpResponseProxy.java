package com.santaba.agent.http;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.execchain.*;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.util.Locale;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
/**
 * A proxy class for {@link org.apache.http.HttpResponse} that can be used to release client connection
 * associated with the original response.
 *
 * @since 4.3
 */
@NotThreadSafe
class HttpResponseProxy implements CloseableHttpResponse {

    private final HttpResponse original;
    private final ConnectionHolder connHolder;

    public HttpResponseProxy(final HttpResponse original, final ConnectionHolder connHolder) {
        this.original = original;
        this.connHolder = connHolder;
        ResponseEntityProxy.enchance(original, connHolder);
    }

    @Override
    public void close() throws IOException {
        if (this.connHolder != null) {
            this.connHolder.abortConnection();
        }
    }

    @Override
    public StatusLine getStatusLine() {
        return original.getStatusLine();
    }

    @Override
    public void setStatusLine(final StatusLine statusline) {
        original.setStatusLine(statusline);
    }

    @Override
    public void setStatusLine(final ProtocolVersion ver, final int code) {
        original.setStatusLine(ver, code);
    }

    @Override
    public void setStatusLine(final ProtocolVersion ver, final int code, final String reason) {
        original.setStatusLine(ver, code, reason);
    }

    @Override
    public void setStatusCode(final int code) throws IllegalStateException {
        original.setStatusCode(code);
    }

    @Override
    public void setReasonPhrase(final String reason) throws IllegalStateException {
        original.setReasonPhrase(reason);
    }

    @Override
    public HttpEntity getEntity() {
        return original.getEntity();
    }

    @Override
    public void setEntity(final HttpEntity entity) {
        original.setEntity(entity);
    }

    @Override
    public Locale getLocale() {
        return original.getLocale();
    }

    @Override
    public void setLocale(final Locale loc) {
        original.setLocale(loc);
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return original.getProtocolVersion();
    }

    @Override
    public boolean containsHeader(final String name) {
        return original.containsHeader(name);
    }

    @Override
    public Header[] getHeaders(final String name) {
        return original.getHeaders(name);
    }

    @Override
    public Header getFirstHeader(final String name) {
        return original.getFirstHeader(name);
    }

    @Override
    public Header getLastHeader(final String name) {
        return original.getLastHeader(name);
    }

    @Override
    public Header[] getAllHeaders() {
        return original.getAllHeaders();
    }

    @Override
    public void addHeader(final Header header) {
        original.addHeader(header);
    }

    @Override
    public void addHeader(final String name, final String value) {
        original.addHeader(name, value);
    }

    @Override
    public void setHeader(final Header header) {
        original.setHeader(header);
    }

    @Override
    public void setHeader(final String name, final String value) {
        original.setHeader(name, value);
    }

    @Override
    public void setHeaders(final Header[] headers) {
        original.setHeaders(headers);
    }

    @Override
    public void removeHeader(final Header header) {
        original.removeHeader(header);
    }

    @Override
    public void removeHeaders(final String name) {
        original.removeHeaders(name);
    }

    @Override
    public HeaderIterator headerIterator() {
        return original.headerIterator();
    }

    @Override
    public HeaderIterator headerIterator(final String name) {
        return original.headerIterator(name);
    }

    @Override
    @Deprecated
    public HttpParams getParams() {
        return original.getParams();
    }

    @Override
    @Deprecated
    public void setParams(final HttpParams params) {
        original.setParams(params);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HttpResponseProxy{");
        sb.append(original);
        sb.append('}');
        return sb.toString();
    }

}

