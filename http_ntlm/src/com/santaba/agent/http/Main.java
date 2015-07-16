package com.santaba.agent.http;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/15/15
 */
public class Main {

    /**
     * @param argv host username password domain workstation
     * @throws IOException
     */
    public static void main(String[] argv) throws IOException {
        int status = -1;
        try {
            if (argv.length < 1) {
                return;
            }

            String path = argv[0];
            String username = "";
            String password = "";
            String domain = "";
            String workstation = "";

            if (argv.length > 2) {
                username = argv[1];
            }
            if (argv.length > 2) {
                password = argv[2];
            }

            if (argv.length > 3) {
                domain = argv[3];
            }

            if (argv.length > 4) {
                workstation = argv[4];
            }

            CloseableHttpClient client = HttpClients.createDefault();
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY,
                    new NTCredentials(username, password, workstation, domain));

            //HttpHost host = new HttpHost("10.130.163.112", 8081, "http");
            HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(provider);
            HttpGet get = new HttpGet(path);
            CloseableHttpResponse resp = client.execute(get, context);
            System.out.println(resp.getStatusLine());
            status = resp.getStatusLine().getStatusCode();
        }
        finally {

            System.out.println("status=" + status);
        }

    }
}
