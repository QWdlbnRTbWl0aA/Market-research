package local.myhome.marketresearch.tools;
import java.net.*;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

final class ProviderHttp {
    private ProviderHttp() {}
    static RestClient client(String base,Environment env) {
        var factory=new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection,String method) throws java.io.IOException {
                super.prepareConnection(connection,method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(15)); factory.setReadTimeout(Duration.ofSeconds(90));
        String proxy=env.getProperty("HTTPS_PROXY",env.getProperty("HTTP_PROXY",""));
        if (!proxy.isBlank()) {
            URI uri=URI.create(proxy);
            factory.setProxy(new Proxy(Proxy.Type.HTTP,new InetSocketAddress(uri.getHost(),uri.getPort()<0?80:uri.getPort())));
        }
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
}
