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
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
        LMSSLConnectionSocketFactory factory = new LMSSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient client = getClient(factory);

        HttpGet get = new HttpGet(new URI(argv[0]));

        Long startEpoch = null ;
        CloseableHttpResponse response = null;
        try {
            startEpoch = System.currentTimeMillis();
            response = client.execute(get);
        }
        finally {
            long endEpoch = System.currentTimeMillis();
            long responseTime = startEpoch == null ? -1 : endEpoch - startEpoch;
            int status = (response == null || response.getStatusLine() == null) ? -1 : response.getStatusLine().getStatusCode();
            StringBuilder sb = new StringBuilder();
            sb.append("responseTime=").append(responseTime).append("\n")
                    .append("status=").append(status).append("\n")
                    .append("handShakeCount=").append(factory.handShakeCount).append("\n")
                    .append("handShakeTime=").append(factory.handShakeTime).append("\n")
                    .append("handShakeStatus=").append(factory.handShakeStatus).append("\n");
            System.out.println(sb.toString());
        }


    }

    public static CloseableHttpClient getClient(LMSSLConnectionSocketFactory factory)
            throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {

        Registry<ConnectionSocketFactory> r = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", factory)
                .register("http", new PlainConnectionSocketFactory())
                .build();
        HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(r);

        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(cm)
                .build();
        return client;
    }

}
