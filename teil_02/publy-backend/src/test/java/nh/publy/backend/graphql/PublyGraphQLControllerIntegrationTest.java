package nh.publy.backend.graphql;

import nh.publy.backend.domain.user.User;
import nh.publy.backend.security.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockito.BDDMockito.given;

@SpringBootTest
@AutoConfigureHttpGraphQlTester
@Testcontainers
@Sql("/stories-test-data.sql")
class PublyGraphQLControllerIntegrationTest {

  @Container
  static JdbcDatabaseContainer<?> database = new PostgreSQLContainer<>("postgres:14")
    .withDatabaseName("springboot")
    .withPassword("springboot")
    .withUsername("springboot")
    .withInitScript("schema.sql");

  @DynamicPropertySource
  static void setDatasourceProperties(DynamicPropertyRegistry propertyRegistry) {
    propertyRegistry.add("spring.datasource.url", database::getJdbcUrl);
    propertyRegistry.add("spring.datasource.password", database::getPassword);
    propertyRegistry.add("spring.datasource.username", database::getUsername);
  }

  @Autowired
  HttpGraphQlTester graphQlTester;

  @MockBean
  AuthenticationService authenticationService;

  private User user = new User("U1", "", "", "", "ROLE_USER");

  @Test
  public void testStoriesField() {
    graphQlTester.document(
        // language=GraphQL
        """
          query {
            stories(page: 1, pageSize: 2,
              orderBy: {direction: desc, field: createdAt}) {
              stories {
                id
                title
              }
            }
          }
          """)
      .execute()
      .path("data.stories.stories[0].title").entity(String.class).isEqualTo("A Story 3")
      .path("data.stories.stories[1].title").entity(String.class).isEqualTo("D Story 2");
  }

  @Test
  public void addComentWithValidUserWorks() {
    given(authenticationService.verify("user-mock-token"))
      .willReturn(user);

    graphQlTester.
      mutate()
      .header("Authorization", "Bearer user-mock-token")
      .build()
      .document(
        // language=GraphQL
        """
          mutation {
            addComment(input: {storyId: "1" content: "Great post"}) {
              ...on AddCommentSuccessPayload {
                newComment {
                  id
                  content        
                }
              }
            }
             
          }
          """)
      .execute()
      .path("data.addComment.newComment.id").hasValue()
      .path("data.addComment.newComment.content").entity(String.class).isEqualTo("Great post");
  }
}