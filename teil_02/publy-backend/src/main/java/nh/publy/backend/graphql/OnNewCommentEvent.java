package nh.publy.backend.graphql;

import nh.publy.backend.domain.Comment;

public record OnNewCommentEvent(Long storyId, Comment newComment) {
}
