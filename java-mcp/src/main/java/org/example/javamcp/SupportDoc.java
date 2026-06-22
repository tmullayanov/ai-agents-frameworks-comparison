package org.example.javamcp;

import java.util.List;

public record SupportDoc(
        String id,
        String title,
        String service,
        String kind,
        List<String> tags,
        String content
) {
}
