package com.santaba.agent.http;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * Created with Intellij IDEA.
 * User: Robin
 * Date: 7/23/15
 */
public class LMHttpClients{

    private LMHttpClients() {
        super();
    }

    /**
     * Creates builder object for construction of custom
     * {@link CloseableHttpClient} instances.
     */
    public static LMHttpClientBuilder custom() {
        return LMHttpClientBuilder.create();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration.
     */
    public static CloseableHttpClient createDefault() {
        return HttpClientBuilder.create().build();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration based on ssytem properties.
     */
    public static CloseableHttpClient createSystem() {
        return HttpClientBuilder.create().useSystemProperties().build();
    }

}
