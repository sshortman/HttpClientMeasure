package com.santaba.agent.http;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.Args;

import java.io.IOException;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
public class LMHttpRequestExecutor extends HttpRequestExecutor {

    private final int waitForContinue = DEFAULT_WAIT_FOR_CONTINUE;
    public int sendRequestCount = 0;
    public long headerResponseTime = 0;
    public long bodyResponseTime = 0;
    long sendRequestEpoch;
    boolean receiveHeader = false;
    public long allResponseTime = 0;
    public long allExecuteTime = 0;
    boolean startSend = true;
    long startEpoch;
    long endEpoch;

    /**
     * Sends the request and obtain a response.
     *
     * @param request   the request to execute.
     * @param conn      the connection over which to execute the request.
     *
     * @return  the response to the request.
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public HttpResponse execute(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) throws IOException, HttpException {
        if (startSend) {
            startSend = false;
            startEpoch = System.currentTimeMillis();
        }
        long startEpoch = System.currentTimeMillis();
        try {
            return super.execute(request, conn, context);
        }
        finally {
            endEpoch = System.currentTimeMillis();
            allResponseTime += (endEpoch - startEpoch);
            sendRequestCount++;
            allExecuteTime = endEpoch - this.startEpoch;
        }
    }

    /**
     * Send the given request over the given connection.
     * <p>
     * This method also handles the expect-continue handshake if necessary.
     * If it does not have to handle an expect-continue handshake, it will
     * not use the connection for reading or anything else that depends on
     * data coming in over the connection.
     *
     * @param request   the request to send, already
     *                  {@link #preProcess preprocessed}
     * @param conn      the connection over which to send the request,
     *                  already established
     * @param context   the context for sending the request
     *
     * @return  a terminal response received as part of an expect-continue
     *          handshake, or
     *          {@code null} if the expect-continue handshake is not used
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    @Override
    protected HttpResponse doSendRequest(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(conn, "Client connection");
        Args.notNull(context, "HTTP context");

        HttpResponse response = null;

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        context.setAttribute(HttpCoreContext.HTTP_REQ_SENT, Boolean.FALSE);

        sendRequestEpoch = System.currentTimeMillis();
        conn.sendRequestHeader(request);
        receiveHeader = true;
        if (request instanceof HttpEntityEnclosingRequest) {
            // Check for expect-continue handshake. We have to flush the
            // headers and wait for an 100-continue response to handle it.
            // If we get a different response, we must not send the entity.
            boolean sendentity = true;
            final ProtocolVersion ver =
                    request.getRequestLine().getProtocolVersion();
            if (((HttpEntityEnclosingRequest) request).expectContinue() &&
                    !ver.lessEquals(HttpVersion.HTTP_1_0)) {

                conn.flush();
                // As suggested by RFC 2616 section 8.2.3, we don't wait for a
                // 100-continue response forever. On timeout, send the entity.
                if (conn.isResponseAvailable(this.waitForContinue)) {
                    response = conn.receiveResponseHeader();
                    if (canResponseHaveBody(request, response)) {
                        conn.receiveResponseEntity(response);
                    }
                    final int status = response.getStatusLine().getStatusCode();
                    if (status < 200) {
                        if (status != HttpStatus.SC_CONTINUE) {
                            throw new ProtocolException(
                                    "Unexpected response: " + response.getStatusLine());
                        }
                        // discard 100-continue
                        response = null;
                    } else {
                        sendentity = false;
                    }
                }
            }
            if (sendentity) {
                conn.sendRequestEntity((HttpEntityEnclosingRequest) request);
            }
        }
        conn.flush();
        context.setAttribute(HttpCoreContext.HTTP_REQ_SENT, Boolean.TRUE);
        return response;
    }

    /**
     * Waits for and receives a response.
     * This method will automatically ignore intermediate responses
     * with status code 1xx.
     *
     * @param request   the request for which to obtain the response
     * @param conn      the connection over which the request was sent
     * @param context   the context for receiving the response
     *
     * @return  the terminal response, not yet post-processed
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    @Override
    protected HttpResponse doReceiveResponse(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(conn, "Client connection");
        Args.notNull(context, "HTTP context");
        HttpResponse response = null;
        int statusCode = 0;

        while (response == null || statusCode < HttpStatus.SC_OK) {

            response = conn.receiveResponseHeader();
            if (canResponseHaveBody(request, response)) {
                conn.receiveResponseEntity(response);
            }
            statusCode = response.getStatusLine().getStatusCode();

        } // while intermediate response
        long responseReceive = System.currentTimeMillis();
        bodyResponseTime += responseReceive - sendRequestEpoch;

        return response;
    }

    /**
     * Decide whether a response comes with an entity.
     * The implementation in this class is based on RFC 2616.
     * <p>
     * Derived executors can override this method to handle
     * methods and response codes not specified in RFC 2616.
     * </p>
     *
     * @param request   the request, to obtain the executed method
     * @param response  the response, to obtain the status code
     */
    protected boolean canResponseHaveBody(final HttpRequest request,
                                          final HttpResponse response) {
        boolean ret = super.canResponseHaveBody(request, response);
        if (ret && receiveHeader) {
            headerResponseTime += System.currentTimeMillis() - sendRequestEpoch;
        }
        return ret;
    }
}
