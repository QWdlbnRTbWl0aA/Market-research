package local.myhome.marketresearch.tools;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("research.models")
public record ModelSettings(Path geminiCredentialsFile,Path openAiCredentialsFile,
        @DefaultValue("gemini-3.5-flash-lite") String geminiModel,
        @DefaultValue("gpt-5.4-mini") String openAiModel,
        @DefaultValue("false") boolean geminiFreeConfirmed) {}
