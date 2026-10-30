package posfiscalnfce.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    public static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.system(BRT);
    }
}
