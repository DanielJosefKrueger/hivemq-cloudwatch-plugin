package com.hivemq.monitoring.cloudwatch;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.blacklocus.metrics.CloudWatchReporter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.hivemq.spi.PluginEntryPoint;
import com.hivemq.spi.annotations.NotNull;
import com.hivemq.spi.services.PluginExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Dominik Obermaier
 */
public class AWSCloudwatchPluginEntryPoint extends PluginEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(AWSCloudwatchPluginEntryPoint.class);


    private final MetricRegistry metricRegistry;
    private final PluginExecutorService pluginExecutorService;
    private final CloudwatchMetricsReader metricsReader;
    private final CloudwatchConfiguration cloudwatchConfiguration;

    @Inject
    public AWSCloudwatchPluginEntryPoint(final MetricRegistry metricRegistry,
                                         final PluginExecutorService pluginExecutorService,
                                         final CloudwatchMetricsReader metricsReader,
                                         final CloudwatchConfiguration cloudwatchConfiguration) {
        this.metricRegistry = metricRegistry;
        this.pluginExecutorService = pluginExecutorService;
        this.metricsReader = metricsReader;
        this.cloudwatchConfiguration = cloudwatchConfiguration;
    }

    @PostConstruct
    public void postConstruct() {

        log.info("Starting CloudWatch Metrics Reporter");

        final List<String> strings = metricsReader.readProperties();

        if (strings.isEmpty()) {
            log.error("Could not start CloudWatch Metrics Reporter because no metrics are defined in the cloudwatch_metrics.properties file");
            return;
        }
        new CloudWatchReporter(
                metricRegistry,
                "hivemq-metrics",
                new ConfiguredMetricsFilter(strings),
                new AmazonCloudWatchAsyncClient(new DefaultAWSCredentialsProviderChain(), new ClientConfiguration()
                        .withConnectionTimeout(cloudwatchConfiguration.getConnectionTimeout()), pluginExecutorService)
        ).start(cloudwatchConfiguration.getReportInterval(), TimeUnit.MINUTES);

    }

    private static class ConfiguredMetricsFilter implements MetricFilter {

        private final Collection<String> metrics;

        public ConfiguredMetricsFilter(@NotNull final Collection<String> metrics) {
            this.metrics = metrics;
            Preconditions.checkNotNull(metrics, "Cloud Metrics must not be null");
        }

        @Override
        public boolean matches(final String name, final Metric metric) {
            return metrics.contains(name);
        }
    }
}
