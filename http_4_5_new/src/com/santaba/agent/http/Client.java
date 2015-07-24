package com.santaba.agent.http;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/13/15
 */
public class Client {

    private final static Options options = new Options();

    static {
        options.addOption("h", "help", false, "Show the help message");
        options.addOption("p", "param", true, "httpclient param, ie, http.useragent=logicmonitor.\n" +
                "example:-p http.useragent=logicmonitor,http.protocol.content-charset=UTF-8");
        options.addOption("header", "header", true, "http request header, ie, connection:close.\n" +
                "example:-header connection:close,test-attribute:test-value");
    }
    public static void main(String[] argv)
            throws URISyntaxException, IOException, ParseException, KeyManagementException, NoSuchAlgorithmException, KeyStoreException {

        if (argv.length < 1) {
            return;
        }

        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                return true;
            }
        }).build();
        sslContext.getSocketFactory();
        LMSSLConnectionSocketFactory factory = new LMSSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", factory)
                .register("http", new PlainConnectionSocketFactory())
                .build();
        LMManagedHttpClientConnectionFactory lmFactory = new LMManagedHttpClientConnectionFactory();
        HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(r, lmFactory);
        LMHttpRequestExecutor executor = new LMHttpRequestExecutor();

        CloseableHttpClient client = LMHttpClients.custom()
                .setConnectionManager(cm)
                .setRequestExecutor(executor)
                .build();

        HttpGet get = new HttpGet(new URI(argv[0]));

        CloseableHttpResponse response = null;
        Metrics.getInstance().startStep(Metrics.STEP_ALL);
        try {
            response = client.execute(get);
        }
        finally {
            Metrics.getInstance().finishStep(Metrics.STEP_ALL);
            System.out.println("status=" + (response == null ? 100 : response.getStatusLine().getStatusCode()));
            /*
            long responseTime = startEpoch == null ? -1 : endEpoch - startEpoch;
            long headerTime = executor.headerResponseTime;
            long receiveAllBodyTime = executor.bodyResponseTime;
            long requestCount = executor.sendRequestCount;
            long processResponse = startEpoch == null ? -1 : executor.endEpoch - startEpoch;
            int status = (response == null || response.getStatusLine() == null) ? -1 : response.getStatusLine().getStatusCode();
            StringBuilder sb = new StringBuilder();
            sb.append("responseTime=").append(responseTime).append("\n")
                    .append("status=").append(status).append("\n")
                    .append("handShakeCount=").append(factory.handShakeCount).append("\n")
                    .append("handShakeTime=").append(factory.handShakeTime).append("\n")
                    .append("handShakeStatus=").append(factory.handShakeStatus).append("\n")
                    .append("connectCount=").append(factory.connectSocketCount).append("\n")
                    .append("connectTime=").append(factory.connectSocketTime).append("\n")
                    .append("prepareCount=").append(factory.prepareCount).append("\n")
                    .append("prepareTime=").append(factory.prepareTime).append("\n")
                    .append("createSockCount=").append(factory.createSockCount).append("\n")
                    .append("createSockTime=").append(factory.createSockTime).append("\n")
                    .append("headerReadTime=").append(headerTime).append("\n")
                    .append("requestCount=").append(requestCount).append("\n")
                    .append("receiveAllBodyTime=").append(receiveAllBodyTime).append("\n")
                    .append("allResponseTime=").append(executor.allResponseTime).append("\n")
                    .append("allExecuteTime=").append(executor.allExecuteTime).append("\n")
                    .append("processResponse=").append(processResponse).append("\n")
                    .append("connectionCount=").append(lmFactory.connectionCount).append("\n");
                    */

            StringBuilder sb = new StringBuilder();
            for (String stepName : Metrics.getInstance().keySet()) {
                Metrics.Step step = Metrics.getInstance().getStep(stepName);
                sb.append(stepName).append("_ResponseTime=").append(step.responseTime).append("\n")
                        .append(stepName).append("_Count=").append(step.count).append("\n")
                        .append(stepName).append("_MaxTime=").append(step.maxTime).append("\n")
                        .append(stepName).append("_MinTime=").append(step.minTime).append("\n");
            }
            System.out.println(sb.toString());
        }


    }

}
