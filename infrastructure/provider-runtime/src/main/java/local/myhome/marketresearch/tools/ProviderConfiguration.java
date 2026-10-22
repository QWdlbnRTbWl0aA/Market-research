package local.myhome.marketresearch.tools;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;
@Configuration(proxyBeanMethods=false)
public class ProviderConfiguration {
    @Bean public ProviderAccess providerAccess(Environment env,ObjectMapper json){return new ProviderAccess(env,json);}
}
