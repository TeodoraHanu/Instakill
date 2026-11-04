package com.instakill.post.application;

import com.instakill.common.clock.Clock;
import com.instakill.common.error.ForbiddenException;
import com.instakill.common.error.NotFoundException;
import com.instakill.common.id.IdGenerator;
import com.instakill.feed.application.FeedService;
import com.instakill.feed.domain.FeedSort;
import com.instakill.post.domain.Post;
import com.instakill.post.domain.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository posts;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final FeedService feedService;
    private final ImageProcessingService imageProcessingService;
    private final NotificationService notificationService;

    public PostService(PostRepository posts,
                       IdGenerator idGenerator,
                       Clock clock,
                       FeedService feedService,
                       ImageProcessingService imageProcessingService,
                       NotificationService notificationService) {
        this.posts = posts;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.feedService = feedService;
        this.imageProcessingService = imageProcessingService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Post createPost(UUID authorId, String content, String imageUrl) {
        Instant now = clock.now();

        if (imageUrl != null && !imageUrl.isBlank()) {
            try {
                String filename = new File(imageUrl).getName();

                File[] candidateFiles = new File[] {
                        new File("media", filename),
                        new File("uploads", filename),
                        new File(imageUrl),
                        new File(filename)
                };

                Set<String> processedPaths = new HashSet<>();
                boolean processedAny = false;

                for (File file : candidateFiles) {
                    String canonicalPath = file.getCanonicalPath();
                    if (file.exists() && !processedPaths.contains(canonicalPath)) {
                        imageProcessingService.processImage(file, file);
                        processedPaths.add(canonicalPath);
                        System.out.println(">>> OK: Imaginea a fost procesata la: " + canonicalPath);
                        processedAny = true;
                    }
                }

                if (!processedAny) {
                    System.err.println(">>> EROARE: Fisierul nu a fost gasit in niciun folder pentru " + filename);
                }

            } catch (Exception e) {
                System.err.println(">>> EROARE la procesarea imaginii: " + e.getMessage());
                e.printStackTrace();
            }
        }

        Post post = new Post(
                idGenerator.generate(),
                authorId,
                content,
                imageUrl,
                now,
                now,
                0L,
                0L
        );

        Post savedPost = posts.save(post);

        notificationService.sendPostCreatedNotification(authorId.toString(), savedPost.id());

        return savedPost;
    }

    public Post getPost(UUID id) {
        return posts.findById(id).orElseThrow(() -> new NotFoundException("Post not found"));
    }

    @Transactional
    public void deleteOwnPost(UUID postId, UUID requesterId) {
        Post post = getPost(postId);
        if (!post.authorId().equals(requesterId)) {
            throw new ForbiddenException("Cannot delete another user's post");
        }
        posts.deleteById(postId);
    }

    public List<Post> listFeed(FeedSort sort, int page, int size) {
        return feedService.listFeed(sort, page, size);
    }
}