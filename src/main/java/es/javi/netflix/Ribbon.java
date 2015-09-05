package es.javi.netflix;

import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.loadbalancer.*;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import rx.Observable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class Ribbon {
    private final ILoadBalancer loadBalancer;

    public Ribbon() {
        loadBalancer = createLoadBalancer(ClientFactory.getNamedConfig("elastic-client"));
    }

    public static void main(String args[]) throws Exception {
        try {
            ConfigurationManager.loadPropertiesFromResources("elastic-client.properties");
            //String servers = ConfigurationManager.getConfigInstance().getProperty("elastic-client.ribbon.listOfServers").toString();
            //System.out.println(servers);

            Ribbon ribbon = new Ribbon();
            System.out.println(ribbon.call(ClientFactory.getNamedConfig("elastic-client")));
            System.out.println(ribbon.getLoadBalancerStats());
        } catch (IOException ioex) {
            System.out.print(ioex);
        }
    }

    public String call(IClientConfig clientConfig) {
        return LoadBalancerCommand.<String>builder()
                .withClientConfig(clientConfig)
                .withLoadBalancer(this.createLoadBalancer(clientConfig))
                .build()
                .submit(server -> {
                    URL url;
                    try {
                        url = new URL(String.format("http://%s:%d/", server.getHost(), server.getPort()));
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        return Observable.just(conn.getResponseMessage());
                    } catch (MalformedURLException mfe) {
                        return Observable.error(mfe);
                    } catch (IOException e) {
                        return Observable.error(e);
                    }
                }).toBlocking().first();
    }

    public ILoadBalancer createLoadBalancer(IClientConfig clientConfig) {
        IRule rule = new AvailabilityFilteringRule();
        return LoadBalancerBuilder.newBuilder()
                .withClientConfig(clientConfig)
                .withRule(rule)
                .withServerListFilter(new ZoneAffinityServerListFilter<>())
                .buildLoadBalancerFromConfigWithReflection();
    }

    public LoadBalancerStats getLoadBalancerStats() {
        return ((BaseLoadBalancer) loadBalancer).getLoadBalancerStats();
    }
}
