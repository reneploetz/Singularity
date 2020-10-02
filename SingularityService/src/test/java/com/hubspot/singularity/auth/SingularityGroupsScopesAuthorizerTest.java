package com.hubspot.singularity.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.hubspot.singularity.RequestType;
import com.hubspot.singularity.SingularityAuthorizationScope;
import com.hubspot.singularity.SingularityRequest;
import com.hubspot.singularity.SingularityRequestBuilder;
import com.hubspot.singularity.SingularityUser;
import com.hubspot.singularity.config.AuthConfiguration;
import com.hubspot.singularity.config.ScopesConfiguration;
import com.hubspot.singularity.config.UserAuthMode;
import com.squarespace.jersey2.guice.JerseyGuiceUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.ws.rs.WebApplicationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SingularityGroupsScopesAuthorizerTest {

  static {
    JerseyGuiceUtils.install((s, serviceLocator) -> null);
  }

  private static final SingularityUser ADMIN_USER = createSingularityUser(
    "superman",
    "admin",
    "SINGULARITY_ADMIN"
  );

  private static final SingularityUser GROUP_AB_READ_WRITE = createSingularityUser(
    "a",
    ImmutableSet.of("a", "b"),
    ImmutableSet.of("SINGULARITY_READONLY", "SINGULARITY_WRITE")
  );

  private static final SingularityUser GROUP_A_READ_WRITE = createSingularityUser(
    "a",
    Collections.singleton("a"),
    ImmutableSet.of("SINGULARITY_READONLY", "SINGULARITY_WRITE")
  );

  private static final SingularityUser GROUP_B_READ_WRITE = createSingularityUser(
    "b",
    Collections.singleton("b"),
    ImmutableSet.of("SINGULARITY_READONLY", "SINGULARITY_WRITE")
  );

  private static final SingularityUser GROUP_B_READ_ONLY = createSingularityUser(
    "b",
    "b",
    "SINGULARITY_READONLY"
  );

  private static final SingularityUser GROUP_A_READ_ONLY = createSingularityUser(
    "a",
    "a",
    "SINGULARITY_READONLY"
  );

  private static final SingularityUser GROUP_A_WRITE_ONLY = createSingularityUser(
    "a",
    "a",
    "SINGULARITY_WRITE"
  );

  private static final SingularityUser DEFAULT_READ_GROUP = createSingularityUser(
    "a",
    "default-read",
    "SINGULARITY_READONLY"
  );

  private static final SingularityUser JITA_USER_READ = createSingularityUser(
    "a",
    "jita",
    "SINGULARITY_READONLY"
  );

  private static final SingularityRequest GROUP_A_REQUEST = new SingularityRequestBuilder(
    "a",
    RequestType.WORKER
  )
    .setGroup(Optional.of("a"))
    .build();

  private static final SingularityRequest GROUP_A_REQUEST_READ_ONLY_OVERRIDE = new SingularityRequestBuilder(
    "a",
    RequestType.WORKER
  )
    .setGroup(Optional.of("a"))
    .setReadOnlyGroups(Optional.of(Collections.singleton("a")))
    .build();

  private static final SingularityRequest GROUP_A_REQUEST_W_READ_ONLY_B = new SingularityRequestBuilder(
    "a",
    RequestType.WORKER
  )
    .setGroup(Optional.of("a"))
    .setReadOnlyGroups(Optional.of(Collections.singleton("b")))
    .build();

  private static final SingularityRequest GROUP_A_REQUEST_W_READ_WRITE_B = new SingularityRequestBuilder(
    "a",
    RequestType.WORKER
  )
    .setGroup(Optional.of("a"))
    .setReadWriteGroups(Optional.of(Collections.singleton("b")))
    .build();

  private static final SingularityRequestBuilder GROUP_A_REQUEST_PERMISSIONS_BUILDER = new SingularityRequestBuilder(
    "a",
    RequestType.WORKER
  )
  .setGroup(Optional.of("a"));

  protected SingularityGroupsScopesAuthorizer authorizer;

  @BeforeEach
  public void setup() {
    AuthConfiguration authConfiguration = getAuthConfiguration();
    authorizer = new SingularityGroupsScopesAuthorizer(null, authConfiguration);
  }

  @NotNull
  private AuthConfiguration getAuthConfiguration() {
    AuthConfiguration authConfiguration = new AuthConfiguration();
    authConfiguration.setEnabled(true);
    authConfiguration.setAuthMode(UserAuthMode.GROUPS_SCOPES);
    authConfiguration.setJitaGroups(Collections.singleton("jita"));
    authConfiguration.setDefaultReadOnlyGroups(Collections.singleton("default-read"));
    return authConfiguration;
  }

  @Test
  public void itRecognizesAdminUsers() {
    assertTrue(authorizer.hasAdminAuthorization(ADMIN_USER));
    assertFalse(authorizer.hasAdminAuthorization(GROUP_A_WRITE_ONLY));
    assertDoesNotThrow(() -> authorizer.checkAdminAuthorization(ADMIN_USER));
    assertThrows(
      WebApplicationException.class,
      () -> authorizer.checkAdminAuthorization(GROUP_A_WRITE_ONLY)
    );
  }

  @Test
  public void itAllowsJita() {
    assertDoesNotThrow(() -> authorizer.checkReadAuthorization(JITA_USER_READ));
    assertDoesNotThrow(
      () ->
        authorizer.checkForAuthorization(
          GROUP_A_REQUEST,
          JITA_USER_READ,
          SingularityAuthorizationScope.READ
        )
    );
    assertThrows(
      WebApplicationException.class,
      () ->
        authorizer.checkForAuthorization(
          GROUP_A_REQUEST,
          JITA_USER_READ,
          SingularityAuthorizationScope.WRITE
        )
    );
  }

  @Test
  public void itAllowsDefaultReadOnlyUserToReadWithNoOverride() {
    assertDoesNotThrow(() -> authorizer.checkReadAuthorization(DEFAULT_READ_GROUP));
    assertDoesNotThrow(
      () ->
        authorizer.checkForAuthorization(
          GROUP_A_REQUEST,
          DEFAULT_READ_GROUP,
          SingularityAuthorizationScope.READ
        )
    );
  }

  @Test
  public void itDeniesDefaultGroupWhenOverriddenForWriteOrRead() {
    assertThrows(
      WebApplicationException.class,
      () ->
        authorizer.checkForAuthorization(
          GROUP_A_REQUEST_W_READ_ONLY_B,
          DEFAULT_READ_GROUP,
          SingularityAuthorizationScope.READ
        )
    );
    assertDoesNotThrow(
      () ->
        authorizer.checkForAuthorization(
          GROUP_A_REQUEST_W_READ_WRITE_B,
          DEFAULT_READ_GROUP,
          SingularityAuthorizationScope.READ
        )
    );
  }

  @Test
  public void itAllowsAccessWhenInGroupAndDeniesOtherwise() {
    // user and request in group a
    assertAuthorized(
      GROUP_A_REQUEST,
      GROUP_A_READ_ONLY,
      SingularityAuthorizationScope.READ
    );
    assertAuthorized(
      GROUP_A_REQUEST,
      GROUP_A_READ_WRITE,
      SingularityAuthorizationScope.WRITE
    );
    assertAuthorized(
      GROUP_A_REQUEST,
      GROUP_A_WRITE_ONLY,
      SingularityAuthorizationScope.WRITE
    );

    // user a is owner AND is a read only owner
    assertAuthorized(
      GROUP_A_REQUEST_READ_ONLY_OVERRIDE,
      GROUP_A_READ_ONLY,
      SingularityAuthorizationScope.READ
    );
    assertNotAuthorized(
      GROUP_A_REQUEST_READ_ONLY_OVERRIDE,
      GROUP_A_READ_WRITE,
      SingularityAuthorizationScope.WRITE
    );

    // user in b not allowed a
    assertNotAuthorized(
      GROUP_A_REQUEST,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.WRITE
    );
    assertNotAuthorized(
      GROUP_A_REQUEST,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.READ
    );

    // user allowed when in override read group
    assertAuthorized(
      GROUP_A_REQUEST_W_READ_ONLY_B,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.READ
    );
    assertAuthorized(
      GROUP_A_REQUEST_W_READ_ONLY_B,
      GROUP_B_READ_ONLY,
      SingularityAuthorizationScope.READ
    );
    assertNotAuthorized(
      GROUP_A_REQUEST_W_READ_ONLY_B,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.WRITE
    );

    // user allowed read/write when in override write group and has write
    assertAuthorized(
      GROUP_A_REQUEST_W_READ_WRITE_B,
      GROUP_B_READ_ONLY,
      SingularityAuthorizationScope.READ
    );

    assertNotAuthorized(
      GROUP_A_REQUEST_W_READ_WRITE_B,
      GROUP_B_READ_ONLY,
      SingularityAuthorizationScope.WRITE
    );

    assertAuthorized(
      GROUP_A_REQUEST_W_READ_WRITE_B,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.READ
    );

    assertAuthorized(
      GROUP_A_REQUEST_W_READ_WRITE_B,
      GROUP_B_READ_WRITE,
      SingularityAuthorizationScope.WRITE
    );
  }

  @Test
  public void itAllowsChangeOfGroupWhenInOriginal() {
    assertDoesNotThrow(
      () ->
        authorizer.checkForAuthorizedChanges(
          GROUP_A_REQUEST,
          GROUP_A_REQUEST_W_READ_WRITE_B,
          GROUP_AB_READ_WRITE
        )
    );
    assertThrows(
      WebApplicationException.class,
      () ->
        authorizer.checkForAuthorizedChanges(
          GROUP_A_REQUEST_W_READ_WRITE_B,
          GROUP_A_REQUEST,
          GROUP_B_READ_WRITE
        )
    );
  }

  @Test
  public void itChecksDeployScopeIfConfigured() {
    SingularityRequest request = GROUP_A_REQUEST
      .toBuilder()
      .setGroupPermissionOverrides(
        Optional.of(
          Collections.singletonMap(
            "a",
            ImmutableSet.of(SingularityAuthorizationScope.WRITE)
          )
        )
      )
      .build();

    assertAuthorized(request, GROUP_A_READ_WRITE, SingularityAuthorizationScope.READ);
    assertAuthorized(request, GROUP_A_READ_WRITE, SingularityAuthorizationScope.WRITE);

    // By default, no deploy scope is specified, so it falls back to check write scope, which is allowed.
    assertAuthorized(request, GROUP_A_READ_WRITE, SingularityAuthorizationScope.DEPLOY);

    // Build a configuration with a deploy scope specified.
    AuthConfiguration authConfiguration = getAuthConfiguration();
    ScopesConfiguration scopes = new ScopesConfiguration();
    scopes.setDeploy(ImmutableSet.of("SINGULARITY_DEPLOY"));
    authConfiguration.setScopes(scopes);
    authorizer = new SingularityGroupsScopesAuthorizer(null, authConfiguration);

    // Deploys no longer fall back to write access, and user a does not have deploy access.
    assertNotAuthorized(
      request,
      GROUP_A_READ_WRITE,
      SingularityAuthorizationScope.DEPLOY
    );

    // Give user a deploy privileges.
    SingularityUser groupAWithDeployScope = createSingularityUser(
      "a",
      Collections.singleton("a"),
      ImmutableSet.of("SINGULARITY_DEPLOY")
    );
    assertAuthorized(
      request,
      groupAWithDeployScope,
      SingularityAuthorizationScope.DEPLOY
    );
  }

  @Test
  public void itRespectsGroupPermissions() {
    Map<String, Set<SingularityAuthorizationScope>> groupPermissions = new HashMap<>();

    groupPermissions.put("a", ImmutableSet.of(SingularityAuthorizationScope.READ));
    groupPermissions.put("b", ImmutableSet.of(SingularityAuthorizationScope.WRITE));
    groupPermissions.put(
      "c",
      ImmutableSet.of(
        SingularityAuthorizationScope.READ,
        SingularityAuthorizationScope.WRITE
      )
    );
    groupPermissions.put(
      "d",
      ImmutableSet.of(
        SingularityAuthorizationScope.WRITE,
        SingularityAuthorizationScope.DEPLOY
      )
    );
    groupPermissions.put(
      "e",
      ImmutableSet.of(
        SingularityAuthorizationScope.READ,
        SingularityAuthorizationScope.WRITE,
        SingularityAuthorizationScope.DEPLOY
      )
    );

    SingularityRequest request = GROUP_A_REQUEST
      .toBuilder()
      .setGroupPermissionOverrides(Optional.of(groupPermissions))
      .build();

    assertAuthorized(request, GROUP_A_READ_ONLY, SingularityAuthorizationScope.READ);
    assertAuthorized(request, GROUP_A_WRITE_ONLY, SingularityAuthorizationScope.READ);
    assertNotAuthorized(request, GROUP_A_READ_ONLY, SingularityAuthorizationScope.WRITE);

    assertAuthorized(request, GROUP_B_READ_WRITE, SingularityAuthorizationScope.READ);
    assertAuthorized(request, GROUP_B_READ_WRITE, SingularityAuthorizationScope.WRITE);

    // By default, no deploy scope is specified, so it falls back to check write scope, so this is allowed.
    // Same for all following deploy checks below.
    assertAuthorized(request, GROUP_B_READ_WRITE, SingularityAuthorizationScope.DEPLOY);
    assertNotAuthorized(request, GROUP_B_READ_WRITE, SingularityAuthorizationScope.ADMIN);

    SingularityUser groupC = createSingularityUser(
      "c",
      Collections.singleton("c"),
      ImmutableSet.of("SINGULARITY_READONLY", "SINGULARITY_WRITE")
    );
    assertAuthorized(request, groupC, SingularityAuthorizationScope.READ);
    assertAuthorized(request, groupC, SingularityAuthorizationScope.WRITE);
    assertAuthorized(request, groupC, SingularityAuthorizationScope.DEPLOY);
    assertNotAuthorized(request, groupC, SingularityAuthorizationScope.ADMIN);

    SingularityUser groupD = createSingularityUser(
      "d",
      Collections.singleton("d"),
      ImmutableSet.of("SINGULARITY_WRITE", "SINGULARITY_DEPLOY")
    );
    assertAuthorized(request, groupD, SingularityAuthorizationScope.READ);
    assertAuthorized(request, groupD, SingularityAuthorizationScope.WRITE);
    assertAuthorized(request, groupD, SingularityAuthorizationScope.DEPLOY);
    assertNotAuthorized(request, groupD, SingularityAuthorizationScope.ADMIN);

    SingularityUser groupE = createSingularityUser(
      "e",
      Collections.singleton("e"),
      ImmutableSet.of("SINGULARITY_READONLY", "SINGULARITY_WRITE")
    );
    assertAuthorized(request, groupE, SingularityAuthorizationScope.READ);
    assertAuthorized(request, groupE, SingularityAuthorizationScope.WRITE);
    assertAuthorized(request, groupE, SingularityAuthorizationScope.DEPLOY);
    assertNotAuthorized(request, groupE, SingularityAuthorizationScope.ADMIN);

    SingularityUser groupF = createSingularityUser(
      "f",
      Collections.singleton("f"),
      ImmutableSet.of("SINGULARITY_ADMIN")
    );
    assertAuthorized(request, groupF, SingularityAuthorizationScope.READ);
    assertAuthorized(request, groupF, SingularityAuthorizationScope.WRITE);
    assertAuthorized(request, groupF, SingularityAuthorizationScope.DEPLOY);
    assertAuthorized(request, groupF, SingularityAuthorizationScope.ADMIN);

    SingularityUser groupG = createSingularityUser(
      "g",
      Collections.singleton("g"),
      ImmutableSet.of("SINGULARITY_READ")
    );
    assertNotAuthorized(request, groupG, SingularityAuthorizationScope.READ);
    assertNotAuthorized(request, groupG, SingularityAuthorizationScope.WRITE);
    assertNotAuthorized(request, groupG, SingularityAuthorizationScope.DEPLOY);
    assertNotAuthorized(request, groupG, SingularityAuthorizationScope.ADMIN);
  }

  private static SingularityUser createSingularityUser(
    String id,
    String groups,
    String scope
  ) {
    return new SingularityUser(
      id,
      Optional.empty(),
      Optional.empty(),
      Collections.singleton(groups),
      ImmutableSet.of(scope),
      true
    );
  }

  private static SingularityUser createSingularityUser(
    String id,
    Set<String> groups,
    Set<String> scopes
  ) {
    return new SingularityUser(
      id,
      Optional.empty(),
      Optional.empty(),
      groups,
      scopes,
      true
    );
  }

  private void assertAuthorized(
    SingularityRequest request,
    SingularityUser user,
    SingularityAuthorizationScope scope
  ) {
    assertDoesNotThrow(() -> authorizer.checkForAuthorization(request, user, scope));
    assertTrue(authorizer.isAuthorizedForRequest(request, user, scope));
  }

  private void assertNotAuthorized(
    SingularityRequest request,
    SingularityUser user,
    SingularityAuthorizationScope scope
  ) {
    assertThrows(
      WebApplicationException.class,
      () -> authorizer.checkForAuthorization(request, user, scope)
    );
    assertFalse(authorizer.isAuthorizedForRequest(request, user, scope));
  }
}
