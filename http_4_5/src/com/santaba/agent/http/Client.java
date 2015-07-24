package com.santaba.agent.http;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/13/15
 */
@Deprecated
public class Client {

    private final static Options options = new Options();

    static {
        options.addOption("h", "help", false, "Show the help message");
        options.addOption("p", "param", true, "httpclient param, ie, http.useragent=logicmonitor.\n" +
                "example:-p http.useragent=logicmonitor,http.protocol.content-charset=UTF-8");
        options.addOption("header", "header", true, "http request header, ie, connection:close.\n" +
                "example:-header connection:close,test-attribute:test-value");
    }
    public static void main(String[] argv) throws URISyntaxException, IOException, ParseException, KeyManagementException, NoSuchAlgorithmException {
        final AbstractVerifier hv = new AbstractVerifier() {

            public void verify(String s, String as[], String as1[]) throws SSLException {
                try {
                    verify(s, as, as1, false);
                }
                catch (SSLException sslexception) {
                    //sslexception.printStackTrace();
                }
            }

            public final String toString() {
                return "DUMMY_VERIFIER";
            }
        };

        // First create a trust manager that won't care.
        X509TrustManager trustManager = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                // Don't do anything.
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                // Don't do anything.
            }

            public X509Certificate[] getAcceptedIssuers() {
                // Don't do anything.
                return null;
            }

            public boolean isServerTrusted(X509Certificate[] certs) {
                return true;
            }

            public boolean isClientTrusted(X509Certificate[] certs) {
                return true;
            }
        };
        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[]{trustManager}, null);
        LMSSLSocketFactory sf = new LMSSLSocketFactory(sslcontext);
        sf.setHostnameVerifier(hv);

        HttpResponse response = null;
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, argv);

            if (commandLine.hasOption("h")) {
                //System.out.println("Usage:java -jar Client.jar uri -p parameters -h header\n" +
                        //"    java -jar Client https://www.baidu.com -p http.useragent=logicmonitor,,http.protocol.content-charset=UTF-8 -header connection:close");
                return;
            }

            String[] args = commandLine.getArgs();
            if (args.length < 1) {
                //System.err.println("Invalid input, miss uri. \n Usage:java -jar Client.jar uri -p parameters -header header");
                return;
            }

            HttpGet get = new HttpGet();
            HttpParams httpParams = new BasicHttpParams();

            if (commandLine.hasOption("p")) {
                //format: key=value,key1=value1
                String paramStr = commandLine.getOptionValue("p");
                String[] params = StringUtils.split(paramStr, ",");
                if (params.length > 0) {
                    for (String param : params) {
                        String[] p = StringUtils.split(param, "=");
                        if(p.length != 2) {
                            System.err.println("Invalid param input , - " + paramStr);
                            return;
                        }
                        //System.out.println("Adding parameters, key=" + p[0] + ", value=" + p[1]);
                        httpParams.setParameter(p[0], p[1]);
                    }
                }
            }

            if (commandLine.hasOption("header")) {
                //format: key:value,key1:value1
                String headerStr = commandLine.getOptionValue("header");
                String headers[] = StringUtils.split(headerStr, ",");
                if (headers.length > 0) {
                    for (String header : headers) {
                        String[] h = StringUtils.split(header, ":");
                        if (h.length != 2) {
                            //System.err.println("Invalid header input, - " + headerStr);
                            return;
                        }
                        //System.out.println("Adding header, key=" + h[0] + ", value=" + h[1]);
                        get.addHeader(h[0], h[1]);
                    }
                }
            }



            LMDefaultHttpClient client = new LMDefaultHttpClient(getManager(httpParams, sf), httpParams);
            get.setURI(new URI(args[0]));
            Metrics.getInstance().startStep(Metrics.STEP_ALL);
        try{
            response = client.execute(get, new BasicHttpContext());
        }
        finally {
            Metrics.getInstance().finishStep(Metrics.STEP_ALL);
            System.out.println("status=" + (response == null ? 100 : response.getStatusLine().getStatusCode()));
            System.out.println(Metrics.getInstance().toString());
        }

    }




    public static ClientConnectionManager getManager(HttpParams params, SocketFactory factory)
            throws NoSuchAlgorithmException, KeyManagementException {




        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("https", factory, 443));
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        SingleClientConnManager manager = new SingleClientConnManager(params, schemeRegistry);
        return manager;
    }
}
