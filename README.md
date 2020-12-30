# WebClient

Spring 5에 새로 추가된 WebClient는 HTTP 요청을 할 때 사용하는 클라이언트 라이브러리이다. 애플리케이션에서 WebClient를 사용하려면 build.gradle 파일에 `spring-boot-starter-webflux` 선언 하면 자동으로 추가된다. 

![spring-boot-starter-webflux](./images/spring-boot-starter-webflux.png)

요청을 보내기 위해서 내부적으로 HTTP 클라이언트를 사용한다. 기본값은 Reactor Netty의 [HttpClient](https://projectreactor.io/docs/netty/0.8.4.RELEASE/api/reactor/netty/http/client/HttpClient.html) 를 사용한다. Reactor Netty 이외에도 다양한 HttpClient를 지원한다.

- [Reactor Netty](https://github.com/reactor/reactor-netty)
- [Jetty Reactive HttpClient](https://github.com/jetty-project/jetty-reactive-httpclient)
- [Apache HttpComponents](https://hc.apache.org/index.html)

![DefaultWebClientBuilder](./images/DefaultWebClientBuilder.png)

## WebClient 특징
- non-blocking
- asynchronous
- synchronous
- streaming

## RestTemplate에서 WebClient으로 이동
`RestTemplate`은 Spring 3 버전부터 지금까지 자주 사용하는 HTTP 클라이언트 라이브러리이다. 그동안 잘쓰던 `RestTemplate` 에서 `WebClient` 로 변경해야 하는 이유는 `RestTemplate`은 새로운 기능이 더 이상 추가되지 않고 유지보수 모드로 들어가기 때문이다.

![RestTemplate](./images/RestTemplate.png)

기존에 작성된 RestTemplate을 지금 당장 모두 들어내고 WebClient로 교체 할 필요는 없다. 문제 없이 잘 동작 하는 코드는 그대로 두고 비동기&논블록킹을 사용해야 하는 경우에만 WebClient를 적용하자.

## Mono와 Flux
Mono와 Flux는 Reactvie Stream의 [Publisher](https://www.reactive-streams.org/reactive-streams-1.0.0-javadoc/org/reactivestreams/Publisher.html) 인터페이스를 구현한 클래스이다.

- Mono: 0 또는 1개의 결과만을 처리하는 Reactor 객체
- Flux: 0 또는 N개인 여러 개의 결과를 처리하는 Reactor 객체

```java
public interface Publisher<T> {

    public void subscribe(Subscriber<? super T> s);
}
```

## WebClient 예제
 
### WebClient 생성

```
HttpClient httpClient = HttpClient.create()
        .tcpConfiguration(tcpClient ->
                tcpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // connection timeout
                        .doOnConnected(conn -> conn
                                .addHandlerLast(new ReadTimeoutHandler(10)) // read timeout
                                .addHandlerLast(new WriteTimeoutHandler(10)))); // write timeout

WebClient webClient = WebClient.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
```

### Retrieve
`retrieve` 메서드를 사용 하면 ResponseBody를 `Mono` 또는 `Flux` 객체로 바꿔준다. 4xx 또는 5xx 에러를 처리 하려면 `onStatus` 핸들러를 사용한다.

```
WebClient client = WebClient.create("https://example.org");

Mono<Person> result = client.get()
        .uri("/persons/{id}", id).accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(HttpStatus::is4xxClientError, response -> ...)
        .onStatus(HttpStatus::is5xxServerError, response -> ...)
        .bodyToMono(Person.class);
```

### Exchange
`exchange` 메서드는 `retrieve` 메서드처럼 HTTP 호출 결과를 가져 오는 동작은 비슷하지만 섬세한 처리를 할 수 있다는 점이 다르다.
 
```
Mono<Object> entityMono = client.get()
        .uri("/persons/1")
        .accept(MediaType.APPLICATION_JSON)
        .exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.OK)) {
                return response.bodyToMono(Person.class);
            }
            else if (response.statusCode().is4xxClientError()) {
                // Suppress error status code
                return response.bodyToMono(ErrorContainer.class);
            }
            else {
                // Turn to error
                return response.createException().flatMap(Mono::error);
            }
        });
```

### Retrieve vs Exchange
`retrieve`와 `exchange` 메서드 반환 타입은 서로 다르다.

```java
interface RequestHeadersSpec<S extends RequestHeadersSpec<S>> {
    ResponseSpec retrieve();

    Mono<ClientResponse> exchange();
}
```

`ClientResponse` 인터페이스 주석에는 다음과 같은 내용이 있다.

> When using a ClientResponse through the WebClient exchange() method, you have to make sure that the body is consumed or released by using one of the following methods: body(BodyExtractor), bodyToMono(Class)

`DefaultWebClient` 클래스의 retrieve 구현을 보면 내부적으로 `exechange` 메서드를 호출하는 것을 볼 수 있다.

```java
/**
 * Default implementation of {@link WebClient}.
 *
 * @author Rossen Stoyanchev
 * @author Brian Clozel
 * @author Sebastien Deleuze
 * @since 5.0
 */
class DefaultWebClient implements WebClient {

    @Override
    public ResponseSpec retrieve() {
        return new DefaultResponseSpec(exchange(), this::createRequest);
    }
}
```

### Request Body
`bodyValue`, `body` 메서드를 사용해서 Request Body 컨텐츠를 설정할 수 있다. 

```
Person person = new Person(1, "jayden");

Mono<Void> result = client.post()
        .uri("/persons/{id}", id)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(person)
        .retrieve()
        .bodyToMono(Void.class);
```

### Form Data
Form Data를 생성 하는 방법은 두 가지가 있다. 첫 번째로 Body에 `MultiValueMap<String, String>` 값을 넣는 방법이 있으며, 두 번째는 `BodyInserters.fromFormData()`를 이용해서 인라인으로 선언하는 방법이 있다. 

`FormHttpMessageWriter` 클래스가 자동으로 `application/x-www-form-urlencoded`을 content에 붙여준다.

```
MultiValueMap<String, String> formData = ... ;

Mono<Void> result = client.post()
        .uri("/path", id)
        .bodyValue(formData)
        .retrieve()
        .bodyToMono(Void.class);
```

or

```
Mono<Void> result = client.post()
        .uri("/path", id)
        .body(fromFormData("k1", "v1").with("k2", "v2"))
        .retrieve()
        .bodyToMono(Void.class);
```

### Filters

```
WebClient client = WebClient.builder()
        .filter((request, next) -> {

            ClientRequest filtered = ClientRequest.from(request)
                    .header("foo", "bar")
                    .build();

            return next.exchange(filtered);
        })
        .build();
```

### Attributes

```
WebClient client = WebClient.builder()
        .filter((request, next) -> {
            Optional<Object> usr = request.attribute("myAttribute");
            // ...
        })
        .build();

client.get().uri("https://example.org/")
        .attribute("myAttribute", "...")
        .retrieve()
        .bodyToMono(Void.class);
```

### Synchronous
`WebClient`는 블로킹 동기 호출도 지원한다.

```
Person person = client.get().uri("/person/{id}", i).retrieve()
    .bodyToMono(Person.class)
    .block();

List<Person> persons = client.get().uri("/persons").retrieve()
    .bodyToFlux(Person.class)
    .collectList()
    .block();
```

### Testing
`WebClient` 사용 해서 통합 테스트 코드를 작성할 때, [OkHttp Mock Server](https://github.com/square/okhttp#mockwebserver) 와 같은 mock web server가 필요하다. 테스트 코드 예제는 [WebClientIntegrationTests](https://github.com/spring-projects/spring-framework/blob/master/spring-webflux/src/test/java/org/springframework/web/reactive/function/client/WebClientIntegrationTests.java) 를 참고하면 된다.  

## References
- [Spring WebClient Docs](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)
- [Spring WebClient vs. RestTemplate, Baeldung](https://www.baeldung.com/spring-webclient-resttemplate)