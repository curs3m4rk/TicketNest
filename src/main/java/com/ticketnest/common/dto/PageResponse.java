package com.ticketnest.common.dto;

import java.util.List;

/**
 * Generic paginated API response wrapper.
 * Encapsulates Spring Data Page metadata without exposing internals.
 */
public record PageResponse<T>(
        List<T> content,        // Page content for current page
        int pageNumber,         // Zero-based page index
        int pageSize,           // Number of elements per page
        long totalElements,     // Total elements across all pages
        int totalPages,         // Total number of pages
        boolean first,          // Is this the first page?
        boolean last,           // Is this the last page?
        boolean empty           // Is this page empty?
) {}