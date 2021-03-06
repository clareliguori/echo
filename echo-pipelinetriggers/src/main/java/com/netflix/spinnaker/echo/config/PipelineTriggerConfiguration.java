package com.netflix.spinnaker.echo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.PubsubEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RestAdapter.LogLevel;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Slf4j
@Configuration
@ComponentScan(value = "com.netflix.spinnaker.echo.pipelinetriggers")
@EnableConfigurationProperties({
  FiatClientConfigurationProperties.class,
  QuietPeriodIndicatorConfigurationProperties.class
})
public class PipelineTriggerConfiguration {
  private Client retrofitClient;
  private RequestInterceptor requestInterceptor;

  @Autowired
  public void setRequestInterceptor(RequestInterceptor spinnakerRequestInterceptor) {
    this.requestInterceptor = spinnakerRequestInterceptor;
  }

  @Autowired
  public void setRetrofitClient(OkHttpClient okHttpClient) {
    this.retrofitClient = new OkClient(okHttpClient);
  }

  @Bean
  public OrcaService orca(@Value("${orca.base-url}") final String endpoint) {
    return bindRetrofitService(OrcaService.class, endpoint);
  }

  @Bean
  public Client retrofitClient() {
    return new OkClient();
  }

  @Bean
  public FiatStatus fiatStatus(
      Registry registry,
      DynamicConfigService dynamicConfigService,
      FiatClientConfigurationProperties fiatClientConfigurationProperties) {
    return new FiatStatus(registry, dynamicConfigService, fiatClientConfigurationProperties);
  }

  @Bean
  @ConditionalOnMissingBean(PubsubEventHandler.class)
  PubsubEventHandler pubsubEventHandler(
      Registry registry,
      ObjectMapper objectMapper,
      FiatPermissionEvaluator fiatPermissionEvaluator) {
    return new PubsubEventHandler(registry, objectMapper, fiatPermissionEvaluator);
  }

  @Bean
  public ExecutorService executorService(
      @Value("${orca.pipeline-initiator-threadpool-size:16}") int threadPoolSize) {
    return Executors.newFixedThreadPool(threadPoolSize);
  }

  private <T> T bindRetrofitService(final Class<T> type, final String endpoint) {
    log.info("Connecting {} to {}", type.getSimpleName(), endpoint);

    return new RestAdapter.Builder()
        .setClient(retrofitClient)
        .setRequestInterceptor(requestInterceptor)
        .setConverter(new JacksonConverter(EchoObjectMapper.getInstance()))
        .setEndpoint(endpoint)
        .setLogLevel(LogLevel.BASIC)
        .setLog(new Slf4jRetrofitLogger(type))
        .build()
        .create(type);
  }
}
