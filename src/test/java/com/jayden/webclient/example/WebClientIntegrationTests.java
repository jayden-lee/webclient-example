package com.jayden.webclient.example;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientIntegrationTests {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ParameterizedTest(name = "[{index}] webClient [{0}]")
    @MethodSource("arguments")
    @interface ParameterizedWebClientTest {
    }

    static Stream<ClientHttpConnector> arguments() {
        return Stream.of(
                new ReactorClientHttpConnector()
        );
    }

    private MockWebServer server;
    private WebClient webClient;

    private void startServer(ClientHttpConnector connector) {
        server = new MockWebServer();
        webClient = WebClient.builder()
                .clientConnector(connector)
                .baseUrl(this.server.url("/").toString())
                .build();
    }

    @ParameterizedWebClientTest
    void retrieve(ClientHttpConnector connector) {
        startServer(connector);

        prepareResponse(response ->
                response.setHeader("Content-Type", "text/plain").setBody("Hello Spring!"));

        Mono<String> result = this.webClient.get()
                .uri("/greeting")
                .cookie("testkey", "testvalue")
                .header("X-Test-Header", "testvalue")
                .retrieve()
                .bodyToMono(String.class);

        StepVerifier.create(result)
                .expectNext("Hello Spring!")
                .expectComplete()
                .verify(Duration.ofSeconds(3));

        expectRequestCount(1);

        expectRequest(request -> {
            assertThat(request.getHeader(HttpHeaders.COOKIE)).isEqualTo("testkey=testvalue");
            assertThat(request.getHeader("X-Test-Header")).isEqualTo("testvalue");
            assertThat(request.getHeader(HttpHeaders.ACCEPT)).isEqualTo("*/*");
            assertThat(request.getPath()).isEqualTo("/greeting");
        });
    }

    private void prepareResponse(Consumer<MockResponse> consumer) {
        MockResponse response = new MockResponse();
        consumer.accept(response);
        server.enqueue(response);
    }

    private void expectRequestCount(int count) {
        assertThat(server.getRequestCount()).isEqualTo(count);
    }

    private void expectRequest(Consumer<RecordedRequest> consumer) {
        try {
            consumer.accept(server.takeRequest());
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }
}