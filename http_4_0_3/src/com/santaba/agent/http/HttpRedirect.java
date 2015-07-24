package com.santaba.agent.http;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;

import java.net.URI;

/**
 * Redirect request (can be either GET or HEAD).
 *
 * @since 4.0
 */
@NotThreadSafe
class HttpRedirect extends HttpRequestBase {

    private String method;

    public HttpRedirect(final String method, final URI uri) {
        super();
        if (method.equalsIgnoreCase(HttpHead.METHOD_NAME)) {
            this.method = HttpHead.METHOD_NAME;
        } else {
            this.method = HttpGet.METHOD_NAME;
        }
        setURI(uri);
    }

    @Override
    public String getMethod() {
        return this.method;
    }

}