package com.hubspot.singularity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.hubspot.horizon.HttpClient;
import com.hubspot.horizon.HttpRequest;
import com.hubspot.horizon.HttpResponse;
import com.hubspot.singularity.SingularityAgent;
import com.hubspot.singularity.SingularitySlave;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SingularityClientTest {
  @Mock
  private HttpClient httpClient;

  @Mock
  private HttpResponse response;

  @Mock
  private HttpRequest request;

  @Captor
  private ArgumentCaptor<HttpRequest> requestCaptor;

  private SingularityClient singularityClient;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    singularityClient = buildClient();

    when(response.getRequest()).thenReturn(request);
    when(request.getUrl()).thenReturn(new URI("test-url"));
  }

  @Test
  public void itRetriesRequestsThatErrorDueToDeadHost() {
    when(httpClient.execute(any())).thenReturn(response);
    when(response.getStatusCode()).thenReturn(503).thenReturn(200);
    when(response.isServerError()).thenReturn(true).thenReturn(false);

    singularityClient.pauseSingularityRequest("requestId", Optional.empty());

    verify(httpClient, times(2)).execute(requestCaptor.capture());
    HttpRequest sentRequest = requestCaptor.getValue();
    assertThat(sentRequest.getUrl().toString())
      .matches("http://host(1|2)/singularity/v2/api/requests/request/requestId/pause");
  }

  @Test
  public void itThrowsAnExceptionOnServerErrors() {
    when(httpClient.execute(any())).thenReturn(response);
    when(response.getStatusCode()).thenReturn(500);
    when(response.isError()).thenReturn(true);

    assertThatExceptionOfType(SingularityClientException.class)
      .isThrownBy(
        () -> singularityClient.pauseSingularityRequest("requestId", Optional.empty())
      );
  }

  @Test
  public void itGetsSingularitySlaves() {
    SingularityAgent agent = new SingularityAgent(
      "agentId",
      "host",
      "rackId",
      new HashMap<>(),
      Optional.empty()
    );
    when(httpClient.execute(any())).thenReturn(response);
    when(response.getStatusCode()).thenReturn(200);
    when(response.isError()).thenReturn(false);
    when(
        response.getAs(
          ArgumentMatchers.<TypeReference<Collection<SingularityAgent>>>any()
        )
      )
      .thenReturn(ImmutableList.of(agent));

    Collection<SingularitySlave> agents = singularityClient.getSlaves(Optional.empty());
    assertThat(agents).isNotEmpty().hasSize(1);
  }

  private SingularityClient buildClient() {
    return new SingularityClient(
      "singularity/v2/api",
      httpClient,
      ImmutableList.of("host1", "host2"),
      Optional.empty()
    );
  }
}
