/**
 * 检索 HTTP 接口：接收查询、可选文档过滤条件和 Top-K 数量并返回排序结果。
 */
package com.financialresearch.retrieval;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public List<RetrievalResult> search(
            @RequestParam String query,
            @RequestParam(required = false) String documentId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "HYBRID") RetrievalMode mode) {
        return service.search(query, documentId, limit, mode);
    }
}
