package nh.publy.backend.graphql;

import graphql.schema.DataFetchingEnvironment;
import nh.publy.backend.domain.*;
import nh.publy.backend.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    MarkdownService markdownService, PublyDomainService publyDomainService, CommentEventPublisher commentEventPublisher,
    BatchLoaderRegistry batchLoaderRegistry) {
    this.storyRepository = storyRepository;
    this.markdownService = markdownService;
    this.publyDomainService = publyDomainService;
    this.profileImageBaseUrl = profileImageBaseUrl;

    this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    this.commentEventPublisher = commentEventPublisher;

    // Laden der User mit DataLoader, Variante 1:
    //   - explizit einen BatchLoader registrieren und implementieren
    //   - den dann in der SchemaMapping-Funktion verwenden (s.u.)
//    batchLoaderRegistry.forTypePair(String.class, User.class)
//      .registerBatchLoader(
//        (List<String> userIds, BatchLoaderEnvironment env) -> {
//          log.info("Loading Users with ids {}", userIds);
//          return webClient.get()
//            .uri(uriBuilder -> uriBuilder
//              .path("/find-users/{userIds}")
//              .build(String.join(",", userIds)))
//            .retrieve()
//            .bodyToFlux(User.class);
//        });
  }

  enum OrderDirection {asc, desc}

  enum StoryOrderField {
    createdAt,
    title
  }

  record StoryOrderCriteria(StoryOrderField field, OrderDirection direction) {
  }

  record StoryPage(List<Story> stories, boolean hasPrevPage,
                   boolean hasNextPage, long totalPages) {
  }

  @QueryMapping
  public StoryPage stories(@Argument @Valid @Min(0) int page,
                           @Argument @Valid @Min(0) @Max(10) int pageSize,
                           @Argument Optional<StoryOrderCriteria> orderBy) {
    var sort = orderBy
      .map(sortOrder -> Sort.by(
        sortOrder.direction() == OrderDirection.asc ? Sort.Direction.ASC : Sort.Direction.DESC,
        sortOrder.field().toString())
      )
      .orElse(Sort.unsorted());

    var pageRequest = PageRequest.of(page, pageSize, sort);
    var result = storyRepository.findAll(pageRequest);

    return new StoryPage(
      result.getContent(),
      result.hasPrevious(),
      result.hasNext(),
      result.getTotalPages()
    );

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


// Laden der User mit DataLoader, Variante 1:
//   - Der oben registrierte DataLoader wird hier verwendet, um die
//     User "gesammelt" zu laden
//  @SchemaMapping
//  public CompletableFuture<User> user(Member member, DataLoader<String, User> userDataLoader) {
//    final var userId = member.getUserId();
//    return userDataLoader.load(userId);
//  }

  // DataLoader Variante 2, mit BatchMapping-Annotation
  @BatchMapping
  public Flux<User> user(List<Member> members) {
    var userIds = members.stream()
      .map(Member::getUserId)
      .collect(Collectors.joining(","));

    return webClient.get()
      .uri(uriBuilder -> uriBuilder
        .path("/find-users/{userIds}")
        .build(userIds))
      .retrieve()
      .bodyToFlux(User.class);
  }


  @SchemaMapping
  public String profileImageUrl(Member member) {
    return format("%s%s", profileImageBaseUrl, member.getProfileImage());
  }

  record AddCommentInput(Long storyId, String content) {
  }

  interface AddCommentPayload {
  }

  record AddCommentSuccessPayload(Comment newComment) implements AddCommentPayload {
  }

  record AddCommentFailedPayload(String errorMessage) implements AddCommentPayload {
  }

  @MutationMapping
  @PreAuthorize("hasAnyRole('ROLE_USER')")
  public AddCommentPayload addComment(@Argument AddCommentInput input) {
    try {
      var newComment = publyDomainService.addComment(
        input.storyId(),
        input.content()
      );
      return new AddCommentSuccessPayload(newComment);
    } catch (CommentCreationFailedException ex) {
      return new AddCommentFailedPayload(ex.getMessage());
    }
  }

  @SubscriptionMapping
  public Flux<OnNewCommentEvent> onNewComment(@Argument Long storyId) {
    return this.commentEventPublisher.getPublisher(storyId);
  }
}
