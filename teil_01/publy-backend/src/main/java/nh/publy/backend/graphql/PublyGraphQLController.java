package nh.publy.backend.graphql;

import nh.publy.backend.domain.*;
import nh.publy.backend.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import org.yaml.snakeyaml.nodes.NodeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

@Controller
public class PublyGraphQLController {

  private static final Logger log = LoggerFactory.getLogger(PublyGraphQLController.class);

  private final StoryRepository storyRepository;
  private final MarkdownService markdownService;
  private final String profileImageBaseUrl;
  private final WebClient webClient;
  private final PublyDomainService publyDomainService;
  private final CommentEventPublisher commentEventPublisher;

  public PublyGraphQLController(
    @Value("${publy.userservice.url}") String userServiceUrl,
    @Value("${publy.profileImageBaseUrl}") String profileImageBaseUrl,
    StoryRepository storyRepository,
    MarkdownService markdownService, PublyDomainService publyDomainService, CommentEventPublisher commentEventPublisher) {
    this.storyRepository = storyRepository;
    this.markdownService = markdownService;
    this.publyDomainService = publyDomainService;
    this.profileImageBaseUrl = profileImageBaseUrl;

    this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    this.commentEventPublisher = commentEventPublisher;
  }

  @QueryMapping
  public List<Story> stories() {
    return storyRepository.findAll();
  }

  @QueryMapping
  public Optional<Story> story(@Argument Long id) {
    return storyRepository.findById(id);
  }

  @SchemaMapping
  public String body(Story story) {
    return markdownService.toHtml(story.getBodyMarkdown());
  }

  @SchemaMapping
  public String excerpt(Story story, @Argument int maxLength) {
    var plainBody = markdownService.toPlainText(story.getBodyMarkdown());
    var excerpt = plainBody.length() > maxLength ? plainBody.substring(0, maxLength) + "..." : plainBody;
    return excerpt;
  }

  @SchemaMapping
  public Mono<User> user(Member member) {
    final var userId = member.getUserId();
    return webClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/users/{userId}")
        .build(userId))
      .retrieve().bodyToMono(User.class);
  }

  @SchemaMapping
  public String profileImageUrl(Member member) {
    return format("%s%s", profileImageBaseUrl, member.getProfileImage());
  }

  record AddCommentInput(Long storyId, String content) {}

  @MutationMapping
  @PreAuthorize("hasAnyRole('ROLE_USER')")
  public Comment addComment(@Argument AddCommentInput input) {
      return publyDomainService.addComment(
        input.storyId(),
        input.content()
      );
  }

  @SubscriptionMapping
  public Flux<OnNewCommentEvent> onNewComment(@Argument Long storyId) {
    return this.commentEventPublisher.getPublisher(storyId);
  }
}
